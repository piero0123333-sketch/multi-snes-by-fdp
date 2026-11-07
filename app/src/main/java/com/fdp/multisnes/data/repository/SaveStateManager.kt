package com.fdp.multisnes.data.repository

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TAREA 2: Gestor de Guardado Rotativo (Cola Circular FIFO de Máximo 10 Slots por juego)
 * y Doble Modo de Carga (Carga Directa del último estado vs Selector Modal de los 10 slots).
 */
class SaveStateManager(private val context: Context) {

    companion object {
        const val MAX_SLOTS_PER_GAME = 10
    }

    @Serializable
    data class SaveSlotInfo(
        val slotNumber: Int,
        val timestampEpochMs: Long,
        val formattedDate: String,
        val stateFileName: String,
        val screenshotPath: String? = null
    )

    @Serializable
    data class GameSlotsMetadata(
        val gameKey: String,
        val latestSlotNumber: Int = 0,
        val slots: List<SaveSlotInfo> = emptyList()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val rootDir = File(context.filesDir, "savestates").apply { mkdirs() }
    private val legacyQuickStateDir = File(context.filesDir, "saves")

    private fun gameDir(gameKey: String): File {
        val safeKey = gameKey.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "default_rom" }
        return File(rootDir, safeKey).apply { mkdirs() }
    }

    private fun metadataFile(gameKey: String): File = File(gameDir(gameKey), "metadata.json")

    @Synchronized
    fun getMetadata(gameKey: String): GameSlotsMetadata {
        val dir = gameDir(gameKey)
        val metaFile = metadataFile(gameKey)
        if (metaFile.exists()) {
            runCatching {
                val parsed = json.decodeFromString<GameSlotsMetadata>(metaFile.readText())
                val validSlots = parsed.slots.filter { slot ->
                    File(dir, slot.stateFileName).exists()
                }
                if (validSlots.size != parsed.slots.size) {
                    val newest = validSlots.maxByOrNull { it.timestampEpochMs }?.slotNumber ?: 0
                    val cleaned = parsed.copy(latestSlotNumber = newest, slots = validSlots)
                    saveMetadataLocked(gameKey, cleaned)
                    return cleaned
                }
                return parsed
            }
        }

        // Migración automática si existía un guardado único antiguo en quickstates/<crc32>.state
        val legacyFile = File(legacyQuickStateDir, "${gameKey.uppercase()}.state")
        if (legacyFile.exists() && legacyFile.length() > 0L) {
            runCatching {
                val slot1File = File(dir, "slot_1.state")
                legacyFile.copyTo(slot1File, overwrite = true)
                val ts = legacyFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                val slotInfo = SaveSlotInfo(
                    slotNumber = 1,
                    timestampEpochMs = ts,
                    formattedDate = formatTimestamp(ts),
                    stateFileName = "slot_1.state",
                    screenshotPath = null
                )
                val migrated = GameSlotsMetadata(
                    gameKey = gameKey,
                    latestSlotNumber = 1,
                    slots = listOf(slotInfo)
                )
                saveMetadataLocked(gameKey, migrated)
                return migrated
            }
        }

        return GameSlotsMetadata(gameKey = gameKey)
    }

