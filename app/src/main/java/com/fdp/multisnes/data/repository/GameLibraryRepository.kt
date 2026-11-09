package com.fdp.multisnes.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.fdp.multisnes.data.model.RomGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.min

/**
 * Gestiona el catálogo de ROMs, el Sistema de Guardado Rotativo de 10 Slots (SaveStateManager),
 * el Autoguardado Invisible al Salir (autosave_exit.state), la memoria de batería (.srm)
 * y el cálculo de progreso (%) + estrella (⭐).
 */
class GameLibraryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val saveStateManager = SaveStateManager(context)
    private val catalogFile = File(context.filesDir, "catalog_v1.json")
    private val sramDir = File(context.filesDir, "sram").apply { mkdirs() }

    private val _games = MutableStateFlow<List<RomGame>>(emptyList())
    val games: StateFlow<List<RomGame>> = _games.asStateFlow()

    init {
        loadSavedCatalogSync()
    }

    private fun loadSavedCatalogSync() {
        if (!catalogFile.exists()) return
        runCatching {
            val raw = catalogFile.readText()
            val stored = json.decodeFromString<List<RomGame>>(raw)
            _games.value = stored.map { syncSaveStateFlags(it) }
        }
    }

    suspend fun mergeDiscoveredGames(scannedGames: List<RomGame>) = withContext(Dispatchers.IO) {
        val currentByCrc = _games.value.associateBy { it.crc32Hex }.toMutableMap()

        for (fresh in scannedGames) {
            val existing = currentByCrc[fresh.crc32Hex]
            if (existing != null) {
                currentByCrc[fresh.crc32Hex] = syncSaveStateFlags(
                    existing.copy(
                        sourcePathOrUri = fresh.sourcePathOrUri,
                        zipEntryName = fresh.zipEntryName,
                        coverImagePath = fresh.coverImagePath ?: existing.coverImagePath
                    )
                )
            } else {
                currentByCrc[fresh.crc32Hex] = syncSaveStateFlags(fresh)
            }
        }

        val merged = currentByCrc.values.sortedBy { it.title }
        _games.value = merged
        persistCatalog(merged)
    }

    /**
     * Inserta o actualiza inmediatamente un juego individual (ej. al abrir una ROM o parche desde "Abrir con...").
     */
    @Synchronized
    fun upsertSingleGameSync(game: RomGame): RomGame {
        val synced = syncSaveStateFlags(game)
        val currentByCrc = _games.value.associateBy { it.crc32Hex }.toMutableMap()
        val existing = currentByCrc[synced.crc32Hex]
        val finalGame = if (existing != null) {
            syncSaveStateFlags(
                existing.copy(
                    title = synced.title,
                    fullInternalTitle = synced.fullInternalTitle,
                    sourcePathOrUri = synced.sourcePathOrUri,
                    zipEntryName = synced.zipEntryName,
                    coverImagePath = synced.coverImagePath ?: existing.coverImagePath
                )
            )
        } else {
            synced
        }
        currentByCrc[finalGame.crc32Hex] = finalGame
        val merged = currentByCrc.values.sortedBy { it.title }
        _games.value = merged
        persistCatalog(merged)
        return finalGame
    }

    suspend fun recordSessionPlaytime(crc32Hex: String, elapsedSeconds: Long) = withContext(Dispatchers.IO) {
        if (elapsedSeconds <= 0) return@withContext
        updateGame(crc32Hex) { game ->
            val updatedSeconds = game.totalPlayTimeSeconds + elapsedSeconds
            val hasManual = hasManualSaveSlots(crc32Hex)
            val hasAuto = hasAutoExitSave(crc32Hex)
            val computedPct = if (game.isMarkedCompleted) {
                100
            } else {
                val baseFromTime = ((updatedSeconds * 100L) / (5L * 3600L)).toInt()
                val milestoneBonus = if (hasManual || hasAuto) 5 else 0
                min(99, maxOf(game.progressPercent, baseFromTime + milestoneBonus, 1))
            }
            game.copy(
                totalPlayTimeSeconds = updatedSeconds,
                progressPercent = computedPct,
                hasSaveState = hasManual,
                hasAutoSaveExit = hasAuto,
                autoSaveExitDate = saveStateManager.getAutoExitStateInfo(crc32Hex)?.formattedDate
            )
        }
    }

    suspend fun toggleCompletedStar(crc32Hex: String) = withContext(Dispatchers.IO) {
        updateGame(crc32Hex) { game ->
            val nextCompleted = !game.isMarkedCompleted
            game.copy(
                isMarkedCompleted = nextCompleted,
                progressPercent = if (nextCompleted) 100 else game.progressPercent.coerceAtMost(96)
            )
        }
    }

    /**
     * Guarda un estado MANUAL en la cola rotativa FIFO de 10 slots (slot_1.state .. slot_10.state + miniatura .png).
     */
    suspend fun saveRotatingState(
        crc32Hex: String,
        stateBytes: ByteArray,
        screenshot: Bitmap? = null
    ): SaveStateManager.SaveSlotInfo? = withContext(Dispatchers.IO) {
        if (stateBytes.isEmpty()) return@withContext null
        val savedSlot = saveStateManager.saveStateRotating(crc32Hex, stateBytes, screenshot)
        if (savedSlot != null) {
            updateGame(crc32Hex) { game ->
                game.copy(
                    hasSaveState = true,
                    progressPercent = maxOf(game.progressPercent, 5)
                )
            }
        }
        savedSlot
    }

    suspend fun saveQuickState(crc32Hex: String, stateBytes: ByteArray): Boolean =
        saveRotatingState(crc32Hex, stateBytes, null) != null

    /**
     * Carga directa del último estado guardado manual (latest_slot).
     */
    suspend fun loadLatestQuickState(crc32Hex: String): Pair<SaveStateManager.SaveSlotInfo, ByteArray>? =
        withContext(Dispatchers.IO) {
            saveStateManager.loadLatestState(crc32Hex)
        }

    suspend fun loadQuickState(crc32Hex: String): ByteArray? = withContext(Dispatchers.IO) {
        saveStateManager.loadLatestState(crc32Hex)?.second
            ?: saveStateManager.loadAutoExitStateBytes(crc32Hex)
    }

    /**
     * MÓDULO 2: Carga directamente el archivo reservado autosave_exit.state ("CONTINUAR DONDE QUEDASTE").
     */
    suspend fun loadAutoExitState(crc32Hex: String): ByteArray? = withContext(Dispatchers.IO) {
        saveStateManager.loadAutoExitStateBytes(crc32Hex)
    }

    /**
     * Carga un slot manual específico (1..10) elegido por el usuario en el selector.
     */
    suspend fun loadQuickStateSlot(crc32Hex: String, slotNumber: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            saveStateManager.loadSlotBytes(crc32Hex, slotNumber)
        }

    fun listSaveSlotsNewestFirst(crc32Hex: String): List<SaveStateManager.SaveSlotInfo> =
        saveStateManager.listSavedSlotsNewestFirst(crc32Hex)

    fun getLatestSlotNumber(crc32Hex: String): Int =
        saveStateManager.getMetadata(crc32Hex).latestSlotNumber

    /**
     * MÓDULO 2: Autoguardado Invisible al Salir (onPause, onStop y Salir al menú).
     * Guarda automáticamente en autosave_exit.state (totalmente separado de las 10 ranuras manuales
     * para no sobrescribirlas jamás) + SRAM (.srm) + porcentaje de progreso.
     */
    @Synchronized
    fun saveAutoProgressSync(
        crc32Hex: String,
        stateBytes: ByteArray?,
        screenshot: Bitmap?,
        sramBytes: ByteArray?,
        elapsedSeconds: Long
    ) {
        if (sramBytes != null && sramBytes.isNotEmpty()) {
            runCatching { sramFile(crc32Hex).writeBytes(sramBytes) }
        }
        val autoExitInfo = if (stateBytes != null && stateBytes.isNotEmpty()) {
            saveStateManager.saveAutoExitState(crc32Hex, stateBytes, screenshot)
        } else {
            saveStateManager.getAutoExitStateInfo(crc32Hex)
        }
        updateGame(crc32Hex) { game ->
            val updatedSeconds = game.totalPlayTimeSeconds + elapsedSeconds.coerceAtLeast(0L)
            val hasManual = hasManualSaveSlots(crc32Hex)
            val hasAuto = autoExitInfo != null || hasAutoExitSave(crc32Hex)
            val hasAnySave = hasManual || hasAuto
            val computedPct = if (game.isMarkedCompleted) {
                100
            } else {
                val baseFromTime = ((updatedSeconds * 100L) / (5L * 3600L)).toInt()
                val milestoneBonus = if (hasAnySave) 5 else 0
                min(99, maxOf(game.progressPercent, baseFromTime + milestoneBonus, if (hasAnySave) 5 else 1))
            }
            game.copy(
                totalPlayTimeSeconds = updatedSeconds,
                progressPercent = computedPct,
                hasSaveState = hasManual,
                hasAutoSaveExit = hasAuto,
                autoSaveExitDate = autoExitInfo?.formattedDate ?: game.autoSaveExitDate
            )
        }
    }

    suspend fun saveSram(crc32Hex: String, sramBytes: ByteArray?) = withContext(Dispatchers.IO) {
        if (sramBytes == null || sramBytes.isEmpty()) return@withContext
        runCatching {
            sramFile(crc32Hex).writeBytes(sramBytes)
        }
    }

    suspend fun loadSram(crc32Hex: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = sramFile(crc32Hex)
        if (f.exists() && f.length() > 0) runCatching { f.readBytes() }.getOrNull() else null
    }

    fun findByCrc32(crc32Hex: String): RomGame? =
        _games.value.firstOrNull { it.crc32Hex.equals(crc32Hex, ignoreCase = true) }

    fun updateCoverPath(crc32Hex: String, coverPath: String) {
        updateGame(crc32Hex) { game ->
            if (game.coverImagePath == coverPath) game
            else game.copy(coverImagePath = coverPath)
        }
    }

    private fun hasManualSaveSlots(crc32Hex: String): Boolean =
        saveStateManager.hasAnySavedState(crc32Hex)

    private fun hasAutoExitSave(crc32Hex: String): Boolean =
        saveStateManager.hasAutoExitState(crc32Hex)

    private fun syncSaveStateFlags(game: RomGame): RomGame {
        val hasManual = hasManualSaveSlots(game.crc32Hex)
        val autoInfo = saveStateManager.getAutoExitStateInfo(game.crc32Hex)
        return game.copy(
            hasSaveState = hasManual,
            hasAutoSaveExit = autoInfo != null,
            autoSaveExitDate = autoInfo?.formattedDate
        )
    }

    private fun sramFile(crc32Hex: String): File =
        File(sramDir, "${crc32Hex.uppercase()}.srm")

    private fun updateGame(crc32Hex: String, transform: (RomGame) -> RomGame) {
        val updated = _games.value.map {
            if (it.crc32Hex.equals(crc32Hex, ignoreCase = true)) transform(it) else it
        }
        _games.value = updated
        persistCatalog(updated)
    }

    private fun persistCatalog(list: List<RomGame>) {
        runCatching {
            catalogFile.writeText(json.encodeToString(list))
        }
    }
}
