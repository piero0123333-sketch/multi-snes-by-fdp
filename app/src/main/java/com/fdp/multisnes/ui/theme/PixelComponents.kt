package com.fdp.multisnes.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Texto estilo Pixel Arcade 16-bit con contorno oscuro nítido (idéntico a todas las capturas).
 */
@Composable
fun OutlinedPixelText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PixelTypography.LabelBold,
    color: Color = Color.White,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 2
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style.copy(
            shadow = Shadow(
                color = Color.Black,
                offset = Offset(3f, 3f),
                blurRadius = 1f
            )
        )
    )
}

/**
 * Cabecera superior compartida por Catálogo (Img 1) y Modo Online (Img 3):
 * - Barra metálica "MULTI SNES BY FDP"
 * - Botón ONLINE + Botón BUSCAR JUEGOS (AUTO)
 * - Línea divisoria LED arcoíris
 */
@Composable
fun MultiSnesTopBar(
    isOnlineSelected: Boolean,
    isScanning: Boolean,
    onToggleOnline: () -> Unit,
    onAutoScanGames: () -> Unit,
    onLongPressFolderPicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SnesPalette.ScreenBlack)
    ) {
        // Barra metálica estilo carcasa SNES
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SnesPalette.HeaderBorderLight,
                            SnesPalette.HeaderMetal,
                            SnesPalette.HeaderBorderDark
                        )
                    )
                )
                .border(2.dp, Color.Black)
        ) {
            // Líneas verticales decorativas tipo consola SNES (izquierda y derecha)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp)
                    .width(2.dp)
                    .height(54.dp)
                    .background(Color(0xFF1F1F26))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp)
                    .width(2.dp)
                    .height(54.dp)
                    .background(Color(0xFF1F1F26))
            )

            val titleString = buildAnnotatedString {
                append("MULTI SNES BY ")
                if (isOnlineSelected) {
                    withStyle(SpanStyle(color = SnesPalette.FdpBlue)) { append("F") }
                    withStyle(SpanStyle(color = SnesPalette.FdpOrange)) { append("D") }
                    withStyle(SpanStyle(color = SnesPalette.FdpGreen)) { append("P") }
                } else {
                    append("FDP")
                }
            }

            Text(
                text = titleString,
                modifier = Modifier.align(Alignment.Center),
                style = PixelTypography.HeaderTitle.copy(
                    shadow = Shadow(Color.Black, Offset(4f, 4f), 1f)
                )
            )
        }

        // Sub-barra: ONLINE (izquierda) y BUSCAR JUEGOS (AUTO) (derecha)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16161C))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón ONLINE
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2B2B34))
                    .border(
                        width = 2.dp,
                        color = if (isOnlineSelected) SnesPalette.OnlineNeonGreen else SnesPalette.FramePurple,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = onToggleOnline)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedPixelText(
                    text = "ONLINE",
                    color = if (isOnlineSelected) SnesPalette.OnlineNeonGreen else Color.White,
                    style = PixelTypography.LabelBold.copy(fontSize = 14.sp)
                )
            }

            // Botón BUSCAR JUEGOS (AUTO) (pulsación larga permite añadir carpeta SAF extra)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2B2B34))
                    .border(2.dp, SnesPalette.FramePurple, RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onAutoScanGames() },
                            onLongPress = { onLongPressFolderPicker() }
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelCartridgeMiniIcon(modifier = Modifier.size(28.dp, 20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedPixelText(
                    text = if (isScanning) "BUSCANDO\nROMS..." else "BUSCAR\nJUEGOS (AUTO)",
                    style = PixelTypography.LabelBold.copy(fontSize = 10.sp, lineHeight = 11.sp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                PixelMagnifierIcon(modifier = Modifier.size(22.dp))
            }
        }

        // Divisor LED arcoíris inferior (idéntico a Img 1 y 3)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1A1A22),
                            Color(0xFF4CFF72),
                            Color(0xFF282834),
                            Color(0xFFFFB74D),
                            Color(0xFF4FC3F7),
                            Color(0xFFB388FF),
                            Color(0xFF1A1A22)
                        )
                    )
                )
        )
    }
}

/**
 * Título arcoíris "HERRAMIENTAS" de Imagen 2.
 */
@Composable
fun RainbowHerramientasTitle(modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFFD98CE8),
        Color(0xFFF078B8),
        Color(0xFF8AA8F8),
        Color(0xFF7CE0C8),
        Color(0xFFB888F8),
        Color(0xFFF088C0)
    )
    val text = "HERRAMIENTAS"
    val styled = buildAnnotatedString {
        text.forEachIndexed { idx, c ->
            withStyle(SpanStyle(color = colors[idx % colors.size])) {
                append(c)
            }
        }
    }
    Text(
        text = styled,
        modifier = modifier,
        style = PixelTypography.HeaderTitle.copy(
            fontSize = 24.sp,
            letterSpacing = 2.sp,
            shadow = Shadow(Color.Black, Offset(4f, 4f), 1f)
        )
    )
}

