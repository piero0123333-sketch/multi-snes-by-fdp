package com.fdp.multisnes

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fdp.multisnes.core.SnesButtons
import com.fdp.multisnes.input.MacroEngine
import com.fdp.multisnes.ui.dialogs.GameLaunchDialog
import com.fdp.multisnes.ui.dialogs.HerramientasDialog
import com.fdp.multisnes.ui.dialogs.KeyMappingDialog
import com.fdp.multisnes.ui.dialogs.MacroConfigDialog
import com.fdp.multisnes.ui.dialogs.PatchRomPickerDialog
import com.fdp.multisnes.ui.dialogs.SaveSlotPickerDialog
import com.fdp.multisnes.ui.screens.CatalogScreen
import com.fdp.multisnes.ui.screens.EmulatorScreen
import com.fdp.multisnes.ui.screens.OnlineHotspotScreen
import com.fdp.multisnes.ui.theme.MultiSnesTheme
import com.fdp.multisnes.ui.viewmodel.AppScreenRoute
import com.fdp.multisnes.ui.viewmodel.MultiSnesViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MultiSnesViewModel by viewModels()
    private var gamepadDigitalMask = 0
    private var gamepadAxisMask = 0
    private var volumeKeysMask = 0
    private var menuNavLastDpadKey = 0

    // Listener de acciones de sistema para mando externo (Guardar, Cargar último, Rewind y Fast-Forward)
    private val systemActionListener = object : MacroEngine.SystemActionListener {
        override fun onSaveStateRequested() {
            viewModel.performQuickSave()
        }

        override fun onLoadLatestStateRequested() {
            viewModel.performQuickLoad()
        }

        override fun onRewindStateChanged(rewinding: Boolean) {
            viewModel.setRewinding(rewinding)
        }

        override fun onToggleFastForwardRequested() {
            viewModel.toggleFastForward()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MÓDULO 3: Procesar Intent.ACTION_VIEW si la app fue abierta desde "Abrir con MULTI SNES BY FDP"
        if (savedInstanceState == null) {
            viewModel.handleExternalIntent(intent)
        }

        setContent {
            MultiSnesTheme {
                val route by viewModel.route.collectAsState()
                val games by viewModel.games.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()
                val selectedLaunchGame by viewModel.selectedLaunchGame.collectAsState()
                val showHerramientas by viewModel.showHerramientas.collectAsState()
                val showKeyMapper by viewModel.showKeyMapper.collectAsState()
                val showMacroConfig by viewModel.showMacroConfig.collectAsState()
                val activeMacroStates by viewModel.activeMacroStates.collectAsState()
                val isEditingButtons by viewModel.isEditingButtons.collectAsState()
                val isFastForward by viewModel.isFastForward.collectAsState()
                val isRewinding by viewModel.isRewinding.collectAsState()
                val statusBanner by viewModel.statusBanner.collectAsState()

                val slotPickerGame by viewModel.slotPickerGame.collectAsState()
                val slotPickerSlots by viewModel.slotPickerSlots.collectAsState()
                val slotPickerLatestSlot by viewModel.slotPickerLatestSlot.collectAsState()
                val pendingPatch by viewModel.pendingPatch.collectAsState()

                val lobbyStatus by viewModel.lobbyStatus.collectAsState()
                val onlineStatusText by viewModel.onlineStatusText.collectAsState()
                val localHotspotIp by viewModel.localHotspotIp.collectAsState()
                val coordinator by viewModel.activeCoordinator.collectAsState()
                val telemetryState = coordinator?.telemetry?.collectAsState()

                // Selector SAF de carpetas de ROMs
                val folderPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { treeUri ->
                    if (treeUri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                treeUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        viewModel.addSafFolderAndScan(treeUri.toString())
                    }
                }

                val legacyStoragePermLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    viewModel.triggerAutoRomScan()
                }

                val triggerSmartScan: () -> Unit = {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        legacyStoragePermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else {
                        viewModel.triggerAutoRomScan()
                        if (games.isEmpty() && settings.safRomTreeUris.isEmpty()) {
                            folderPickerLauncher.launch(null)
                        }
                    }
                }

                // Orientación automática: Horizontal pantalla completa en juego, Libre/Vertical en menús
                LaunchedEffect(route) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (route == AppScreenRoute.EMULATOR_GAMEPLAY) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                BackHandler(enabled = route != AppScreenRoute.CATALOG) {
                    when (route) {
                        AppScreenRoute.EMULATOR_GAMEPLAY -> viewModel.openHerramientasOverlay()
                        AppScreenRoute.ONLINE_HOTSPOT -> viewModel.openCatalogScreen()
                        AppScreenRoute.CATALOG -> Unit
                    }
                }

                when (route) {
                    AppScreenRoute.CATALOG -> {
                        CatalogScreen(
                            games = games,
                            isScanning = isScanning,
                            onOpenOnlineTab = viewModel::openOnlineScreen,
                            onAutoScanGames = triggerSmartScan,
                            onPickRomFolder = { folderPickerLauncher.launch(null) },
                            onSelectGame = viewModel::onGameCardClicked,
                            onOpenHerramientas = viewModel::openHerramientasOverlay
                        )
                    }

                    AppScreenRoute.ONLINE_HOTSPOT -> {
                        OnlineHotspotScreen(
                            games = games,
                            isScanning = isScanning,
                            lobbyStatus = lobbyStatus,
                            statusMessage = onlineStatusText,
                            localHotspotIp = localHotspotIp,
                            onBackToCatalog = viewModel::openCatalogScreen,
                            onAutoScanGames = triggerSmartScan,
                            onPickRomFolder = { folderPickerLauncher.launch(null) },
                            onClickCreateHost = viewModel::startCreateHostFlow,
                            onHostGameChosen = viewModel::launchHostWaitingRoom,
                            onClickJoinGuest = viewModel::startGuestAutoJoinFlow,
                            onManualJoinIp = viewModel::connectGuestManualIp,
                            onCancelLobbyWait = viewModel::cancelOnlineLobby,
                            onOpenHerramientas = viewModel::openHerramientasOverlay
                        )
                    }

                    AppScreenRoute.EMULATOR_GAMEPLAY -> {
                        EmulatorScreen(
                            nativeBridge = viewModel.nativeBridge,
                            settings = settings,
                            isOnlineSession = coordinator != null,
                            isFastForwardActive = isFastForward,
                            isRewindingActive = isRewinding,
                            netplayTelemetry = telemetryState?.value,
                            isEditingButtonLayout = isEditingButtons,
                            statusBanner = statusBanner,
                            activeMacroStates = activeMacroStates,
                            onTouchMaskChanged = viewModel::onTouchMaskUpdated,
                            onMacroTouchEvent = viewModel::onMacroTouchEvent,
                            onQuickSave = viewModel::performQuickSave,
                            onQuickLoad = viewModel::performQuickLoad,
                            onToggleFastForward = viewModel::toggleFastForward,
                            onSetRewinding = viewModel::setRewinding,
                            onOpenPauseHerramientas = viewModel::openHerramientasOverlay,
                            onSaveButtonLayout = viewModel::saveTouchControlsLayout,
                            onResetButtonLayout = viewModel::resetTouchControlsLayout,
                            onExitLayoutEditor = viewModel::finishMoveButtonsEditor
                        )
                    }
                }

                // MÓDULO 2: Modal de Selección de Juego con 3 Botones:
                // 1. JUGAR (DESDE CERO)
                // 2. CONTINUAR DONDE QUEDASTE (autosave_exit.state)
                // 3. CARGAR PROGRESO (GUARDADOS) (Selector de 1..10 slots manuales)
                selectedLaunchGame?.let { chosenGame ->
                    GameLaunchDialog(
                        game = chosenGame,
                        onDismiss = viewModel::dismissLaunchDialog,
                        onPlayFromScratch = { viewModel.launchSinglePlayer(it, loadSavedProgress = false) },
                        onContinueWhereLeftOff = viewModel::launchContinueWhereLeftOff,
                        onOpenManualSavesPicker = viewModel::openSaveSlotPickerFromCatalog,
                        onToggleCompletedStar = viewModel::toggleCompletedStar
                    )
                }

                // Modal Selector de los 10 Estados Guardados manualmente
                slotPickerGame?.let { targetGame ->
                    SaveSlotPickerDialog(
                        gameTitle = targetGame.title,
                        slotsNewestFirst = slotPickerSlots,
                        latestSlotNumber = slotPickerLatestSlot,
                        onSelectSlot = viewModel::loadSelectedSlot,
                        onDismiss = viewModel::closeSaveSlotPicker
                    )
                }

                // MÓDULO 3: Modal Selector de ROM cuando el usuario abre un parche (.ips o .bps) con "Abrir con..."
                pendingPatch?.let { patchPayload ->
                    PatchRomPickerDialog(
                        patchPayload = patchPayload,
                        games = games,
                        onSelectBaseRom = viewModel::applyPendingPatchToRom,
                        onDismiss = viewModel::dismissPendingPatch
                    )
                }

                // Modal HERRAMIENTAS
                if (showHerramientas) {
                    HerramientasDialog(
                        settings = settings,
                        isInGame = route == AppScreenRoute.EMULATOR_GAMEPLAY,
                        onDismiss = viewModel::closeHerramientasOverlay,
                        onSetDpad = viewModel::setDpadEnabled,
                        onSetJoystick = viewModel::setJoystickEnabled,
                        onSetExternalController = viewModel::setExternalControllerEnabled,
                        onOpenKeyMapper = viewModel::openKeyMapperDialog,
                        onOpenMacroConfig = viewModel::openMacroConfigDialog,
                        onSetVibration = viewModel::setVibration,
                        onSetSound = viewModel::setSound,
                        onSelectScale = viewModel::setScaleMode,
                        onQuickSave = {
                            viewModel.performQuickSave()
                            viewModel.closeHerramientasOverlay()
                        },
                        onQuickLoad = viewModel::openSaveSlotPickerFromTools,
                        onToggleCrt = viewModel::toggleCrtShader,
                        onMoveButtonsEditor = viewModel::startMoveButtonsEditor,
                        onExitToCatalog = viewModel::exitGameToCatalog
                    )
                }

                // Sub-modal MAPEAR BOTONES
                if (showKeyMapper) {
                    KeyMappingDialog(
                        currentKeyMap = settings.hardwareKeyMap,
                        onBindKey = viewModel::bindControllerKey,
                        onResetDefaults = viewModel::resetControllerKeys,
                        onDismiss = viewModel::closeKeyMapperDialog
                    )
                }

                // Sub-modal CONFIGURAR MACROS
                if (showMacroConfig) {
                    MacroConfigDialog(
                        macroSlots = settings.macroSlots,
                        onUpdateMacroSlot = viewModel::updateMacroSlot,
                        onDismiss = viewModel::closeMacroConfigDialog
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // MÓDULO 3: Procesar nuevo Intent.ACTION_VIEW si la app ya estaba en memoria (launchMode="singleTask")
        viewModel.handleExternalIntent(intent)
    }

    // --- MÓDULO 2: Autoguardado Invisible al Salir (autosave_exit.state) en onPause() y onStop() ---

    override fun onPause() {
        viewModel.autoSaveProgressIfInGame()
        super.onPause()
    }

    override fun onStop() {
        viewModel.autoSaveProgressIfInGame()
        super.onStop()
    }

    // --- Soporte nativo de Mando Externo Bluetooth / USB (SOURCE_GAMEPAD y SOURCE_JOYSTICK) ---

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val cfg = viewModel.settings.value
        val isMapperOpen = viewModel.showKeyMapper.value
        val code = event.keyCode
        val isVolumeKey = SnesButtons.isVolumeKey(code)

        // Las teclas de VOLUMEN +/- del celular funcionan como botones mapeables aunque no haya
        // un mando externo conectado. Si no están asignadas a nada, conservan su función normal de volumen.
        if (!isMapperOpen &&
            viewModel.route.value == AppScreenRoute.EMULATOR_GAMEPLAY &&
            (cfg.externalControllerEnabled || isVolumeKey)
        ) {
            val isMappedAnywhere = cfg.hardwareKeyMap.values.any { it == code && it != 0 }
            if (isVolumeKey && !isMappedAnywhere) {
                return super.dispatchKeyEvent(event)
            }
            if (event.repeatCount > 0 && (event.action == KeyEvent.ACTION_DOWN)) {
                return if (isMappedAnywhere) true else super.dispatchKeyEvent(event)
            }
            val isDown = event.action == KeyEvent.ACTION_DOWN
            val isUp = event.action == KeyEvent.ACTION_UP

            if (isDown || isUp) {
                val app = application as MultiSnesApp
                val sysOrMacroTriggered = app.macroEngine.handleHardwareKeyForSystemAndMacros(
                    code,
                    isDown,
                    cfg.hardwareKeyMap,
                    systemActionListener
                )

                var matchedBit = 0
                for (action in SnesButtons.Action.entries) {
                    if (cfg.hardwareKeyMap[action.name] == code) {
                        matchedBit = matchedBit or action.bitMask
                    }
                }

                if (matchedBit != 0) {
                    if (isVolumeKey) {
                        volumeKeysMask = if (isDown) volumeKeysMask or matchedBit else volumeKeysMask and matchedBit.inv()
                        viewModel.onDeviceKeysMaskUpdated(volumeKeysMask)
                    } else {
                        gamepadDigitalMask = if (isDown) {
                            gamepadDigitalMask or matchedBit
                        } else {
                            gamepadDigitalMask and matchedBit.inv()
                        }
                        viewModel.onHardwareMaskUpdated(gamepadDigitalMask or gamepadAxisMask)
                    }
                    return true
                }

                if (sysOrMacroTriggered || isMappedAnywhere) {
                    return true
                }
            }
        } else if (volumeKeysMask != 0 && viewModel.route.value != AppScreenRoute.EMULATOR_GAMEPLAY) {
            volumeKeysMask = 0
            viewModel.onDeviceKeysMaskUpdated(0)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val cfg = viewModel.settings.value
        if (MacroEngine.isGamepadOrJoystick(ev)) {
            val x = ev.getAxisValue(MotionEvent.AXIS_HAT_X).takeIf { it != 0f }
                ?: ev.getAxisValue(MotionEvent.AXIS_X)
            val y = ev.getAxisValue(MotionEvent.AXIS_HAT_Y).takeIf { it != 0f }
                ?: ev.getAxisValue(MotionEvent.AXIS_Y)

            if (viewModel.route.value == AppScreenRoute.EMULATOR_GAMEPLAY && cfg.externalControllerEnabled) {
                val app = application as MultiSnesApp
                var axisBits = 0
                if (x < -0.45f) axisBits = axisBits or SnesButtons.LEFT
                if (x > 0.45f) axisBits = axisBits or SnesButtons.RIGHT
                if (y < -0.45f) axisBits = axisBits or SnesButtons.UP
                if (y > 0.45f) axisBits = axisBits or SnesButtons.DOWN

                val triggerBits = app.macroEngine.extractTriggerButtonsMask(
                    ev,
                    cfg.hardwareKeyMap,
                    systemActionListener
                )
                gamepadAxisMask = axisBits or triggerBits
                viewModel.onHardwareMaskUpdated(gamepadDigitalMask or gamepadAxisMask)
                return true
            } else if (viewModel.route.value != AppScreenRoute.EMULATOR_GAMEPLAY) {
                // MÓDULO 1: Convertir movimiento de Joystick / D-Pad HAT en eventos D-Pad de navegación
                // para mover el foco (Focus State) fluidamente entre tarjetas de la biblioteca
                val nextDpadKey = when {
                    x < -0.55f -> KeyEvent.KEYCODE_DPAD_LEFT
                    x > 0.55f -> KeyEvent.KEYCODE_DPAD_RIGHT
                    y < -0.55f -> KeyEvent.KEYCODE_DPAD_UP
                    y > 0.55f -> KeyEvent.KEYCODE_DPAD_DOWN
                    else -> 0
                }
                if (nextDpadKey != menuNavLastDpadKey) {
                    if (menuNavLastDpadKey != 0) {
                        super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, menuNavLastDpadKey))
                    }
                    menuNavLastDpadKey = nextDpadKey
                    if (nextDpadKey != 0) {
                        super.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, nextDpadKey))
                        return true
                    }
                }
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}
