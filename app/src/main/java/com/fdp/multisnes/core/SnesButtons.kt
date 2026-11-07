package com.fdp.multisnes.core

import android.view.KeyEvent

/**
 * Máscaras de bits oficiales de Super Nintendo (SNES) idénticas a Libretro Snes9x,
 * más Acciones de Sistema (Guardar, Cargar Rápido, Rewind, Fast-Forward) y Macros M1..M4
 * para mapeo total con mando externo Bluetooth/USB.
 */
object SnesButtons {
    const val B      = 1 shl 0
    const val Y      = 1 shl 1
    const val SELECT = 1 shl 2
    const val START  = 1 shl 3
    const val UP     = 1 shl 4
    const val DOWN   = 1 shl 5
    const val LEFT   = 1 shl 6
    const val RIGHT  = 1 shl 7
    const val A      = 1 shl 8
    const val X      = 1 shl 9
    const val L      = 1 shl 10
    const val R      = 1 shl 11

    enum class Action(val bitMask: Int, val displayName: String, val shortTag: String) {
        UP(SnesButtons.UP, "ARRIBA", "UP"),
        DOWN(SnesButtons.DOWN, "ABAJO", "DN"),
        LEFT(SnesButtons.LEFT, "IZQUIERDA", "LF"),
        RIGHT(SnesButtons.RIGHT, "DERECHA", "RG"),
        A(SnesButtons.A, "BOTÓN A", "A"),
        B(SnesButtons.B, "BOTÓN B", "B"),
        X(SnesButtons.X, "BOTÓN X", "X"),
        Y(SnesButtons.Y, "BOTÓN Y", "Y"),
        L(SnesButtons.L, "GATILLO L", "L"),
        R(SnesButtons.R, "GATILLO R", "R"),
        START(SnesButtons.START, "START", "ST"),
        SELECT(SnesButtons.SELECT, "SELECT", "SL")
    }

    /**
     * TAREA 4: Acciones de Sistema en partida vinculables a cualquier botón físico, gatillo (L2/R2) o stick (L3/R3).
     */
    enum class SystemBindAction(val keyName: String, val displayName: String) {
        SAVE_STATE("SYS_SAVE_STATE", "GUARDAR PARTIDA"),
        LOAD_LATEST("SYS_LOAD_LATEST", "CARGAR PARTIDA RÁPIDA"),
        REWIND("SYS_REWIND", "RETROCEDER TIEMPO (REWIND)"),
        FAST_FORWARD("SYS_FAST_FORWARD", "ALTERNAR VELOCIDAD (TURBO)")
    }

    enum class MacroBindAction(val keyName: String, val displayName: String) {
        MACRO_1("MACRO_1", "MACRO 1 (M1)"),
        MACRO_2("MACRO_2", "MACRO 2 (M2)"),
        MACRO_3("MACRO_3", "MACRO 3 (M3)"),
        MACRO_4("MACRO_4", "MACRO 4 (M4)")
    }

    fun formatBitMaskSummary(mask: Int): String {
        if (mask == 0) return "NINGUNO"
        val tags = Action.entries.filter { (mask and it.bitMask) != 0 }.map { it.shortTag }
        return tags.joinToString("+")
    }

    fun defaultHardwareKeyMap(): Map<String, Int> = mapOf(
        Action.UP.name to KeyEvent.KEYCODE_DPAD_UP,
        Action.DOWN.name to KeyEvent.KEYCODE_DPAD_DOWN,
        Action.LEFT.name to KeyEvent.KEYCODE_DPAD_LEFT,
        Action.RIGHT.name to KeyEvent.KEYCODE_DPAD_RIGHT,
        Action.B.name to KeyEvent.KEYCODE_BUTTON_A,      // Posición inferior mando moderno -> B SNES
        Action.A.name to KeyEvent.KEYCODE_BUTTON_B,      // Posición derecha mando moderno -> A SNES
        Action.Y.name to KeyEvent.KEYCODE_BUTTON_X,      // Posición izquierda mando moderno -> Y SNES
        Action.X.name to KeyEvent.KEYCODE_BUTTON_Y,      // Posición superior mando moderno -> X SNES
        Action.L.name to KeyEvent.KEYCODE_BUTTON_L1,
        Action.R.name to KeyEvent.KEYCODE_BUTTON_R1,
        Action.START.name to KeyEvent.KEYCODE_BUTTON_START,
        Action.SELECT.name to KeyEvent.KEYCODE_BUTTON_SELECT,
        // Acciones de Sistema por defecto en L2, R2, L3, R3
        SystemBindAction.REWIND.keyName to KeyEvent.KEYCODE_BUTTON_L2,
        SystemBindAction.FAST_FORWARD.keyName to KeyEvent.KEYCODE_BUTTON_R2,
        SystemBindAction.SAVE_STATE.keyName to KeyEvent.KEYCODE_BUTTON_THUMBL,
        SystemBindAction.LOAD_LATEST.keyName to KeyEvent.KEYCODE_BUTTON_THUMBR,
        // Ranuras de Macros (M1..M4)
        MacroBindAction.MACRO_1.keyName to KeyEvent.KEYCODE_BUTTON_C,
        MacroBindAction.MACRO_2.keyName to KeyEvent.KEYCODE_BUTTON_Z,
        MacroBindAction.MACRO_3.keyName to KeyEvent.KEYCODE_BUTTON_1,
        MacroBindAction.MACRO_4.keyName to KeyEvent.KEYCODE_BUTTON_2
    )
}
