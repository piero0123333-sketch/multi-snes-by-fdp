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
 * la memoria de batería (.srm) y el cálculo de progreso (%) + estrella (⭐).
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
            _games.value = stored.map { syncSaveStateFlag(it) }
        }
    }

    suspend fun mergeDiscoveredGames(scannedGames: List<RomGame>) = withContext(Dispatchers.IO) {
        val currentByCrc = _games.value.associateBy { it.crc32Hex }.toMutableMap()

        for (fresh in scannedGames) {
            val existing = currentByCrc[fresh.crc32Hex]
            if (existing != null) {
                currentByCrc[fresh.crc32Hex] = syncSaveStateFlag(
                    existing.copy(
                        sourcePathOrUri = fresh.sourcePathOrUri,
                        zipEntryName = fresh.zipEntryName,
                        coverImagePath = fresh.coverImagePath ?: existing.coverImagePath
                    )
                )
            } else {
                currentByCrc[fresh.crc32Hex] = syncSaveStateFlag(fresh)
            }
        }

        val merged = currentByCrc.values.sortedBy { it.title }
        _games.value = merged
        persistCatalog(merged)
    }

    suspend fun recordSessionPlaytime(crc32Hex: String, elapsedSeconds: Long) = withContext(Dispatchers.IO) {
        if (elapsedSeconds <= 0) return@withContext
        updateGame(crc32Hex) { game ->
            val updatedSeconds = game.totalPlayTimeSeconds + elapsedSeconds
            val computedPct = if (game.isMarkedCompleted) {
                100
            } else {
                val baseFromTime = ((updatedSeconds * 100L) / (5L * 3600L)).toInt()
                val milestoneBonus = if (hasQuickSaveFile(crc32Hex)) 5 else 0
                min(99, maxOf(game.progressPercent, baseFromTime + milestoneBonus, 1))
            }
            game.copy(
                totalPlayTimeSeconds = updatedSeconds,
                progressPercent = computedPct,
                hasSaveState = hasQuickSaveFile(crc32Hex)
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
     * TAREA 2: Guarda en la cola rotativa FIFO de 10 slots (slot_1.state .. slot_10.state + miniatura .png)
     * y devuelve la información del slot escrito (o null en caso de error).
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
     * TAREA 2 (Modo 1 - Carga Directa): Carga inmediatamente el último estado guardado (latest_slot).
     */
    suspend fun loadLatestQuickState(crc32Hex: String): Pair<SaveStateManager.SaveSlotInfo, ByteArray>? =
        withContext(Dispatchers.IO) {
            saveStateManager.loadLatestState(crc32Hex)
        }

    suspend fun loadQuickState(crc32Hex: String): ByteArray? = withContext(Dispatchers.IO) {
        saveStateManager.loadLatestState(crc32Hex)?.second
    }

    /**
     * TAREA 2 (Modo 2 - Carga por Selector): Carga un slot específico (1..10) elegido por el usuario.
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
     * Guardado automático síncrono al salir del juego o cerrar/minimizar la aplicación,
     * garantizando que tanto el Save State como la SRAM (.srm) y el porcentaje de progreso
     * queden persistidos en disco de inmediato para acceso rápido al volver a entrar.
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
        val savedSlot = if (stateBytes != null && stateBytes.isNotEmpty()) {
            saveStateManager.saveStateRotating(crc32Hex, stateBytes, screenshot)
        } else {
            null
        }
        updateGame(crc32Hex) { game ->
            val updatedSeconds = game.totalPlayTimeSeconds + elapsedSeconds.coerceAtLeast(0L)
            val hasSaveNow = savedSlot != null || hasQuickSaveFile(crc32Hex)
            val computedPct = if (game.isMarkedCompleted) {
                100
            } else {
                val baseFromTime = ((updatedSeconds * 100L) / (5L * 3600L)).toInt()
                val milestoneBonus = if (hasSaveNow) 5 else 0
                min(99, maxOf(game.progressPercent, baseFromTime + milestoneBonus, if (hasSaveNow) 5 else 1))
            }
            game.copy(
                totalPlayTimeSeconds = updatedSeconds,
                progressPercent = computedPct,
                hasSaveState = hasSaveNow
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

    private fun hasQuickSaveFile(crc32Hex: String): Boolean =
        saveStateManager.hasAnySavedState(crc32Hex)

    private fun syncSaveStateFlag(game: RomGame): RomGame =
        game.copy(hasSaveState = hasQuickSaveFile(game.crc32Hex))

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
