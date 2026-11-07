package com.fdp.multisnes.data.rom

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/**
 * Analizador universal de ROMs de Super Nintendo sin restricciones.
 * Soporta:
 * - Formatos: .sfc, .smc, .swc, .fig y archivos comprimidos .zip
 * - Cabeceras de copiador SMC de 512 bytes (detección y limpieza automática)
 * - Mapeos LoROM (0x7FC0), HiROM (0xFFC0) y ExHiROM (0x40FFC0)
 * - Chips especiales: CX4 (Mega Man X2/X3), SuperFX (Yoshi's Island/Star Fox),
 *   SA-1 (Super Mario RPG/Kirby), DSP-1..4 (Mario Kart), S-DD1 (Street Fighter Alpha 2), etc.
 */
object SnesRomHeaderParser {

    val SUPPORTED_ROM_EXTENSIONS = setOf("sfc", "smc", "swc", "fig")
    val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("zip")

    data class ParsedRomMetadata(
        val displayTitle: String,
        val internalHeaderTitle: String,
        val crc32Hex: String,
        val enhancementChip: String,
        val isPalRegion: Boolean,
        val zipEntryName: String? = null
    )

    data class LoadedRomPayload(
        val bytes: ByteArray,
        val fileNameHint: String,
        val metadata: ParsedRomMetadata
    )

