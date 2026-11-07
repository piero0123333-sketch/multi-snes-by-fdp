package com.fdp.multisnes.ui.dialogs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fdp.multisnes.data.repository.SaveStateManager
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette
import java.io.File

/**
 * TAREA 2: Diálogo/Modal estilo retro para seleccionar cuál de los hasta 10 estados guardados
 * desea cargar el usuario (ordenados del más reciente al más antiguo, con número de slot,
 * fecha/hora y miniatura capturada del framebuffer).
 */
@Composable
fun SaveSlotPickerDialog(
    gameTitle: String,
    slotsNewestFirst: List<SaveStateManager.SaveSlotInfo>,
    latestSlotNumber: Int,
    onSelectSlot: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22222C))
                .border(3.dp, Color.Black, RoundedCornerShape(12.dp))
                .padding(4.dp)
                .border(2.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            // Encabezado con título y botón cerrar [X]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedPixelText(
                        text = "CARGAR PARTIDA (10 SLOTS)",
                        style = PixelTypography.SectionTitle.copy(fontSize = 14.sp),
                        color = SnesPalette.StarGold
                    )
                    OutlinedPixelText(
                        text = gameTitle,
                        style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                        color = Color(0xFFD0D4E4)
                    )
                }
                Box(
                    modifier = Modifier
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedPixelText(
                text = "COLA ROTATIVA FIFO (${slotsNewestFirst.size}/10 OCUPADOS) - MÁS RECIENTE PRIMERO:",
                style = PixelTypography.LabelBold.copy(fontSize = 9.sp),
                color = SnesPalette.OnlineNeonGreen,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (slotsNewestFirst.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A22))
                        .border(2.dp, Color.Black, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedPixelText(
                        text = "AÚN NO HAY GUARDADOS RÁPIDOS\nEN ESTE JUEGO",
                        style = PixelTypography.LabelBold.copy(fontSize = 12.sp),
                        color = Color(0xFFB8BCD0)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A22))
                        .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(slotsNewestFirst, key = { _, item -> item.slotNumber }) { index, slot ->
                        val isLatest = slot.slotNumber == latestSlotNumber || index == 0
                        SaveSlotRowItem(
                            slot = slot,
                            isLatest = isLatest,
                            onClick = { onSelectSlot(slot.slotNumber) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveSlotRowItem(
    slot: SaveStateManager.SaveSlotInfo,
    isLatest: Boolean,
    onClick: () -> Unit
) {
    val thumbBitmap = remember(slot.screenshotPath, slot.timestampEpochMs) {
        val path = slot.screenshotPath
        if (!path.isNullOrBlank() && File(path).exists()) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    val borderCol = if (isLatest) SnesPalette.OnlineNeonGreen else SnesPalette.FramePurple
    val bgCol = if (isLatest) Color(0xFF283C34) else Color(0xFF2C2C38)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgCol)
            .border(2.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Miniatura capturada del framebuffer (4:3)
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 58.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .border(1.5.dp, Color.Black, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap,
                    contentDescription = "Slot ${slot.slotNumber}",
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                OutlinedPixelText(
                    text = "SNES\n#${slot.slotNumber}",
                    style = PixelTypography.LabelBold.copy(fontSize = 9.sp),
                    color = Color(0xFF9EA4B8)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Información de número de Slot y Fecha/Hora
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedPixelText(
                    text = "SLOT #${slot.slotNumber}",
                    style = PixelTypography.LabelBold.copy(fontSize = 13.sp),
                    color = if (isLatest) SnesPalette.OnlineNeonGreen else Color.White
                )
                if (isLatest) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SnesPalette.PlayGreenDark)
                            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        OutlinedPixelText(
                            text = "ÚLTIMO",
                            style = PixelTypography.LabelBold.copy(fontSize = 8.sp),
                            color = SnesPalette.StarGold
                        )
                    }
                }
            }
            OutlinedPixelText(
                text = slot.formattedDate,
                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                color = Color(0xFFD6DAE8),
                textAlign = TextAlign.Start
            )
        }

        // Botón CARGAR
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SnesPalette.LoadBlueMain)
                .border(1.5.dp, Color.Black, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            OutlinedPixelText(
                text = "CARGAR",
                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                color = Color.White
            )
        }
    }
}
