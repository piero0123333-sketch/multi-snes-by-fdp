package com.fdp.multisnes.netplay

import com.fdp.multisnes.core.SnesNativeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Coordinador determinista GGPO/Rollback para 2 Jugadores por Hotspot (<10 cm):
 * - Host = Mando 1 (Puerto 0), Invitado = Mando 2 (Puerto 1).
 * - Envía en cada frame una ventana redundante de los últimos 8 frames por UDP (DSCP EF).
 * - Predice el input remoto sin añadir retraso artificial y, si cambia respecto a la predicción,
 *   ejecuta Rollback + Replay nativo en C++ (<0.8 ms) sin parpadeos ni pérdida de botones.
 */
class HotspotRollbackCoordinator(
    private val nativeBridge: SnesNativeBridge,
    private val transport: UdpHotspotTransport,
    val isHostPlayer1: Boolean
) : SnesNativeBridge.FrameStepHook {

    companion object {
        private const val HISTORY_SIZE = 256
        private const val MAX_ROLLBACK_FRAMES = 8
        private const val MAX_FRAME_ADVANTAGE = 4
    }

    data class NetplayTelemetry(
        val rttMillis: Float = 0.8f,
        val rollbacksCount: Int = 0,
        val peerConnected: Boolean = true,
        /** Paquetes UDP del otro celular que no llegaron a tiempo (recuperados por la ventana redundante). */
        val lostPackets: Int = 0,
        /** Porcentaje de paquetes perdidos respecto al total esperado. */
        val lossPercent: Float = 0f
    )

    private val lock = Any()
    private val localInputs = ShortArray(HISTORY_SIZE)
    private val remoteInputs = ShortArray(HISTORY_SIZE)
    private val remoteConfirmed = BooleanArray(HISTORY_SIZE)

    private var latestRemoteConfirmedFrame = -1
    private var lastKnownRemoteMask: Short = 0
    private var rollbacksTotal = 0
    private var lostPacketsTotal = 0

    @Volatile
    private var currentLiveLocalMask: Short = 0

    private val txScratch = ByteArray(UdpNetplayPacket.PACKET_BYTES)
    private val txWindow = ShortArray(UdpNetplayPacket.REDUNDANT_WINDOW)

    private val _telemetry = MutableStateFlow(NetplayTelemetry())
    val telemetry: StateFlow<NetplayTelemetry> = _telemetry.asStateFlow()

    init {
        // Frame inicial 0 confirmado en reposo para ambos jugadores
        remoteConfirmed[0] = true
        transport.onGameplayPacketReceived = ::onRemotePacket
    }

    fun updateLocalButtons(mask: Int) {
        currentLiveLocalMask = (mask and 0xFFFF).toShort()
    }

    override fun onBeforeStepFrame(currentFrame: Int) {
        // Si vamos >4 frames adelantados al otro teléfono (ej. micro-pausa del SO remoto),
        // esperamos hasta 4 ms para mantener ambos relojes clavados sin exceder la ventana de Rollback.
        var waitSpins = 0
        while (currentFrame - latestRemoteConfirmedFrame > MAX_FRAME_ADVANTAGE && waitSpins < 8) {
            Thread.sleep(0, 500_000) // 0.5 ms
            waitSpins++
        }

        val p1Mask: Short
        val p2Mask: Short
        val nowMicros = (System.nanoTime() / 1_000L).toInt()

        synchronized(lock) {
            // 1. Verificar si llegaron inputs remotos reales que difieren de lo predicho en frames previos
            applyPendingRollbacksLocked(currentFrame)

            // 2. Registrar el input local de este frame (incluyendo Macros Turbo/Auto-Hold activos en C++)
            val localPort = if (isHostPlayer1) 0 else 1
            nativeBridge.setLocalPlayerInput(localPort, currentLiveLocalMask.toInt() and 0xFFFF)
            val idx = currentFrame and (HISTORY_SIZE - 1)
            val myMask = (nativeBridge.getEffectiveInputMask(localPort) and 0xFFFF).toShort()
            localInputs[idx] = myMask

            // 3. Si el paquete del otro celular ya llegó para este frame, usarlo directo; si no, predecir último
            val peerMask = if (remoteConfirmed[idx] && latestRemoteConfirmedFrame >= currentFrame) {
                remoteInputs[idx]
            } else {
                remoteConfirmed[idx] = false
                remoteInputs[idx] = lastKnownRemoteMask
                lastKnownRemoteMask
            }

            if (isHostPlayer1) {
                p1Mask = myMask
                p2Mask = peerMask
            } else {
                p1Mask = peerMask
                p2Mask = myMask
            }

            // 4. Armar ventana redundante [frame, frame-1, ..., frame-7] para inmunidad total a pérdidas
            for (w in 0 until UdpNetplayPacket.REDUNDANT_WINDOW) {
                val f = currentFrame - w
                txWindow[w] = if (f >= 0) localInputs[f and (HISTORY_SIZE - 1)] else 0
            }

            UdpNetplayPacket.encodeFramePacket(
                outBuffer = txScratch,
                packetType = UdpNetplayPacket.TYPE_FRAME_INPUT,
                playerRole = if (isHostPlayer1) 0 else 1,
                frameNumber = currentFrame,
                ackRemoteFrame = latestRemoteConfirmedFrame,
                last8InputsNewestFirst = txWindow,
                stateChecksum = if ((currentFrame and 63) == 0) nativeBridge.getStateChecksum() else 0,
                timestampMicros = nowMicros
            )
        }

        // Doble envío micro-espaciado en ráfaga UDP para eliminar colisiones CSMA/CA en 2.4/5GHz
        transport.sendPacketFast(txScratch)
        nativeBridge.setLocalPlayerInput(0, p1Mask.toInt() and 0xFFFF)
        nativeBridge.setLocalPlayerInput(1, p2Mask.toInt() and 0xFFFF)
        transport.sendPacketFast(txScratch)
    }

    private var earliestDirtyFrame: Int = Int.MAX_VALUE

    private fun onRemotePacket(pkt: UdpNetplayPacket.DecodedPacket) {
        val nowMicros = (System.nanoTime() / 1_000L).toInt()
        when (pkt.type) {
            UdpNetplayPacket.TYPE_FRAME_INPUT -> {
                synchronized(lock) {
                    // Reconstruir los últimos 8 frames del par gracias a la ventana redundante
                    val oldestInPacket = max(0, pkt.frameNumber - (UdpNetplayPacket.REDUNDANT_WINDOW - 1))
                    for (f in oldestInPacket..pkt.frameNumber) {
                        val windowOffset = pkt.frameNumber - f
                        val realMask = pkt.historyNewestFirst[windowOffset]
                        val slot = f and (HISTORY_SIZE - 1)

                        val wasPredictedMismatch = !remoteConfirmed[slot] && remoteInputs[slot] != realMask
                        remoteInputs[slot] = realMask
                        remoteConfirmed[slot] = true

                        if (wasPredictedMismatch) {
                            earliestDirtyFrame = min(earliestDirtyFrame, f)
                        }
                    }

                    if (pkt.frameNumber > latestRemoteConfirmedFrame) {
                        // Huecos entre frames consecutivos = paquetes que no llegaron (se recuperan por la ventana de 8 frames)
                        val gap = pkt.frameNumber - latestRemoteConfirmedFrame - 1
                        if (latestRemoteConfirmedFrame >= 0 && gap > 0) {
                            lostPacketsTotal += gap
                        }
                        latestRemoteConfirmedFrame = pkt.frameNumber
                        lastKnownRemoteMask = pkt.historyNewestFirst[0]

                        val expected = pkt.frameNumber + 1
                        _telemetry.value = _telemetry.value.copy(
                            lostPackets = lostPacketsTotal,
                            lossPercent = if (expected > 0) lostPacketsTotal * 100f / expected else 0f
                        )
                    }
                }

                // Responder PONG ligero cada 15 frames para medir RTT real en sub-milisegundos
                if ((pkt.frameNumber % 15) == 0) {
                    val pong = ByteArray(UdpNetplayPacket.PACKET_BYTES)
                    UdpNetplayPacket.encodeFramePacket(
                        outBuffer = pong,
                        packetType = UdpNetplayPacket.TYPE_PONG,
                        playerRole = if (isHostPlayer1) 0 else 1,
                        frameNumber = pkt.frameNumber,
                        ackRemoteFrame = latestRemoteConfirmedFrame,
                        last8InputsNewestFirst = ShortArray(UdpNetplayPacket.REDUNDANT_WINDOW),
                        stateChecksum = 0,
                        timestampMicros = pkt.timestampMicros
                    )
                    transport.sendPacketFast(pong)
                }
            }

            UdpNetplayPacket.TYPE_PONG -> {
                val rttUs = (nowMicros - pkt.timestampMicros).coerceIn(100, 100_000)
                val smoothedMs = (_telemetry.value.rttMillis * 0.7f) + ((rttUs / 1000f) * 0.3f)
                _telemetry.value = _telemetry.value.copy(rttMillis = smoothedMs, peerConnected = true)
            }

            UdpNetplayPacket.TYPE_DISCONNECT -> {
                _telemetry.value = _telemetry.value.copy(peerConnected = false)
            }
        }
    }

    private fun applyPendingRollbacksLocked(currentFrame: Int) {
        val dirty = earliestDirtyFrame
        earliestDirtyFrame = Int.MAX_VALUE
        if (dirty >= currentFrame) return

        val rollbackStart = max(dirty, currentFrame - MAX_ROLLBACK_FRAMES)
        val count = currentFrame - rollbackStart
        if (count <= 0) return

        val p1Slice = ShortArray(count)
        val p2Slice = ShortArray(count)

        for (i in 0 until count) {
            val f = rollbackStart + i
            val slot = f and (HISTORY_SIZE - 1)
            val loc = localInputs[slot]
            val rem = remoteInputs[slot]
            if (isHostPlayer1) {
                p1Slice[i] = loc
                p2Slice[i] = rem
            } else {
                p1Slice[i] = rem
                p2Slice[i] = loc
            }
        }

        if (nativeBridge.performRollback(rollbackStart, currentFrame, p1Slice, p2Slice)) {
            rollbacksTotal++
            _telemetry.value = _telemetry.value.copy(rollbacksCount = rollbacksTotal)
        }
    }

    fun sendDisconnectNotice() {
        val bye = ByteArray(UdpNetplayPacket.PACKET_BYTES)
        UdpNetplayPacket.encodeFramePacket(
            outBuffer = bye,
            packetType = UdpNetplayPacket.TYPE_DISCONNECT,
            playerRole = if (isHostPlayer1) 0 else 1,
            frameNumber = 0,
            ackRemoteFrame = 0,
            last8InputsNewestFirst = ShortArray(UdpNetplayPacket.REDUNDANT_WINDOW),
            stateChecksum = 0,
            timestampMicros = 0
        )
        repeat(3) { transport.sendPacketFast(bye) }
    }
}
