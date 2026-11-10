package com.fdp.multisnes.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.fdp.multisnes.core.SnesButtons;
import com.fdp.multisnes.core.SnesNativeBridge;
import com.fdp.multisnes.data.model.MacroSlotConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MÓDULO 3: Motor de Macros (Turbo / Auto-Hold) y Mapeo Completo de Mando Físico (Bluetooth/USB).
 * - Soporta todos los botones de SNES (A, B, X, Y, L, R, START, SELECT, UP, DOWN, LEFT, RIGHT).
 * - Modo 1 (TURBO / Auto-Fire): Alterna nativeSetButtonState(buttonId, true/false) cada X ms (10..500 ms).
 * - Modo 2 (AUTO-HOLD / Toggle): Mantiene el botón en estado PRESSED (true) hasta volver a tocar el macro.
 * - Soporta mapeo de mando externo (SOURCE_GAMEPAD / SOURCE_JOYSTICK) tanto a botones SNES como a M1..M4.
 */
public final class MacroEngine {

    public static final String PREFS_NAME = "multisnes_macros_and_gamepad_prefs";
    public static final int MACRO_COUNT = 4;
    public static final int MIN_INTERVAL_MS = 10;
    public static final int MAX_INTERVAL_MS = 500;

    public static final String ACTION_MACRO_1 = "MACRO_1";
    public static final String ACTION_MACRO_2 = "MACRO_2";
    public static final String ACTION_MACRO_3 = "MACRO_3";
    public static final String ACTION_MACRO_4 = "MACRO_4";

    public static final String ACTION_SYS_SAVE_STATE = "SYS_SAVE_STATE";
    public static final String ACTION_SYS_LOAD_LATEST = "SYS_LOAD_LATEST";
    public static final String ACTION_SYS_REWIND = "SYS_REWIND";
    public static final String ACTION_SYS_FAST_FORWARD = "SYS_FAST_FORWARD";

    public enum MacroMode {
        TURBO_REPEAT,
        AUTO_HOLD
    }

    public interface MacroStateListener {
        void onMacroActiveStateChanged(boolean[] activeStates4);
    }

    public interface SystemActionListener {
        void onSaveStateRequested();
        void onLoadLatestStateRequested();
        void onRewindStateChanged(boolean rewinding);
        void onToggleFastForwardRequested();
    }

    private final Context appContext;
    private final SnesNativeBridge nativeBridge;
    private final SharedPreferences prefs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final MacroSlotConfig[] slots = new MacroSlotConfig[MACRO_COUNT];
    private final boolean[] slotActive = new boolean[MACRO_COUNT];
    private final boolean[] turboPhaseHigh = new boolean[MACRO_COUNT];
    private final long[] pressTimestampMs = new long[MACRO_COUNT];
    private final ScheduledFuture<?>[] turboTasks = new ScheduledFuture<?>[MACRO_COUNT];

    private boolean l2TriggerDown = false;
    private boolean r2TriggerDown = false;
    private volatile int activePlayerPort = 0;
    private MacroStateListener stateListener;

    public MacroEngine(Context context, SnesNativeBridge nativeBridge) {
        this.appContext = context.getApplicationContext();
        this.nativeBridge = nativeBridge;
        this.prefs = this.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadSlotsFromPrefs();
    }

    public void setActivePlayerPort(int port) {
        this.activePlayerPort = (port == 1) ? 1 : 0;
    }

    public void setStateListener(MacroStateListener listener) {
        this.stateListener = listener;
    }

    public synchronized List<MacroSlotConfig> getSlotConfigs() {
        List<MacroSlotConfig> list = new ArrayList<MacroSlotConfig>(MACRO_COUNT);
        for (int i = 0; i < MACRO_COUNT; i++) {
            list.add(slots[i]);
        }
        return list;
    }

    public synchronized boolean[] getEnabledMacrosArray() {
        boolean[] arr = new boolean[MACRO_COUNT];
        for (int i = 0; i < MACRO_COUNT; i++) {
            arr[i] = slots[i] != null && slots[i].getEnabled();
        }
        return arr;
    }

