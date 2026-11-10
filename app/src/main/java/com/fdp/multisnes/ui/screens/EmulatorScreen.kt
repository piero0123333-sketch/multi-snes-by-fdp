package com.fdp.multisnes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fdp.multisnes.core.SnesButtons
import com.fdp.multisnes.core.SnesGlSurfaceView
import com.fdp.multisnes.core.SnesNativeBridge
import com.fdp.multisnes.data.model.EmulatorSettings
import com.fdp.multisnes.data.model.MacroSlotConfig
import com.fdp.multisnes.data.model.TouchControlsLayout
import com.fdp.multisnes.input.HudLayoutManager
import com.fdp.multisnes.netplay.HotspotRollbackCoordinator
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pantalla de partida horizontal (Imagen 5) actualizada con:
 * - Módulo 2: Botones X, Y, B, A 100% desagrupados e independientes (posX, posY, scale 0.5x..2.0x, alpha)
 *   tanto en juego como en el modo "MOVER BOTONES".
 * - Módulo 3: Botones virtuales flotantes de Macro independientes (M1, M2, M3, M4) con indicador
 *   visual de estado (Turbo / Auto-Hold) conectados al motor nativo C++.
 */
@Composable
fun EmulatorScreen(
    nativeBridge: SnesNativeBridge,
    settings: EmulatorSettings,
    isOnlineSession: Boolean,
    isFastForwardActive: Boolean,
    isRewindingActive: Boolean,
    netplayTelemetry: HotspotRollbackCoordinator.NetplayTelemetry?,
    isEditingButtonLayout: Boolean,
    statusBanner: String?,
    activeMacroStates: BooleanArray,
    onTouchMaskChanged: (Int) -> Unit,
    onMacroTouchEvent: (Int, Boolean) -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onToggleFastForward: () -> Unit,
    onSetRewinding: (Boolean) -> Unit,
    onOpenPauseHerramientas: () -> Unit,
    onSaveButtonLayout: (TouchControlsLayout) -> Unit,
    onResetButtonLayout: () -> TouchControlsLayout,
    onExitLayoutEditor: () -> Unit
) {
    var draftLayout by remember(settings.touchLayout, isEditingButtonLayout) {
        mutableStateOf(settings.touchLayout)
    }

    DisposableEffect(Unit) {
        onDispose {
            onTouchMaskChanged(0)
        }
    }

    // Rendimiento en vivo (refresco cada 500 ms para no recomponer en cada frame)
    val perfStats by produceState(initialValue = SnesNativeBridge.PerfStats(), nativeBridge) {
        while (true) {
            value = nativeBridge.perfStats
            delay(500)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // 1. Renderizador OpenGL ES 3.0 de 60FPS con Escalas y Filtro CRT
        AndroidView(
            factory = { ctx ->
                SnesGlSurfaceView(ctx, nativeBridge).apply {
                    updateVisualOptions(settings.scaleMode, settings.crtFilterEnabled)
                }
            },
            update = { glView ->
                glView.updateVisualOptions(settings.scaleMode, settings.crtFilterEnabled)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Controles Táctiles en Pantalla (Multitáctil real con X, Y, B, A individuales + Macros M1..M4)
        val activeLayout = if (isEditingButtonLayout) draftLayout else settings.touchLayout
        val showDirectional = settings.dpadEnabled || settings.joystickEnabled
        val showFaceControls = settings.dpadEnabled || settings.joystickEnabled || !settings.externalControllerEnabled

        if (!isEditingButtonLayout && (showDirectional || showFaceControls)) {
            MultiTouchSnesOverlay(
                screenWidthPx = screenW,
                screenHeightPx = screenH,
                layout = activeLayout,
                useDpad = settings.dpadEnabled,
                useJoystick = settings.joystickEnabled,
                showActionButtons = showFaceControls,
                macroSlots = settings.macroSlots,
                activeMacroStates = activeMacroStates,
                onMaskUpdated = onTouchMaskChanged,
                onMacroTouchEvent = onMacroTouchEvent
            )
        }

        // 3. Editor interactivo "MOVER BOTONES" con desagrupación individual de X, Y, B, A y M1..M4 (Módulo 2)
        if (isEditingButtonLayout) {
            ButtonLayoutEditorLayer(
                screenWidthPx = screenW,
                screenHeightPx = screenH,
                layout = draftLayout,
                macroSlots = settings.macroSlots,
                onLayoutChanged = { draftLayout = it },
                onSave = {
                    onSaveButtonLayout(draftLayout)
                    onExitLayoutEditor()
                },
                onReset = {
                    draftLayout = onResetButtonLayout()
                }
            )
        } else {
            // 4. Barra HUD superior idéntica a la Imagen 5
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Izquierda: Guardado Rápido directo + Cargar Rápido directo
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TranslucentHudSquareButton(onClick = onQuickSave) {
                        HudFloppyGlyph()
                    }
                    TranslucentHudSquareButton(onClick = onQuickLoad) {
                        HudFolderDownGlyph()
                    }
                }

                // Centro: Retroceder Tiempo '<<' (Rewind mientras se mantiene presionado) + Avance Rápido '>>' (solo offline)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isOnlineSession) {
                        TranslucentHudHoldButton(
                            highlighted = isRewindingActive,
                            onPressChanged = onSetRewinding
                        ) {
                            HudRewindGlyph(active = isRewindingActive)
                        }
                        TranslucentHudSquareButton(
                            highlighted = isFastForwardActive,
                            onClick = onToggleFastForward
                        ) {
                            HudFastForwardGlyph(active = isFastForwardActive)
                        }
                    }
                }

                // Derecha: Rendimiento (FPS, velocidad, ms; + ping y pérdida en Online) al costado de Pausa '||'
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PerformanceHud(
                        stats = perfStats,
                        netplay = if (isOnlineSession) netplayTelemetry else null
                    )
                    TranslucentHudSquareButton(onClick = onOpenPauseHerramientas) {
                        HudPauseGlyph()
                    }
                }
            }
        }

        // Banner breve de confirmación
        if (!statusBanner.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC1E1E2A))
                    .border(1.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                OutlinedPixelText(
                    text = statusBanner,
                    style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Superficie multitáctil completa con:
 * - Botones de acción X, Y, B, A completamente independientes (cada uno con su propio posX, posY, scale, alpha).
 * - Botones virtuales de Macro M1, M2, M3, M4 independientes (Turbo / Auto-Hold).
 * - Cruceta / Joystick dinámico flotante donde presiona el dedo.
 */
@Composable
private fun MultiTouchSnesOverlay(
    screenWidthPx: Float,
    screenHeightPx: Float,
    layout: TouchControlsLayout,
    useDpad: Boolean,
    useJoystick: Boolean,
    showActionButtons: Boolean,
    macroSlots: List<MacroSlotConfig>,
    activeMacroStates: BooleanArray,
    onMaskUpdated: (Int) -> Unit,
    onMacroTouchEvent: (Int, Boolean) -> Unit
) {
    val density = LocalDensity.current
    val baseDpadSizePx = with(density) { 142.dp.toPx() }
    val dpadSizePx = baseDpadSizePx * layout.dpadScale.coerceIn(0.5f, 2.0f)

    val baseShoulderW = with(density) { 96.dp.toPx() }
    val baseShoulderH = with(density) { 42.dp.toPx() }
    val basePillW = with(density) { 78.dp.toPx() }
    val basePillH = with(density) { 32.dp.toPx() }
    val baseActionSizePx = with(density) { 58.dp.toPx() }
    val baseMacroSizePx = with(density) { 48.dp.toPx() }

    val dpadCenter = Offset(
        layout.dpadX * screenWidthPx + dpadSizePx / 2f,
        layout.dpadY * screenHeightPx + dpadSizePx / 2f
    )

    val lScale = layout.lButtonScale.coerceIn(0.5f, 2.0f)
    val lRect = Rect(
        layout.lButtonX * screenWidthPx,
        layout.lButtonY * screenHeightPx,
        layout.lButtonX * screenWidthPx + baseShoulderW * lScale,
        layout.lButtonY * screenHeightPx + baseShoulderH * lScale
    )

    val rScale = layout.rButtonScale.coerceIn(0.5f, 2.0f)
    val rRect = Rect(
        layout.rButtonX * screenWidthPx,
        layout.rButtonY * screenHeightPx,
        layout.rButtonX * screenWidthPx + baseShoulderW * rScale,
        layout.rButtonY * screenHeightPx + baseShoulderH * rScale
    )

    val selectScale = layout.selectScale.coerceIn(0.5f, 2.0f)
    val selectRect = Rect(
        layout.selectX * screenWidthPx,
        layout.selectY * screenHeightPx,
        layout.selectX * screenWidthPx + basePillW * selectScale,
        layout.selectY * screenHeightPx + basePillH * selectScale
    )

    val startScale = layout.startScale.coerceIn(0.5f, 2.0f)
    val startRect = Rect(
        layout.startX * screenWidthPx,
        layout.startY * screenHeightPx,
        layout.startX * screenWidthPx + basePillW * startScale,
        layout.startY * screenHeightPx + basePillH * startScale
    )

    // Coordenadas, radios y escalas individuales de X, Y, B, A (Módulo 2)
    val radX = (baseActionSizePx * 0.5f) * layout.btnXScale.coerceIn(0.5f, 2.0f)
    val posXBtn = Offset(layout.btnXX * screenWidthPx + radX, layout.btnXY * screenHeightPx + radX)

    val radY = (baseActionSizePx * 0.5f) * layout.btnYScale.coerceIn(0.5f, 2.0f)
    val posYBtn = Offset(layout.btnYX * screenWidthPx + radY, layout.btnYY * screenHeightPx + radY)

    val radB = (baseActionSizePx * 0.5f) * layout.btnBScale.coerceIn(0.5f, 2.0f)
    val posBBtn = Offset(layout.btnBX * screenWidthPx + radB, layout.btnBY * screenHeightPx + radB)

    val radA = (baseActionSizePx * 0.5f) * layout.btnAScale.coerceIn(0.5f, 2.0f)
    val posABtn = Offset(layout.btnAX * screenWidthPx + radA, layout.btnAY * screenHeightPx + radA)

    // Coordenadas y radios individuales de los Macros M1, M2, M3, M4 (Módulo 3)
    val macroXs = floatArrayOf(layout.macro1X, layout.macro2X, layout.macro3X, layout.macro4X)
    val macroYs = floatArrayOf(layout.macro1Y, layout.macro2Y, layout.macro3Y, layout.macro4Y)
    val macroScales = floatArrayOf(layout.macro1Scale, layout.macro2Scale, layout.macro3Scale, layout.macro4Scale)
    val macroAlphas = floatArrayOf(layout.macro1Alpha, layout.macro2Alpha, layout.macro3Alpha, layout.macro4Alpha)

    val macroRadii = FloatArray(4) { i ->
        (baseMacroSizePx * 0.5f) * macroScales[i].coerceIn(0.5f, 2.0f)
    }
    val macroCenters = Array(4) { i ->
        Offset(
            macroXs[i] * screenWidthPx + macroRadii[i],
            macroYs[i] * screenHeightPx + macroRadii[i]
        )
    }

    var activeMask by remember { mutableStateOf(0) }
    var stickOffset by remember { mutableStateOf(Offset.Zero) }

    // Cruceta / Joystick dinámico flotante: se forma donde presiona el dedo
    var floatingOrigin by remember { mutableStateOf<Offset?>(null) }
    val dirCenter = floatingOrigin ?: dpadCenter
    val hasDirectional = useDpad || useJoystick
    val directionOnLeft = dpadCenter.x < screenWidthPx / 2f
    val hudTopMarginPx = with(density) { 72.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(layout, useDpad, useJoystick, showActionButtons, macroSlots) {
                awaitEachGesture {
                    var dirPointerId: PointerId? = null
                    var origin = Offset.Zero
                    val macroFingerDown = BooleanArray(4)

                    while (true) {
                        val event = awaitPointerEvent()
                        var mask = 0
                        var joyDelta = Offset.Zero
                        val nextMacroDown = BooleanArray(4)

                        val activeTouches = event.changes.filter { it.pressed }

                        // Si el dedo direccional se levanta, liberar origen flotante
                        if (dirPointerId != null && activeTouches.none { it.id == dirPointerId }) {
                            dirPointerId = null
                            floatingOrigin = null
                        }

                        for (touch in activeTouches) {
                            val p = touch.position

                            // Verificar primero si el toque cae sobre algún botón explícito (L, R, SELECT, START, X, Y, B, A o Macros M1..M4)
                            val hitsMacro = (0 until 4).any { i ->
                                val slot = macroSlots.getOrNull(i)
                                slot != null && slot.enabled &&
                                    hypot(p.x - macroCenters[i].x, p.y - macroCenters[i].y) <= macroRadii[i] * 1.25f
                            }
                            val hitsExplicitButton = showActionButtons && (
                                lRect.inflate(14f).contains(p) ||
                                    rRect.inflate(14f).contains(p) ||
                                    selectRect.inflate(10f).contains(p) ||
                                    startRect.inflate(10f).contains(p) ||
                                    hypot(p.x - posXBtn.x, p.y - posXBtn.y) <= radX * 1.28f ||
                                    hypot(p.x - posYBtn.x, p.y - posYBtn.y) <= radY * 1.28f ||
                                    hypot(p.x - posBBtn.x, p.y - posBBtn.y) <= radB * 1.28f ||
                                    hypot(p.x - posABtn.x, p.y - posABtn.y) <= radA * 1.28f ||
                                    hitsMacro
                                )

                            // 1. Detectar nuevo toque en la zona direccional -> el control se forma donde toca el dedo
                            if (hasDirectional && dirPointerId == null && touch.pressed && !touch.previousPressed) {
                                val inDirectionalHalf = (p.x < screenWidthPx / 2f) == directionOnLeft && p.y > hudTopMarginPx
                                if (inDirectionalHalf && !hitsExplicitButton) {
                                    val margin = dpadSizePx * 0.35f
                                    origin = Offset(
                                        p.x.coerceIn(margin, (screenWidthPx - margin).coerceAtLeast(margin)),
                                        p.y.coerceIn(margin, (screenHeightPx - margin).coerceAtLeast(margin))
                                    )
                                    dirPointerId = touch.id
                                    floatingOrigin = origin
                                }
                            }

                            // 2. Evaluar movimiento del dedo direccional respecto al punto donde presionó
                            if (dirPointerId != null && touch.id == dirPointerId) {
                                var dx = p.x - origin.x
                                var dy = p.y - origin.y
                                var dist = hypot(dx, dy)

                                val maxFollowRadius = dpadSizePx * 0.48f
                                if (dist > maxFollowRadius) {
                                    val shiftRatio = (dist - maxFollowRadius) / dist
                                    origin = Offset(
                                        origin.x + dx * shiftRatio,
                                        origin.y + dy * shiftRatio
                                    )
                                    dx = p.x - origin.x
                                    dy = p.y - origin.y
                                    dist = hypot(dx, dy)
                                }
                                floatingOrigin = origin

                                val deadZone = dpadSizePx * 0.11f
                                if (dist > deadZone) {
                                    val clampedDist = dist.coerceAtMost(dpadSizePx * 0.36f)
                                    val angle = atan2(dy, dx)
                                    joyDelta = Offset(cos(angle) * clampedDist, sin(angle) * clampedDist)

                                    if (dx < -deadZone) mask = mask or SnesButtons.LEFT
                                    if (dx > deadZone) mask = mask or SnesButtons.RIGHT
                                    if (dy < -deadZone) mask = mask or SnesButtons.UP
                                    if (dy > deadZone) mask = mask or SnesButtons.DOWN
                                }
                                continue
                            }

                            if (showActionButtons) {
                                // 3. Gatillos L y R
                                if (lRect.inflate(14f).contains(p)) mask = mask or SnesButtons.L
                                if (rRect.inflate(14f).contains(p)) mask = mask or SnesButtons.R

                                // 4. SELECT y START
                                if (selectRect.inflate(10f).contains(p)) mask = mask or SnesButtons.SELECT
                                if (startRect.inflate(10f).contains(p)) mask = mask or SnesButtons.START

                                // 5. Botones X, Y, B, A individuales (con su propio radio escalado)
                                if (hypot(p.x - posXBtn.x, p.y - posXBtn.y) <= radX * 1.28f) mask = mask or SnesButtons.X
                                if (hypot(p.x - posYBtn.x, p.y - posYBtn.y) <= radY * 1.28f) mask = mask or SnesButtons.Y
                                if (hypot(p.x - posBBtn.x, p.y - posBBtn.y) <= radB * 1.28f) mask = mask or SnesButtons.B
                                if (hypot(p.x - posABtn.x, p.y - posABtn.y) <= radA * 1.28f) mask = mask or SnesButtons.A

                                // 6. Botones virtuales de Macro M1..M4
                                for (i in 0 until 4) {
                                    val slot = macroSlots.getOrNull(i)
                                    if (slot != null && slot.enabled) {
                                        if (hypot(p.x - macroCenters[i].x, p.y - macroCenters[i].y) <= macroRadii[i] * 1.22f) {
                                            nextMacroDown[i] = true
                                        }
                                    }
                                }
                            }
                        }

                        // Notificar transiciones de pulsación/liberación de M1..M4 al MacroEngine
                        for (i in 0 until 4) {
                            if (nextMacroDown[i] != macroFingerDown[i]) {
                                macroFingerDown[i] = nextMacroDown[i]
                                onMacroTouchEvent(i, nextMacroDown[i])
                            }
                        }

                        stickOffset = joyDelta
                        if (mask != activeMask) {
                            activeMask = mask
                            onMaskUpdated(mask)
                        }

                        if (activeTouches.isEmpty()) {
                            for (i in 0 until 4) {
                                if (macroFingerDown[i]) {
                                    macroFingerDown[i] = false
                                    onMacroTouchEvent(i, false)
                                }
                            }
                            stickOffset = Offset.Zero
                            floatingOrigin = null
                            if (activeMask != 0) {
                                activeMask = 0
                                onMaskUpdated(0)
                            }
                            break
                        }
                    }
                }
            }
    ) {
        // Dibujo traslúcido con opacidad y escala individual por botón
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun fillForAlpha(alpha: Float, pressed: Boolean): Color {
                val baseA = (alpha.coerceIn(0.2f, 1.0f) * if (pressed) 0.78f else 0.42f).coerceIn(0.1f, 0.92f)
                return if (pressed) Color.White.copy(alpha = baseA) else Color(0xFFE0E0E8).copy(alpha = baseA)
            }
            val borderDark = Color(0x55000000)

            // Cruceta D-Pad clásica (Imagen 5)
            if (useDpad) {
                val dpadFill = fillForAlpha(layout.dpadAlpha, false)
                val dpadPressed = fillForAlpha(layout.dpadAlpha, true)
                val armLen = dpadSizePx * 0.46f
                val armHalfThick = dpadSizePx * 0.17f
                drawRoundRect(
                    color = borderDark,
                    topLeft = Offset(dirCenter.x - armHalfThick - 2f, dirCenter.y - armLen - 2f),
                    size = Size(armHalfThick * 2 + 4f, armLen * 2 + 4f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = borderDark,
                    topLeft = Offset(dirCenter.x - armLen - 2f, dirCenter.y - armHalfThick - 2f),
                    size = Size(armLen * 2 + 4f, armHalfThick * 2 + 4f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                drawRoundRect(
                    color = dpadFill,
                    topLeft = Offset(dirCenter.x - armHalfThick, dirCenter.y - armLen),
                    size = Size(armHalfThick * 2, armLen * 2),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = dpadFill,
                    topLeft = Offset(dirCenter.x - armLen, dirCenter.y - armHalfThick),
                    size = Size(armLen * 2, armHalfThick * 2),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                val triColor = Color(0x66404048)
                drawCircle(
                    color = if ((activeMask and SnesButtons.UP) != 0) dpadPressed else triColor,
                    radius = 7f,
                    center = Offset(dirCenter.x, dirCenter.y - armLen * 0.68f)
                )
                drawCircle(
                    color = if ((activeMask and SnesButtons.DOWN) != 0) dpadPressed else triColor,
                    radius = 7f,
                    center = Offset(dirCenter.x, dirCenter.y + armLen * 0.68f)
                )
                drawCircle(
                    color = if ((activeMask and SnesButtons.LEFT) != 0) dpadPressed else triColor,
                    radius = 7f,
                    center = Offset(dirCenter.x - armLen * 0.68f, dirCenter.y)
                )
                drawCircle(
                    color = if ((activeMask and SnesButtons.RIGHT) != 0) dpadPressed else triColor,
                    radius = 7f,
                    center = Offset(dirCenter.x + armLen * 0.68f, dirCenter.y)
                )
            } else if (useJoystick) {
                val joyFill = fillForAlpha(layout.dpadAlpha, false)
                val joyKnob = fillForAlpha(layout.dpadAlpha, true)
                drawCircle(borderDark, radius = dpadSizePx * 0.44f, center = dirCenter)
                drawCircle(joyFill, radius = dpadSizePx * 0.42f, center = dirCenter)
                drawCircle(joyKnob, radius = dpadSizePx * 0.20f, center = dirCenter + stickOffset)
            }

            if (showActionButtons) {
                // Gatillos L y R
                drawRoundRect(
                    color = fillForAlpha(layout.lButtonAlpha, (activeMask and SnesButtons.L) != 0),
                    topLeft = lRect.topLeft,
                    size = lRect.size,
                    cornerRadius = CornerRadius(22f, 22f)
                )
                drawRoundRect(
                    color = fillForAlpha(layout.rButtonAlpha, (activeMask and SnesButtons.R) != 0),
                    topLeft = rRect.topLeft,
                    size = rRect.size,
                    cornerRadius = CornerRadius(22f, 22f)
                )

                // Píldoras SELECT y START
                drawRoundRect(
                    color = fillForAlpha(layout.selectAlpha, (activeMask and SnesButtons.SELECT) != 0),
                    topLeft = selectRect.topLeft,
                    size = selectRect.size,
                    cornerRadius = CornerRadius(32f, 32f)
                )
                drawRoundRect(
                    color = fillForAlpha(layout.startAlpha, (activeMask and SnesButtons.START) != 0),
                    topLeft = startRect.topLeft,
                    size = startRect.size,
                    cornerRadius = CornerRadius(32f, 32f)
                )

                // Círculos individuales X, Y, B, A (cada uno con su propio centro, radio y opacidad)
                drawCircle(
                    color = fillForAlpha(layout.btnXAlpha, (activeMask and SnesButtons.X) != 0),
                    radius = radX,
                    center = posXBtn
                )
                drawCircle(
                    color = fillForAlpha(layout.btnYAlpha, (activeMask and SnesButtons.Y) != 0),
                    radius = radY,
                    center = posYBtn
                )
                drawCircle(
                    color = fillForAlpha(layout.btnBAlpha, (activeMask and SnesButtons.B) != 0),
                    radius = radB,
                    center = posBBtn
                )
                drawCircle(
                    color = fillForAlpha(layout.btnAAlpha, (activeMask and SnesButtons.A) != 0),
                    radius = radA,
                    center = posABtn
                )

                // Botones flotantes de Macro M1..M4 habilitados
                for (i in 0 until 4) {
                    val slot = macroSlots.getOrNull(i) ?: continue
                    if (!slot.enabled) continue
                    val isMacroActive = activeMacroStates.getOrElse(i) { false }
                    val alpha = macroAlphas[i].coerceIn(0.2f, 1.0f)
                    val fill = if (isMacroActive) {
                        Color(0xFF28583C).copy(alpha = (alpha * 0.85f).coerceIn(0.25f, 0.95f))
                    } else {
                        Color(0xFF3E355A).copy(alpha = (alpha * 0.65f).coerceIn(0.2f, 0.85f))
                    }
                    val ringColor = if (isMacroActive) {
                        SnesPalette.OnlineNeonGreen
                    } else {
                        SnesPalette.FramePurpleBright.copy(alpha = alpha)
                    }

                    drawCircle(color = fill, radius = macroRadii[i], center = macroCenters[i])
                    drawCircle(
                        color = ringColor,
                        radius = macroRadii[i],
                        center = macroCenters[i],
                        style = Stroke(width = if (isMacroActive) 4f else 2f)
                    )
                }
            }
        }

        // Etiquetas de texto limpias sobre cada botón individual
        if (showActionButtons) {
            HudButtonLabel("L", lRect.center, fontSizeSp = (18 * lScale).roundToInt().coerceIn(11, 28))
            HudButtonLabel("R", rRect.center, fontSizeSp = (18 * rScale).roundToInt().coerceIn(11, 28))
            HudButtonLabel("SELECT", selectRect.center, fontSizeSp = (12 * selectScale).roundToInt().coerceIn(9, 20))
            HudButtonLabel("START", startRect.center, fontSizeSp = (12 * startScale).roundToInt().coerceIn(9, 20))
            HudButtonLabel("X", posXBtn, fontSizeSp = (20 * layout.btnXScale).roundToInt().coerceIn(12, 32))
            HudButtonLabel("Y", posYBtn, fontSizeSp = (20 * layout.btnYScale).roundToInt().coerceIn(12, 32))
            HudButtonLabel("B", posBBtn, fontSizeSp = (20 * layout.btnBScale).roundToInt().coerceIn(12, 32))
            HudButtonLabel("A", posABtn, fontSizeSp = (20 * layout.btnAScale).roundToInt().coerceIn(12, 32))

            for (i in 0 until 4) {
                val slot = macroSlots.getOrNull(i) ?: continue
                if (!slot.enabled) continue
                val isMacroActive = activeMacroStates.getOrElse(i) { false }
                val modeTag = if (slot.autoHoldMode) "HOLD" else "${slot.intervalMs}ms"
                HudMacroButtonLabel(
                    title = slot.label,
                    subtitle = if (isMacroActive) "ON" else modeTag,
                    centerPx = macroCenters[i],
                    active = isMacroActive
                )
            }
        }
    }
}

@Composable
private fun HudMacroButtonLabel(
    title: String,
    subtitle: String,
    centerPx: Offset,
    active: Boolean
) {
    Column(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerPx.x - 36.dp.toPx()).roundToInt(),
                    (centerPx.y - 18.dp.toPx()).roundToInt()
                )
            }
            .size(72.dp, 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = if (active) SnesPalette.OnlineNeonGreen else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = if (active) SnesPalette.StarGold else Color(0xFFD0D4E4),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Panel de rendimiento junto al botón de pausa:
 * - FPS reales + velocidad respecto al hardware original (100% = exacto)
 * - Coste de CPU por frame en ms
 * - Solo Online: PING (ms) y PÉRDIDA de paquetes (%) del Hotspot
 */
@Composable
private fun PerformanceHud(
    stats: SnesNativeBridge.PerfStats,
    netplay: HotspotRollbackCoordinator.NetplayTelemetry?
) {
    val speedColor = when {
        stats.speedPercent >= 95 -> SnesPalette.OnlineNeonGreen
        stats.speedPercent >= 80 -> SnesPalette.StarGold
        else -> Color(0xFFFF5252)
    }
    val hudFont = PixelTypography.LabelBold.copy(fontSize = 9.sp, lineHeight = 11.sp)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x9914141E))
            .border(1.dp, Color(0x88D0DCE8), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        OutlinedPixelText(
            text = "${stats.fps} FPS  ${stats.speedPercent}%",
            style = hudFont,
            color = speedColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            maxLines = 1
        )
        OutlinedPixelText(
            text = String.format("CPU %.1f ms", stats.frameMs),
            style = hudFont,
            color = Color(0xFFE8EEF5),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            maxLines = 1
        )
        if (netplay != null) {
            val pingColor = when {
                !netplay.peerConnected -> Color(0xFFFF5252)
                netplay.rttMillis <= 5f -> SnesPalette.OnlineNeonGreen
                netplay.rttMillis <= 15f -> SnesPalette.StarGold
                else -> Color(0xFFFF5252)
            }
            val lossColor = if (netplay.lossPercent < 0.5f) SnesPalette.OnlineNeonGreen
            else if (netplay.lossPercent < 3f) SnesPalette.StarGold else Color(0xFFFF5252)
            val lossMs = netplay.lostPackets * 16.67f
            OutlinedPixelText(
                text = String.format("PING %.1f ms", netplay.rttMillis),
                style = hudFont,
                color = pingColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                maxLines = 1
            )
            OutlinedPixelText(
                text = String.format("PÉRDIDA %.1f ms (%.1f%%)", lossMs, netplay.lossPercent),
                style = hudFont,
                color = lossColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HudButtonLabel(
    label: String,
    centerPx: Offset,
    fontSizeSp: Int = 18
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerPx.x - 42.dp.toPx()).roundToInt(),
                    (centerPx.y - 16.dp.toPx()).roundToInt()
                )
            }
            .size(84.dp, 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xE6FFFFFF),
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Editor interactivo de Interfaz (Módulo 2):
 * - Desagrupa por completo X, Y, B y A en 4 elementos independientes (Button_X, Button_Y, Button_B, Button_A).
 * - Incluye también D-PAD, L, R, SELECT, START y los botones de Macro M1, M2, M3, M4.
 * - Permite arrastrar cada botón de forma 100% libre y ajustar su Escala (0.5x..2.0x) y Opacidad (20%..100%).
 * - "POR DEFECTO" recalcula el diamante clásico de SNES en la esquina inferior derecha como coordenadas independientes.
 */
@Composable
private fun ButtonLayoutEditorLayer(
    screenWidthPx: Float,
    screenHeightPx: Float,
    layout: TouchControlsLayout,
    macroSlots: List<MacroSlotConfig>,
    onLayoutChanged: (TouchControlsLayout) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    var selectedBtn by remember { mutableStateOf(HudLayoutManager.ButtonId.BTN_A) }

    fun getScaleFor(id: HudLayoutManager.ButtonId): Float = when (id) {
        HudLayoutManager.ButtonId.D_PAD -> layout.dpadScale
        HudLayoutManager.ButtonId.BTN_L -> layout.lButtonScale
        HudLayoutManager.ButtonId.BTN_R -> layout.rButtonScale
        HudLayoutManager.ButtonId.BTN_SELECT -> layout.selectScale
        HudLayoutManager.ButtonId.BTN_START -> layout.startScale
        HudLayoutManager.ButtonId.BTN_X -> layout.btnXScale
        HudLayoutManager.ButtonId.BTN_Y -> layout.btnYScale
        HudLayoutManager.ButtonId.BTN_B -> layout.btnBScale
        HudLayoutManager.ButtonId.BTN_A -> layout.btnAScale
        HudLayoutManager.ButtonId.MACRO_1 -> layout.macro1Scale
        HudLayoutManager.ButtonId.MACRO_2 -> layout.macro2Scale
        HudLayoutManager.ButtonId.MACRO_3 -> layout.macro3Scale
        HudLayoutManager.ButtonId.MACRO_4 -> layout.macro4Scale
    }

    fun getAlphaFor(id: HudLayoutManager.ButtonId): Float = when (id) {
        HudLayoutManager.ButtonId.D_PAD -> layout.dpadAlpha
        HudLayoutManager.ButtonId.BTN_L -> layout.lButtonAlpha
        HudLayoutManager.ButtonId.BTN_R -> layout.rButtonAlpha
        HudLayoutManager.ButtonId.BTN_SELECT -> layout.selectAlpha
        HudLayoutManager.ButtonId.BTN_START -> layout.startAlpha
        HudLayoutManager.ButtonId.BTN_X -> layout.btnXAlpha
        HudLayoutManager.ButtonId.BTN_Y -> layout.btnYAlpha
        HudLayoutManager.ButtonId.BTN_B -> layout.btnBAlpha
        HudLayoutManager.ButtonId.BTN_A -> layout.btnAAlpha
        HudLayoutManager.ButtonId.MACRO_1 -> layout.macro1Alpha
        HudLayoutManager.ButtonId.MACRO_2 -> layout.macro2Alpha
        HudLayoutManager.ButtonId.MACRO_3 -> layout.macro3Alpha
        HudLayoutManager.ButtonId.MACRO_4 -> layout.macro4Alpha
    }

    fun updateScaleAndAlpha(id: HudLayoutManager.ButtonId, newScale: Float, newAlpha: Float) {
        val s = newScale.coerceIn(0.5f, 2.0f)
        val a = newAlpha.coerceIn(0.2f, 1.0f)
        val next = when (id) {
            HudLayoutManager.ButtonId.D_PAD -> layout.copy(dpadScale = s, dpadAlpha = a)
            HudLayoutManager.ButtonId.BTN_L -> layout.copy(lButtonScale = s, lButtonAlpha = a)
            HudLayoutManager.ButtonId.BTN_R -> layout.copy(rButtonScale = s, rButtonAlpha = a)
            HudLayoutManager.ButtonId.BTN_SELECT -> layout.copy(selectScale = s, selectAlpha = a)
            HudLayoutManager.ButtonId.BTN_START -> layout.copy(startScale = s, startAlpha = a)
            HudLayoutManager.ButtonId.BTN_X -> layout.copy(btnXScale = s, btnXAlpha = a)
            HudLayoutManager.ButtonId.BTN_Y -> layout.copy(btnYScale = s, btnYAlpha = a)
            HudLayoutManager.ButtonId.BTN_B -> layout.copy(btnBScale = s, btnBAlpha = a)
            HudLayoutManager.ButtonId.BTN_A -> layout.copy(btnAScale = s, btnAAlpha = a)
            HudLayoutManager.ButtonId.MACRO_1 -> layout.copy(macro1Scale = s, macro1Alpha = a)
            HudLayoutManager.ButtonId.MACRO_2 -> layout.copy(macro2Scale = s, macro2Alpha = a)
            HudLayoutManager.ButtonId.MACRO_3 -> layout.copy(macro3Scale = s, macro3Alpha = a)
            HudLayoutManager.ButtonId.MACRO_4 -> layout.copy(macro4Scale = s, macro4Alpha = a)
        }
        onLayoutChanged(next)
    }

    val selScale = getScaleFor(selectedBtn)
    val selAlpha = getAlphaFor(selectedBtn)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
    ) {
        // Barra superior del editor individual (Selección + Escala + Opacidad + Por Defecto + Guardar)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SnesPalette.PanelDarkTranslucent)
                .border(2.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedPixelText(
                    text = "BOTÓN: ${selectedBtn.label}",
                    style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                    color = SnesPalette.OnlineNeonGreen
                )

                // Ajuste de Escala individual (0.5x a 2.0x)
                MiniStepControl(
                    label = String.format("ESCALA %.1fx", selScale),
                    onMinus = { updateScaleAndAlpha(selectedBtn, selScale - 0.1f, selAlpha) },
                    onPlus = { updateScaleAndAlpha(selectedBtn, selScale + 0.1f, selAlpha) }
                )

                // Ajuste de Opacidad individual (20% a 100%)
                MiniStepControl(
                    label = "OPACIDAD ${(selAlpha * 100).roundToInt()}%",
                    onMinus = { updateScaleAndAlpha(selectedBtn, selScale, selAlpha - 0.1f) },
                    onPlus = { updateScaleAndAlpha(selectedBtn, selScale, selAlpha + 0.1f) }
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF555566))
                        .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
                        .clickable(onClick = onReset)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    OutlinedPixelText("POR DEFECTO", style = PixelTypography.LabelBold.copy(fontSize = 9.sp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SnesPalette.PlayGreenMain)
                        .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
                        .clickable(onClick = onSave)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    OutlinedPixelText("GUARDAR", style = PixelTypography.LabelBold.copy(fontSize = 10.sp))
                }
            }
        }

        // Cruceta / Stick
        DraggableControlHandle(
            title = "D-PAD",
            normX = layout.dpadX,
            normY = layout.dpadY,
            widthDp = (136f * layout.dpadScale).dp,
            heightDp = (136f * layout.dpadScale).dp,
            isCircular = false,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.D_PAD,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.D_PAD },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(dpadX = nx, dpadY = ny)) }
        )

        // Gatillos L y R
        DraggableControlHandle(
            title = "L",
            normX = layout.lButtonX,
            normY = layout.lButtonY,
            widthDp = (94f * layout.lButtonScale).dp,
            heightDp = (42f * layout.lButtonScale).dp,
            isCircular = false,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_L,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_L },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(lButtonX = nx, lButtonY = ny)) }
        )
        DraggableControlHandle(
            title = "R",
            normX = layout.rButtonX,
            normY = layout.rButtonY,
            widthDp = (94f * layout.rButtonScale).dp,
            heightDp = (42f * layout.rButtonScale).dp,
            isCircular = false,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_R,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_R },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(rButtonX = nx, rButtonY = ny)) }
        )

        // SELECT y START
        DraggableControlHandle(
            title = "SELECT",
            normX = layout.selectX,
            normY = layout.selectY,
            widthDp = (78f * layout.selectScale).dp,
            heightDp = (32f * layout.selectScale).dp,
            isCircular = false,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_SELECT,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_SELECT },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(selectX = nx, selectY = ny)) }
        )
        DraggableControlHandle(
            title = "START",
            normX = layout.startX,
            normY = layout.startY,
            widthDp = (78f * layout.startScale).dp,
            heightDp = (32f * layout.startScale).dp,
            isCircular = false,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_START,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_START },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(startX = nx, startY = ny)) }
        )

        // MÓDULO 2: Botones de Acción X, Y, B, A 100% Independientes (sin contenedor grupal)
        DraggableControlHandle(
            title = "X",
            normX = layout.btnXX,
            normY = layout.btnXY,
            widthDp = (58f * layout.btnXScale).dp,
            heightDp = (58f * layout.btnXScale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_X,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_X },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(btnXX = nx, btnXY = ny)) }
        )
        DraggableControlHandle(
            title = "Y",
            normX = layout.btnYX,
            normY = layout.btnYY,
            widthDp = (58f * layout.btnYScale).dp,
            heightDp = (58f * layout.btnYScale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_Y,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_Y },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(btnYX = nx, btnYY = ny)) }
        )
        DraggableControlHandle(
            title = "B",
            normX = layout.btnBX,
            normY = layout.btnBY,
            widthDp = (58f * layout.btnBScale).dp,
            heightDp = (58f * layout.btnBScale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_B,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_B },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(btnBX = nx, btnBY = ny)) }
        )
        DraggableControlHandle(
            title = "A",
            normX = layout.btnAX,
            normY = layout.btnAY,
            widthDp = (58f * layout.btnAScale).dp,
            heightDp = (58f * layout.btnAScale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.BTN_A,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.BTN_A },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(btnAX = nx, btnAY = ny)) }
        )

        // MÓDULO 3: Botones de Macro Independientes (M1, M2, M3, M4)
        val m1Enabled = macroSlots.getOrNull(0)?.enabled ?: true
        val m2Enabled = macroSlots.getOrNull(1)?.enabled ?: true
        val m3Enabled = macroSlots.getOrNull(2)?.enabled ?: false
        val m4Enabled = macroSlots.getOrNull(3)?.enabled ?: false

        DraggableControlHandle(
            title = if (m1Enabled) "M1" else "M1 (OFF)",
            normX = layout.macro1X,
            normY = layout.macro1Y,
            widthDp = (48f * layout.macro1Scale).dp,
            heightDp = (48f * layout.macro1Scale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.MACRO_1,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.MACRO_1 },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(macro1X = nx, macro1Y = ny)) }
        )
        DraggableControlHandle(
            title = if (m2Enabled) "M2" else "M2 (OFF)",
            normX = layout.macro2X,
            normY = layout.macro2Y,
            widthDp = (48f * layout.macro2Scale).dp,
            heightDp = (48f * layout.macro2Scale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.MACRO_2,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.MACRO_2 },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(macro2X = nx, macro2Y = ny)) }
        )
        DraggableControlHandle(
            title = if (m3Enabled) "M3" else "M3 (OFF)",
            normX = layout.macro3X,
            normY = layout.macro3Y,
            widthDp = (48f * layout.macro3Scale).dp,
            heightDp = (48f * layout.macro3Scale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.MACRO_3,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.MACRO_3 },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(macro3X = nx, macro3Y = ny)) }
        )
        DraggableControlHandle(
            title = if (m4Enabled) "M4" else "M4 (OFF)",
            normX = layout.macro4X,
            normY = layout.macro4Y,
            widthDp = (48f * layout.macro4Scale).dp,
            heightDp = (48f * layout.macro4Scale).dp,
            isCircular = true,
            isSelected = selectedBtn == HudLayoutManager.ButtonId.MACRO_4,
            screenW = screenWidthPx,
            screenH = screenHeightPx,
            onSelect = { selectedBtn = HudLayoutManager.ButtonId.MACRO_4 },
            onMove = { nx, ny -> onLayoutChanged(layout.copy(macro4X = nx, macro4Y = ny)) }
        )
    }
}

