package com.fdp.multisnes.ui.viewmodel

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fdp.multisnes.MultiSnesApp
import com.fdp.multisnes.data.model.MacroSlotConfig
import com.fdp.multisnes.data.model.RomGame
import com.fdp.multisnes.data.model.ScreenScaleMode
import com.fdp.multisnes.data.model.TouchControlsLayout
import com.fdp.multisnes.data.repository.SaveStateManager
import com.fdp.multisnes.data.rom.SnesRomHeaderParser
import com.fdp.multisnes.netplay.HotspotRollbackCoordinator
import com.fdp.multisnes.ui.screens.HotspotLobbyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreenRoute {
    CATALOG,
    ONLINE_HOTSPOT,
    EMULATOR_GAMEPLAY
}

class MultiSnesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MultiSnesApp
    val nativeBridge = app.nativeBridge
    val games = app.libraryRepository.games
    val settings = app.settingsRepository.settings

    private val _route = MutableStateFlow(AppScreenRoute.CATALOG)
    val route: StateFlow<AppScreenRoute> = _route.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedLaunchGame = MutableStateFlow<RomGame?>(null)
    val selectedLaunchGame: StateFlow<RomGame?> = _selectedLaunchGame.asStateFlow()

    private val _activeGame = MutableStateFlow<RomGame?>(null)
    val activeGame: StateFlow<RomGame?> = _activeGame.asStateFlow()

    private val _showHerramientas = MutableStateFlow(false)
    val showHerramientas: StateFlow<Boolean> = _showHerramientas.asStateFlow()

    private val _showKeyMapper = MutableStateFlow(false)
    val showKeyMapper: StateFlow<Boolean> = _showKeyMapper.asStateFlow()

    private val _showMacroConfig = MutableStateFlow(false)
    val showMacroConfig: StateFlow<Boolean> = _showMacroConfig.asStateFlow()

    private val _activeMacroStates = MutableStateFlow(BooleanArray(4))
    val activeMacroStates: StateFlow<BooleanArray> = _activeMacroStates.asStateFlow()

    private val _isEditingButtons = MutableStateFlow(false)
    val isEditingButtons: StateFlow<Boolean> = _isEditingButtons.asStateFlow()

    private val _isFastForward = MutableStateFlow(false)
    val isFastForward: StateFlow<Boolean> = _isFastForward.asStateFlow()

    private val _isRewinding = MutableStateFlow(false)
    val isRewinding: StateFlow<Boolean> = _isRewinding.asStateFlow()

    // Selector modal de los 10 slots rotativos (TAREA 2)
    private val _slotPickerGame = MutableStateFlow<RomGame?>(null)
    val slotPickerGame: StateFlow<RomGame?> = _slotPickerGame.asStateFlow()

    private val _slotPickerSlots = MutableStateFlow<List<SaveStateManager.SaveSlotInfo>>(emptyList())
    val slotPickerSlots: StateFlow<List<SaveStateManager.SaveSlotInfo>> = _slotPickerSlots.asStateFlow()

    private val _slotPickerLatestSlot = MutableStateFlow(0)
    val slotPickerLatestSlot: StateFlow<Int> = _slotPickerLatestSlot.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Estados del modo ONLINE Hotspot 2 Jugadores
    private val _lobbyStatus = MutableStateFlow(HotspotLobbyStatus.IDLE)
    val lobbyStatus: StateFlow<HotspotLobbyStatus> = _lobbyStatus.asStateFlow()

    private val _onlineStatusText = MutableStateFlow("")
    val onlineStatusText: StateFlow<String> = _onlineStatusText.asStateFlow()

    private val _localHotspotIp = MutableStateFlow("")
    val localHotspotIp: StateFlow<String> = _localHotspotIp.asStateFlow()

    private val _activeCoordinator = MutableStateFlow<HotspotRollbackCoordinator?>(null)
    val activeCoordinator: StateFlow<HotspotRollbackCoordinator?> = _activeCoordinator.asStateFlow()

    private var lobbyJob: Job? = null
    private var bannerJob: Job? = null
    private var sessionStartEpochMs: Long = 0L
    private var lastAutoSaveEpochMs: Long = 0L

    private var touchButtonsMask = 0
    private var hardwareButtonsMask = 0

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = app.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Vibrator::class.java)
        }
    }

    init {
        app.macroEngine.setStateListener { activeArray ->
            _activeMacroStates.value = activeArray.clone()
        }
        viewModelScope.launch {
            settings.collect { cfg ->
                nativeBridge.audioPlayer.setSoundConfig(cfg.soundEnabled, cfg.soundVolume)
            }
        }
    }

    // --- Navegación y Escaneo Automático de ROMs ---

    fun openOnlineScreen() {
        refreshDetectedIp()
        _route.value = AppScreenRoute.ONLINE_HOTSPOT
    }

    fun openCatalogScreen() {
        cancelOnlineLobby()
        _route.value = AppScreenRoute.CATALOG
    }

    fun triggerAutoRomScan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val found = app.romScanner.scanAllSources(settings.value.safRomTreeUris)
            app.libraryRepository.mergeDiscoveredGames(found)
            _isScanning.value = false
            showTransientBanner("Juegos detectados: ${games.value.size}")
        }
    }

    fun addSafFolderAndScan(treeUriString: String) {
        app.settingsRepository.addSafTreeUri(treeUriString)
        triggerAutoRomScan()
    }

    fun onGameCardClicked(game: RomGame) {
        _selectedLaunchGame.value = game
    }

    fun dismissLaunchDialog() {
        _selectedLaunchGame.value = null
    }

    fun toggleCompletedStar(game: RomGame) {
        viewModelScope.launch {
            app.libraryRepository.toggleCompletedStar(game.crc32Hex)
            _selectedLaunchGame.value = app.libraryRepository.findByCrc32(game.crc32Hex)
        }
    }

    // --- Arranque de Partida Individual (JUGAR vs CARGAR PROGRESO de Imagen 4) ---

    fun launchSinglePlayer(game: RomGame, loadSavedProgress: Boolean, specificSlotNumber: Int? = null) {
        _selectedLaunchGame.value = null
        _slotPickerGame.value = null
        viewModelScope.launch {
            val payload = withContext(Dispatchers.IO) {
                SnesRomHeaderParser.loadRomPayload(app, game.sourcePathOrUri, game.zipEntryName)
            }
            if (payload == null) {
                showTransientBanner("No se pudo leer el archivo ROM")
                return@launch
            }

            val sram = app.libraryRepository.loadSram(game.crc32Hex)
            val quickState = when {
                specificSlotNumber != null -> app.libraryRepository.loadQuickStateSlot(game.crc32Hex, specificSlotNumber)
                loadSavedProgress -> app.libraryRepository.loadQuickState(game.crc32Hex)
                else -> null
            }

            nativeBridge.netplayHook = null
            _activeCoordinator.value = null
            app.macroEngine.stopAllMacros()
            app.macroEngine.setActivePlayerPort(0)

            val started = nativeBridge.startSession(
                romNameHint = payload.fileNameHint,
                romBytes = payload.bytes,
                initialSram = sram,
                initialQuickState = quickState,
                isOnlineSession = false
            )

            if (started) {
                _activeGame.value = game
                _isFastForward.value = false
                _isRewinding.value = false
                sessionStartEpochMs = System.currentTimeMillis()
                _route.value = AppScreenRoute.EMULATOR_GAMEPLAY
                if (specificSlotNumber != null && quickState != null) {
                    showTransientBanner("Slot #$specificSlotNumber cargado (${game.progressPercent}%)")
                } else if (loadSavedProgress && quickState != null) {
                    showTransientBanner("Progreso cargado (${game.progressPercent}%)")
                }
            }
        }
    }

    // --- Modo ONLINE Hotspot 2 Jugadores (<10 cm, Cero Lag, Rollback UDP) ---

    fun startCreateHostFlow() {
        refreshDetectedIp()
        _lobbyStatus.value = HotspotLobbyStatus.SELECTING_HOST_GAME
    }

    fun launchHostWaitingRoom(game: RomGame) {
        lobbyJob?.cancel()
        refreshDetectedIp()
        _lobbyStatus.value = HotspotLobbyStatus.HOST_WAITING_GUEST
        _onlineStatusText.value = "SALA CREADA: ${game.title}\nESPERANDO INVITADO EN HOTSPOT..."

        lobbyJob = viewModelScope.launch {
            val payload = withContext(Dispatchers.IO) {
                SnesRomHeaderParser.loadRomPayload(app, game.sourcePathOrUri, game.zipEntryName)
            }
            if (payload == null) {
                _onlineStatusText.value = "Error abriendo ROM ${game.title}"
                _lobbyStatus.value = HotspotLobbyStatus.IDLE
                return@launch
            }

            // Pre-inicializar núcleo para generar snapshot determinista de Frame 0
            nativeBridge.initializeCore()
            val sram = app.libraryRepository.loadSram(game.crc32Hex)

            val handshake = app.hotspotTransport.hostWaitForGuest(
                hostName = Build.MODEL ?: "HOST",
                romCrc32 = game.crc32Hex,
                gameTitle = game.title,
                initialStateProvider = { ByteArray(0) }
            )

            if (handshake == null) {
                if (_lobbyStatus.value == HotspotLobbyStatus.HOST_WAITING_GUEST) {
                    _onlineStatusText.value = "No se completó la sincronización (verifica misma ROM en ambos celulares)."
                    _lobbyStatus.value = HotspotLobbyStatus.IDLE
                }
                return@launch
            }

            startSynchronizedOnlineMatch(
                game = game,
                romFileName = payload.fileNameHint,
                romBytes = payload.bytes,
                sram = sram,
                initialState = handshake.initialSyncState,
                isHostPlayer1 = true
            )
        }
    }

    fun startGuestAutoJoinFlow() {
        lobbyJob?.cancel()
        refreshDetectedIp()
        _lobbyStatus.value = HotspotLobbyStatus.GUEST_SEARCHING_HOST
        _onlineStatusText.value = "BUSCANDO PARTIDA EN EL HOTSPOT (<10 CM)..."

        lobbyJob = viewModelScope.launch {
            val discovered = app.hotspotTransport.guestDiscoverHostRoom(timeoutMs = 20_000L)
            if (discovered == null) {
                if (_lobbyStatus.value == HotspotLobbyStatus.GUEST_SEARCHING_HOST) {
                    _onlineStatusText.value = "No se detectó sala automática. Verifica estar conectado al Hotspot del Host o usa su IP."
                }
                return@launch
            }

            connectGuestToHostRoom(discovered.hostIp, discovered.romCrc32, discovered.gameTitle)
        }
    }

    fun connectGuestManualIp(hostIp: String) {
        if (hostIp.isBlank()) return
        lobbyJob?.cancel()
        _lobbyStatus.value = HotspotLobbyStatus.GUEST_SEARCHING_HOST
        _onlineStatusText.value = "CONECTANDO DIRECTO A $hostIp..."

        lobbyJob = viewModelScope.launch {
            val firstGame = games.value.firstOrNull()
            if (firstGame == null) {
                _onlineStatusText.value = "Escanea primero tus ROMs con BUSCAR JUEGOS (AUTO)."
                _lobbyStatus.value = HotspotLobbyStatus.IDLE
                return@launch
            }
            // Probar coincidencia con el catálogo local
            for (candidate in games.value) {
                val ok = connectGuestToHostRoom(hostIp, candidate.crc32Hex, candidate.title)
                if (ok) return@launch
            }
            _onlineStatusText.value = "No se encontró en tu catálogo la misma ROM que eligió el Host."
            _lobbyStatus.value = HotspotLobbyStatus.IDLE
        }
    }

    private suspend fun connectGuestToHostRoom(
        hostIp: String,
        expectedCrc32: String,
        hostGameTitle: String
    ): Boolean {
        val matchingGame = app.libraryRepository.findByCrc32(expectedCrc32)
        if (matchingGame == null) {
            _onlineStatusText.value = "Sala encontrada ($hostGameTitle), pero falta esa ROM en este celular."
            _lobbyStatus.value = HotspotLobbyStatus.IDLE
            return false
        }

        _onlineStatusText.value = "SINCRONIZANDO ${matchingGame.title} CON $hostIp..."
        val handshake = app.hotspotTransport.guestConnectAndSync(hostIp, matchingGame.crc32Hex)
            ?: return false

        val payload = withContext(Dispatchers.IO) {
            SnesRomHeaderParser.loadRomPayload(app, matchingGame.sourcePathOrUri, matchingGame.zipEntryName)
        } ?: return false

        startSynchronizedOnlineMatch(
            game = matchingGame,
            romFileName = payload.fileNameHint,
            romBytes = payload.bytes,
            sram = null,
            initialState = handshake.initialSyncState,
            isHostPlayer1 = false
        )
        return true
    }

    private fun startSynchronizedOnlineMatch(
        game: RomGame,
        romFileName: String,
        romBytes: ByteArray,
        sram: ByteArray?,
        initialState: ByteArray,
        isHostPlayer1: Boolean
    ) {
        val coordinator = HotspotRollbackCoordinator(
            nativeBridge = nativeBridge,
            transport = app.hotspotTransport,
            isHostPlayer1 = isHostPlayer1
        )
        nativeBridge.netplayHook = coordinator
        _activeCoordinator.value = coordinator
        app.macroEngine.stopAllMacros()
        app.macroEngine.setActivePlayerPort(if (isHostPlayer1) 0 else 1)

        val started = nativeBridge.startSession(
            romNameHint = romFileName,
            romBytes = romBytes,
            initialSram = sram,
            initialQuickState = initialState.takeIf { it.isNotEmpty() },
            isOnlineSession = true
        )

        if (started) {
            _lobbyStatus.value = HotspotLobbyStatus.IDLE
            _onlineStatusText.value = ""
            _activeGame.value = game
            _isFastForward.value = false
            sessionStartEpochMs = System.currentTimeMillis()
            _route.value = AppScreenRoute.EMULATOR_GAMEPLAY
            showTransientBanner(
                if (isHostPlayer1) "Conectado: Eres Jugador 1 (Host)"
                else "Conectado: Eres Jugador 2 (Invitado)"
            )
        }
    }

    fun cancelOnlineLobby() {
        lobbyJob?.cancel()
        lobbyJob = null
        app.hotspotTransport.stopAll()
        _lobbyStatus.value = HotspotLobbyStatus.IDLE
        _onlineStatusText.value = ""
    }

    private fun refreshDetectedIp() {
        val ep = app.networkHelper.detectActiveEndpoints().firstOrNull()
        _localHotspotIp.value = ep?.localIp ?: "192.168.43.1"
    }

    // --- Controles en Partida, Guardado Rápido, Turbo y HERRAMIENTAS ---

    fun onTouchMaskUpdated(newTouchMask: Int) {
        val newlyPressed = newTouchMask and touchButtonsMask.inv()
        touchButtonsMask = newTouchMask
        if (newlyPressed != 0) {
            triggerHapticTick()
        }
        pushCombinedInputMask()
    }

    fun onHardwareMaskUpdated(newHardwareMask: Int) {
        if (!settings.value.externalControllerEnabled) {
            hardwareButtonsMask = 0
        } else {
            hardwareButtonsMask = newHardwareMask
        }
        pushCombinedInputMask()
    }

    private fun pushCombinedInputMask() {
        val combined = touchButtonsMask or hardwareButtonsMask
        val coordinator = _activeCoordinator.value
        if (coordinator != null) {
            coordinator.updateLocalButtons(combined)
        } else {
            nativeBridge.setLocalPlayerInput(0, combined)
        }
    }

    private fun triggerHapticTick() {
        val cfg = settings.value
        if (!cfg.vibrationEnabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val amplitude = (cfg.vibrationIntensity * 255f).toInt().coerceIn(1, 255)
        runCatching {
            vib.vibrate(VibrationEffect.createOneShot(11L, amplitude))
        }
    }

    /**
     * TAREA 2: Guardado Rotativo en Cola Circular FIFO de máximo 10 slots por juego + miniatura .png.
     */
    fun performQuickSave() {
        val game = _activeGame.value ?: return
        viewModelScope.launch {
            val stateBytes = nativeBridge.saveStateBytes()
            val screenshot = nativeBridge.captureCurrentScreenshotBitmap()
            val savedSlot = if (stateBytes != null) {
                app.libraryRepository.saveRotatingState(game.crc32Hex, stateBytes, screenshot)
            } else {
                null
            }
            if ( savedSlot != null) {
                lastAutoSaveEpochMs = System.currentTimeMillis()
                _activeGame.value = app.libraryRepository.findByCrc32(game.crc32Hex)
                val totalCount = app.libraryRepository.listSaveSlotsNewestFirst(game.crc32Hex).size
                showTransientBanner("Guardado en Slot #${savedSlot.slotNumber} ($totalCount/10)")
            } else {
                showTransientBanner("Error al guardar estado")
            }
        }
    }

    /**
     * TAREA 2 (Modo 1 - Carga Directa desde HUD in-game o botón físico del mando):
     * Carga inmediatamente y sin preguntar el último estado guardado (latest_slot).
     */
    fun performQuickLoad() {
        val game = _activeGame.value ?: return
        if (_activeCoordinator.value != null) {
            showTransientBanner("Carga rápida deshabilitada durante Online 2J")
            return
        }
        viewModelScope.launch {
            val latest = app.libraryRepository.loadLatestQuickState(game.crc32Hex)
            if (latest != null && nativeBridge.loadStateBytes(latest.second)) {
                showTransientBanner("Cargado último estado (Slot #${latest.first.slotNumber})")
            } else {
                showTransientBanner("No hay Guardado Rápido previo")
            }
        }
    }

    /**
     * TAREA 2 (Modo 2 - Carga con Selector desde el Menú HERRAMIENTAS -> "CARGAR R."):
     * Despliega el modal que lista los hasta 10 estados guardados (ordenados del más reciente al más antiguo).
     */
    fun openSaveSlotPickerFromTools() {
        val game = _activeGame.value ?: return
        if (_activeCoordinator.value != null) {
            showTransientBanner("Carga rápida deshabilitada durante Online 2J")
            return
        }
        _showHerramientas.value = false
        nativeBridge.setPaused(true)
        _slotPickerSlots.value = app.libraryRepository.listSaveSlotsNewestFirst(game.crc32Hex)
        _slotPickerLatestSlot.value = app.libraryRepository.getLatestSlotNumber(game.crc32Hex)
        _slotPickerGame.value = game
    }

    /**
     * TAREA 2 (Modo 2 - Carga con Selector desde el Catálogo -> "CARGAR PROGRESO"):
     * Despliega el modal con los hasta 10 estados guardados para elegir cuál iniciar.
     */
    fun openSaveSlotPickerFromCatalog(game: RomGame) {
        _selectedLaunchGame.value = null
        val slots = app.libraryRepository.listSaveSlotsNewestFirst(game.crc32Hex)
        if (slots.size <= 1) {
            launchSinglePlayer(game, loadSavedProgress = true)
            return
        }
        _slotPickerSlots.value = slots
        _slotPickerLatestSlot.value = app.libraryRepository.getLatestSlotNumber(game.crc32Hex)
        _slotPickerGame.value = game
    }

    fun closeSaveSlotPicker() {
        _slotPickerGame.value = null
        if (_route.value == AppScreenRoute.EMULATOR_GAMEPLAY &&
            !_showHerramientas.value &&
            !_isEditingButtons.value
        ) {
            nativeBridge.setPaused(false)
        }
    }

    fun loadSelectedSlot(slotNumber: Int) {
        val targetGame = _slotPickerGame.value ?: return
        _slotPickerGame.value = null

        if (_route.value == AppScreenRoute.EMULATOR_GAMEPLAY &&
            _activeGame.value?.crc32Hex == targetGame.crc32Hex
        ) {
            viewModelScope.launch {
                val bytes = app.libraryRepository.loadQuickStateSlot(targetGame.crc32Hex, slotNumber)
                if (bytes != null && nativeBridge.loadStateBytes(bytes)) {
                    showTransientBanner("Slot #$slotNumber cargado")
                } else {
                    showTransientBanner("No se pudo cargar el Slot #$slotNumber")
                }
                if (!_showHerramientas.value && !_isEditingButtons.value) {
                    nativeBridge.setPaused(false)
                }
            }
        } else {
            launchSinglePlayer(targetGame, loadSavedProgress = true, specificSlotNumber = slotNumber)
        }
    }

    /**
     * TAREA 3: Motor de Rewind (Retroceder el Tiempo en RAM mientras se mantiene presionado).
     */
    fun setRewinding(active: Boolean) {
        if (_activeCoordinator.value != null) {
            _isRewinding.value = false
            nativeBridge.setRewinding(false)
            return
        }
        if (_isRewinding.value == active) return
        _isRewinding.value = active
        nativeBridge.setRewinding(active)
        if (active) {
            showTransientBanner("⏪ REBOBINANDO TIEMPO...")
        }
    }

    fun toggleFastForward() {
        if (_activeCoordinator.value != null) return
        val next = nativeBridge.toggleFastForward()
        _isFastForward.value = next
        showTransientBanner(if (next) "Velocidad Turbo x2 ACTIVADA" else "Velocidad Original 1x")
    }

    fun openHerramientasOverlay() {
        if (_route.value == AppScreenRoute.EMULATOR_GAMEPLAY) {
            nativeBridge.setPaused(true)
        }
        _showHerramientas.value = true
    }

    fun closeHerramientasOverlay() {
        _showHerramientas.value = false
        if (_route.value == AppScreenRoute.EMULATOR_GAMEPLAY && !_isEditingButtons.value) {
            nativeBridge.setPaused(false)
        }
    }

    fun startMoveButtonsEditor() {
        _showHerramientas.value = false
        nativeBridge.setPaused(true)
        _isEditingButtons.value = true
    }

    fun finishMoveButtonsEditor() {
        _isEditingButtons.value = false
        nativeBridge.setPaused(false)
    }

    /**
     * Guardado automático inmediato al salir al catálogo, minimizar o cerrar la app mientras se está en partida.
     * Persiste el estado actual en el último slot rotativo + miniatura + SRAM (.srm) + porcentaje de progreso.
     */
    fun autoSaveProgressIfInGame() {
        if (_route.value != AppScreenRoute.EMULATOR_GAMEPLAY) return
        val currentGame = _activeGame.value ?: return
        val now = System.currentTimeMillis()
        if (now - lastAutoSaveEpochMs < 1500L) return

        val stateBytes = if (_activeCoordinator.value == null) nativeBridge.saveStateBytes() else null
        val screenshot = if (_activeCoordinator.value == null) nativeBridge.captureCurrentScreenshotBitmap() else null
        val sramBytes = nativeBridge.readSramBytes()
        val elapsedSec = ((now - sessionStartEpochMs) / 1000L).coerceAtLeast(1L)
        sessionStartEpochMs = now
        lastAutoSaveEpochMs = now

        app.libraryRepository.saveAutoProgressSync(
            crc32Hex = currentGame.crc32Hex,
            stateBytes = stateBytes,
            screenshot = screenshot,
            sramBytes = sramBytes,
            elapsedSeconds = elapsedSec
        )
        _activeGame.value = app.libraryRepository.findByCrc32(currentGame.crc32Hex)
    }

    fun exitGameToCatalog() {
        _showHerramientas.value = false
        _isEditingButtons.value = false
        app.macroEngine.stopAllMacros()

        val wasOnline = _activeCoordinator.value != null
        _activeCoordinator.value?.sendDisconnectNotice()
        _activeCoordinator.value = null
        nativeBridge.netplayHook = null
        app.hotspotTransport.stopAll()

        val finishedGame = _activeGame.value
        val now = System.currentTimeMillis()
        val shouldCaptureState = !wasOnline && (now - lastAutoSaveEpochMs >= 1500L)
        val finalStateBytes = if (shouldCaptureState && finishedGame != null) nativeBridge.saveStateBytes() else null
        val finalScreenshot = if (shouldCaptureState && finishedGame != null) nativeBridge.captureCurrentScreenshotBitmap() else null
        val finalSram = nativeBridge.stopSession()
        val elapsedSec = ((now - sessionStartEpochMs) / 1000L).coerceAtLeast(1L)
        lastAutoSaveEpochMs = now

        _activeGame.value = null
        touchButtonsMask = 0
        hardwareButtonsMask = 0
        _route.value = AppScreenRoute.CATALOG

        if (finishedGame != null) {
            app.libraryRepository.saveAutoProgressSync(
                crc32Hex = finishedGame.crc32Hex,
                stateBytes = finalStateBytes,
                screenshot = finalScreenshot,
                sramBytes = finalSram,
                elapsedSeconds = elapsedSec
            )
            showTransientBanner("Progreso guardado automáticamente")
        }
    }

    // --- Acciones del Modal HERRAMIENTAS (Imagen 2) y Sistema de Macros (Módulo 3) ---

    fun setDpadEnabled(v: Boolean) = app.settingsRepository.setDpadEnabled(v)
    fun setJoystickEnabled(v: Boolean) = app.settingsRepository.setJoystickEnabled(v)
    fun setExternalControllerEnabled(v: Boolean) = app.settingsRepository.setExternalControllerEnabled(v)
    fun setVibration(enabled: Boolean, intensity: Float) = app.settingsRepository.setVibration(enabled, intensity)
    fun setSound(enabled: Boolean, volume: Float) = app.settingsRepository.setSound(enabled, volume)
    fun setScaleMode(mode: ScreenScaleMode) = app.settingsRepository.setScaleMode(mode)

    fun toggleCrtShader() {
        val nowOn = app.settingsRepository.toggleCrtFilter()
        showTransientBanner(if (nowOn) "Filtro CRT Activado" else "Filtro CRT Desactivado")
    }

    fun openKeyMapperDialog() {
        _showKeyMapper.value = true
    }

    fun closeKeyMapperDialog() {
        _showKeyMapper.value = false
    }

    fun openMacroConfigDialog() {
        _showMacroConfig.value = true
    }

    fun closeMacroConfigDialog() {
        _showMacroConfig.value = false
    }

    fun updateMacroSlot(slotIndex: Int, config: MacroSlotConfig) {
        app.macroEngine.updateSlotConfig(slotIndex, config)
        app.settingsRepository.syncMacroSlots(app.macroEngine.slotConfigs)
    }

    fun onMacroTouchEvent(slotIndex: Int, pressedDown: Boolean) {
        if (pressedDown) {
            triggerHapticTick()
        }
        app.macroEngine.onMacroTriggerEvent(slotIndex, pressedDown)
    }

    fun bindControllerKey(actionName: String, keyCode: Int) {
        app.macroEngine.saveHardwareKeyBinding(actionName, keyCode)
        app.settingsRepository.bindHardwareKey(actionName, keyCode)
    }

    fun resetControllerKeys() {
        app.settingsRepository.resetHardwareKeys()
        app.macroEngine.resetHardwareKeyBindings(settings.value.hardwareKeyMap)
    }

    fun saveTouchControlsLayout(layout: TouchControlsLayout) {
        app.settingsRepository.updateTouchLayout(layout)
        showTransientBanner("Layout de controles guardado")
    }

    fun resetTouchControlsLayout(): TouchControlsLayout {
        val defaults = app.settingsRepository.resetTouchLayoutToDefaultDiamond()
        showTransientBanner("Controles restablecidos al diamante SNES")
        return defaults
    }

    private fun showTransientBanner(msg: String) {
        bannerJob?.cancel()
        _statusBanner.value = msg
        bannerJob = viewModelScope.launch {
            delay(2000L)
            _statusBanner.value = null
        }
    }

    override fun onCleared() {
        autoSaveProgressIfInGame()
        super.onCleared()
        app.macroEngine.stopAllMacros()
        app.hotspotTransport.stopAll()
        nativeBridge.stopSession()
    }
}
