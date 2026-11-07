package com.fdp.multisnes.netplay

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Transporte híbrido para Hotspot (<10 cm):
 * 1. UDP Discovery en puerto 55442: El Host anuncia la sala y ROM (CRC32 + Título) cada 250ms;
 *    el Invitado lo detecta automáticamente sin escribir IPs.
 * 2. Handshake inicial TCP NoDelay en puerto 55444: Verifica ROM exacta y transfiere el estado
 *    inicial de arranque en ~10 ms para que ambos procesadores SNES arranquen en el ciclo 0 idéntico.
 * 3. Stream de juego UDP puro en puerto 55443 con DSCP Expedited Forwarding (0xB8) para <1.5 ms RTT.
 */
class UdpHotspotTransport(
    private val networkHelper: HotspotNetworkHelper
) {
    companion object {
        const val DISCOVERY_UDP_PORT = 55442
        const val GAMEPLAY_UDP_PORT = 55443
        const val HANDSHAKE_TCP_PORT = 55444
        private const val BEACON_PREFIX = "MULTISNES_FDP_V1|"
    }

    data class DiscoveredHostRoom(
        val hostIp: String,
        val hostTitle: String,
        val romCrc32: String,
        val gameTitle: String
    )

    data class HandshakeResult(
        val peerAddress: InetAddress,
        val romCrc32: String,
        val gameTitle: String,
        val initialSyncState: ByteArray
    )

    private val running = AtomicBoolean(false)
    private val discoveryActive = AtomicBoolean(false)

    private var gameplaySocket: DatagramSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var handshakeServer: ServerSocket? = null

    @Volatile
    private var peerEndpoint: InetSocketAddress? = null

    var onGameplayPacketReceived: ((UdpNetplayPacket.DecodedPacket) -> Unit)? = null

    /**
     * HOST: Anuncia la partida por UDP en el Hotspot y espera al Invitado (Jugador 2) por TCP NoDelay.
     */
    suspend fun hostWaitForGuest(
        hostName: String,
        romCrc32: String,
        gameTitle: String,
        initialStateProvider: () -> ByteArray
    ): HandshakeResult? = withContext(Dispatchers.IO) {
        stopAll()
        networkHelper.acquireUltraLowLatencyLocks()
        discoveryActive.set(true)

        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(HANDSHAKE_TCP_PORT))
            soTimeout = 0
        }
        handshakeServer = server

        // Hilo emisor de beacons UDP cada 250ms para autodescubrimiento instantáneo
        val beaconThread = thread(start = true, name = "HotspotHostBeacon") {
            runCatching {
                DatagramSocket().use { bcastSock ->
                    bcastSock.broadcast = true
                    bcastSock.trafficClass = 0xB8
                    val payload = "$BEACON_PREFIX$hostName|$romCrc32|$gameTitle"
                        .toByteArray(StandardCharsets.UTF_8)
                    while (discoveryActive.get()) {
                        for (target in networkHelper.candidateBroadcastTargets()) {
                            runCatching {
                                bcastSock.send(DatagramPacket(payload, payload.size, target, DISCOVERY_UDP_PORT))
                            }
                        }
                        Thread.sleep(250)
                    }
                }
            }
        }

        val result = runCatching {
            val client = server.accept()
            client.tcpNoDelay = true
            client.trafficClass = 0xB8
            val guestAddress = client.inetAddress

            val dis = DataInputStream(client.getInputStream())
            val dos = DataOutputStream(client.getOutputStream())

            val guestCrc = dis.readUTF()
            if (!guestCrc.equals(romCrc32, ignoreCase = true)) {
                dos.writeBoolean(false)
                dos.writeUTF("El invitado no tiene la misma ROM ($gameTitle)")
                dos.flush()
                client.close()
                return@runCatching null
            }

            val initialState = initialStateProvider()
            dos.writeBoolean(true)
            dos.writeUTF(gameTitle)
            dos.writeInt(initialState.size)
            if (initialState.isNotEmpty()) {
                dos.write(initialState)
            }
            dos.flush()
            // Esperar confirmación lista del invitado antes de arrancar Frame 0
            dis.readBoolean()
            client.close()

            startGameplayUdpStream(guestAddress)
            HandshakeResult(guestAddress, romCrc32, gameTitle, initialState)
        }.getOrNull()

        discoveryActive.set(false)
        beaconThread.join(300)
        runCatching { server.close() }
        handshakeServer = null
        result
    }

    /**
     * INVITADO: Escucha el Hotspot hasta detectar al Host (<0.3s a 10 cm).
     */
    suspend fun guestDiscoverHostRoom(timeoutMs: Long = 15_000L): DiscoveredHostRoom? =
        withContext(Dispatchers.IO) {
            networkHelper.acquireUltraLowLatencyLocks()
            discoveryActive.set(true)
            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 400
                bind(InetSocketAddress(DISCOVERY_UDP_PORT))
            }
            discoverySocket = sock

            val buf = ByteArray(512)
            val deadline = System.currentTimeMillis() + timeoutMs
            var found: DiscoveredHostRoom? = null

            while (discoveryActive.get() && System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                val received = runCatching {
                    sock.receive(pkt)
                    true
                }.getOrDefault(false)

                if (received && pkt.length > BEACON_PREFIX.length) {
                    val text = String(pkt.data, 0, pkt.length, StandardCharsets.UTF_8)
                    if (text.startsWith(BEACON_PREFIX)) {
                        val parts = text.removePrefix(BEACON_PREFIX).split('|')
                        if (parts.size >= 3) {
                            found = DiscoveredHostRoom(
                                hostIp = pkt.address.hostAddress ?: "192.168.43.1",
                                hostTitle = parts[0],
                                romCrc32 = parts[1],
                                gameTitle = parts[2]
                            )
                            break
                        }
                    }
                }
            }

            runCatching { sock.close() }
            discoverySocket = null
            found
        }

    /**
     * INVITADO: Conecta directamente con el Host, valida CRC32 y descarga el snapshot inicial exacto.
     */
    suspend fun guestConnectAndSync(hostIp: String, localRomCrc32: String): HandshakeResult? =
        withContext(Dispatchers.IO) {
            networkHelper.acquireUltraLowLatencyLocks()
            runCatching {
                val hostAddr = InetAddress.getByName(hostIp)
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.trafficClass = 0xB8
                socket.connect(InetSocketAddress(hostAddr, HANDSHAKE_TCP_PORT), 3000)

                val dis = DataInputStream(socket.getInputStream())
                val dos = DataOutputStream(socket.getOutputStream())

                dos.writeUTF(localRomCrc32)
                dos.flush()

                val accepted = dis.readBoolean()
                if (!accepted) {
                    socket.close()
                    return@runCatching null
                }

                val gameTitle = dis.readUTF()
                val stateLen = dis.readInt()
                val initialState = if (stateLen > 0) {
                    ByteArray(stateLen).also { dis.readFully(it) }
                } else {
                    ByteArray(0)
                }

                dos.writeBoolean(true)
                dos.flush()
                socket.close()

                startGameplayUdpStream(hostAddr)
                HandshakeResult(hostAddr, localRomCrc32, gameTitle, initialState)
            }.getOrNull()
        }

    private fun startGameplayUdpStream(peerAddress: InetAddress) {
        runCatching { gameplaySocket?.close() }
        val sock = DatagramSocket(null).apply {
            reuseAddress = true
            // 0xB8 = DSCP 46 (Expedited Forwarding) para máxima prioridad WMM Voice/Gaming en Wi-Fi Hotspot
            trafficClass = 0xB8
            sendBufferSize = 64 * 1024
            receiveBufferSize = 64 * 1024
            bind(InetSocketAddress(GAMEPLAY_UDP_PORT))
        }
        gameplaySocket = sock
        peerEndpoint = InetSocketAddress(peerAddress, GAMEPLAY_UDP_PORT)
        running.set(true)

        thread(start = true, name = "UdpLowLatencyReceiver") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val rxBuf = ByteArray(64)
            val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
            while (running.get()) {
                val ok = runCatching {
                    sock.receive(rxPacket)
                    true
                }.getOrDefault(false)
                if (!ok) break

                val decoded = UdpNetplayPacket.decode(rxPacket.data, rxPacket.length)
                if (decoded != null) {
                    onGameplayPacketReceived?.invoke(decoded)
                }
            }
        }
    }

    fun sendPacketFast(bytes: ByteArray) {
        val sock = gameplaySocket ?: return
        val dest = peerEndpoint ?: return
        runCatching {
            sock.send(DatagramPacket(bytes, UdpNetplayPacket.PACKET_BYTES, dest))
        }
    }

    fun stopAll() {
        discoveryActive.set(false)
        running.set(false)
        runCatching { discoverySocket?.close() }
        runCatching { handshakeServer?.close() }
        runCatching { gameplaySocket?.close() }
        discoverySocket = null
        handshakeServer = null
        gameplaySocket = null
        peerEndpoint = null
        networkHelper.releaseLocks()
    }
}
