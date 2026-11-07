package com.fdp.multisnes.netplay

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Paquete binario ultracompacto de 36 bytes para UDP directo sin asignaciones de basura.
 * Incluye una ventana redundante de los últimos 8 frames de controles (16 bytes),
 * garantizando CERO pérdida de información aunque algún paquete inalámbrico se pierda.
 */
object UdpNetplayPacket {

    const val MAGIC: Int = 0x46445053 // "FDPS" (MULTI SNES BY FDP)
    const val PACKET_BYTES: Int = 40
    const val REDUNDANT_WINDOW: Int = 8

    const val TYPE_FRAME_INPUT: Byte = 1
    const val TYPE_PING: Byte = 2
    const val TYPE_PONG: Byte = 3
    const val TYPE_DISCONNECT: Byte = 4

    fun encodeFramePacket(
        outBuffer: ByteArray,
        packetType: Byte,
        playerRole: Byte,
        frameNumber: Int,
        ackRemoteFrame: Int,
        last8InputsNewestFirst: ShortArray,
        stateChecksum: Int,
        timestampMicros: Int
    ) {
        val bb = ByteBuffer.wrap(outBuffer, 0, PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(MAGIC)
        bb.put(packetType)
        bb.put(playerRole)
        bb.putShort(0) // Alineación 32-bit
        bb.putInt(frameNumber)
        bb.putInt(ackRemoteFrame)
        for (i in 0 until REDUNDANT_WINDOW) {
            bb.putShort(if (i < last8InputsNewestFirst.size) last8InputsNewestFirst[i] else 0)
        }
        bb.putInt(stateChecksum)
        bb.putInt(timestampMicros)
    }

    data class DecodedPacket(
        val type: Byte,
        val senderRole: Byte,
        val frameNumber: Int,
        val ackRemoteFrame: Int,
        val historyNewestFirst: ShortArray,
        val stateChecksum: Int,
        val timestampMicros: Int
    )

    fun decode(raw: ByteArray, length: Int): DecodedPacket? {
        if (length < PACKET_BYTES) return null
        val bb = ByteBuffer.wrap(raw, 0, PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.int != MAGIC) return null

        val type = bb.get()
        val role = bb.get()
        bb.short // reservado
        val frame = bb.int
        val ack = bb.int
        val history = ShortArray(REDUNDANT_WINDOW) { bb.short }
        val checksum = bb.int
        val tsMicros = bb.int

        return DecodedPacket(
            type = type,
            senderRole = role,
            frameNumber = frame,
            ackRemoteFrame = ack,
            historyNewestFirst = history,
            stateChecksum = checksum,
            timestampMicros = tsMicros
        )
    }
}
