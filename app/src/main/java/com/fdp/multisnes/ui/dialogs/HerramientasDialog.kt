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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fdp.multisnes.data.model.EmulatorSettings
import com.fdp.multisnes.data.model.ScreenScaleMode
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelCrtTvIcon
import com.fdp.multisnes.ui.theme.PixelFloppyIcon
import com.fdp.multisnes.ui.theme.PixelFolderArrowIcon
import com.fdp.multisnes.ui.theme.PixelMoveControlsIcon
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.RainbowHerramientasTitle
import com.fdp.multisnes.ui.theme.RetroBlueSlider
import com.fdp.multisnes.ui.theme.RetroBlueSwitch
import com.fdp.multisnes.ui.theme.RetroOnOffPillSwitch
import com.fdp.multisnes.ui.theme.SnesPalette

/**
 * Menú superpuesto HERRAMIENTAS fiel al 100% a la Imagen 2:
 * - Se abre desde el engranaje (⚙️) del catálogo y desde Pausa (||) en partida.
 * - Incluye CONTROLES EN PANTALLA, PREFERENCIAS, PANTALLA (ESCALA), EXTRAS y
 *   el botón SALIR AL MENÚ cuando se invoca dentro de un juego activo.
 */
@Composable
fun HerramientasDialog(
    settings: EmulatorSettings,
    isInGame: Boolean,
    onDismiss: () -> Unit,
    onSetDpad: (Boolean) -> Unit,
    onSetJoystick: (Boolean) -> Unit,
    onSetExternalController: (Boolean) -> Unit,
    onOpenKeyMapper: () -> Unit,
    onOpenMacroConfig: () -> Unit = {},
    onSetVibration: (Boolean, Float) -> Unit,
    onSetSound: (Boolean, Float) -> Unit,
    onSelectScale: (ScreenScaleMode) -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onToggleCrt: () -> Unit,
    onMoveButtonsEditor: () -> Unit,
    onExitToCatalog: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 430.dp)
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(10.dp))
                .background(SnesPalette.PanelDarkTranslucent)
                .border(3.dp, SnesPalette.FramePurple, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Botón cerrar [X] esquina superior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF4F4F5E))
                    .border(2.dp, Color.Black, RoundedCornerShape(5.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                OutlinedPixelText(
                    text = "X",
                    style = PixelTypography.LabelBold.copy(fontSize = 15.sp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cabecera HERRAMIENTAS + subtítulo MULTI SNES BY FDP
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RainbowHerramientasTitle()
                    Spacer(modifier = Modifier.height(2.dp))
                    OutlinedPixelText(
                        text = "MULTI SNES BY FDP",
                        style = PixelTypography.LabelBold.copy(fontSize = 12.sp),
                        color = Color(0xFFD0D0DC)
                    )
                }

                // 1. CONTROLES EN PANTALLA
                OutlinedPixelText(
                    text = "CONTROLES EN PANTALLA",
                    style = PixelTypography.SectionTitle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SnesPalette.SectionCardDark)
                        .border(2.dp, Color(0xFF4A4A58), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // BOTONES (D-PAD)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedPixelText(
                                text = "BOTONES\n(D-PAD)",
                                style = PixelTypography.LabelBold.copy(fontSize = 11.sp, lineHeight = 12.sp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MiniDpadPixelIcon()
                                Spacer(modifier = Modifier.width(4.dp))
                                RetroOnOffPillSwitch(
                                    checked = settings.dpadEnabled,
                                    onCheckedChange = onSetDpad
                                )
                            }
                        }

                        // JOYSTICK
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedPixelText(
                                text = "JOYSTICK\n ",
                                style = PixelTypography.LabelBold.copy(fontSize = 11.sp, lineHeight = 12.sp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MiniJoystickPixelIcon()
                                Spacer(modifier = Modifier.width(4.dp))
                                RetroOnOffPillSwitch(
                                    checked = settings.joystickEnabled,
                                    onCheckedChange = onSetJoystick
                                )
                            }
                        }

                        // MANDO EXTERNO
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedPixelText(
                                text = "MANDO\nEXTERNO",
                                style = PixelTypography.LabelBold.copy(fontSize = 11.sp, lineHeight = 12.sp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MiniGamepadPixelIcon()
                                Spacer(modifier = Modifier.width(4.dp))
                                RetroOnOffPillSwitch(
                                    checked = settings.externalControllerEnabled,
                                    onCheckedChange = onSetExternalController
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF252530))
                                .border(1.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenKeyMapper)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            OutlinedPixelText(
                                text = "MAPEAR BOTONES",
                                style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                                color = Color(0xFFE0E0EA)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2B3548))
                                .border(1.dp, SnesPalette.OnlineNeonGreen, RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenMacroConfig)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            OutlinedPixelText(
                                text = "MACROS (M1-M4)",
                                style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                                color = SnesPalette.OnlineNeonGreen
                            )
                        }
                    }
                }

                // 2. PREFERENCIAS
                OutlinedPixelText(
                    text = "PREFERENCIAS",
                    style = PixelTypography.SectionTitle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SnesPalette.SectionCardDark)
                        .border(2.dp, Color(0xFF4A4A58), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedPixelText(text = "VIBRACIÓN", style = PixelTypography.LabelBold.copy(fontSize = 13.sp))
                        RetroBlueSwitch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = { onSetVibration(it, settings.vibrationIntensity) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VibratingPhonePixelIcon()
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedPixelText(text = "INTENSIDAD", style = PixelTypography.LabelBold.copy(fontSize = 12.sp))
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroBlueSlider(
                            value = settings.vibrationIntensity,
                            enabled = settings.vibrationEnabled,
                            onValueChange = { onSetVibration(settings.vibrationEnabled, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedPixelText(text = "SONIDO", style = PixelTypography.LabelBold.copy(fontSize = 13.sp))
                        RetroBlueSwitch(
                            checked = settings.soundEnabled,
                            onCheckedChange = { onSetSound(it, settings.soundVolume) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeakerPixelIcon()
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedPixelText(text = "VOLUMEN", style = PixelTypography.LabelBold.copy(fontSize = 12.sp))
                        Spacer(modifier = Modifier.width(12.dp))
                        RetroBlueSlider(
                            value = settings.soundVolume,
                            enabled = settings.soundEnabled,
                            onValueChange = { onSetSound(settings.soundEnabled, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. PANTALLA (ESCALA)
                OutlinedPixelText(
                    text = "PANTALLA",
                    style = PixelTypography.SectionTitle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SnesPalette.SectionCardDark)
                        .border(2.dp, Color(0xFF4A4A58), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    OutlinedPixelText(
                        text = "ESCALA",
                        style = PixelTypography.LabelBold.copy(fontSize = 13.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenScaleMode.entries.forEach { mode ->
                            val isSelected = settings.scaleMode == mode
                            val borderBrush = if (isSelected) {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF79B0), Color(0xFF64FFDA), Color(0xFF82B1FF))
                                )
                            } else {
                                Brush.horizontalGradient(listOf(Color.Black, Color.Black))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF3A344E) else Color(0xFF4A4458))
                                    .border(2.dp, borderBrush, RoundedCornerShape(6.dp))
                                    .clickable { onSelectScale(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedPixelText(
                                    text = mode.label,
                                    style = PixelTypography.LabelBold.copy(fontSize = 10.sp, lineHeight = 12.sp)
                                )
                            }
                        }
                    }
                }

                // 4. EXTRAS (GUARDADO R., CARGAR R., FILTROS CRT, MOVER BOTONES)
                OutlinedPixelText(
                    text = "EXTRAS",
                    style = PixelTypography.SectionTitle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SnesPalette.SectionCardDark)
                        .border(2.dp, Color(0xFF4A4A58), RoundedCornerShape(8.dp))
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ExtraSquareButton(
                        label = "GUARDADO R.",
                        enabled = isInGame,
                        highlighted = false,
                        onClick = onQuickSave
                    ) {
                        PixelFloppyIcon(isSavedBlue = true, modifier = Modifier.size(32.dp))
                    }

                    ExtraSquareButton(
                        label = "CARGAR R.",
                        enabled = isInGame,
                        highlighted = false,
                        onClick = onQuickLoad
                    ) {
                        PixelFolderArrowIcon(modifier = Modifier.size(34.dp))
                    }

                    ExtraSquareButton(
                        label = "FILTROS CRT",
                        enabled = true,
                        highlighted = settings.crtFilterEnabled,
                        onClick = onToggleCrt
                    ) {
                        PixelCrtTvIcon(isActive = settings.crtFilterEnabled, modifier = Modifier.size(34.dp))
                    }

                    ExtraSquareButton(
                        label = "MOVER BOTONES",
                        enabled = isInGame,
                        highlighted = false,
                        onClick = onMoveButtonsEditor
                    ) {
                        PixelMoveControlsIcon(modifier = Modifier.size(34.dp))
                    }
                }

                // 5. Botón SALIR AL MENÚ cuando se abre en medio de una partida (confirmado por el usuario)
                if (isInGame) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7A2832))
                            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                            .clickable(onClick = onExitToCatalog),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedPixelText(
                            text = "SALIR AL MENÚ PRINCIPAL",
                            style = PixelTypography.LabelBold.copy(fontSize = 13.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraSquareButton(
    label: String,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp, 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (highlighted) Color(0xFF6A569E) else Color(0xFF564F68))
                .border(
                    width = 2.dp,
                    color = if (highlighted) SnesPalette.OnlineNeonGreen else Color.Black,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedPixelText(
            text = label,
            style = PixelTypography.LabelBold.copy(fontSize = 9.sp)
        )
    }
}

@Composable
private fun MiniDpadPixelIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawRect(Color.Black, Offset(w * 0.32f, h * 0.08f), Size(w * 0.36f, h * 0.84f))
        drawRect(Color.Black, Offset(w * 0.08f, h * 0.32f), Size(w * 0.84f, h * 0.36f))
        drawRect(Color(0xFF767684), Offset(w * 0.36f, h * 0.12f), Size(w * 0.28f, h * 0.76f))
        drawRect(Color(0xFF767684), Offset(w * 0.12f, h * 0.36f), Size(w * 0.76f, h * 0.28f))
    }
}

@Composable
private fun MiniJoystickPixelIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val r = size.minDimension / 2f
        drawCircle(Color.Black, radius = r)
        drawCircle(Color(0xFF585866), radius = r * 0.85f)
        drawCircle(Color(0xFF8C8C9C), radius = r * 0.52f)
    }
}

@Composable
private fun MiniGamepadPixelIcon() {
    Canvas(modifier = Modifier.size(26.dp, 20.dp)) {
        drawRoundRect(
            color = Color(0xFF686878),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawCircle(Color(0xFF2196F3), radius = 2.5f, center = Offset(size.width * 0.76f, size.height * 0.38f))
        drawCircle(Color(0xFFE53935), radius = 2.5f, center = Offset(size.width * 0.84f, size.height * 0.56f))
    }
}

@Composable
private fun VibratingPhonePixelIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF9090A4), Offset(w * 0.26f, h * 0.10f), Size(w * 0.48f, h * 0.80f), CornerRadius(3f, 3f))
        drawRect(Color(0xFF2C3E50), Offset(w * 0.32f, h * 0.20f), Size(w * 0.36f, h * 0.56f))
        // Líneas de vibración laterales
        drawLine(Color(0xFF9C7CF4), Offset(w * 0.12f, h * 0.24f), Offset(w * 0.12f, h * 0.76f), strokeWidth = 3f)
        drawLine(Color(0xFF9C7CF4), Offset(w * 0.88f, h * 0.24f), Offset(w * 0.88f, h * 0.76f), strokeWidth = 3f)
    }
}

@Composable
private fun SpeakerPixelIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFFD69E5C), Offset(w * 0.12f, h * 0.32f), Size(w * 0.32f, h * 0.36f))
        drawCircle(Color(0xFFE6B472), radius = w * 0.24f, center = Offset(w * 0.44f, h * 0.50f))
        drawLine(Color(0xFF64B5F6), Offset(w * 0.74f, h * 0.28f), Offset(w * 0.82f, h * 0.50f), strokeWidth = 3f)
        drawLine(Color(0xFF64B5F6), Offset(w * 0.82f, h * 0.50f), Offset(w * 0.74f, h * 0.72f), strokeWidth = 3f)
    }
}
