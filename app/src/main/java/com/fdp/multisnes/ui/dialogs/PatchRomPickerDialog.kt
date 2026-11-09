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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fdp.multisnes.data.model.RomGame
import com.fdp.multisnes.data.rom.RomIntentHandler
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette

private val FocusCyan = Color(0xFF00E5FF)
private val FocusYellow = Color(0xFFFFD600)

/**
 * MÓDULO 3: Diálogo retro que se muestra cuando el usuario abre un archivo de parche (.ips o .bps)
 * con "Abrir con MULTI SNES BY FDP", permitiéndole elegir a cuál ROM de su biblioteca aplicar el parche.
 */
@Composable
fun PatchRomPickerDialog(
    patchPayload: RomIntentHandler.PendingPatchPayload,
    games: List<RomGame>,
    onSelectBaseRom: (RomGame) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 410.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF23232E))
                .border(3.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedPixelText(
                text = "APLICAR PARCHE (.${patchPayload.patchExtension.uppercase()})",
                style = PixelTypography.SectionTitle,
                color = FocusYellow,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedPixelText(
                text = "Parche: ${patchPayload.patchFileName}\nElige la ROM de tu biblioteca sobre la que deseas aplicar el parche e iniciar el juego:",
                style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                color = Color(0xFFD8D8E8),
                maxLines = 4
            )

            if (games.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A22))
                        .border(2.dp, Color(0xFF3A3A3C), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText(
                        text = "Tu biblioteca está vacía.\nPrimero escanea o abre una ROM base (.sfc/.smc/.zip) para poder aplicarle este parche.",
                        style = PixelTypography.LabelBold.copy(fontSize = 11.sp),
                        color = SnesPalette.FdpOrange,
                        maxLines = 5
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = games, key = { it.id }) { game ->
                        var isFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .onKeyEvent { ev ->
                                    val nk = ev.nativeKeyEvent
                                    if (nk.action == KeyEvent.ACTION_DOWN &&
                                        (nk.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                            nk.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                            nk.keyCode == KeyEvent.KEYCODE_ENTER)
                                    ) {
                                        onSelectBaseRom(game)
                                        true
                                    } else false
                                }
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isFocused) Color(0xFF3E3E56) else Color(0xFF323242))
                                .border(
                                    width = if (isFocused) 3.dp else 2.dp,
                                    color = if (isFocused) FocusCyan else Color(0xFF3A3A3C),
                                    shape = RoundedCornerShape(7.dp)
                                )
                                .focusable()
                                .clickable { onSelectBaseRom(game) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedPixelText(
                                text = game.title,
                                style = PixelTypography.LabelBold.copy(fontSize = 12.sp),
                                color = if (isFocused) FocusYellow else Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            OutlinedPixelText(
                                text = "PARCHEAR Y JUGAR",
                                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                                color = SnesPalette.OnlineNeonGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF555566))
                    .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                    .focusable()
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                OutlinedPixelText("CANCELAR", style = PixelTypography.LabelBold)
            }
        }
    }
}
