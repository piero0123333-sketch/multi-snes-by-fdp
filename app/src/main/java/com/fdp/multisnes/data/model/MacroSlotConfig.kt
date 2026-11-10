package com.fdp.multisnes.data.model

import kotlinx.serialization.Serializable

/**
 * Configuración de cada botón de Macro (M1, M2, M3, M4):
 * - enabled: si el botón flotante Mx aparece en pantalla y está activo
 * - targetButtonsMask: máscara de 1 hasta 3 botones SNES simultáneos (A, B, X, Y, L, R, START, SELECT, D-PAD)
 * - autoHoldMode: false = Modo 1 Repetición Automática (Turbo cada intervalMs), true = Modo 2 Mantener Presionado (Auto-Hold)
 * - intervalMs: intervalo de repetición en ms (entre 10 ms y 500 ms)
 * - comboDirectMode: true = Modo 3 Combo Directo (los hasta 3 botones se presionan juntos mientras
 *   mantengas pulsado el macro y se sueltan al soltarlo). Tiene prioridad sobre autoHoldMode.
 */
@Serializable
data class MacroSlotConfig(
    val slotIndex: Int = 0,
    val label: String = "M1",
    val enabled: Boolean = false,
    val targetButtonsMask: Int = 0,
    val autoHoldMode: Boolean = false,
    val intervalMs: Int = 55,
    val comboDirectMode: Boolean = false
)
