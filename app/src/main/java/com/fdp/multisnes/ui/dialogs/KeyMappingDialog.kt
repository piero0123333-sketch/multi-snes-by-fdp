package com.fdp.multisnes.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fdp.multisnes.core.SnesButtons
import com.fdp.multisnes.input.MacroEngine
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette

/**
 * TAREA 4: Mapeo Ampliado para Mando Conectado (Gamepad Bluetooth/USB: SOURCE_GAMEPAD / SOURCE_JOYSTICK).
 * Permite vincular cualquier botón físico, gatillo (L2/R2) o stick (L3/R3) a:
 * 1. Controles base de SNES (A, B, X, Y, L, R, START, SELECT, D-PAD).
 * 2. Acciones de Sistema en partida (GUARDAR PARTIDA, CARGAR PARTIDA RÁPIDA, RETROCEDER TIEMPO / REWIND, ALTERNAR VELOCIDAD).
 * 3. Ranuras de Macros (Macro 1, Macro 2, Macro 3, Macro 4).
 */
@Composable
fun KeyMappingDialog(
    currentKeyMap: Map<String, Int>,
    onBindKey: (actionName: String, keyCode: Int) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var listeningKeyName by remember { mutableStateOf<String?>(null) }
    var listeningDisplayLabel by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val detectedController = remember { MacroEngine.detectConnectedControllerName() }

    LaunchedEffect(listeningKeyName) {
        if (listeningKeyName != null) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF22222C))
                .border(3.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(10.dp))
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    val targetKey = listeningKeyName
                    if (targetKey != null && keyEvent.type == KeyEventType.KeyDown) {
                        val code = keyEvent.nativeKeyEvent.keyCode
                        if (code != KeyEvent.KEYCODE_BACK) {
                            onBindKey(targetKey, code)
                            listeningKeyName = null
                            return@onKeyEvent true
                        }
                    }
                    false
                }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedPixelText(
                text = "MAPEAR MANDO, SISTEMA Y MACROS",
                style = PixelTypography.SectionTitle.copy(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedPixelText(
                text = if (detectedController != null) "MANDO DETECTADO: $detectedController"
                else "CONECTA UN MANDO BLUETOOTH / USB OTG",
                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                color = if (detectedController != null) SnesPalette.OnlineNeonGreen else SnesPalette.StarGold,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedPixelText(
                text = if (listeningKeyName != null)
                    "PULSA UN BOTÓN, GATILLO (L2/R2) O STICK (L3/R3) PARA: $listeningDisplayLabel"
                else
                    "TOCA CUALQUIER FUNCIÓN PARA ASIGNARLE UN BOTÓN DEL MANDO",
                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                color = if (listeningKeyName != null) SnesPalette.OnlineNeonGreen else Color(0xFFB8B8C8),
                modifier = Modifier.fillMaxWidth()
            )

            // Atajos rápidos para L2, R2, L3, R3 cuando está esperando un botón (por si el mando envía L2/R2 solo por eje analógico)
            if (listeningKeyName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickTriggers = listOf(
                        "L2" to KeyEvent.KEYCODE_BUTTON_L2,
                        "R2" to KeyEvent.KEYCODE_BUTTON_R2,
                        "L3 (STICK IZQ)" to KeyEvent.KEYCODE_BUTTON_THUMBL,
                        "R3 (STICK DER)" to KeyEvent.KEYCODE_BUTTON_THUMBR
                    )
                    quickTriggers.forEach { (label, code) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF3A3454))
                                .border(1.dp, SnesPalette.OnlineNeonGreen, RoundedCornerShape(5.dp))
                                .clickable {
                                    listeningKeyName?.let { target ->
                                        onBindKey(target, code)
                                        listeningKeyName = null
                                    }
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedPixelText(
                                text = label,
                                style = PixelTypography.LabelBold.copy(fontSize = 8.sp),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Controles base de SNES
                OutlinedPixelText(
                    text = "1. CONTROLES BASE DE SNES",
                    style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                    color = SnesPalette.FdpBlue,
                    textAlign = TextAlign.Start
                )

                SnesButtons.Action.entries.forEach { action ->
                    KeyMapRow(
                        label = action.displayName,
                        assignedCode = currentKeyMap[action.name] ?: 0,
                        isWaiting = listeningKeyName == action.name,
                        onClick = {
                            listeningKeyName = action.name
                            listeningDisplayLabel = action.displayName
                        }
                    )
                }

                // 2. Acciones de Sistema en partida (Guardar, Cargar Rápido, Rewind, Fast Forward)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedPixelText(
                    text = "2. ACCIONES DE SISTEMA EN PARTIDA",
                    style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                    color = SnesPalette.StarGold,
                    textAlign = TextAlign.Start
                )

                SnesButtons.SystemBindAction.entries.forEach { sysAction ->
                    KeyMapRow(
                        label = sysAction.displayName,
                        assignedCode = currentKeyMap[sysAction.keyName] ?: 0,
                        isWaiting = listeningKeyName == sysAction.keyName,
                        onClick = {
                            listeningKeyName = sysAction.keyName
                            listeningDisplayLabel = sysAction.displayName
                        }
                    )
                }

                // 3. Ranuras de Macros (M1 - M4)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedPixelText(
                    text = "3. RANURAS DE MACROS (M1 - M4)",
                    style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                    color = SnesPalette.OnlineNeonGreen,
                    textAlign = TextAlign.Start
                )

                SnesButtons.MacroBindAction.entries.forEach { macroAction ->
                    KeyMapRow(
                        label = macroAction.displayName,
                        assignedCode = currentKeyMap[macroAction.keyName] ?: 0,
                        isWaiting = listeningKeyName == macroAction.keyName,
                        onClick = {
                            listeningKeyName = macroAction.keyName
                            listeningDisplayLabel = macroAction.displayName
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF4A4A5A))
                        .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                        .clickable(onClick = onResetDefaults),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText("RESTABLECER", style = PixelTypography.LabelBold.copy(fontSize = 11.sp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SnesPalette.PlayGreenMain)
                        .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText("LISTO", style = PixelTypography.LabelBold.copy(fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun KeyMapRow(
    label: String,
    assignedCode: Int,
    isWaiting: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isWaiting) Color(0xFF3E3560) else Color(0xFF30303C))
            .border(
                width = 1.dp,
                color = if (isWaiting) SnesPalette.OnlineNeonGreen else Color.Black,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedPixelText(
            text = label,
            style = PixelTypography.LabelBold.copy(fontSize = 10.sp)
        )
        OutlinedPixelText(
            text = if (isWaiting) "ESPERANDO..."
            else if (assignedCode == 0) "SIN ASIGNAR"
            else KeyEvent.keyCodeToString(assignedCode).removePrefix("KEYCODE_"),
            style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
            color = SnesPalette.FdpBlue
        )
    }
}
