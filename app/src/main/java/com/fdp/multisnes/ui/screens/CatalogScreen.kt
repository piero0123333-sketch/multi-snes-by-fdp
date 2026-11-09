package com.fdp.multisnes.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.fdp.multisnes.MultiSnesApp
import com.fdp.multisnes.data.model.RomGame
import com.fdp.multisnes.data.model.StarBadge
import com.fdp.multisnes.data.rom.CoverLoader
import com.fdp.multisnes.ui.theme.MultiSnesTopBar
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelFloppyIcon
import com.fdp.multisnes.ui.theme.PixelGearFloatingButton
import com.fdp.multisnes.ui.theme.PixelStarBadge
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette
import java.io.File

private val FocusCyanBorder = Color(0xFF00E5FF)
private val FocusYellowShadow = Color(0xFFFFD600)
private val NormalSubtleBorder = Color(0xFF3A3A3C)

/**
 * Pantalla principal del Catálogo de Juegos con soporte de resaltado de alto contraste
 * y animación de escala para navegación con mando físico (D-Pad / Joystick) — MÓDULO 1.
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
    val firstCardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(games.size) {
        if (games.isNotEmpty()) {
            runCatching { firstCardFocusRequester.requestFocus() }
        }
    }

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
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 84.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(items = games, key = { _, item -> item.id }) { index, game ->
                        GameCartridgeCard(
                            game = game,
                            onClick = { onSelectGame(game) },
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstCardFocusRequester)
                            } else {
                                Modifier
                            }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var isFocused by remember { mutableStateOf(false) }

    // MÓDULO 1: Animación suave al recibir foco del mando (scaleX = 1.06f, scaleY = 1.06f, translationZ = 8f)
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "cardFocusScale"
    )
    val animatedTranslationZPx by animateFloatAsState(
        targetValue = if (isFocused) with(density) { 8.dp.toPx() } else 0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "cardFocusTranslationZ"
    )

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

    val cardShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 2f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                shadowElevation = animatedTranslationZPx
                shape = cardShape
                clip = false
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused || focusState.hasFocus
            }
            .onKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                if (native.action == KeyEvent.ACTION_DOWN &&
                    (native.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                        native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        native.keyCode == KeyEvent.KEYCODE_ENTER ||
                        native.keyCode == KeyEvent.KEYCODE_BUTTON_START)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        SnesPalette.CardCartridgeLight,
                        SnesPalette.CardCartridgeGrey,
                        SnesPalette.CardCartridgeDark
                    )
                )
            )
            // MÓDULO 1: Borde Retro de Alto Contraste al enfocar con mando (4dp #00E5FF + detalle/sombra amarilla #FFD600)
            // vs Estado normal (2dp #3A3A3C)
            .then(
                if (isFocused) {
                    Modifier
                        .border(4.dp, FocusCyanBorder, cardShape)
                        .drawWithContent {
                            drawContent()
                            val cornerPx = 10.dp.toPx()
                            val outerStroke = 2.dp.toPx()
                            val inset = 4.dp.toPx()
                            // Detalle/sombra interior amarilla estilo pixel-art (#FFD600) junto al borde cian de 4dp
                            drawRoundRect(
                                color = FocusYellowShadow,
                                topLeft = Offset(inset, inset),
                                size = Size(
                                    width = (size.width - inset * 2f).coerceAtLeast(0f),
                                    height = (size.height - inset * 2f).coerceAtLeast(0f)
                                ),
                                cornerRadius = CornerRadius(cornerPx - 2.dp.toPx(), cornerPx - 2.dp.toPx()),
                                style = Stroke(width = outerStroke)
                            )
                        }
                } else {
                    Modifier.border(2.dp, NormalSubtleBorder, cardShape)
                }
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Recuadro superior oscuro con borde morado (o amarillo brillante si está enfocado) para la portada
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.12f)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF14141C))
                .border(
                    width = 2.dp,
                    color = if (isFocused) FocusYellowShadow else SnesPalette.FramePurple,
                    shape = RoundedCornerShape(7.dp)
                )
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

        // Título centrado en mayúsculas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            OutlinedPixelText(
                text = game.title,
                style = PixelTypography.CardGameTitle,
                color = if (isFocused) FocusYellowShadow else Color.White,
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

            // Disquete azul si tiene partida guardada (manual o autoguardado al salir), gris si tiene sesiones
            val hasAnySave = game.hasSaveState || game.hasAutoSaveExit
            if (hasAnySave || game.progressPercent > 0) {
                PixelFloppyIcon(
                    isSavedBlue = hasAnySave,
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
                text = "Compatible con Super Bomberman, Mega Man X/X2/X3 (CX4), Chrono Trigger, Donkey Kong Country y cualquier .SFC / .SMC / .ZIP / .IPS / .BPS.",
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
                        .focusable()
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
                        .focusable()
                        .clickable(onClick = onSelectFolder),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText("ELEGIR CARPETA", style = PixelTypography.LabelBold)
                }
            }
        }
    }
}