    fun loadRomPayload(
        context: Context,
        pathOrUri: String,
        preferredZipEntry: String? = null
    ): LoadedRomPayload? {
        val rawStream = openStream(context, pathOrUri) ?: return null
        rawStream.use { stream ->
            val lowerName = pathOrUri.lowercase(Locale.ROOT)
            if (lowerName.endsWith(".zip") || preferredZipEntry != null) {
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                            val matchesTarget = preferredZipEntry == null || entry.name == preferredZipEntry
                            if (ext in SUPPORTED_ROM_EXTENSIONS && matchesTarget) {
                                val bytes = readAllBytesBounded(zis, maxBytes = 16 * 1024 * 1024)
                                if (bytes.size >= 32 * 1024) {
                                    val fallbackName = File(entry.name).nameWithoutExtension
                                    val meta = parseFromRawBytes(bytes, fallbackName, entry.name)
                                    return LoadedRomPayload(bytes, entry.name, meta)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
                return null
            } else {
                val bytes = readAllBytesBounded(stream, maxBytes = 16 * 1024 * 1024)
                if (bytes.size < 32 * 1024) return null
                val fallbackName = extractCleanFileName(pathOrUri)
                val meta = parseFromRawBytes(bytes, fallbackName, null)
                return LoadedRomPayload(bytes, "$fallbackName.sfc", meta)
            }
        }
    }

    fun parseFromRawBytes(
        rawBytes: ByteArray,
        fallbackFileName: String,
        zipEntryName: String? = null
    ): ParsedRomMetadata {
        val smcOffset = if (rawBytes.size > 512 && (rawBytes.size % 1024) == 512) 512 else 0
        val cleanLength = rawBytes.size - smcOffset

        val crc = CRC32()
        crc.update(rawBytes, smcOffset, cleanLength)
        val crcHex = String.format(Locale.US, "%08X", crc.value)

        // Probar posiciones candidatas de cabecera interna SNES: LoROM, HiROM y ExHiROM
        val candidates = intArrayOf(0x7FC0, 0xFFC0, 0x40FFC0)
        var bestHeaderOffset = -1
        var bestScore = Int.MIN_VALUE

        for (candidate in candidates) {
            val actualPos = smcOffset + candidate
            if (actualPos + 32 <= rawBytes.size) {
                val score = scoreSnesHeader(rawBytes, actualPos)
                if (score > bestScore) {
                    bestScore = score
                    bestHeaderOffset = actualPos
                }
            }
        }

        var internalTitle = ""
        var chipType = "SNES Estándar"
        var isPal = false

        if (bestHeaderOffset >= 0 && bestScore >= 0) {
            internalTitle = decodeSnesAsciiTitle(rawBytes, bestHeaderOffset)
            val mapMode = rawBytes[bestHeaderOffset + 0x15].toInt() and 0xFF
            val romType = rawBytes[bestHeaderOffset + 0x16].toInt() and 0xFF
            val country = rawBytes[bestHeaderOffset + 0x19].toInt() and 0xFF

            chipType = detectEnhancementChip(mapMode, romType)
            // Códigos de región PAL en cabecera SNES: Europa (0x02..0x0C, 0x11)
            isPal = country in listOf(0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x11)
        }

        val cleanFallback = sanitizeDisplayTitle(fallbackFileName)
        val displayTitle = when {
            cleanFallback.isNotBlank() && cleanFallback.length >= 3 -> cleanFallback
            internalTitle.isNotBlank() -> internalTitle
            else -> "JUEGO SNES"
        }

        return ParsedRomMetadata(
            displayTitle = displayTitle.uppercase(Locale.ROOT),
            internalHeaderTitle = internalTitle.ifBlank { displayTitle },
            crc32Hex = crcHex,
            enhancementChip = chipType,
            isPalRegion = isPal,
            zipEntryName = zipEntryName
        )
    }

    private fun scoreSnesHeader(data: ByteArray, offset: Int): Int {
        var score = 0
        val complement = ((data[offset + 0x1C].toInt() and 0xFF) or ((data[offset + 0x1D].toInt() and 0xFF) shl 8))
        val checksum = ((data[offset + 0x1E].toInt() and 0xFF) or ((data[offset + 0x1F].toInt() and 0xFF) shl 8))
        if ((complement xor checksum) == 0xFFFF) {
            score += 8
        }

        var printableChars = 0
        for (i in 0 until 21) {
            val ch = data[offset + i].toInt() and 0xFF
            if (ch in 0x20..0x7E) printableChars++
        }
        score += printableChars / 3

        val romSizeByte = data[offset + 0x17].toInt() and 0xFF
        if (romSizeByte in 0x07..0x0E) {
            score += 3
        }
        return score
    }

    private fun decodeSnesAsciiTitle(data: ByteArray, offset: Int): String {
        val sb = StringBuilder(21)
        for (i in 0 until 21) {
            val b = data[offset + i].toInt() and 0xFF
            if (b in 0x20..0x7E) {
                sb.append(b.toChar())
            } else if (b != 0x00) {
                sb.append(' ')
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private fun detectEnhancementChip(mapMode: Int, romType: Int): String {
        val highNibble = (romType ushr 4) and 0x0F
        if (romType < 0x03) return "SNES Estándar"
        return when (highNibble) {
            0x0 -> "DSP-1 / DSP"
            0x1 -> "SuperFX (GSU)"
            0x2 -> "OBC-1"
            0x3 -> "SA-1"
            0x4 -> "S-DD1"
            0x5 -> "S-RTC"
            0xF -> if ((mapMode and 0x10) != 0) "SPC7110" else "CX4 (Capcom)"
            else -> "Chip Especial (0x${romType.toString(16).uppercase(Locale.US)})"
        }
    }

    private fun sanitizeDisplayTitle(rawName: String): String {
        return rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringAfterLast(':')
            .substringBeforeLast('.')
            .replace(Regex("\\[[^]]*]"), "") // Quita etiquetas como [!], [U], [T+Spa]
            .replace(Regex("\\([^)]*\\)"), "") // Quita (USA), (Rev 1), etc.
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractCleanFileName(pathOrUri: String): String {
        val decoded = Uri.decode(pathOrUri)
        return sanitizeDisplayTitle(decoded)
    }

    private fun openStream(context: Context, pathOrUri: String): InputStream? {
        return runCatching {
            if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))
            } else {
                val f = File(pathOrUri)
                if (f.exists() && f.canRead()) f.inputStream() else null
            }
        }.getOrNull()
    }

    private fun readAllBytesBounded(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(256 * 1024)
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val r = input.read(buf)
            if (r <= 0) break
            if (total + r > maxBytes) break
            out.write(buf, 0, r)
            total += r
        }
        return out.toByteArray()
    }
}
