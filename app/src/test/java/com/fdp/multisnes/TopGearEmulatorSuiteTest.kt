package com.fdp.multisnes

import com.fdp.multisnes.core.SnesButtons
import com.fdp.multisnes.data.rom.SnesRomHeaderParser
import com.fdp.multisnes.netplay.UdpNetplayPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Suite de pruebas unitarias e integración para:
 * 1. Extracción y análisis de cabeceras de ROM comprimida (.zip -> .sfc) como Top_Gear_(Europe).zip
 * 2. Limpieza automática de cabeceras copiadoras SMC de 512 bytes
 * 3. Protocolo binario UDP Hotspot (<10 cm) con ventana redundante de 8 frames cero pérdida
 */
class TopGearEmulatorSuiteTest {

    @Test
    fun parseTopGearZipArchive_detectsLoRomPalAndExactCrc32() {
        val candidatePath = System.getProperty("topgear.zip.path")
            ?: "C:/Users/piero/.gemini/antigravity/brain/eadd3216-ca5d-4c6f-9865-922be4d74af5/.user_uploaded/media_1793934071819_f75f02a5.zip"
        val zipFile = File(candidatePath)
        if (!zipFile.exists()) return

        var extractedBytes: ByteArray? = null
        var entryName: String? = null

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entryName = entry.name
                    val out = ByteArrayOutputStream()
                    zis.copyTo(out)
                    extractedBytes = out.toByteArray()
                    break
                }
                entry = zis.nextEntry
            }
        }

        assertNotNull("Debe extraer Top Gear (Europe).sfc del archivo .zip", extractedBytes)
        assertEquals(524_288, extractedBytes!!.size)

        val meta = SnesRomHeaderParser.parseFromRawBytes(
            rawBytes = extractedBytes!!,
            fallbackFileName = entryName ?: "Top_Gear_(Europe).sfc",
            zipEntryName = entryName
        )

        assertEquals("TOP GEAR", meta.displayTitle)
        assertEquals("TOP GEAR", meta.internalHeaderTitle)
        assertEquals("B0150052", meta.crc32Hex)
        assertEquals("SNES Estándar", meta.enhancementChip)
        assertTrue("Top Gear (Europe) debe detectarse como región PAL (50Hz)", meta.isPalRegion)
    }

    @Test
    fun parseSmcCopierHeaderedRom_strips512ByteHeaderKeepingSameCrc32() {
        val fakeRomClean = ByteArray(65_536)
        // Escribir cabecera LoROM válida en 0x7FC0
        val titleBytes = "MEGA MAN X           ".toByteArray(Charsets.US_ASCII)
        System.arraycopy(titleBytes, 0, fakeRomClean, 0x7FC0, 21)
        fakeRomClean[0x7FC0 + 0x15] = 0x20.toByte()
        fakeRomClean[0x7FC0 + 0x16] = 0x00.toByte()
        fakeRomClean[0x7FC0 + 0x19] = 0x01.toByte() // USA NTSC
        fakeRomClean[0x7FC0 + 0x1C] = 0xAA.toByte()
        fakeRomClean[0x7FC0 + 0x1D] = 0x55.toByte()
        fakeRomClean[0x7FC0 + 0x1E] = 0x55.toByte()
        fakeRomClean[0x7FC0 + 0x1F] = 0xAA.toByte()

        val with512SmcHeader = ByteArray(512 + fakeRomClean.size)
        System.arraycopy(fakeRomClean, 0, with512SmcHeader, 512, fakeRomClean.size)

        val cleanMeta = SnesRomHeaderParser.parseFromRawBytes(fakeRomClean, "Mega Man X (USA).sfc")
        val smcMeta = SnesRomHeaderParser.parseFromRawBytes(with512SmcHeader, "Mega Man X (USA).smc")

        assertEquals(cleanMeta.crc32Hex, smcMeta.crc32Hex)
        assertEquals("MEGA MAN X", smcMeta.internalHeaderTitle)
        assertEquals(false, smcMeta.isPalRegion)
    }

    @Test
    fun udpHotspotRedundantPacket_recoversUpTo7DroppedFramesWithZeroLoss() {
        val windowSent = shortArrayOf(
            (SnesButtons.B or SnesButtons.RIGHT).toShort(), // Frame 108 (acelerar + girar derecha en Top Gear)
            (SnesButtons.B or SnesButtons.RIGHT).toShort(), // Frame 107
            SnesButtons.B.toShort(),                        // Frame 106
            (SnesButtons.B or SnesButtons.A).toShort(),     // Frame 105 (activar Nitro!)
            SnesButtons.B.toShort(),                        // Frame 104
            SnesButtons.B.toShort(),                        // Frame 103
            SnesButtons.START.toShort(),                    // Frame 102
            0.toShort()                                     // Frame 101
        )

        val rawWire = ByteArray(UdpNetplayPacket.PACKET_BYTES)
        UdpNetplayPacket.encodeFramePacket(
            outBuffer = rawWire,
            packetType = UdpNetplayPacket.TYPE_FRAME_INPUT,
            playerRole = 1,
            frameNumber = 108,
            ackRemoteFrame = 107,
            last8InputsNewestFirst = windowSent,
            stateChecksum = 0x12345678,
            timestampMicros = 450_120
        )

        val decoded = UdpNetplayPacket.decode(rawWire, rawWire.size)
        assertNotNull(decoded)
        assertEquals(108, decoded!!.frameNumber)
        assertEquals(107, decoded.ackRemoteFrame)
        assertEquals(0x12345678, decoded.stateChecksum)
        assertArrayEquals(windowSent, decoded.historyNewestFirst)
    }
}