/**
 * Botón flotante de Engranaje (⚙️) en esquina inferior derecha (Img 1, 3, 4).
 */
@Composable
fun PixelGearFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(Color(0xFF6B6B75))
            .border(3.dp, Color.Black, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(38.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val gearColor = Color(0xFF9C7CF4)
            // 8 dientes del engranaje
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                val tx = cx + (size.width * 0.34f) * cos(angle).toFloat()
                val ty = cy + (size.height * 0.34f) * sin(angle).toFloat()
                drawCircle(color = Color.Black, radius = 6.5f, center = Offset(tx, ty))
                drawCircle(color = gearColor, radius = 4.5f, center = Offset(tx, ty))
            }
            drawCircle(color = Color.Black, radius = size.minDimension * 0.33f, center = Offset(cx, cy))
            drawCircle(color = gearColor, radius = size.minDimension * 0.28f, center = Offset(cx, cy))
            drawCircle(color = Color(0xFF3A3A44), radius = size.minDimension * 0.12f, center = Offset(cx, cy))
        }
    }
}

/**
 * Interruptor retro ON / OFF de CONTROLES EN PANTALLA (Img 2).
 */
@Composable
fun RetroOnOffPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .width(64.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) SnesPalette.ToggleOnGreen else SnesPalette.ToggleOffGrey)
            .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (checked) {
            OutlinedPixelText(
                text = "ON",
                style = PixelTypography.LabelBold.copy(fontSize = 11.sp)
            )
            Box(
                modifier = Modifier
                    .size(16.dp, 18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFD6D6DE))
                    .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp, 18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFB4B4BE))
                    .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            )
            OutlinedPixelText(
                text = "OFF",
                style = PixelTypography.LabelBold.copy(fontSize = 10.sp)
            )
        }
    }
}

/**
 * Interruptor azul de VIBRACIÓN y SONIDO en PREFERENCIAS (Img 2).
 */
@Composable
fun RetroBlueSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) SnesPalette.PrefBlue else SnesPalette.ToggleOffGrey)
            .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp, 18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFC8C8D2))
                .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
        )
    }
}

/**
 * Barra deslizante retro azul para INTENSIDAD y VOLUMEN (Img 2).
 */
@Composable
fun RetroBlueSlider(
    value: Float,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clamped = value.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Pista oscura con relleno azul
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SnesPalette.PrefTrackDark)
                .border(2.dp, Color.Black, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(14.dp)
                    .background(if (enabled) SnesPalette.PrefBlue else SnesPalette.ToggleOffGrey)
            )
        }
        // Cursor rectangular vertical estilo pixel
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thumbW = 14.dp.toPx()
            val thumbH = 24.dp.toPx()
            val x = (size.width - thumbW) * clamped
            val y = (size.height - thumbH) / 2f
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(x - 2f, y - 2f),
                size = Size(thumbW + 4f, thumbH + 4f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = Color(0xFF72A8FF),
                topLeft = Offset(x, y),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

/**
 * Icono de disquete 3.5" (Azul cuando tiene partida guardada, Gris cuando no).
 */
@Composable
fun PixelFloppyIcon(
    isSavedBlue: Boolean,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bodyColor = if (isSavedBlue) Color(0xFF4A6CF7) else Color(0xFF767680)
        val labelColor = if (isSavedBlue) Color.White else Color(0xFFA2A2AC)
        val stripeColor = if (isSavedBlue) Color(0xFFE53935) else Color(0xFF8A8A94)

        drawRect(Color.Black, Offset.Zero, Size(w, h))
        drawRect(bodyColor, Offset(w * 0.08f, h * 0.08f), Size(w * 0.84f, h * 0.84f))
        // Obturador metálico superior
        drawRect(Color(0xFFC8C8D4), Offset(w * 0.25f, h * 0.08f), Size(w * 0.45f, h * 0.28f))
        drawRect(Color.Black, Offset(w * 0.52f, h * 0.12f), Size(w * 0.10f, h * 0.20f))
        // Etiqueta inferior
        drawRect(labelColor, Offset(w * 0.18f, h * 0.48f), Size(w * 0.64f, h * 0.44f))
        drawRect(stripeColor, Offset(w * 0.18f, h * 0.78f), Size(w * 0.64f, h * 0.14f))
    }
}

