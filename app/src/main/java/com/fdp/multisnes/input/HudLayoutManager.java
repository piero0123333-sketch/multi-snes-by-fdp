package com.fdp.multisnes.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.fdp.multisnes.data.model.TouchControlsLayout;

/**
 * MÓDULO 2: Editor y Gestor de Interfaz con Botones 100% Individuales.
 * - Desagrupa el bloque de acción (X, Y, B, A) en 4 elementos independientes (Button_X, Button_Y, Button_B, Button_A).
 * - Añade los 4 botones virtuales de Macro independientes (M1, M2, M3, M4).
 * - Soporta posX, posY, scale (0.5f a 2.0f) y alpha (0.2f a 1.0f) individuales por botón en SharedPreferences.
 */
public final class HudLayoutManager {

    public static final String PREFS_NAME = "multisnes_hud_layout_prefs";
    private static final String KEY_MIGRATED_V2 = "migrated_individual_abxy_v2";
    private static final String KEY_MIGRATED_V3 = "migrated_individual_abxy_v3";

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    public enum ButtonId {
        D_PAD("btn_dpad", "D-PAD", true, 140f, 140f),
        BTN_L("btn_l", "L", false, 96f, 42f),
        BTN_R("btn_r", "R", false, 96f, 42f),
        BTN_SELECT("btn_select", "SELECT", false, 78f, 32f),
        BTN_START("btn_start", "START", false, 78f, 32f),
        BTN_X("btn_x", "X", true, 58f, 58f),
        BTN_Y("btn_y", "Y", true, 58f, 58f),
        BTN_B("btn_b", "B", true, 58f, 58f),
        BTN_A("btn_a", "A", true, 58f, 58f),
        MACRO_1("btn_m1", "M1", true, 48f, 48f),
        MACRO_2("btn_m2", "M2", true, 48f, 48f),
        MACRO_3("btn_m3", "M3", true, 48f, 48f),
        MACRO_4("btn_m4", "M4", true, 48f, 48f);

        public final String prefPrefix;
        public final String label;
        public final boolean isCircular;
        public final float baseWidthDp;
        public final float baseHeightDp;

        ButtonId(String prefPrefix, String label, boolean isCircular, float baseWidthDp, float baseHeightDp) {
            this.prefPrefix = prefPrefix;
            this.label = label;
            this.isCircular = isCircular;
            this.baseWidthDp = baseWidthDp;
            this.baseHeightDp = baseHeightDp;
        }
    }

    public static final class ButtonConfig {
        public float posX;   // Centro normalizado X (0.0 .. 1.0)
        public float posY;   // Centro normalizado Y (0.0 .. 1.0)
        public float scale;  // Escala individual (0.5f .. 2.0f)
        public float alpha;  // Transparencia individual (0.2f .. 1.0f)

        public ButtonConfig(float posX, float posY, float scale, float alpha) {
            this.posX = clamp(posX, 0.03f, 0.97f);
            this.posY = clamp(posY, 0.08f, 0.95f);
            this.scale = clamp(scale, MIN_SCALE, MAX_SCALE);
            this.alpha = clamp(alpha, 0.2f, 1.0f);
        }

        public RectF computeBoundsPx(ButtonId id, float screenW, float screenH, float density) {
            float w = id.baseWidthDp * density * scale;
            float h = id.baseHeightDp * density * scale;
            float left = posX * screenW;
            float top = posY * screenH;
            return new RectF(left, top, left + w, top + h);
        }

        public boolean hitTest(ButtonId id, float touchX, float touchY, float screenW, float screenH, float density) {
            RectF rect = computeBoundsPx(id, screenW, screenH, density);
            if (id.isCircular) {
                float cx = rect.centerX();
                float cy = rect.centerY();
                float radius = (Math.max(rect.width(), rect.height()) / 2f) * 1.18f;
                float dx = touchX - cx;
                float dy = touchY - cy;
                return (dx * dx + dy * dy) <= (radius * radius);
            } else {
                RectF expanded = new RectF(rect.left - 12f, rect.top - 12f, rect.right + 12f, rect.bottom + 12f);
                return expanded.contains(touchX, touchY);
            }
        }
    }

