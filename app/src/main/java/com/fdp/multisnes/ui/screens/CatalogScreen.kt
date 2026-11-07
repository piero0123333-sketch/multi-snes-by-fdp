package com.fdp.multisnes.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fdp.multisnes.MultiSnesApp
import com.fdp.multisnes.data.model.RomGame
import com.fdp.multisnes.data.model.StarBadge
import com.fdp.multisnes.data.rom.CoverLoader
import java.io.File
import com.fdp.multisnes.ui.theme.MultiSnesTopBar
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelFloppyIcon
import com.fdp.multisnes.ui.theme.PixelGearFloatingButton
import com.fdp.multisnes.ui.theme.PixelStarBadge
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette

/**
 * Pantalla principal del Catálogo de Juegos idéntica a la Imagen 1 (y fondo horizontal de Imagen 4).
 */
@Composable
fun CatalogScreen(
    games: List<RomGame>,
    isScanning: Boolean,
    onOpenOnlineTab: () -> Unit,
    onAutoScanGames: () -> Unit,
    onPickRomFolder: () -> Unit,
    onSelectGame: (RomGame) -> Unit,
    onOpenHerramientas: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SnesPalette.ScreenBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MultiSnesTopBar(
                isOnlineSelected = false,
                isScanning = isScanning,
                onToggleOnline = onOpenOnlineTab,
                onAutoScanGames = onAutoScanGames,
                onLongPressFolderPicker = onPickRomFolder
            )

            if (games.isEmpty()) {
                EmptyLibraryPrompt(
                    isScanning = isScanning,
                    onAutoScan = onAutoScanGames,
                    onSelectFolder = onPickRomFolder,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        end = 10.dp,
                        top = 10.dp,
                        bottom = 84.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = games, key = { it.id }) { game ->
                        GameCartridgeCard(
                            game = game,
                            onClick = { onSelectGame(game) }
                        )
                    }
                }
            }
        }

        // Botón flotante de Engranaje (⚙️) abajo a la derecha (abre HERRAMIENTAS)
        PixelGearFloatingButton(
            onClick = onOpenHerramientas,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun GameCartridgeCard(
    game: RomGame,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var resolvedCoverPath by remember(game.id, game.coverImagePath) {
        mutableStateOf(game.coverImagePath)
    }

    DisposableEffect(game.id, resolvedCoverPath) {
        if (resolvedCoverPath == null || !File(resolvedCoverPath!!).exists()) {
            val fileNameOrSource = game.zipEntryName ?: game.sourcePathOrUri
            CoverLoader.loadCover(
                context,
                fileNameOrSource,
                game.fullInternalTitle,
                null,
                null,
                object : CoverLoader.CoverCallback {
                    override fun onCoverLoaded(cachedFile: File, bitmap: Bitmap) {
                        resolvedCoverPath = cachedFile.absolutePath
                        val app = context.applicationContext as? MultiSnesApp
                        app?.libraryRepository?.updateCoverPath(game.crc32Hex, cachedFile.absolutePath)
                    }

                    override fun onCoverUnavailable() {
                        // Mantiene el diseño fallback con degradado y título centrado
                    }
                }
            )
        }
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SnesPalette.CardCartridgeLight,
                        SnesPalette.CardCartridgeGrey,
                        SnesPalette.CardCartridgeDark
                    )
                )
            )
            .border(2.dp, Color.Black, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Recuadro superior oscuro con borde morado para la portada del juego
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.12f)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF14141C))
                .border(2.dp, SnesPalette.FramePurple, RoundedCornerShape(7.dp))
                .padding(3.dp)
        ) {
            if (resolvedCoverPath != null) {
                AsyncImage(
                    model = resolvedCoverPath,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                GeneratedCartridgeCover(game = game, compact = false)
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Título centrado en mayúsculas (hasta 2 líneas como CHRONO TRIGGER / THE LEGEND OF ZELDA)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            OutlinedPixelText(
                text = game.title,
                style = PixelTypography.CardGameTitle,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Fila inferior: % + Estrella (izquierda) y Disquete Azul/Gris (derecha)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.starType == StarBadge.GOLD) {
                    PixelStarBadge(isGold = true, sizeDp = 14.dp)
                    Spacer(modifier = Modifier.width(2.dp))
                }
                OutlinedPixelText(
                    text = "${game.progressPercent}%",
                    style = PixelTypography.LabelBold.copy(fontSize = 12.sp)
                )
                if (game.starType == StarBadge.GREEN) {
                    Spacer(modifier = Modifier.width(2.dp))
                    PixelStarBadge(isGold = false, sizeDp = 14.dp)
                }
            }

            // Disquete azul si tiene partida guardada, gris si tiene sesiones sin SaveState, oculto en 0%
            if (game.hasSaveState || game.progressPercent > 0) {
                PixelFloppyIcon(
                    isSavedBlue = game.hasSaveState,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Carátula estilo caja oficial "SUPER NINTENDO" generada dinámicamente cuando el archivo ROM
 * no viene acompañado de una imagen .png/.jpg externa.
 */
@Composable
fun GeneratedCartridgeCover(
    game: RomGame,
    compact: Boolean
) {
    val hue = (game.accentHue % 360).toFloat()
    val topColor = Color.hsv(hue, 0.72f, 0.65f)
    val bottomColor = Color.hsv((hue + 45f) % 360f, 0.85f, 0.32f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 2.dp else 5.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!compact && game.enhancementChip != "SNES Estándar") {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    OutlinedPixelText(
                        text = game.enhancementChip.take(7),
                        style = PixelTypography.LabelBold.copy(fontSize = 7.sp),
                        color = SnesPalette.StarGold
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            OutlinedPixelText(
                text = game.title,
                modifier = Modifier.fillMaxWidth(),
                style = PixelTypography.CardGameTitle.copy(
                    fontSize = if (compact) 7.sp else 11.sp,
                    lineHeight = if (compact) 8.sp else 12.sp
                ),
                maxLines = if (compact) 2 else 3
            )

            // Franja inferior clásica "SUPER NINTENDO"
            if (!compact) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xDD111116))
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText(
                        text = "SUPER NINTENDO",
                        style = PixelTypography.LabelBold.copy(fontSize = 7.sp),
                        color = Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryPrompt(
    isScanning: Boolean,
    onAutoScan: () -> Unit,
    onSelectFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SnesPalette.SectionCardDark)
                .border(2.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(12.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedPixelText(
                text = if (isScanning) "ESCANEANDO ROMS DE SNES..." else "SIN RESTRICCIONES DE ROMS",
                style = PixelTypography.SectionTitle
            )
            OutlinedPixelText(
                text = "Compatible con Super Bomberman, Mega Man X/X2/X3 (CX4), Chrono Trigger, Donkey Kong Country y cualquier .SFC / .SMC / .ZIP.",
                style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                color = Color(0xFFD0D0DC),
                maxLines = 5
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SnesPalette.PlayGreenMain)
                        .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                        .clickable(onClick = onAutoScan),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText("BUSCAR AUTO", style = PixelTypography.LabelBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SnesPalette.LoadBlueMain)
                        .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                        .clickable(onClick = onSelectFolder),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText("ELEGIR CARPETA", style = PixelTypography.LabelBold)
                }
            }
        }
    }
}