@Composable
fun PixelStarBadge(
    isGold: Boolean,
    sizeDp: Dp = 16.dp
) {
    Text(
        text = "★",
        color = if (isGold) SnesPalette.StarGold else SnesPalette.StarGreen,
        fontSize = (sizeDp.value).sp,
        fontWeight = FontWeight.Black,
        style = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), 1f))
    )
}

@Composable
fun PixelCartridgeMiniIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF8E8E98), Offset.Zero, Size(w, h), CornerRadius(3f, 3f))
        drawRect(Color(0xFF4A4A54), Offset(w * 0.12f, 0f), Size(w * 0.76f, h * 0.25f))
        drawRect(Color(0xFF5E4B8B), Offset(w * 0.18f, h * 0.35f), Size(w * 0.64f, h * 0.45f))
    }
}

@Composable
fun PixelMagnifierIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(Color(0xFF8AB4F8), radius = r, center = center)
        drawCircle(Color.White, radius = r, center = center, style = Stroke(width = 3f))
        drawLine(
            color = Color(0xFFB0BEC5),
            start = Offset(size.width * 0.64f, size.height * 0.64f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Icono de mando clásico SNES (usado en JUGAR de Img 4 y tarjetas Online de Img 3).
 */
@Composable
fun PixelSnesControllerIcon(
    modifier: Modifier = Modifier.size(54.dp, 28.dp),
    superFamicomColors: Boolean = true
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val body = Color(0xFFC4C4CE)
        val circleBg = Color(0xFF9A9AA6)

        drawRoundRect(Color.Black, Offset.Zero, Size(w, h), CornerRadius(h * 0.48f, h * 0.48f))
        drawRoundRect(body, Offset(2f, 2f), Size(w - 4f, h - 4f), CornerRadius(h * 0.45f, h * 0.45f))
        drawCircle(circleBg, radius = h * 0.38f, center = Offset(w * 0.76f, h * 0.52f))

        // Cruceta D-Pad izquierda
        val dx = w * 0.23f
        val dy = h * 0.52f
        val arm = h * 0.24f
        val thick = h * 0.16f
        drawRect(Color(0xFF484852), Offset(dx - arm, dy - thick / 2), Size(arm * 2, thick))
        drawRect(Color(0xFF484852), Offset(dx - thick / 2, dy - arm), Size(thick, arm * 2))

        // Select / Start inclinados al centro
        drawLine(Color(0xFF50505A), Offset(w * 0.42f, h * 0.62f), Offset(w * 0.46f, h * 0.50f), strokeWidth = 3f)
        drawLine(Color(0xFF50505A), Offset(w * 0.50f, h * 0.62f), Offset(w * 0.54f, h * 0.50f), strokeWidth = 3f)

        // 4 Botones X, Y, A, B
        val bx = w * 0.76f
        val by = h * 0.52f
        val d = h * 0.18f
        val br = h * 0.09f
        drawCircle(if (superFamicomColors) Color(0xFF2196F3) else Color(0xFF9575CD), br, Offset(bx, by - d)) // X
        drawCircle(if (superFamicomColors) Color(0xFF4CAF50) else Color(0xFF9575CD), br, Offset(bx - d, by)) // Y
        drawCircle(if (superFamicomColors) Color(0xFFE53935) else Color(0xFF673AB7), br, Offset(bx + d, by)) // A
        drawCircle(if (superFamicomColors) Color(0xFFFFCA28) else Color(0xFF673AB7), br, Offset(bx, by + d)) // B
    }
}

/**
 * Ilustración de CREAR PARTIDA (HOST) (Consola SNES + Ondas Wi-Fi + Mando) de Imagen 3.
 */
@Composable
fun PixelHostIllustration(modifier: Modifier = Modifier.size(116.dp)) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Consola SNES arriba-izquierda
            drawRoundRect(Color.Black, Offset(w * 0.04f, h * 0.08f), Size(w * 0.58f, h * 0.54f), CornerRadius(6f, 6f))
            drawRoundRect(Color(0xFFC6C6D0), Offset(w * 0.06f, h * 0.10f), Size(w * 0.54f, h * 0.50f), CornerRadius(5f, 5f))
            drawRect(Color(0xFF9E9EA8), Offset(w * 0.12f, h * 0.18f), Size(w * 0.42f, h * 0.16f))
            // Botones Power / Reset morados clásicos
            drawRect(Color(0xFF673AB7), Offset(w * 0.14f, h * 0.40f), Size(w * 0.14f, h * 0.08f))
            drawRect(Color(0xFF673AB7), Offset(w * 0.38f, h * 0.40f), Size(w * 0.14f, h * 0.08f))

            // Ondas Wi-Fi azules a la derecha de la consola
            val arcCenter = Offset(w * 0.66f, h * 0.34f)
            drawCircle(Color(0xFF90CAF9), radius = 5f, center = arcCenter)
            for (ring in 1..3) {
                val rad = ring * (w * 0.09f)
                drawArc(
                    color = Color(0xFF7BB8F5),
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(arcCenter.x - rad, arcCenter.y - rad),
                    size = Size(rad * 2, rad * 2),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }
        }
        PixelSnesControllerIcon(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 4.dp)
                .size(78.dp, 36.dp),
            superFamicomColors = false
        )
    }
}

