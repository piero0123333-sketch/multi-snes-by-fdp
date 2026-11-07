package com.fdp.multisnes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object SnesPalette {
    val ScreenBlack = Color(0xFF101014)
    val BgDark = Color(0xFF18181E)
    val HeaderMetal = Color(0xFF42424A)
    val HeaderBorderLight = Color(0xFF6B6B78)
    val HeaderBorderDark = Color(0xFF232329)

    val CardCartridgeGrey = Color(0xFF8B8B93)
    val CardCartridgeLight = Color(0xFFB4B4BC)
    val CardCartridgeDark = Color(0xFF56565E)

    val FramePurple = Color(0xFF5B468A)
    val FramePurpleBright = Color(0xFF886CE4)
    val PanelDarkTranslucent = Color(0xDD25252E)
    val SectionCardDark = Color(0xE632323D)

    val PlayGreenTop = Color(0xFF5CE65C)
    val PlayGreenMain = Color(0xFF43B843)
    val PlayGreenDark = Color(0xFF287A28)

    val LoadBlueTop = Color(0xFF63A4FF)
    val LoadBlueMain = Color(0xFF3E7BFA)
    val LoadBlueDark = Color(0xFF234CA6)

    val ToggleOnGreen = Color(0xFF4CAF50)
    val ToggleOffGrey = Color(0xFF62626C)
    val PrefBlue = Color(0xFF4F86E8)
    val PrefTrackDark = Color(0xFF222233)

    val OnlineNeonGreen = Color(0xFF8CFF9E)
    val StarGold = Color(0xFFFFD54F)
    val StarGreen = Color(0xFF52E552)

    val FdpBlue = Color(0xFF4DA6FF)
    val FdpOrange = Color(0xFFFF7043)
    val FdpGreen = Color(0xFF66BB6A)
}

object PixelTypography {
    val HeaderTitle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 21.sp,
        letterSpacing = 1.5.sp,
        color = Color.White
    )

    val SectionTitle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        letterSpacing = 1.sp,
        color = Color.White
    )

    val CardGameTitle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.4.sp,
        color = Color.White
    )

    val LabelBold = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        color = Color.White
    )
}

private val MultiSnesColorScheme = darkColorScheme(
    primary = SnesPalette.FramePurpleBright,
    secondary = SnesPalette.PrefBlue,
    background = SnesPalette.ScreenBlack,
    surface = SnesPalette.HeaderMetal,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MultiSnesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MultiSnesColorScheme,
        content = content
    )
}
