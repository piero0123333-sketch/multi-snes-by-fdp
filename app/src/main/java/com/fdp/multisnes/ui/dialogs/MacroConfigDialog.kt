package com.fdp.multisnes.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fdp.multisnes.core.SnesButtons
import com.fdp.multisnes.data.model.MacroSlotConfig
import com.fdp.multisnes.input.MacroEngine
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.RetroBlueSlider
import com.fdp.multisnes.ui.theme.RetroOnOffPillSwitch
import com.fdp.multisnes.ui.theme.SnesPalette
import kotlin.math.roundToInt

/**
 * MÓDULO 3: Configuración interactiva de los 4 Macros (M1, M2, M3, M4):
 * - Activar/Desactivar botón flotante en pantalla
 * - Elegir combinación de 1 hasta 3 botones de SNES simultáneos (A, B, X, Y, L, R, START, SELECT, D-PAD)
 * - Elegir Modo 1 (Repetición Turbo cada 10..500 ms), Modo 2 (Mantener Presionado / Auto-Hold)
 *   o Modo 3 (Combo Directo: presiona juntos los botones mientras se mantiene el macro)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroConfigDialog(
    macroSlots: List<MacroSlotConfig>,
    onUpdateMacroSlot: (Int, MacroSlotConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentSlot = macroSlots.getOrNull(selectedTab) ?: MacroSlotConfig(slotIndex = selectedTab, label = "M${selectedTab + 1}")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 410.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF22222E))
                .border(3.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedPixelText(
                text = "MACROS TURBO / AUTO-HOLD",
                style = PixelTypography.SectionTitle.copy(fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth()
            )

            // Pestañas M1, M2, M3, M4
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until MacroEngine.MACRO_COUNT) {
                    val slot = macroSlots.getOrNull(i)
                    val isSelected = selectedTab == i
                    val isEnabled = slot?.enabled == true
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isSelected -> Color(0xFF5A4694)
                                    isEnabled -> Color(0xFF334D3B)
                                    else -> Color(0xFF323240)
                                }
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSelected) SnesPalette.OnlineNeonGreen else Color.Black,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTab = i },
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedPixelText(
                            text = if (isEnabled) "M${i + 1} ON" else "M${i + 1}",
                            style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                            color = if (isEnabled) SnesPalette.OnlineNeonGreen else Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(8.dp))
                    .background(SnesPalette.SectionCardDark)
                    .border(2.dp, Color(0xFF454556), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Habilitar botón virtual Mx en pantalla
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedPixelText(
                        text = "MOSTRAR BOTÓN ${currentSlot.label}",
                        style = PixelTypography.LabelBold.copy(fontSize = 12.sp)
                    )
                    RetroOnOffPillSwitch(
                        checked = currentSlot.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateMacroSlot(selectedTab, currentSlot.copy(enabled = enabled))
                        }
                    )
                }

                // 2. Selección de Modo: Repetición (Turbo) vs Mantener (Auto-Hold)
                OutlinedPixelText(
                    text = "MODO DE OPERACIÓN:",
                    style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                    color = Color(0xFFC8C8DC),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isCombo = currentSlot.comboDirectMode
                    val isTurbo = !isCombo && !currentSlot.autoHoldMode
                    val isHold = !isCombo && currentSlot.autoHoldMode
                    MacroModeChip(
                        text = "REPETICIÓN\n(TURBO)",
                        selected = isTurbo,
                        selectedColor = SnesPalette.LoadBlueMain,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdateMacroSlot(selectedTab, currentSlot.copy(autoHoldMode = false, comboDirectMode = false))
                        }
                    )
                    MacroModeChip(
                        text = "MANTENER\n(AUTO-HOLD)",
                        selected = isHold,
                        selectedColor = SnesPalette.PlayGreenMain,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdateMacroSlot(selectedTab, currentSlot.copy(autoHoldMode = true, comboDirectMode = false))
                        }
                    )
                    MacroModeChip(
                        text = "COMBO\n(DIRECTO)",
                        selected = isCombo,
                        selectedColor = Color(0xFFB4532A),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdateMacroSlot(selectedTab, currentSlot.copy(comboDirectMode = true))
                        }
                    )
                }

                OutlinedPixelText(
                    text = when {
                        currentSlot.comboDirectMode -> "Presiona juntos los botones elegidos mientras mantengas el macro."
                        currentSlot.autoHoldMode -> "Un toque los deja presionados; otro toque los suelta."
                        else -> "Pulsa y suelta los botones elegidos repetidamente."
                    },
                    style = PixelTypography.LabelBold.copy(fontSize = 9.sp),
                    color = Color(0xFFB8B8C8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                // 3. Intervalo en milisegundos (10 ms a 500 ms) cuando está en Modo Repetición (Turbo)
                if (!currentSlot.autoHoldMode && !currentSlot.comboDirectMode) {
                    val fraction = ((currentSlot.intervalMs - MacroEngine.MIN_INTERVAL_MS).toFloat() /
                        (MacroEngine.MAX_INTERVAL_MS - MacroEngine.MIN_INTERVAL_MS).toFloat()).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedPixelText(
                            text = "INTERVALO TURBO:",
                            style = PixelTypography.LabelBold.copy(fontSize = 11.sp)
                        )
                        OutlinedPixelText(
                            text = "${currentSlot.intervalMs} ms",
                            style = PixelTypography.LabelBold.copy(fontSize = 12.sp),
                            color = SnesPalette.OnlineNeonGreen
                        )
                    }

                    RetroBlueSlider(
                        value = fraction,
                        enabled = true,
                        onValueChange = { norm ->
                            val ms = (MacroEngine.MIN_INTERVAL_MS +
                                norm * (MacroEngine.MAX_INTERVAL_MS - MacroEngine.MIN_INTERVAL_MS)).roundToInt()
                            onUpdateMacroSlot(selectedTab, currentSlot.copy(intervalMs = ms))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Combinación de botones SNES del Macro (de 1 hasta 3 botones al mismo tiempo)
                val selectedCount = Integer.bitCount(currentSlot.targetButtonsMask)
                val comboFull = selectedCount >= SnesButtons.MAX_MACRO_COMBO_BUTTONS
                OutlinedPixelText(
                    text = "COMBINACIÓN ($selectedCount/${SnesButtons.MAX_MACRO_COMBO_BUTTONS}): ${SnesButtons.formatBitMaskSummary(currentSlot.targetButtonsMask)}",
                    style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                    color = if (comboFull) SnesPalette.StarGold else Color(0xFFC8C8DC),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SnesButtons.Action.entries.forEach { action ->
                        val active = (currentSlot.targetButtonsMask and action.bitMask) != 0
                        val blocked = !active && comboFull
                        Box(
                            modifier = Modifier
                                .alpha(if (blocked) 0.35f else 1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) Color(0xFF4F86E8) else Color(0xFF2C2C38))
                                .border(
                                    width = 1.5.dp,
                                    color = if (active) SnesPalette.OnlineNeonGreen else Color.Black,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = !blocked) {
                                    val nextMask = if (active) {
                                        currentSlot.targetButtonsMask and action.bitMask.inv()
                                    } else {
                                        currentSlot.targetButtonsMask or action.bitMask
                                    }
                                    onUpdateMacroSlot(selectedTab, currentSlot.copy(targetButtonsMask = nextMask))
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            OutlinedPixelText(
                                text = action.displayName,
                                style = PixelTypography.LabelBold.copy(fontSize = 10.sp)
                            )
                        }
                    }
                }

                if (comboFull) {
                    OutlinedPixelText(
                        text = "MÁXIMO 3 BOTONES. QUITA UNO PARA ELEGIR OTRO.",
                        style = PixelTypography.LabelBold.copy(fontSize = 9.sp),
                        color = SnesPalette.StarGold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SnesPalette.PlayGreenMain)
                    .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                OutlinedPixelText("GUARDAR Y CERRAR", style = PixelTypography.LabelBold.copy(fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun MacroModeChip(
    text: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) selectedColor else Color(0xFF383846))
            .border(2.dp, if (selected) SnesPalette.OnlineNeonGreen else Color.Black, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        OutlinedPixelText(
            text = text,
            style = PixelTypography.LabelBold.copy(fontSize = 9.sp, lineHeight = 10.sp)
        )
    }
}