    public synchronized boolean[] getActiveMacrosArray() {
        return slotActive.clone();
    }

    public synchronized void updateSlotConfig(int slotIndex, MacroSlotConfig newConfig) {
        if (slotIndex < 0 || slotIndex >= MACRO_COUNT || newConfig == null) return;
        stopSlotInternal(slotIndex);

        int clampedInterval = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, newConfig.getIntervalMs()));
        MacroSlotConfig sanitized = new MacroSlotConfig(
                slotIndex,
                newConfig.getLabel(),
                newConfig.getEnabled(),
                SnesButtons.clampComboMask(newConfig.getTargetButtonsMask()),
                newConfig.getAutoHoldMode(),
                clampedInterval,
                newConfig.getComboDirectMode()
        );
        slots[slotIndex] = sanitized;
        saveSlotToPrefs(slotIndex, sanitized);
        notifyActiveChanged();
    }

    /**
     * Procesa la pulsación/liberación de un botón de Macro (M1..M4), ya sea desde
     * la pantalla táctil o desde un botón físico de un mando Bluetooth/USB.
     */
    public synchronized void onMacroTriggerEvent(int slotIndex, boolean pressedDown) {
        if (slotIndex < 0 || slotIndex >= MACRO_COUNT) return;
        MacroSlotConfig cfg = slots[slotIndex];
        if (cfg == null || !cfg.getEnabled() || cfg.getTargetButtonsMask() == 0) return;

        if (cfg.getComboDirectMode()) {
            // MODO 3: Combo Directo — los hasta 3 botones se presionan a la vez mientras se mantiene el macro
            if (pressedDown) {
                if (!slotActive[slotIndex]) {
                    slotActive[slotIndex] = true;
                    applyTargetBitsToNative(cfg.getTargetButtonsMask(), true, false);
                    notifyActiveChanged();
                }
            } else if (slotActive[slotIndex]) {
                stopSlotInternal(slotIndex);
                notifyActiveChanged();
            }
            return;
        }

        if (cfg.getAutoHoldMode()) {
            // MODO 2: Mantener Presionado (Toggle / Auto-Hold)
            if (pressedDown) {
                boolean nextState = !slotActive[slotIndex];
                if (nextState) {
                    slotActive[slotIndex] = true;
                    applyTargetBitsToNative(cfg.getTargetButtonsMask(), true, false);
                } else {
                    stopSlotInternal(slotIndex);
                }
                notifyActiveChanged();
            }
        } else {
            // MODO 1: Repetición Automática (Turbo / Auto-Fire cada X ms)
            if (pressedDown) {
                pressTimestampMs[slotIndex] = System.currentTimeMillis();
                if (slotActive[slotIndex]) {
                    // Si estaba fijado por toque rápido previo, un nuevo toque lo apaga
                    stopSlotInternal(slotIndex);
                    notifyActiveChanged();
                } else {
                    startTurboTaskLocked(slotIndex, cfg);
                    notifyActiveChanged();
                }
            } else {
                // Si el usuario mantuvo presionado (>220 ms), al soltar el dedo/botón se detiene;
                // si fue un toque corto (<220 ms), queda enganchado en Turbo hasta que vuelva a tocarlo.
                long heldMs = System.currentTimeMillis() - pressTimestampMs[slotIndex];
                if (slotActive[slotIndex] && heldMs >= 220L) {
                    stopSlotInternal(slotIndex);
                    notifyActiveChanged();
                }
            }
        }
    }

    private void startTurboTaskLocked(final int slotIndex, final MacroSlotConfig cfg) {
        stopSlotInternal(slotIndex);
        slotActive[slotIndex] = true;
        turboPhaseHigh[slotIndex] = true;
        final int mask = cfg.getTargetButtonsMask();
        final int intervalMs = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, cfg.getIntervalMs()));

        // Aplicar pulso inicial inmediato en el core C++
        applyTargetBitsToNative(mask, true, false);

        turboTasks[slotIndex] = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                synchronized (MacroEngine.this) {
                    if (!slotActive[slotIndex]) return;
                    boolean nextHigh = !turboPhaseHigh[slotIndex];
                    turboPhaseHigh[slotIndex] = nextHigh;
                    applyTargetBitsToNative(mask, nextHigh, !nextHigh);
                }
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopSlotInternal(int slotIndex) {
        ScheduledFuture<?> task = turboTasks[slotIndex];
        if (task != null) {
            task.cancel(false);
            turboTasks[slotIndex] = null;
        }
        MacroSlotConfig cfg = slots[slotIndex];
        slotActive[slotIndex] = false;
        turboPhaseHigh[slotIndex] = false;

        if (cfg != null && cfg.getTargetButtonsMask() != 0) {
            applyTargetBitsToNative(cfg.getTargetButtonsMask(), false, false);
        }
        // Re-aplicar cualquier otro macro que siga activo para no apagar botones compartidos
        for (int i = 0; i < MACRO_COUNT; i++) {
            if (i != slotIndex && slotActive[i] && slots[i] != null) {
                boolean isHigh = slots[i].getAutoHoldMode() || slots[i].getComboDirectMode() || turboPhaseHigh[i];
                applyTargetBitsToNative(slots[i].getTargetButtonsMask(), isHigh, !isHigh);
            }
        }
    }

    public synchronized void stopAllMacros() {
        for (int i = 0; i < MACRO_COUNT; i++) {
            ScheduledFuture<?> task = turboTasks[i];
            if (task != null) {
                task.cancel(false);
                turboTasks[i] = null;
            }
            slotActive[i] = false;
            turboPhaseHigh[i] = false;
        }
        nativeBridge.clearAllMacros(0);
        nativeBridge.clearAllMacros(1);
        notifyActiveChanged();
    }

    /**
     * Invoca tanto nativeSetButtonState(buttonId, true/false) para cada bit individual de Libretro
     * como nativeSetMacroMaskState para sincronización atómica con el bucle de 60Hz y Netplay.
     */
    private void applyTargetBitsToNative(int bitMask, boolean pressed, boolean isTurboLowPhase) {
        int port = activePlayerPort;
        for (int buttonId = 0; buttonId <= 11; buttonId++) {
            if ((bitMask & (1 << buttonId)) != 0) {
                nativeBridge.setButtonState(buttonId, pressed, port);
            }
        }
        nativeBridge.setMacroMaskState(bitMask, pressed, isTurboLowPhase, port);
    }

    private void notifyActiveChanged() {
        if (stateListener != null) {
            stateListener.onMacroActiveStateChanged(slotActive.clone());
        }
    }

    // --- Emparejamiento y Mapeo Completo de Mando Externo (KeyEvent / MotionEvent) ---

    public static boolean isGamepadOrJoystick(InputEvent event) {
        if (event == null) return false;
        int src = event.getSource();
        return (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    /**
     * Devuelve el nombre de un mando Bluetooth/USB conectado si existe, o null si no hay ninguno.
     */
    public static String detectConnectedControllerName() {
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev == null) continue;
            int sources = dev.getSources();
            boolean isGamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (isGamepad && !dev.isVirtual()) {
                return dev.getName();
            }
        }
        return null;
    }

    /**
     * Procesa un código de tecla física (KeyEvent) verificando si está asignado a alguno de los
     * 4 Macros (MACRO_1..MACRO_4) o a las 4 Acciones de Sistema (Guardar, Cargar Rápido, Rewind, Fast-Forward).
     */
    public boolean handleHardwareKeyForSystemAndMacros(
            int keyCode,
            boolean isKeyDown,
            Map<String, Integer> keyMap,
            SystemActionListener sysListener
    ) {
        if (keyMap == null) return false;
        boolean handled = false;

        if (sysListener != null) {
            Integer saveCode = keyMap.get(ACTION_SYS_SAVE_STATE);
            if (saveCode != null && saveCode == keyCode) {
                if (isKeyDown) sysListener.onSaveStateRequested();
                handled = true;
            }
            Integer loadCode = keyMap.get(ACTION_SYS_LOAD_LATEST);
            if (loadCode != null && loadCode == keyCode) {
                if (isKeyDown) sysListener.onLoadLatestStateRequested();
                handled = true;
            }
            Integer rewindCode = keyMap.get(ACTION_SYS_REWIND);
            if (rewindCode != null && rewindCode == keyCode) {
                sysListener.onRewindStateChanged(isKeyDown);
                handled = true;
            }
            Integer ffCode = keyMap.get(ACTION_SYS_FAST_FORWARD);
            if (ffCode != null && ffCode == keyCode) {
                if (isKeyDown) sysListener.onToggleFastForwardRequested();
                handled = true;
            }
        }

        String[] macroKeys = new String[]{ACTION_MACRO_1, ACTION_MACRO_2, ACTION_MACRO_3, ACTION_MACRO_4};
        for (int i = 0; i < MACRO_COUNT; i++) {
            Integer boundCode = keyMap.get(macroKeys[i]);
            if (boundCode != null && boundCode == keyCode) {
                onMacroTriggerEvent(i, isKeyDown);
                handled = true;
            }
        }
        return handled;
    }

    public boolean handleHardwareKeyForMacros(int keyCode, boolean isKeyDown, Map<String, Integer> keyMap) {
        return handleHardwareKeyForSystemAndMacros(keyCode, isKeyDown, keyMap, null);
    }

    /**
     * Convierte los gatillos analógicos (L2/R2 / AXIS_LTRIGGER / AXIS_RTRIGGER / AXIS_GAS / AXIS_BRAKE)
     * de un MotionEvent en pulsaciones digitales (KEYCODE_BUTTON_L2 / KEYCODE_BUTTON_R2) para que
     * también puedan mapearse a botones SNES, Acciones de Sistema (Rewind/Guardar/Cargar/Turbo) o Macros M1..M4.
     */
    public int extractTriggerButtonsMask(
            MotionEvent ev,
            Map<String, Integer> keyMap,
            SystemActionListener sysListener
    ) {
        if (ev == null || keyMap == null) return 0;
        float lTrig = Math.max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE));
        float rTrig = Math.max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS));

        boolean lNow = lTrig > 0.45f;
        boolean rNow = rTrig > 0.45f;

        if (lNow != l2TriggerDown) {
            l2TriggerDown = lNow;
            handleHardwareKeyForSystemAndMacros(KeyEvent.KEYCODE_BUTTON_L2, lNow, keyMap, sysListener);
        }
        if (rNow != r2TriggerDown) {
            r2TriggerDown = rNow;
            handleHardwareKeyForSystemAndMacros(KeyEvent.KEYCODE_BUTTON_R2, rNow, keyMap, sysListener);
        }

        int mask = 0;
        for (SnesButtons.Action action : SnesButtons.Action.getEntries()) {
            Integer code = keyMap.get(action.name());
            if (code == null) continue;
            if (lNow && code == KeyEvent.KEYCODE_BUTTON_L2) {
                mask |= action.getBitMask();
            }
            if (rNow && code == KeyEvent.KEYCODE_BUTTON_R2) {
                mask |= action.getBitMask();
            }
        }
        return mask;
    }

    public int extractTriggerButtonsMask(MotionEvent ev, Map<String, Integer> keyMap) {
        return extractTriggerButtonsMask(ev, keyMap, null);
    }

    public Map<String, Integer> loadHardwareKeyMap(Map<String, Integer> fallbackDefaults) {
        Map<String, Integer> result = new HashMap<String, Integer>(fallbackDefaults);
        if (!result.containsKey(ACTION_SYS_REWIND)) result.put(ACTION_SYS_REWIND, KeyEvent.KEYCODE_BUTTON_L2);
        if (!result.containsKey(ACTION_SYS_FAST_FORWARD)) result.put(ACTION_SYS_FAST_FORWARD, KeyEvent.KEYCODE_BUTTON_R2);
        if (!result.containsKey(ACTION_SYS_SAVE_STATE)) result.put(ACTION_SYS_SAVE_STATE, KeyEvent.KEYCODE_BUTTON_THUMBL);
        if (!result.containsKey(ACTION_SYS_LOAD_LATEST)) result.put(ACTION_SYS_LOAD_LATEST, KeyEvent.KEYCODE_BUTTON_THUMBR);
        if (!result.containsKey(ACTION_MACRO_1)) result.put(ACTION_MACRO_1, KeyEvent.KEYCODE_BUTTON_C);
        if (!result.containsKey(ACTION_MACRO_2)) result.put(ACTION_MACRO_2, KeyEvent.KEYCODE_BUTTON_Z);
        if (!result.containsKey(ACTION_MACRO_3)) result.put(ACTION_MACRO_3, KeyEvent.KEYCODE_BUTTON_1);
        if (!result.containsKey(ACTION_MACRO_4)) result.put(ACTION_MACRO_4, KeyEvent.KEYCODE_BUTTON_2);

        for (String key : new ArrayList<String>(result.keySet())) {
            int saved = prefs.getInt("keymap_" + key, result.get(key));
            result.put(key, saved);
        }
        return result;
    }

    public void saveHardwareKeyBinding(String actionName, int keyCode) {
        prefs.edit().putInt("keymap_" + actionName, keyCode).apply();
    }

    public void resetHardwareKeyBindings(Map<String, Integer> defaults) {
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
            editor.putInt("keymap_" + entry.getKey(), entry.getValue());
        }
        editor.putInt("keymap_" + ACTION_SYS_REWIND, KeyEvent.KEYCODE_BUTTON_L2);
        editor.putInt("keymap_" + ACTION_SYS_FAST_FORWARD, KeyEvent.KEYCODE_BUTTON_R2);
        editor.putInt("keymap_" + ACTION_SYS_SAVE_STATE, KeyEvent.KEYCODE_BUTTON_THUMBL);
        editor.putInt("keymap_" + ACTION_SYS_LOAD_LATEST, KeyEvent.KEYCODE_BUTTON_THUMBR);
        editor.putInt("keymap_" + ACTION_MACRO_1, KeyEvent.KEYCODE_BUTTON_C);
        editor.putInt("keymap_" + ACTION_MACRO_2, KeyEvent.KEYCODE_BUTTON_Z);
        editor.putInt("keymap_" + ACTION_MACRO_3, KeyEvent.KEYCODE_BUTTON_1);
        editor.putInt("keymap_" + ACTION_MACRO_4, KeyEvent.KEYCODE_BUTTON_2);
        editor.apply();
    }

    private void loadSlotsFromPrefs() {
        // Configuración inicial útil por defecto (M1=Turbo Y 50ms, M2=Turbo B 50ms, M3=Auto-Hold R, M4=Combo L+R)
        int[] defaultMasks = new int[]{
                SnesButtons.Y,
                SnesButtons.B,
                SnesButtons.R,
                SnesButtons.L | SnesButtons.R
        };
        boolean[] defaultHold = new boolean[]{false, false, true, false};
        int[] defaultIntervals = new int[]{55, 55, 100, 80};

        for (int i = 0; i < MACRO_COUNT; i++) {
            String p = "macro_slot_" + i + "_";
            boolean enabled = prefs.getBoolean(p + "enabled", false);
            int mask = prefs.getInt(p + "mask", defaultMasks[i]);
            boolean holdMode = prefs.getBoolean(p + "hold_mode", defaultHold[i]);
            int interval = prefs.getInt(p + "interval_ms", defaultIntervals[i]);
            boolean comboDirect = prefs.getBoolean(p + "combo_direct", false);
            slots[i] = new MacroSlotConfig(
                    i,
                    "M" + (i + 1),
                    enabled,
                    SnesButtons.clampComboMask(mask),
                    holdMode,
                    Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, interval)),
                    comboDirect
            );
        }
    }

    private void saveSlotToPrefs(int index, MacroSlotConfig cfg) {
        String p = "macro_slot_" + index + "_";
        prefs.edit()
                .putBoolean(p + "enabled", cfg.getEnabled())
                .putInt(p + "mask", cfg.getTargetButtonsMask())
                .putBoolean(p + "hold_mode", cfg.getAutoHoldMode())
                .putInt(p + "interval_ms", cfg.getIntervalMs())
                .putBoolean(p + "combo_direct", cfg.getComboDirectMode())
                .apply();
    }
}