@Composable
private fun MiniStepControl(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF3E355A))
                .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                .clickable(onClick = onMinus),
            contentAlignment = Alignment.Center
        ) {
            OutlinedPixelText("-", style = PixelTypography.LabelBold.copy(fontSize = 11.sp))
        }
        OutlinedPixelText(
            text = label,
            style = PixelTypography.LabelBold.copy(fontSize = 9.sp),
            color = Color.White
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF3E355A))
                .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center
        ) {
            OutlinedPixelText("+", style = PixelTypography.LabelBold.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun DraggableControlHandle(
    title: String,
    normX: Float,
    normY: Float,
    widthDp: Dp,
    heightDp: Dp,
    isCircular: Boolean,
    isSelected: Boolean,
    screenW: Float,
    screenH: Float,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    var curX by remember(normX) { mutableStateOf(normX) }
    var curY by remember(normY) { mutableStateOf(normY) }
    val shape = if (isCircular) CircleShape else RoundedCornerShape(12.dp)
    val borderColor = if (isSelected) SnesPalette.StarGold else SnesPalette.OnlineNeonGreen
    val bgColor = if (isSelected) Color(0xAA7B5EA7) else Color(0x665E4B8B)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (curX * screenW).roundToInt(),
                    (curY * screenH).roundToInt()
                )
            }
            .size(widthDp, heightDp)
            .clip(shape)
            .background(bgColor)
            .border(if (isSelected) 2.5.dp else 1.8.dp, borderColor, shape)
            .clickable { onSelect() }
            .pointerInput(screenW, screenH) {
                detectDragGestures(
                    onDragStart = { onSelect() }
                ) { change, dragAmount ->
                    change.consume()
                    curX = (curX + dragAmount.x / screenW).coerceIn(0.01f, 0.97f)
                    curY = (curY + dragAmount.y / screenH).coerceIn(0.05f, 0.94f)
                    onMove(curX, curY)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        OutlinedPixelText(
            text = title,
            style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
            color = if (isSelected) SnesPalette.StarGold else Color.White
        )
    }
}

/**
 * Botones superiores cuadrados traslúcidos (Disquete, Carpeta flecha abajo, >>, ||) de la Imagen 5.
 */
@Composable
private fun TranslucentHudSquareButton(
    highlighted: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (highlighted) Color(0x884F86E8) else Color(0x668898AC))
            .border(
                width = 1.5.dp,
                color = if (highlighted) SnesPalette.OnlineNeonGreen else Color(0x88D0DCE8),
                shape = RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun HudFloppyGlyph() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFFE8EEF5), Offset.Zero, Size(w, h), CornerRadius(3f, 3f))
        drawRect(Color(0xFF4A5A6A), Offset(w * 0.24f, h * 0.08f), Size(w * 0.52f, h * 0.28f))
        drawRect(Color(0xFF4A5A6A), Offset(w * 0.18f, h * 0.52f), Size(w * 0.64f, h * 0.36f))
    }
}