    /**
     * Guarda un nuevo estado en la cola circular de 10 ranuras:
     * - Si hay menos de 10 guardados, ocupa la primera ranura libre (1..10).
     * - Si ya existen los 10 guardados, sobrescribe automáticamente el MÁS ANTIGUO (rotación FIFO)
     *   y marca la ranura recién escrita como latest_slot (latestSlotNumber).
     */
    @Synchronized
    fun saveStateRotating(
        gameKey: String,
        stateBytes: ByteArray,
        screenshot: Bitmap? = null
    ): SaveSlotInfo? {
        if (stateBytes.isEmpty()) return null
        return runCatching {
            val dir = gameDir(gameKey)
            val currentMeta = getMetadata(gameKey)
            val existingBySlot = currentMeta.slots.associateBy { it.slotNumber }.toMutableMap()

            // Determinar qué slot (1..10) usar: primero libre o el más antiguo (FIFO)
            val targetSlotNumber = (1..MAX_SLOTS_PER_GAME).firstOrNull { !existingBySlot.containsKey(it) }
                ?: currentMeta.slots.minByOrNull { it.timestampEpochMs }?.slotNumber
                ?: 1

            val stateFileName = "slot_${targetSlotNumber}.state"
            val stateFile = File(dir, stateFileName)
            stateFile.writeBytes(stateBytes)

            var screenshotPath: String? = null
            if (screenshot != null) {
                val pngFile = File(dir, "slot_${targetSlotNumber}.png")
                runCatching {
                    FileOutputStream(pngFile).use { out ->
                        screenshot.compress(Bitmap.CompressFormat.PNG, 90, out)
                        out.flush()
                    }
                    screenshotPath = pngFile.absolutePath
                }
            }

            val now = System.currentTimeMillis()
            val newSlotInfo = SaveSlotInfo(
                slotNumber = targetSlotNumber,
                timestampEpochMs = now,
                formattedDate = formatTimestamp(now),
                stateFileName = stateFileName,
                screenshotPath = screenshotPath
            )

            existingBySlot[targetSlotNumber] = newSlotInfo
            val sortedNewestFirst = existingBySlot.values.sortedByDescending { it.timestampEpochMs }
            val updatedMeta = GameSlotsMetadata(
                gameKey = gameKey,
                latestSlotNumber = targetSlotNumber,
                slots = sortedNewestFirst
            )
            saveMetadataLocked(gameKey, updatedMeta)
            newSlotInfo
        }.getOrNull()
    }

    /**
     * Devuelve la lista de hasta 10 ranuras guardadas para este juego,
     * ordenadas siempre de la MÁS RECIENTE a la MÁS ANTIGUA.
     */
    @Synchronized
    fun listSavedSlotsNewestFirst(gameKey: String): List<SaveSlotInfo> {
        return getMetadata(gameKey).slots.sortedByDescending { it.timestampEpochMs }
    }

    /**
     * Modo 1 — Carga Directa (HUD in-game o botón físico del mando):
     * Carga inmediatamente y sin preguntar el último estado guardado (latest_slot).
     */
    @Synchronized
    fun loadLatestState(gameKey: String): Pair<SaveSlotInfo, ByteArray>? {
        val meta = getMetadata(gameKey)
        if (meta.slots.isEmpty()) return null
        val latestSlot = meta.slots.firstOrNull { it.slotNumber == meta.latestSlotNumber }
            ?: meta.slots.maxByOrNull { it.timestampEpochMs }
            ?: return null
        val bytes = loadSlotBytes(gameKey, latestSlot.slotNumber) ?: return null
        return latestSlot to bytes
    }

    /**
     * Modo 2 — Carga de una ranura específica elegida desde el selector de 10 estados ("CARGAR R.").
     */
    @Synchronized
    fun loadSlotBytes(gameKey: String, slotNumber: Int): ByteArray? {
        if (slotNumber !in 1..MAX_SLOTS_PER_GAME) return null
        val dir = gameDir(gameKey)
        val meta = getMetadata(gameKey)
        val slotInfo = meta.slots.firstOrNull { it.slotNumber == slotNumber }
        val file = if (slotInfo != null) {
            File(dir, slotInfo.stateFileName)
        } else {
            File(dir, "slot_${slotNumber}.state")
        }
        if (!file.exists()) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    @Synchronized
    fun hasAnySavedState(gameKey: String): Boolean {
        return getMetadata(gameKey).slots.isNotEmpty()
    }

    private fun saveMetadataLocked(gameKey: String, metadata: GameSlotsMetadata) {
        runCatching {
            metadataFile(gameKey).writeText(json.encodeToString(metadata))
        }
    }

    private fun formatTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }
}