/**
 * Ilustración de UNIRSE A PARTIDA (INVITADO) (Mando SNES + Ondas Wi-Fi dobles) de Imagen 3.
 */
@Composable
fun PixelGuestIllustration(modifier: Modifier = Modifier.size(116.dp)) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Cable central superior
            drawLine(
                color = Color.Black,
                start = Offset(w * 0.50f, h * 0.52f),
                end = Offset(w * 0.48f, h * 0.24f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            // Ondas Wi-Fi izquierda y derecha
            for (ring in 1..2) {
                val rad = ring * (w * 0.11f)
                drawArc(
                    color = Color(0xFF7BB8F5),
                    startAngle = 135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.34f - rad, h * 0.28f - rad),
                    size = Size(rad * 2, rad * 2),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF7BB8F5),
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.66f - rad, h * 0.28f - rad),
                    size = Size(rad * 2, rad * 2),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }
        }
        PixelSnesControllerIcon(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .size(96.dp, 44.dp),
            superFamicomColors = true
        )
    }
}

/**
 * Iconos cuadrados de la fila EXTRAS en HERRAMIENTAS (Img 2).
 */
@Composable
fun PixelFolderArrowIcon(modifier: Modifier = Modifier.size(34.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color.Black, Offset(w * 0.06f, h * 0.16f), Size(w * 0.80f, h * 0.68f), CornerRadius(4f, 4f))
        drawRect(Color(0xFFE6A84C), Offset(w * 0.10f, h * 0.12f), Size(w * 0.34f, h * 0.14f))
        drawRect(Color(0xFFF4C26B), Offset(w * 0.10f, h * 0.22f), Size(w * 0.72f, h * 0.58f))
        // Flecha verde derecha
        drawRect(Color(0xFF66BB6A), Offset(w * 0.42f, h * 0.44f), Size(w * 0.34f, h * 0.18f))
        drawCircle(Color(0xFF66BB6A), radius = w * 0.14f, center = Offset(w * 0.78f, h * 0.53f))
    }
}

@Composable
fun PixelCrtTvIcon(isActive: Boolean, modifier: Modifier = Modifier.size(34.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Antenas
        drawLine(Color.Black, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.30f, h * 0.04f), strokeWidth = 4f)
        drawLine(Color.Black, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.70f, h * 0.04f), strokeWidth = 4f)
        // Caja TV
        drawRoundRect(Color.Black, Offset(w * 0.08f, h * 0.22f), Size(w * 0.84f, h * 0.68f), CornerRadius(5f, 5f))
        drawRoundRect(Color(0xFF8C969E), Offset(w * 0.12f, h * 0.26f), Size(w * 0.76f, h * 0.60f), CornerRadius(4f, 4f))
        // Pantalla CRT
        val screenColor = if (isActive) Color(0xFF4DD0E1) else Color(0xFF3E4E56)
        drawRoundRect(screenColor, Offset(w * 0.18f, h * 0.33f), Size(w * 0.50f, h * 0.46f), CornerRadius(4f, 4f))
        // Botones laterales TV
        drawCircle(Color(0xFF66BB6A), radius = 3f, center = Offset(w * 0.78f, h * 0.42f))
        drawCircle(Color(0xFF263238), radius = 3f, center = Offset(w * 0.78f, h * 0.56f))
    }
}

@Composable
fun PixelMoveControlsIcon(modifier: Modifier = Modifier.size(34.dp)) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.46f
        val cy = size.height * 0.46f
        val arrowColor = Color(0xFF9090A0)
        // Líneas de cruz de movimiento
        drawLine(arrowColor, Offset(cx - 18f, cy), Offset(cx + 18f, cy), strokeWidth = 4f)
        drawLine(arrowColor, Offset(cx, cy - 18f), Offset(cx, cy + 18f), strokeWidth = 4f)
        // Punto rojo central (idéntico a Img 2)
        drawCircle(Color.Black, radius = 6.5f, center = Offset(cx, cy))
        drawCircle(Color(0xFFE53935), radius = 4.5f, center = Offset(cx, cy))
        // Cursor mano blanca abajo-derecha
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.58f, size.height * 0.56f),
            size = Size(size.width * 0.28f, size.height * 0.34f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}