    private final SharedPreferences prefs;
    private ButtonId activeDraggedButton = null;
    private float dragDeltaX = 0f;
    private float dragDeltaY = 0f;

    public HudLayoutManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Calcula las coordenadas iniciales por defecto formando el diamante clásico de SNES en la
     * esquina inferior derecha, pero guardando X, Y, B y A como coordenadas 100% independientes.
     */
    public static TouchControlsLayout createDefaultDiamondLayout() {
        final float diamondCenterX = 0.885f;
        final float diamondCenterY = 0.690f;
        final float offsetX = 0.065f;
        final float offsetY = 0.135f;

        return new TouchControlsLayout(
                0.055f, 0.58f, 1.0f, 0.75f, // D-PAD (pos X/Y normalizado)
                0.045f, 0.36f, 1.0f, 0.75f, // Botón L
                0.845f, 0.36f, 1.0f, 0.75f, // Botón R
                0.380f, 0.89f, 1.0f, 0.75f, // Botón SELECT
                0.520f, 0.89f, 1.0f, 0.75f, // Botón START
                diamondCenterX, diamondCenterY - offsetY, 1.0f, 0.80f, // Button_X (Arriba)
                diamondCenterX - offsetX, diamondCenterY, 1.0f, 0.80f, // Button_Y (Izquierda)
                diamondCenterX, diamondCenterY + offsetY, 1.0f, 0.80f, // Button_B (Abajo)
                diamondCenterX + offsetX, diamondCenterY, 1.0f, 0.80f, // Button_A (Derecha)
                0.20f, 0.36f, 1.0f, 0.85f, // Macro M1
                0.28f, 0.36f, 1.0f, 0.85f, // Macro M2
                0.70f, 0.36f, 1.0f, 0.85f, // Macro M3
                0.78f, 0.36f, 1.0f, 0.85f  // Macro M4
        );
    }

