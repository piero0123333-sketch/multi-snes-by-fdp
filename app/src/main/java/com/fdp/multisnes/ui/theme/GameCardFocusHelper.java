package com.fdp.multisnes.ui.theme;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import com.fdp.multisnes.R;

/**
 * MÓDULO 1: Configurador de foco y animación para tarjetas de juego al navegar con mando físico (D-Pad / Joystick).
 * - Habilita android:focusable="true", android:focusableInTouchMode="false" y android:clickable="true".
 * - Aplica el StateListDrawable R.drawable.selector_game_card (borde normal 2dp #3A3A3C,
 *   borde enfocado 4dp cian #00E5FF con sombra/detalle amarillo #FFD600 estilo pixel-art).
 * - Aplica animación suave en el ViewHolder cuando hasFocus == true (scaleX = 1.06f, scaleY = 1.06f, translationZ = 8f)
 *   y restablece a 1.0f y 0f al perder el foco.
 */
public final class GameCardFocusHelper {

    private static final float FOCUSED_SCALE = 1.06f;
    private static final float NORMAL_SCALE = 1.0f;
    private static final float FOCUSED_TRANSLATION_Z_DP = 8f;
    private static final float NORMAL_TRANSLATION_Z_DP = 0f;
    private static final long ANIMATION_DURATION_MS = 140L;

    private GameCardFocusHelper() {
    }

    public static void bindGameCardFocus(final View cardRootView) {
        if (cardRootView == null) return;

        cardRootView.setFocusable(true);
        cardRootView.setFocusableInTouchMode(false);
        cardRootView.setClickable(true);
        cardRootView.setBackgroundResource(R.drawable.selector_game_card);

        final float density = cardRootView.getResources().getDisplayMetrics().density;
        final float focusedTranslationZPx = FOCUSED_TRANSLATION_Z_DP * density;

        cardRootView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setSelected(hasFocus);
                if (hasFocus) {
                    v.animate()
                            .scaleX(FOCUSED_SCALE)
                            .scaleY(FOCUSED_SCALE)
                            .translationZ(focusedTranslationZPx)
                            .setDuration(ANIMATION_DURATION_MS)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else {
                    v.animate()
                            .scaleX(NORMAL_SCALE)
                            .scaleY(NORMAL_SCALE)
                            .translationZ(NORMAL_TRANSLATION_Z_DP)
                            .setDuration(ANIMATION_DURATION_MS)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            }
        });
    }
}
