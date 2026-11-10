package com.fdp.multisnes.data.model

import com.fdp.multisnes.core.SnesButtons
import kotlinx.serialization.Serializable

@Serializable
enum class ScreenScaleMode(val label: String) {
    ORIGINAL_4_3("4:3\n(ORIGINAL)"),
    STRETCHED_16_9("16:9\n(ESTIRADO)"),
    PIXEL_PERFECT("PIXEL\nPERFECT")
}

/**
 * MÓDULO 2: Posiciones normalizadas (0.0 a 1.0), escala individual (0.5f a 2.0f) y
 * transparencia (0.2f a 1.0f) para cada botón de forma 100% independiente:
 * - Button_X, Button_Y, Button_B y Button_A desagrupados individualmente.
 * - Botones de Macro M1, M2, M3 y M4 independientes.
 */
@Serializable
data class TouchControlsLayout(
    val dpadX: Float = 0.055f,
    val dpadY: Float = 0.58f,
    val dpadScale: Float = 1.0f,
    val dpadAlpha: Float = 0.75f,

    val lButtonX: Float = 0.045f,
    val lButtonY: Float = 0.36f,
    val lButtonScale: Float = 1.0f,
    val lButtonAlpha: Float = 0.75f,

    val rButtonX: Float = 0.845f,
    val rButtonY: Float = 0.36f,
    val rButtonScale: Float = 1.0f,
    val rButtonAlpha: Float = 0.75f,

    val selectX: Float = 0.38f,
    val selectY: Float = 0.89f,
    val selectScale: Float = 1.0f,
    val selectAlpha: Float = 0.75f,

    val startX: Float = 0.52f,
    val startY: Float = 0.89f,
    val startScale: Float = 1.0f,
    val startAlpha: Float = 0.75f,

    // Botones de acción 100% individuales (por defecto forman el diamante clásico de SNES abajo a la derecha)
    val btnXX: Float = 0.885f,
    val btnXY: Float = 0.555f,
    val btnXScale: Float = 1.0f,
    val btnXAlpha: Float = 0.80f,

    val btnYX: Float = 0.820f,
    val btnYY: Float = 0.690f,
    val btnYScale: Float = 1.0f,
    val btnYAlpha: Float = 0.80f,

    val btnBX: Float = 0.885f,
    val btnBY: Float = 0.825f,
    val btnBScale: Float = 1.0f,
    val btnBAlpha: Float = 0.80f,

    val btnAX: Float = 0.950f,
    val btnAY: Float = 0.690f,
    val btnAScale: Float = 1.0f,
    val btnAAlpha: Float = 0.80f,

    // Botones flotantes de Macro independientes (M1, M2, M3, M4)
    val macro1X: Float = 0.20f,
    val macro1Y: Float = 0.36f,
    val macro1Scale: Float = 1.0f,
    val macro1Alpha: Float = 0.85f,

    val macro2X: Float = 0.28f,
    val macro2Y: Float = 0.36f,
    val macro2Scale: Float = 1.0f,
    val macro2Alpha: Float = 0.85f,

    val macro3X: Float = 0.70f,
    val macro3Y: Float = 0.36f,
    val macro3Scale: Float = 1.0f,
    val macro3Alpha: Float = 0.85f,

    val macro4X: Float = 0.78f,
    val macro4Y: Float = 0.36f,
    val macro4Scale: Float = 1.0f,
    val macro4Alpha: Float = 0.85f
)

/**
 * Configuración global sincronizada con el modal HERRAMIENTAS (Imagen 2) y SharedPreferences.
 */
@Serializable
data class EmulatorSettings(
    val dpadEnabled: Boolean = true,
    val joystickEnabled: Boolean = false,
    val externalControllerEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val vibrationIntensity: Float = 0.68f,
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.95f,
    val scaleMode: ScreenScaleMode = ScreenScaleMode.ORIGINAL_4_3,
    val crtFilterEnabled: Boolean = false,
    val hardwareKeyMap: Map<String, Int> = SnesButtons.defaultHardwareKeyMap(),
    val touchLayout: TouchControlsLayout = TouchControlsLayout(),
    val macroSlots: List<MacroSlotConfig> = emptyList(),
    val safRomTreeUris: List<String> = emptyList()
)