    /**
     * Carga el layout desde SharedPreferences aplicando migración automática si existían
     * las coordenadas grupales antiguas o si se actualizan los defaults ergonómicos V3.
     */
    public TouchControlsLayout loadFromSharedPreferences(TouchControlsLayout legacyCandidate) {
        TouchControlsLayout defaults = createDefaultDiamondLayout();

        if (!prefs.getBoolean(KEY_MIGRATED_V2, false)) {
            migrateLegacyGroupIfNeeded(legacyCandidate, defaults);
        }

        if (!prefs.getBoolean(KEY_MIGRATED_V3, false)) {
            // Actualizar posiciones por defecto a las ergonómicas corregidas (D-pad, R y diamante alineados)
            saveToSharedPreferences(defaults);
            prefs.edit().putBoolean(KEY_MIGRATED_V3, true).apply();
            return defaults;
        }

        return new TouchControlsLayout(
                readFloat(ButtonId.D_PAD, "pos_x", defaults.getDpadX()),
                readFloat(ButtonId.D_PAD, "pos_y", defaults.getDpadY()),
                readFloat(ButtonId.D_PAD, "scale", defaults.getDpadScale()),
                readFloat(ButtonId.D_PAD, "alpha", defaults.getDpadAlpha()),

                readFloat(ButtonId.BTN_L, "pos_x", defaults.getLButtonX()),
                readFloat(ButtonId.BTN_L, "pos_y", defaults.getLButtonY()),
                readFloat(ButtonId.BTN_L, "scale", defaults.getLButtonScale()),
                readFloat(ButtonId.BTN_L, "alpha", defaults.getLButtonAlpha()),

                readFloat(ButtonId.BTN_R, "pos_x", defaults.getRButtonX()),
                readFloat(ButtonId.BTN_R, "pos_y", defaults.getRButtonY()),
                readFloat(ButtonId.BTN_R, "scale", defaults.getRButtonScale()),
                readFloat(ButtonId.BTN_R, "alpha", defaults.getRButtonAlpha()),

                readFloat(ButtonId.BTN_SELECT, "pos_x", defaults.getSelectX()),
                readFloat(ButtonId.BTN_SELECT, "pos_y", defaults.getSelectY()),
                readFloat(ButtonId.BTN_SELECT, "scale", defaults.getSelectScale()),
                readFloat(ButtonId.BTN_SELECT, "alpha", defaults.getSelectAlpha()),

                readFloat(ButtonId.BTN_START, "pos_x", defaults.getStartX()),
                readFloat(ButtonId.BTN_START, "pos_y", defaults.getStartY()),
                readFloat(ButtonId.BTN_START, "scale", defaults.getStartScale()),
                readFloat(ButtonId.BTN_START, "alpha", defaults.getStartAlpha()),

                readFloat(ButtonId.BTN_X, "pos_x", defaults.getBtnXX()),
                readFloat(ButtonId.BTN_X, "pos_y", defaults.getBtnXY()),
                readFloat(ButtonId.BTN_X, "scale", defaults.getBtnXScale()),
                readFloat(ButtonId.BTN_X, "alpha", defaults.getBtnXAlpha()),

                readFloat(ButtonId.BTN_Y, "pos_x", defaults.getBtnYX()),
                readFloat(ButtonId.BTN_Y, "pos_y", defaults.getBtnYY()),
                readFloat(ButtonId.BTN_Y, "scale", defaults.getBtnYScale()),
                readFloat(ButtonId.BTN_Y, "alpha", defaults.getBtnYAlpha()),

                readFloat(ButtonId.BTN_B, "pos_x", defaults.getBtnBX()),
                readFloat(ButtonId.BTN_B, "pos_y", defaults.getBtnBY()),
                readFloat(ButtonId.BTN_B, "scale", defaults.getBtnBScale()),
                readFloat(ButtonId.BTN_B, "alpha", defaults.getBtnBAlpha()),

                readFloat(ButtonId.BTN_A, "pos_x", defaults.getBtnAX()),
                readFloat(ButtonId.BTN_A, "pos_y", defaults.getBtnAY()),
                readFloat(ButtonId.BTN_A, "scale", defaults.getBtnAScale()),
                readFloat(ButtonId.BTN_A, "alpha", defaults.getBtnAAlpha()),

                readFloat(ButtonId.MACRO_1, "pos_x", defaults.getMacro1X()),
                readFloat(ButtonId.MACRO_1, "pos_y", defaults.getMacro1Y()),
                readFloat(ButtonId.MACRO_1, "scale", defaults.getMacro1Scale()),
                readFloat(ButtonId.MACRO_1, "alpha", defaults.getMacro1Alpha()),

                readFloat(ButtonId.MACRO_2, "pos_x", defaults.getMacro2X()),
                readFloat(ButtonId.MACRO_2, "pos_y", defaults.getMacro2Y()),
                readFloat(ButtonId.MACRO_2, "scale", defaults.getMacro2Scale()),
                readFloat(ButtonId.MACRO_2, "alpha", defaults.getMacro2Alpha()),

                readFloat(ButtonId.MACRO_3, "pos_x", defaults.getMacro3X()),
                readFloat(ButtonId.MACRO_3, "pos_y", defaults.getMacro3Y()),
                readFloat(ButtonId.MACRO_3, "scale", defaults.getMacro3Scale()),
                readFloat(ButtonId.MACRO_3, "alpha", defaults.getMacro3Alpha()),

                readFloat(ButtonId.MACRO_4, "pos_x", defaults.getMacro4X()),
                readFloat(ButtonId.MACRO_4, "pos_y", defaults.getMacro4Y()),
                readFloat(ButtonId.MACRO_4, "scale", defaults.getMacro4Scale()),
                readFloat(ButtonId.MACRO_4, "alpha", defaults.getMacro4Alpha())
        );
    }

