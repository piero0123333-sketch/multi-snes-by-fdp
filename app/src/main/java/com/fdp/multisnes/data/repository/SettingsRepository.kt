package com.fdp.multisnes.data.repository

import android.content.Context
import com.fdp.multisnes.core.SnesButtons
import com.fdp.multisnes.data.model.EmulatorSettings
import com.fdp.multisnes.data.model.MacroSlotConfig
import com.fdp.multisnes.data.model.ScreenScaleMode
import com.fdp.multisnes.data.model.TouchControlsLayout
import com.fdp.multisnes.input.HudLayoutManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repositorio reactivo de todas las opciones del menú HERRAMIENTAS (Imagen 2)
 * sincronizado con SharedPreferences para el layout individual (Módulo 2) y mapeo de mandos/macros (Módulo 3).
 */
class SettingsRepository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val settingsFile = File(context.filesDir, "emulator_settings_v2.json")
    val hudLayoutManager = HudLayoutManager(context)

    private val _settings = MutableStateFlow(loadInitial())
    val settings: StateFlow<EmulatorSettings> = _settings.asStateFlow()

    private fun loadInitial(): EmulatorSettings {
        val base = if (settingsFile.exists()) {
            runCatching {
                json.decodeFromString<EmulatorSettings>(settingsFile.readText())
            }.getOrDefault(EmulatorSettings())
        } else {
            EmulatorSettings()
        }
        val migratedLayout = hudLayoutManager.loadFromSharedPreferences(base.touchLayout)
        val result = base.copy(touchLayout = migratedLayout)
        if (migratedLayout != base.touchLayout) {
            runCatching {
                settingsFile.writeText(json.encodeToString(result))
            }
        }
        return result
    }

    fun update(transform: (EmulatorSettings) -> EmulatorSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        runCatching {
            settingsFile.writeText(json.encodeToString(next))
        }
    }

    fun syncMacroSlots(slots: List<MacroSlotConfig>, keyMap: Map<String, Int>? = null) = update { current ->
        current.copy(
            macroSlots = slots,
            hardwareKeyMap = keyMap ?: current.hardwareKeyMap
        )
    }

    fun setDpadEnabled(enabled: Boolean) = update { current ->
        current.copy(
            dpadEnabled = enabled,
            joystickEnabled = if (enabled) false else current.joystickEnabled
        )
    }

    fun setJoystickEnabled(enabled: Boolean) = update { current ->
        current.copy(
            joystickEnabled = enabled,
            dpadEnabled = if (enabled) false else current.dpadEnabled
        )
    }

    fun setExternalControllerEnabled(enabled: Boolean) = update {
        it.copy(externalControllerEnabled = enabled)
    }

    fun setVibration(enabled: Boolean, intensity: Float = _settings.value.vibrationIntensity) = update {
        it.copy(vibrationEnabled = enabled, vibrationIntensity = intensity.coerceIn(0f, 1f))
    }

    fun setSound(enabled: Boolean, volume: Float = _settings.value.soundVolume) = update {
        it.copy(soundEnabled = enabled, soundVolume = volume.coerceIn(0f, 1f))
    }

    fun setScaleMode(mode: ScreenScaleMode) = update {
        it.copy(scaleMode = mode)
    }

    fun toggleCrtFilter(): Boolean {
        var newState = false
        update {
            newState = !it.crtFilterEnabled
            it.copy(crtFilterEnabled = newState)
        }
        return newState
    }

    fun bindHardwareKey(actionName: String, keyCode: Int) = update { current ->
        val nextMap = current.hardwareKeyMap.toMutableMap()
        nextMap[actionName] = keyCode
        current.copy(hardwareKeyMap = nextMap)
    }

    fun resetHardwareKeys() = update { current ->
        current.copy(hardwareKeyMap = SnesButtons.defaultHardwareKeyMap())
    }

    fun updateTouchLayout(layout: TouchControlsLayout) {
        hudLayoutManager.saveToSharedPreferences(layout)
        update { it.copy(touchLayout = layout) }
    }

    fun resetTouchLayoutToDefaultDiamond(): TouchControlsLayout {
        val defaults = hudLayoutManager.resetToDefaults()
        update { it.copy(touchLayout = defaults) }
        return defaults
    }

    fun addSafTreeUri(uriString: String) = update { current ->
        if (uriString in current.safRomTreeUris) current
        else current.copy(safRomTreeUris = current.safRomTreeUris + uriString)
    }
}
