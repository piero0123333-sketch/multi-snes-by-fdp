package com.fdp.multisnes.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * Modal de selección de partida idéntico a Imagen 4:
 * - Marco gris metálico con 4 remaches en las esquinas y botón [X]
 * - Título del juego + miniatura de portada arriba
 * - Botón verde JUGAR (arranque normal con SRAM)
 * - Botón azul CARGAR PROGRESO (carga Guardado Rápido con % + ★ + disquete)
 */
@Composable
fun GameLaunchDialog(
    game: RomGame,
    onDismiss: () -> Unit,
    onPlayNormal: (RomGame) -> Unit,
    onLoadProgress: (RomGame) -> Unit,
    onToggleCompletedStar: (RomGame) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF6E6E7A))
                .border(3.dp, Color.Black, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            // 4 Remaches metálicos en las esquinas (fiel a Img 4)
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
                        style = PixelTypography.HeaderTitle.copy(fontSize = 20.sp),
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

                Spacer(modifier = Modifier.height(14.dp))

                // Recuadro interior hundido oscuro con JUGAR y CARGAR PROGRESO
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF23232B))
                        .border(3.dp, Color(0xFF3E3E4A), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Botón Verde JUGAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
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
                            .border(3.dp, Color.Black, RoundedCornerShape(8.dp))
                            .clickable { onPlayNormal(game) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PixelSnesControllerIcon(
                            modifier = Modifier.size(64.dp, 32.dp),
                            superFamicomColors = true
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        OutlinedPixelText(
                            text = "JUGAR",
                            style = PixelTypography.HeaderTitle.copy(fontSize = 25.sp)
                        )
                    }

                    // 2. Botón Azul CARGAR PROGRESO
                    val canLoad = game.hasSaveState
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .alpha(if (canLoad) 1f else 0.52f)
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
                            .border(3.dp, Color.Black, RoundedCornerShape(8.dp))
                            .clickable(enabled = canLoad) { onLoadProgress(game) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PixelFloppyIcon(
                            isSavedBlue = canLoad,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedPixelText(
                                text = "CARGAR\nPROGRESO",
                                style = PixelTypography.HeaderTitle.copy(
                                    fontSize = 19.sp,
                                    lineHeight = 21.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedPixelText(
                                    text = if (canLoad) "${game.progressPercent}%" else "SIN GUARDADO",
                                    style = PixelTypography.LabelBold.copy(fontSize = 12.sp)
                                )
                                if (game.starType != StarBadge.NONE) {
                                    PixelStarBadge(
                                        isGold = game.starType == StarBadge.GOLD,
                                        sizeDp = 14.dp
                                    )
                                }
                                if (canLoad) {
                                    PixelFloppyIcon(isSavedBlue = true, modifier = Modifier.size(13.dp))
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
