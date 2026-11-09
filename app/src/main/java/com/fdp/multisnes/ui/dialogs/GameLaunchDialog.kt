package com.fdp.multisnes.ui.dialogs

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fdp.multisnes.data.model.RomGame
import com.fdp.multisnes.data.model.StarBadge
import com.fdp.multisnes.ui.screens.GeneratedCartridgeCover
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelFloppyIcon
import com.fdp.multisnes.ui.theme.PixelSnesControllerIcon
import com.fdp.multisnes.ui.theme.PixelStarBadge
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette

private val FocusCyan = Color(0xFF00E5FF)
private val FocusYellow = Color(0xFFFFD600)

/**
 * MÓDULO 2: Modal de selección de partida con 3 Botones diferenciados:
 * 1. JUGAR (DESDE CERO): Inicia la ROM limpia desde el principio.
 * 2. CONTINUAR DONDE QUEDASTE: Carga directamente autosave_exit.state (deshabilitado/opaco si aún no existe).
 * 3. CARGAR PROGRESO (GUARDADOS): Abre el selector con la lista de los hasta 10 estados guardados manualmente.
 */
@Composable
fun GameLaunchDialog(
    game: RomGame,
    onDismiss: () -> Unit,
    onPlayFromScratch: (RomGame) -> Unit,
    onContinueWhereLeftOff: (RomGame) -> Unit,
    onOpenManualSavesPicker: (RomGame) -> Unit,
    onToggleCompletedStar: (RomGame) -> Unit
) {
    val canContinueAuto = game.hasAutoSaveExit
    val canLoadManual = game.hasSaveState

    val playScratchFocusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }

    LaunchedEffect(game.id, canContinueAuto) {
        runCatching {
            if (canContinueAuto) {
                continueFocusRequester.requestFocus()
            } else {
                playScratchFocusRequester.requestFocus()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF6E6E7A))
                .border(3.dp, Color.Black, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            // 4 Remaches metálicos en las esquinas
            CornerRivet(Modifier.align(Alignment.TopStart))
            CornerRivet(Modifier.align(Alignment.BottomStart))
            CornerRivet(Modifier.align(Alignment.BottomEnd))

            // Botón cerrar [X] arriba a la derecha
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF555560))
                    .border(2.dp, Color.Black, RoundedCornerShape(4.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                OutlinedPixelText(
                    text = "X",
                    style = PixelTypography.LabelBold.copy(fontSize = 15.sp),
                    color = Color(0xFFD0D0DC)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera: Nombre del juego + miniatura de carátula
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedPixelText(
                        text = game.title,
                        modifier = Modifier.weight(1f),
                        style = PixelTypography.HeaderTitle.copy(fontSize = 19.sp),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(50.dp, 40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(2.dp, Color.Black, RoundedCornerShape(4.dp))
                            .clickable { onToggleCompletedStar(game) }
                    ) {
                        if (game.coverImagePath != null) {
                            AsyncImage(
                                model = game.coverImagePath,
                                contentDescription = game.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            GeneratedCartridgeCover(game = game, compact = true)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recuadro interior hundido oscuro con los 3 botones del MÓDULO 2
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF23232B))
                        .border(3.dp, Color(0xFF3E3E4A), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    // 1. Botón Verde: JUGAR (DESDE CERO)
                    var focusPlay by remember { mutableStateOf(false) }
                    val scalePlay by animateFloatAsState(
                        targetValue = if (focusPlay) 1.03f else 1.0f,
                        animationSpec = tween(120),
                        label = "scalePlay"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                            .graphicsLayer {
                                scaleX = scalePlay
                                scaleY = scalePlay
                            }
                            .focusRequester(playScratchFocusRequester)
                            .onFocusChanged { focusPlay = it.isFocused || it.hasFocus }
                            .onKeyEvent { ev ->
                                val nk = ev.nativeKeyEvent
                                if (nk.action == KeyEvent.ACTION_DOWN &&
                                    (nk.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                        nk.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                        nk.keyCode == KeyEvent.KEYCODE_ENTER)
                                ) {
                                    onPlayFromScratch(game)
                                    true
                                } else false
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        SnesPalette.PlayGreenTop,
                                        SnesPalette.PlayGreenMain,
                                        SnesPalette.PlayGreenDark
                                    )
                                )
                            )
                            .border(
                                width = if (focusPlay) 4.dp else 3.dp,
                                color = if (focusPlay) FocusCyan else Color.Black,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusable()
                            .clickable { onPlayFromScratch(game) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PixelSnesControllerIcon(
                            modifier = Modifier.size(54.dp, 28.dp),
                            superFamicomColors = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedPixelText(
                                text = "JUGAR (DESDE CERO)",
                                style = PixelTypography.HeaderTitle.copy(fontSize = 16.sp),
                                color = if (focusPlay) FocusYellow else Color.White
                            )
                            OutlinedPixelText(
                                text = "INICIAR PARTIDA LIMPIA",
                                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                                color = Color(0xFFE0FFE8)
                            )
                        }
                    }

                    // 2. Botón Dorado/Ámbar: CONTINUAR DONDE QUEDASTE (autosave_exit.state)
                    var focusContinue by remember { mutableStateOf(false) }
                    val scaleContinue by animateFloatAsState(
                        targetValue = if (focusContinue && canContinueAuto) 1.03f else 1.0f,
                        animationSpec = tween(120),
                        label = "scaleContinue"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .alpha(if (canContinueAuto) 1f else 0.42f)
                            .graphicsLayer {
                                scaleX = scaleContinue
                                scaleY = scaleContinue
                            }
                            .focusRequester(continueFocusRequester)
                            .onFocusChanged { focusContinue = it.isFocused || it.hasFocus }
                            .onKeyEvent { ev ->
                                val nk = ev.nativeKeyEvent
                                if (canContinueAuto && nk.action == KeyEvent.ACTION_DOWN &&
                                    (nk.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                        nk.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                        nk.keyCode == KeyEvent.KEYCODE_ENTER)
                                ) {
                                    onContinueWhereLeftOff(game)
                                    true
                                } else false
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFFF59E0B),
                                        Color(0xFFD97706),
                                        Color(0xFF92400E)
                                    )
                                )
                            )
                            .border(
                                width = if (focusContinue && canContinueAuto) 4.dp else 3.dp,
                                color = if (focusContinue && canContinueAuto) FocusCyan else Color.Black,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusable(enabled = canContinueAuto)
                            .clickable(enabled = canContinueAuto) { onContinueWhereLeftOff(game) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PixelFloppyIcon(
                            isSavedBlue = canContinueAuto,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedPixelText(
                                text = "CONTINUAR DONDE QUEDASTE",
                                style = PixelTypography.HeaderTitle.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 16.sp
                                ),
                                color = if (focusContinue && canContinueAuto) FocusYellow else Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            OutlinedPixelText(
                                text = if (canContinueAuto) {
                                    game.autoSaveExitDate?.let { "AUTOGUARDADO: $it" } ?: "PUNTO EXACTO AL SALIR"
                                } else {
                                    "SIN AUTOGUARDADO AL SALIR"
                                },
                                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                                color = if (canContinueAuto) Color(0xFFFFF8DC) else Color(0xFFCCCCCC)
                            )
                        }
                    }

                    // 3. Botón Azul: CARGAR PROGRESO (GUARDADOS MANUALES 1..10)
                    var focusManual by remember { mutableStateOf(false) }
                    val scaleManual by animateFloatAsState(
                        targetValue = if (focusManual && canLoadManual) 1.03f else 1.0f,
                        animationSpec = tween(120),
                        label = "scaleManual"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .alpha(if (canLoadManual) 1f else 0.42f)
                            .graphicsLayer {
                                scaleX = scaleManual
                                scaleY = scaleManual
                            }
                            .onFocusChanged { focusManual = it.isFocused || it.hasFocus }
                            .onKeyEvent { ev ->
                                val nk = ev.nativeKeyEvent
                                if (canLoadManual && nk.action == KeyEvent.ACTION_DOWN &&
                                    (nk.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                        nk.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                        nk.keyCode == KeyEvent.KEYCODE_ENTER)
                                ) {
                                    onOpenManualSavesPicker(game)
                                    true
                                } else false
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        SnesPalette.LoadBlueTop,
                                        SnesPalette.LoadBlueMain,
                                        SnesPalette.LoadBlueDark
                                    )
                                )
                            )
                            .border(
                                width = if (focusManual && canLoadManual) 4.dp else 3.dp,
                                color = if (focusManual && canLoadManual) FocusCyan else Color.Black,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusable(enabled = canLoadManual)
                            .clickable(enabled = canLoadManual) { onOpenManualSavesPicker(game) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PixelFloppyIcon(
                            isSavedBlue = canLoadManual,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedPixelText(
                                text = "CARGAR PROGRESO\n(GUARDADOS)",
                                style = PixelTypography.HeaderTitle.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 17.sp
                                ),
                                color = if (focusManual && canLoadManual) FocusYellow else Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedPixelText(
                                    text = if (canLoadManual) "SELECTOR 1-10 SLOTS (${game.progressPercent}%)" else "SIN GUARDADOS MANUALES",
                                    style = PixelTypography.LabelBold.copy(fontSize = 10.sp)
                                )
                                if (game.starType != StarBadge.NONE) {
                                    PixelStarBadge(
                                        isGold = game.starType == StarBadge.GOLD,
                                        sizeDp = 13.dp
                                    )
                                }
                                if (canLoadManual) {
                                    PixelFloppyIcon(isSavedBlue = true, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CornerRivet(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
    ) {
        drawCircle(Color.Black)
        drawCircle(Color(0xFF9E9EA8), radius = size.minDimension * 0.36f)
    }
}
