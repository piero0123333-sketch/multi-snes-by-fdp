package com.fdp.multisnes.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fdp.multisnes.data.model.RomGame
import com.fdp.multisnes.ui.theme.MultiSnesTopBar
import com.fdp.multisnes.ui.theme.OutlinedPixelText
import com.fdp.multisnes.ui.theme.PixelGearFloatingButton
import com.fdp.multisnes.ui.theme.PixelGuestIllustration
import com.fdp.multisnes.ui.theme.PixelHostIllustration
import com.fdp.multisnes.ui.theme.PixelTypography
import com.fdp.multisnes.ui.theme.SnesPalette

enum class HotspotLobbyStatus {
    IDLE,
    SELECTING_HOST_GAME,
    HOST_WAITING_GUEST,
    GUEST_SEARCHING_HOST
}

/**
 * Pantalla del Modo ONLINE por Hotspot directo (<10 cm, 2 Jugadores, Cero Lag)
 * idéntica a la Imagen 3:
 * - CREAR PARTIDA (HOST)
 * - UNIRSE A PARTIDA (INVITADO)
 */
@Composable
fun OnlineHotspotScreen(
    games: List<RomGame>,
    isScanning: Boolean,
    lobbyStatus: HotspotLobbyStatus,
    statusMessage: String,
    localHotspotIp: String,
    onBackToCatalog: () -> Unit,
    onAutoScanGames: () -> Unit,
    onPickRomFolder: () -> Unit,
    onClickCreateHost: () -> Unit,
    onHostGameChosen: (RomGame) -> Unit,
    onClickJoinGuest: () -> Unit,
    onManualJoinIp: (String) -> Unit,
    onCancelLobbyWait: () -> Unit,
    onOpenHerramientas: () -> Unit
) {
    var manualIpText by remember { mutableStateOf("192.168.43.1") }
    var showManualIpInput by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SnesPalette.ScreenBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MultiSnesTopBar(
                isOnlineSelected = true,
                isScanning = isScanning,
                onToggleOnline = onBackToCatalog,
                onAutoScanGames = onAutoScanGames,
                onLongPressFolderPicker = onPickRomFolder
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Tarjeta 1: CREAR PARTIDA (HOST)
                HotspotBigActionCard(
                    title = "CREAR\nPARTIDA\n(HOST)",
                    onClick = onClickCreateHost
                ) {
                    PixelHostIllustration()
                }

                // Tarjeta 2: UNIRSE A PARTIDA (INVITADO)
                HotspotBigActionCard(
                    title = "UNIRSE A\nPARTIDA\n(INVITADO)",
                    onClick = onClickJoinGuest
                ) {
                    PixelGuestIllustration()
                }

                // Estado en vivo de la conexión Hotspot 2 Jugadores cuando está buscando/esperando
                if (lobbyStatus == HotspotLobbyStatus.HOST_WAITING_GUEST ||
                    lobbyStatus == HotspotLobbyStatus.GUEST_SEARCHING_HOST ||
                    statusMessage.isNotBlank()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SnesPalette.SectionCardDark)
                            .border(2.dp, SnesPalette.OnlineNeonGreen, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedPixelText(
                            text = statusMessage.ifBlank { "MODO HOTSPOT ULTRA-BAJA LATENCIA LISTO" },
                            style = PixelTypography.LabelBold.copy(fontSize = 12.sp),
                            color = SnesPalette.OnlineNeonGreen,
                            maxLines = 4
                        )

                        if (localHotspotIp.isNotBlank()) {
                            OutlinedPixelText(
                                text = "IP LOCAL DETECTADA: $localHotspotIp • UDP DIRECTO (<1.5ms)",
                                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                                color = Color(0xFFB8C4E0)
                            )
                        }

                        if (lobbyStatus == HotspotLobbyStatus.GUEST_SEARCHING_HOST) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF383848))
                                    .border(1.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(6.dp))
                                    .clickable { showManualIpInput = !showManualIpInput }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                OutlinedPixelText(
                                    text = "CONECTAR POR IP MANUAL DEL HOST",
                                    style = PixelTypography.LabelBold.copy(fontSize = 10.sp)
                                )
                            }

                            if (showManualIpInput) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualIpText,
                                        onValueChange = { manualIpText = it.trim() },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = SnesPalette.OnlineNeonGreen,
                                            unfocusedBorderColor = SnesPalette.FramePurple
                                        ),
                                        label = { Text("IP del Host", color = Color.LightGray, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SnesPalette.PlayGreenMain)
                                            .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                                            .clickable { onManualJoinIp(manualIpText) }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        OutlinedPixelText("UNIR", style = PixelTypography.LabelBold)
                                    }
                                }
                            }
                        }

                        if (lobbyStatus != HotspotLobbyStatus.IDLE) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF7A2832))
                                    .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                                    .clickable(onClick = onCancelLobbyWait)
                                    .padding(horizontal = 18.dp, vertical = 6.dp)
                            ) {
                                OutlinedPixelText("CANCELAR ESPERA", style = PixelTypography.LabelBold.copy(fontSize = 11.sp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        // Engranaje inferior derecho (⚙️) idéntico a Imagen 3
        PixelGearFloatingButton(
            onClick = onOpenHerramientas,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // Selector rápido de juego para el Host al tocar CREAR PARTIDA (HOST)
        if (lobbyStatus == HotspotLobbyStatus.SELECTING_HOST_GAME) {
            HostGamePickerDialog(
                games = games,
                onSelectGame = onHostGameChosen,
                onDismiss = onCancelLobbyWait
            )
        }
    }
}

@Composable
private fun HotspotBigActionCard(
    title: String,
    onClick: () -> Unit,
    illustration: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF3E3356))
            .border(3.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF70707A),
                            Color(0xFF5A5A64),
                            Color(0xFF484852)
                        )
                    )
                )
                .border(3.dp, Color.Black, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            illustration()
            OutlinedPixelText(
                text = title,
                modifier = Modifier.weight(1f),
                style = PixelTypography.HeaderTitle.copy(
                    fontSize = 23.sp,
                    lineHeight = 27.sp
                ),
                maxLines = 3
            )
        }
    }
}

@Composable
private fun HostGamePickerDialog(
    games: List<RomGame>,
    onSelectGame: (RomGame) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF23232E))
                .border(3.dp, SnesPalette.FramePurpleBright, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedPixelText(
                text = "ELIGE JUEGO 2 JUGADORES (HOST)",
                style = PixelTypography.SectionTitle,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedPixelText(
                text = "Prende tu Zona Wi-Fi (Hotspot), conecta el segundo celular (<10 cm) y elige la ROM:",
                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                color = Color(0xFFC8C8D8),
                maxLines = 3
            )

            if (games.isEmpty()) {
                OutlinedPixelText(
                    text = "Primero escanea tus ROMs con BUSCAR JUEGOS (AUTO).",
                    style = PixelTypography.LabelBold,
                    color = SnesPalette.FdpOrange
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    games.forEach { game ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF343444))
                                .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
                                .clickable { onSelectGame(game) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedPixelText(
                                text = game.title,
                                style = PixelTypography.LabelBold.copy(fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                            OutlinedPixelText(
                                text = "MANDO 1+2",
                                style = PixelTypography.LabelBold.copy(fontSize = 10.sp),
                                color = SnesPalette.OnlineNeonGreen
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF555566))
                    .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                OutlinedPixelText("CERRAR", style = PixelTypography.LabelBold)
            }
        }
    }
}