    private void migrateLegacyGroupIfNeeded(TouchControlsLayout legacy, TouchControlsLayout defaults) {
        SharedPreferences.Editor editor = prefs.edit();
        // Eliminar claves grupales antiguas si existieran
        if (prefs.contains("abxy_group_x") || prefs.contains("abxy_group_y")) {
            float gx = prefs.getFloat("abxy_group_x", 0.76f) + 0.08f;
            float gy = prefs.getFloat("abxy_group_y", 0.54f) + 0.15f;
            editor.remove("abxy_group_x").remove("abxy_group_y");
            writeButton(editor, ButtonId.BTN_X, gx, gy - 0.135f, 1.0f, 0.80f);
            writeButton(editor, ButtonId.BTN_Y, gx - 0.068f, gy, 1.0f, 0.80f);
            writeButton(editor, ButtonId.BTN_B, gx, gy + 0.135f, 1.0f, 0.80f);
            writeButton(editor, ButtonId.BTN_A, gx + 0.068f, gy, 1.0f, 0.80f);
        } else {
            saveLayoutToEditor(editor, legacy != null ? legacy : defaults);
        }
        editor.putBoolean(KEY_MIGRATED_V2, true);
        editor.apply();
    }

    public void saveToSharedPreferences(TouchControlsLayout layout) {
        SharedPreferences.Editor editor = prefs.edit();
        saveLayoutToEditor(editor, layout);
        editor.putBoolean(KEY_MIGRATED_V2, true);
        editor.apply();
    }

    public TouchControlsLayout resetToDefaults() {
        TouchControlsLayout defaults = createDefaultDiamondLayout();
        saveToSharedPreferences(defaults);
        return defaults;
    }