@Composable
private fun HudFolderDownGlyph() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFFE8EEF5), Offset(w * 0.06f, h * 0.22f), Size(w * 0.88f, h * 0.64f), CornerRadius(4f, 4f))
        drawRect(Color(0xFFE8EEF5), Offset(w * 0.08f, h * 0.12f), Size(w * 0.38f, h * 0.16f))
        drawRect(Color(0xFF1E2630), Offset(w * 0.42f, h * 0.38f), Size(w * 0.16f, h * 0.26f))
        val arrowHead = Path().apply {
            moveTo(w * 0.28f, h * 0.60f)
            lineTo(w * 0.72f, h * 0.60f)
            lineTo(w * 0.50f, h * 0.82f)
            close()
        }
        drawPath(arrowHead, Color(0xFF1E2630))
    }
}

@Composable
private fun TranslucentHudHoldButton(
    highlighted: Boolean = false,
    onPressChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (highlighted) Color(0x88E89A3C) else Color(0x668898AC))
            .border(
                width = 1.5.dp,
                color = if (highlighted) SnesPalette.StarGold else Color(0x88D0DCE8),
                shape = RoundedCornerShape(11.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent()
                    if (down.changes.any { it.pressed }) {
                        onPressChanged(true)
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.none { it.pressed }) {
                                break
                            }
                        }
                        onPressChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun HudRewindGlyph(active: Boolean) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val tint = if (active) SnesPalette.StarGold else Color(0xFFE8EEF5)
        val t1 = Path().apply {
            moveTo(w * 0.88f, h * 0.20f)
            lineTo(w * 0.48f, h * 0.50f)
            lineTo(w * 0.88f, h * 0.80f)
            close()
        }
        val t2 = Path().apply {
            moveTo(w * 0.50f, h * 0.20f)
            lineTo(w * 0.10f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.80f)
            close()
        }
        drawPath(t1, tint)
        drawPath(t2, tint)
    }
}

@Composable
private fun HudFastForwardGlyph(active: Boolean) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val tint = if (active) SnesPalette.OnlineNeonGreen else Color(0xFFE8EEF5)
        val t1 = Path().apply {
            moveTo(w * 0.12f, h * 0.20f)
            lineTo(w * 0.52f, h * 0.50f)
            lineTo(w * 0.12f, h * 0.80f)
            close()
        }
        val t2 = Path().apply {
            moveTo(w * 0.50f, h * 0.20f)
            lineTo(w * 0.90f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.80f)
            close()
        }
        drawPath(t1, tint)
        drawPath(t2, tint)
    }
}

@Composable
private fun HudPauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp, 22.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8EEF5))
        )
        Box(
            modifier = Modifier
                .size(6.dp, 22.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8EEF5))
        )
    }
}