    /**
     * Hit-Testing individual en modo "Modificar Interfaz" (MOVER BOTONES):
     * En ACTION_DOWN identifica únicamente el botón específico tocado por su bounding box / radio
     * y en ACTION_MOVE arrastra ese único botón sin mover ningún otro.
     */
    public ButtonId onEditTouchEvent(
            MotionEvent event,
            TouchControlsLayout currentLayout,
            boolean[] enabledMacros4,
            float screenW,
            float screenH,
            float density,
            LayoutUpdateListener listener
    ) {
        int action = event.getActionMasked();
        float tx = event.getX();
        float ty = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            activeDraggedButton = findHitButton(currentLayout, enabledMacros4, tx, ty, screenW, screenH, density);
            if (activeDraggedButton != null) {
                ButtonConfig cfg = getButtonConfig(currentLayout, activeDraggedButton);
                dragDeltaX = (cfg.posX * screenW) - tx;
                dragDeltaY = (cfg.posY * screenH) - ty;
                if (listener != null) {
                    listener.onButtonSelected(activeDraggedButton);
                }
            }
            return activeDraggedButton;
        } else if (action == MotionEvent.ACTION_MOVE && activeDraggedButton != null) {
            float newNormX = clamp((tx + dragDeltaX) / screenW, 0.03f, 0.97f);
            float newNormY = clamp((ty + dragDeltaY) / screenH, 0.08f, 0.95f);
            TouchControlsLayout updated = updateButtonPosition(currentLayout, activeDraggedButton, newNormX, newNormY);
            if (listener != null) {
                listener.onLayoutUpdated(updated);
            }
            return activeDraggedButton;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            ButtonId released = activeDraggedButton;
            activeDraggedButton = null;
            return released;
        }
        return activeDraggedButton;
    }

    public static ButtonId findHitButton(
            TouchControlsLayout layout,
            boolean[] enabledMacros4,
            float tx,
            float ty,
            float screenW,
            float screenH,
            float density
    ) {
        // Orden inverso de dibujo para priorizar botones individuales superiores (Macros, X, Y, B, A)
        ButtonId[] order = new ButtonId[]{
                ButtonId.MACRO_4, ButtonId.MACRO_3, ButtonId.MACRO_2, ButtonId.MACRO_1,
                ButtonId.BTN_X, ButtonId.BTN_Y, ButtonId.BTN_B, ButtonId.BTN_A,
                ButtonId.BTN_L, ButtonId.BTN_R, ButtonId.BTN_SELECT, ButtonId.BTN_START,
                ButtonId.D_PAD
        };

        for (ButtonId id : order) {
            if (!isMacroVisible(id, enabledMacros4)) continue;
            ButtonConfig cfg = getButtonConfig(layout, id);
            if (cfg.hitTest(id, tx, ty, screenW, screenH, density)) {
                return id;
            }
        }
        return null;
    }

    private static boolean isMacroVisible(ButtonId id, boolean[] enabledMacros4) {
        if (enabledMacros4 == null || enabledMacros4.length < 4) return true;
        switch (id) {
            case MACRO_1: return enabledMacros4[0];
            case MACRO_2: return enabledMacros4[1];
            case MACRO_3: return enabledMacros4[2];
            case MACRO_4: return enabledMacros4[3];
            default: return true;
        }
    }

    public static ButtonConfig getButtonConfig(TouchControlsLayout l, ButtonId id) {
        switch (id) {
            case D_PAD:      return new ButtonConfig(l.getDpadX(), l.getDpadY(), l.getDpadScale(), l.getDpadAlpha());
            case BTN_L:      return new ButtonConfig(l.getLButtonX(), l.getLButtonY(), l.getLButtonScale(), l.getLButtonAlpha());
            case BTN_R:      return new ButtonConfig(l.getRButtonX(), l.getRButtonY(), l.getRButtonScale(), l.getRButtonAlpha());
            case BTN_SELECT: return new ButtonConfig(l.getSelectX(), l.getSelectY(), l.getSelectScale(), l.getSelectAlpha());
            case BTN_START:  return new ButtonConfig(l.getStartX(), l.getStartY(), l.getStartScale(), l.getStartAlpha());
            case BTN_X:      return new ButtonConfig(l.getBtnXX(), l.getBtnXY(), l.getBtnXScale(), l.getBtnXAlpha());
            case BTN_Y:      return new ButtonConfig(l.getBtnYX(), l.getBtnYY(), l.getBtnYScale(), l.getBtnYAlpha());
            case BTN_B:      return new ButtonConfig(l.getBtnBX(), l.getBtnBY(), l.getBtnBScale(), l.getBtnBAlpha());
            case BTN_A:      return new ButtonConfig(l.getBtnAX(), l.getBtnAY(), l.getBtnAScale(), l.getBtnAAlpha());
            case MACRO_1:    return new ButtonConfig(l.getMacro1X(), l.getMacro1Y(), l.getMacro1Scale(), l.getMacro1Alpha());
            case MACRO_2:    return new ButtonConfig(l.getMacro2X(), l.getMacro2Y(), l.getMacro2Scale(), l.getMacro2Alpha());
            case MACRO_3:    return new ButtonConfig(l.getMacro3X(), l.getMacro3Y(), l.getMacro3Scale(), l.getMacro3Alpha());
            case MACRO_4:    return new ButtonConfig(l.getMacro4X(), l.getMacro4Y(), l.getMacro4Scale(), l.getMacro4Alpha());
            default:         return new ButtonConfig(0.5f, 0.5f, 1.0f, 0.8f);
        }
    }

    public static TouchControlsLayout updateButtonPosition(
            TouchControlsLayout l,
            ButtonId id,
            float nx,
            float ny
    ) {
        ButtonConfig current = getButtonConfig(l, id);
        return updateButtonFull(l, id, nx, ny, current.scale, current.alpha);
    }

    public static TouchControlsLayout updateButtonScaleAndAlpha(
            TouchControlsLayout l,
            ButtonId id,
            float newScale,
            float newAlpha
    ) {
        ButtonConfig current = getButtonConfig(l, id);
        return updateButtonFull(l, id, current.posX, current.posY, newScale, newAlpha);
    }

    public static TouchControlsLayout updateButtonFull(
            TouchControlsLayout l,
            ButtonId id,
            float nx,
            float ny,
            float scale,
            float alpha
    ) {
        float cx = clamp(nx, 0.03f, 0.97f);
        float cy = clamp(ny, 0.08f, 0.95f);
        float cs = clamp(scale, MIN_SCALE, MAX_SCALE);
        float ca = clamp(alpha, 0.2f, 1.0f);

        return new TouchControlsLayout(
                id == ButtonId.D_PAD ? cx : l.getDpadX(),
                id == ButtonId.D_PAD ? cy : l.getDpadY(),
                id == ButtonId.D_PAD ? cs : l.getDpadScale(),
                id == ButtonId.D_PAD ? ca : l.getDpadAlpha(),

                id == ButtonId.BTN_L ? cx : l.getLButtonX(),
                id == ButtonId.BTN_L ? cy : l.getLButtonY(),
                id == ButtonId.BTN_L ? cs : l.getLButtonScale(),
                id == ButtonId.BTN_L ? ca : l.getLButtonAlpha(),

                id == ButtonId.BTN_R ? cx : l.getRButtonX(),
                id == ButtonId.BTN_R ? cy : l.getRButtonY(),
                id == ButtonId.BTN_R ? cs : l.getRButtonScale(),
                id == ButtonId.BTN_R ? ca : l.getRButtonAlpha(),

                id == ButtonId.BTN_SELECT ? cx : l.getSelectX(),
                id == ButtonId.BTN_SELECT ? cy : l.getSelectY(),
                id == ButtonId.BTN_SELECT ? cs : l.getSelectScale(),
                id == ButtonId.BTN_SELECT ? ca : l.getSelectAlpha(),

                id == ButtonId.BTN_START ? cx : l.getStartX(),
                id == ButtonId.BTN_START ? cy : l.getStartY(),
                id == ButtonId.BTN_START ? cs : l.getStartScale(),
                id == ButtonId.BTN_START ? ca : l.getStartAlpha(),

                id == ButtonId.BTN_X ? cx : l.getBtnXX(),
                id == ButtonId.BTN_X ? cy : l.getBtnXY(),
                id == ButtonId.BTN_X ? cs : l.getBtnXScale(),
                id == ButtonId.BTN_X ? ca : l.getBtnXAlpha(),

                id == ButtonId.BTN_Y ? cx : l.getBtnYX(),
                id == ButtonId.BTN_Y ? cy : l.getBtnYY(),
                id == ButtonId.BTN_Y ? cs : l.getBtnYScale(),
                id == ButtonId.BTN_Y ? ca : l.getBtnYAlpha(),

                id == ButtonId.BTN_B ? cx : l.getBtnBX(),
                id == ButtonId.BTN_B ? cy : l.getBtnBY(),
                id == ButtonId.BTN_B ? cs : l.getBtnBScale(),
                id == ButtonId.BTN_B ? ca : l.getBtnBAlpha(),

                id == ButtonId.BTN_A ? cx : l.getBtnAX(),
                id == ButtonId.BTN_A ? cy : l.getBtnAY(),
                id == ButtonId.BTN_A ? cs : l.getBtnAScale(),
                id == ButtonId.BTN_A ? ca : l.getBtnAAlpha(),

                id == ButtonId.MACRO_1 ? cx : l.getMacro1X(),
                id == ButtonId.MACRO_1 ? cy : l.getMacro1Y(),
                id == ButtonId.MACRO_1 ? cs : l.getMacro1Scale(),
                id == ButtonId.MACRO_1 ? ca : l.getMacro1Alpha(),

                id == ButtonId.MACRO_2 ? cx : l.getMacro2X(),
                id == ButtonId.MACRO_2 ? cy : l.getMacro2Y(),
                id == ButtonId.MACRO_2 ? cs : l.getMacro2Scale(),
                id == ButtonId.MACRO_2 ? ca : l.getMacro2Alpha(),

                id == ButtonId.MACRO_3 ? cx : l.getMacro3X(),
                id == ButtonId.MACRO_3 ? cy : l.getMacro3Y(),
                id == ButtonId.MACRO_3 ? cs : l.getMacro3Scale(),
                id == ButtonId.MACRO_3 ? ca : l.getMacro3Alpha(),

                id == ButtonId.MACRO_4 ? cx : l.getMacro4X(),
                id == ButtonId.MACRO_4 ? cy : l.getMacro4Y(),
                id == ButtonId.MACRO_4 ? cs : l.getMacro4Scale(),
                id == ButtonId.MACRO_4 ? ca : l.getMacro4Alpha()
        );
    }

    private void saveLayoutToEditor(SharedPreferences.Editor editor, TouchControlsLayout l) {
        writeButton(editor, ButtonId.D_PAD, l.getDpadX(), l.getDpadY(), l.getDpadScale(), l.getDpadAlpha());
        writeButton(editor, ButtonId.BTN_L, l.getLButtonX(), l.getLButtonY(), l.getLButtonScale(), l.getLButtonAlpha());
        writeButton(editor, ButtonId.BTN_R, l.getRButtonX(), l.getRButtonY(), l.getRButtonScale(), l.getRButtonAlpha());
        writeButton(editor, ButtonId.BTN_SELECT, l.getSelectX(), l.getSelectY(), l.getSelectScale(), l.getSelectAlpha());
        writeButton(editor, ButtonId.BTN_START, l.getStartX(), l.getStartY(), l.getStartScale(), l.getStartAlpha());
        writeButton(editor, ButtonId.BTN_X, l.getBtnXX(), l.getBtnXY(), l.getBtnXScale(), l.getBtnXAlpha());
        writeButton(editor, ButtonId.BTN_Y, l.getBtnYX(), l.getBtnYY(), l.getBtnYScale(), l.getBtnYAlpha());
        writeButton(editor, ButtonId.BTN_B, l.getBtnBX(), l.getBtnBY(), l.getBtnBScale(), l.getBtnBAlpha());
        writeButton(editor, ButtonId.BTN_A, l.getBtnAX(), l.getBtnAY(), l.getBtnAScale(), l.getBtnAAlpha());
        writeButton(editor, ButtonId.MACRO_1, l.getMacro1X(), l.getMacro1Y(), l.getMacro1Scale(), l.getMacro1Alpha());
        writeButton(editor, ButtonId.MACRO_2, l.getMacro2X(), l.getMacro2Y(), l.getMacro2Scale(), l.getMacro2Alpha());
        writeButton(editor, ButtonId.MACRO_3, l.getMacro3X(), l.getMacro3Y(), l.getMacro3Scale(), l.getMacro3Alpha());
        writeButton(editor, ButtonId.MACRO_4, l.getMacro4X(), l.getMacro4Y(), l.getMacro4Scale(), l.getMacro4Alpha());
    }

    private void writeButton(SharedPreferences.Editor editor, ButtonId id, float x, float y, float scale, float alpha) {
        editor.putFloat(id.prefPrefix + "_pos_x", x);
        editor.putFloat(id.prefPrefix + "_pos_y", y);
        editor.putFloat(id.prefPrefix + "_scale", scale);
        editor.putFloat(id.prefPrefix + "_alpha", alpha);
    }

    private float readFloat(ButtonId id, String suffix, float defaultVal) {
        return prefs.getFloat(id.prefPrefix + "_" + suffix, defaultVal);
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public interface LayoutUpdateListener {
        void onButtonSelected(ButtonId selectedId);
        void onLayoutUpdated(TouchControlsLayout updatedLayout);
    }
}
