package com.fdp.multisnes.data.rom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.fdp.multisnes.data.model.RomGame
import com.fdp.multisnes.data.repository.GameLibraryRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * MÓDULO 3: Procesador de Intent.ACTION_VIEW ("Abrir con MULTI SNES BY FDP")
 * para archivos externos (.sfc, .smc, .zip, .fig, .swc, .ips, .bps) desde gestores de archivos,
 * descargas o WhatsApp.
 * - Obtiene el nombre real mediante ContentResolver (OpenableColumns.DISPLAY_NAME).
 * - Si es una ROM (.sfc, .smc, .zip, .fig, .swc): la copia automáticamente al directorio local de
 *   juegos de la app para que quede persistida en la biblioteca e inicia la partida de inmediato.
 * - Si es un parche (.ips o .bps): carga el parche y permite aplicarlo sobre cualquier ROM de la
 *   biblioteca, guardando la ROM parcheada y lanzándola automáticamente.
 */
object RomIntentHandler {

    val ROM_EXTENSIONS = setOf("sfc", "smc", "zip", "fig", "swc")
    val PATCH_EXTENSIONS = setOf("ips", "bps")

    data class PendingPatchPayload(
        val patchFileName: String,
        val patchExtension: String,
        val patchBytes: ByteArray
    )

    sealed class IntentResult {
        data class RomImportedAndReady(val game: RomGame) : IntentResult()
        data class PatchReadyToSelectRom(val patch: PendingPatchPayload) : IntentResult()
        data class Error(val message: String) : IntentResult()
    }

    /**
     * Extrae el URI de un Intent entrante (ACTION_VIEW o ACTION_SEND).
     */
    fun extractUriFromIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri ?: intent.data
            }
            else -> null
        }
    }

    /**
     * Obtiene el nombre real del archivo usando ContentResolver (OpenableColumns.DISPLAY_NAME)
     * con fallback al último segmento de ruta del URI.
     */
    fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme.equals("content", ignoreCase = true)) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        val displayName = cursor.getString(nameIndex)
                        if (!displayName.isNullOrBlank()) {
                            return displayName.trim()
                        }
                    }
                }
            }
        }
        val rawSegment = Uri.decode(uri.lastPathSegment ?: uri.path ?: "")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringAfterLast(':')
            .trim()
        return rawSegment.ifBlank { "Juego_Importado.sfc" }
    }

    /**
     * Procesa un URI entrante desde "Abrir con MULTI SNES BY FDP":
     * - Si es ROM (.sfc, .smc, .zip, .fig, .swc): la copia al almacenamiento local de juegos de la app,
     *   la registra en la biblioteca y devuelve RomImportedAndReady(game).
     * - Si es parche (.ips, .bps): lee los bytes del parche y devuelve PatchReadyToSelectRom(patch).
     */
    fun handleIncomingUri(
        context: Context,
        uri: Uri,
        libraryRepository: GameLibraryRepository
    ): IntentResult {
        val rawDisplayName = resolveDisplayName(context, uri)
        val rawBytes = readBytesFromUri(context, uri, maxBytes = 24 * 1024 * 1024)
            ?: return IntentResult.Error("No se pudo abrir el archivo seleccionado")

        if (rawBytes.isEmpty()) {
            return IntentResult.Error("El archivo está vacío")
        }

        // Determinar extensión (por nombre o por firma mágica si el proveedor omitió la extensión)
        var ext = rawDisplayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in ROM_EXTENSIONS && ext !in PATCH_EXTENSIONS) {
            ext = detectExtensionFromMagicBytes(rawBytes)
        }

        val normalizedFileName = if (rawDisplayName.contains('.')) {
            rawDisplayName
        } else {
            "$rawDisplayName.$ext"
        }

        return when {
            ext in PATCH_EXTENSIONS -> {
                IntentResult.PatchReadyToSelectRom(
                    PendingPatchPayload(
                        patchFileName = normalizedFileName,
                        patchExtension = ext,
                        patchBytes = rawBytes
                    )
                )
            }
            ext in ROM_EXTENSIONS -> {
                val imported = saveRomToLocalLibraryDirAndRegister(
                    context = context,
                    fileName = normalizedFileName,
                    fileBytes = rawBytes,
                    libraryRepository = libraryRepository
                )
                if (imported != null) {
                    IntentResult.RomImportedAndReady(imported)
                } else {
                    IntentResult.Error("Archivo ROM no válido o corrupto ($normalizedFileName)")
                }
            }
            else -> IntentResult.Error("Formato no compatible: .$ext")
        }
    }

    /**
     * Aplica un parche .ips o .bps sobre la ROM base seleccionada por el usuario en su biblioteca,
     * guarda la ROM resultante en el directorio local de juegos y la registra para lanzarla de inmediato.
     */
    fun applyPatchToGameAndImport(
        context: Context,
        baseGame: RomGame,
        patchPayload: PendingPatchPayload,
        libraryRepository: GameLibraryRepository
    ): IntentResult {
        val basePayload = SnesRomHeaderParser.loadRomPayload(
            context = context,
            pathOrUri = baseGame.sourcePathOrUri,
            preferredZipEntry = baseGame.zipEntryName
        ) ?: return IntentResult.Error("No se pudo leer la ROM base: ${baseGame.title}")

        val patchedBytes = runCatching {
            when (patchPayload.patchExtension.lowercase(Locale.ROOT)) {
                "ips" -> applyIpsPatch(basePayload.bytes, patchPayload.patchBytes)
                "bps" -> applyBpsPatch(basePayload.bytes, patchPayload.patchBytes)
                else -> if (isBpsMagic(patchPayload.patchBytes)) {
                    applyBpsPatch(basePayload.bytes, patchPayload.patchBytes)
                } else {
                    applyIpsPatch(basePayload.bytes, patchPayload.patchBytes)
                }
            }
        }.getOrElse { err ->
            return IntentResult.Error(err.message ?: "Error al aplicar el parche ${patchPayload.patchFileName}")
        }

        val baseClean = sanitizeFileStem(baseGame.title)
        val patchClean = sanitizeFileStem(patchPayload.patchFileName.substringBeforeLast('.'))
        val patchedFileName = "${baseClean}_${patchClean}.sfc"

        val importedGame = saveRomToLocalLibraryDirAndRegister(
            context = context,
            fileName = patchedFileName,
            fileBytes = patchedBytes,
            libraryRepository = libraryRepository
        ) ?: return IntentResult.Error("El resultado del parche no produjo una ROM SNES válida")

        return IntentResult.RomImportedAndReady(importedGame)
    }

    /**
     * Guarda los bytes de la ROM en el directorio local de juegos de la app (filesDir/roms)
     * para que permanezca en la biblioteca automáticamente en futuras sesiones.
     */
    private fun saveRomToLocalLibraryDirAndRegister(
        context: Context,
        fileName: String,
        fileBytes: ByteArray,
        libraryRepository: GameLibraryRepository
    ): RomGame? {
        val localRomsDir = File(context.filesDir, "roms").apply { mkdirs() }
        val safeFileName = sanitizeFileName(fileName)
        val targetFile = File(localRomsDir, safeFileName)

        return runCatching {
            targetFile.writeBytes(fileBytes)
            // Copia espejo opcional en externalFilesDir("roms") si está disponible
            runCatching {
                context.getExternalFilesDir("roms")?.let { extDir ->
                    extDir.mkdirs()
                    File(extDir, safeFileName).writeBytes(fileBytes)
                }
            }

            val payload = SnesRomHeaderParser.loadRomPayload(context, targetFile.absolutePath)
                ?: return null
            val meta = payload.metadata

            val newGame = RomGame(
                id = "${meta.crc32Hex}_${targetFile.name.hashCode()}",
                title = meta.displayTitle,
                fullInternalTitle = meta.internalHeaderTitle,
                sourcePathOrUri = targetFile.absolutePath,
                zipEntryName = meta.zipEntryName,
                crc32Hex = meta.crc32Hex,
                enhancementChip = meta.enhancementChip,
                isPalRegion = meta.isPalRegion,
                accentHue = abs(meta.crc32Hex.hashCode()) % 360
            )
            libraryRepository.upsertSingleGameSync(newGame)
        }.getOrNull()
    }

    /**
     * Implementación completa del estándar IPS (International Patching System):
     * - Cabecera "PATCH" (5 bytes)
     * - Registros estándar [offset 24-bit BE, length 16-bit BE, payload]
     * - Registros RLE [offset 24-bit BE, 0x0000, rleCount 16-bit BE, rleByte]
     * - Marcador "EOF" + truncado opcional de 24-bit BE.
     */
    fun applyIpsPatch(sourceRom: ByteArray, patch: ByteArray): ByteArray {
        if (patch.size < 8 ||
            patch[0] != 'P'.code.toByte() ||
            patch[1] != 'A'.code.toByte() ||
            patch[2] != 'T'.code.toByte() ||
            patch[3] != 'C'.code.toByte() ||
            patch[4] != 'H'.code.toByte()
        ) {
            throw IllegalArgumentException("El archivo .ips no tiene una cabecera PATCH válida")
        }

        var result = sourceRom.copyOf()
        var pos = 5

        while (pos + 3 <= patch.size) {
            // Verificar marcador "EOF" (0x45, 0x4F, 0x46)
            if (patch[pos] == 'E'.code.toByte() &&
                patch[pos + 1] == 'O'.code.toByte() &&
                patch[pos + 2] == 'F'.code.toByte()
            ) {
                pos += 3
                // Truncado opcional de 3 bytes tras EOF
                if (pos + 3 <= patch.size) {
                    val truncateLen = ((patch[pos].toInt() and 0xFF) shl 16) or
                        ((patch[pos + 1].toInt() and 0xFF) shl 8) or
                        (patch[pos + 2].toInt() and 0xFF)
                    if (truncateLen in 32768..(16 * 1024 * 1024)) {
                        result = result.copyOf(truncateLen)
                    }
                }
                break
            }

            if (pos + 5 > patch.size) break
            val offset = ((patch[pos].toInt() and 0xFF) shl 16) or
                ((patch[pos + 1].toInt() and 0xFF) shl 8) or
                (patch[pos + 2].toInt() and 0xFF)
            val length = ((patch[pos + 3].toInt() and 0xFF) shl 8) or
                (patch[pos + 4].toInt() and 0xFF)
            pos += 5

            if (length == 0) {
                // Registro RLE (Run-Length Encoding)
                if (pos + 3 > patch.size) break
                val rleCount = ((patch[pos].toInt() and 0xFF) shl 8) or
                    (patch[pos + 1].toInt() and 0xFF)
                val rleByte = patch[pos + 2]
                pos += 3

                val requiredSize = offset + rleCount
                if (requiredSize > result.size) {
                    result = result.copyOf(max(requiredSize, result.size))
                }
                result.fill(rleByte, offset, offset + rleCount)
            } else {
                // Registro directo
                if (pos + length > patch.size) break
                val requiredSize = offset + length
                if (requiredSize > result.size) {
                    result = result.copyOf(max(requiredSize, result.size))
                }
                System.arraycopy(patch, pos, result, offset, length)
                pos += length
            }
        }

        return result
    }

    /**
     * Implementación completa del estándar BPS (Beat Patch System - "BPS1"):
     * - Cabecera "BPS1"
     * - Enteros de longitud variable (VLV) para sourceSize, targetSize y metadata
     * - Ajuste automático de cabecera SMC de 512 bytes si el parche espera ROM limpia o con cabecera
     * - Comandos SourceRead (0), TargetRead (1), SourceCopy (2) y TargetCopy (3).
     */
    fun applyBpsPatch(rawSourceRom: ByteArray, patch: ByteArray): ByteArray {
        if (!isBpsMagic(patch) || patch.size < 16) {
            throw IllegalArgumentException("El archivo .bps no tiene una cabecera BPS1 válida")
        }

        var pos = 4

        fun readVlv(): Long {
            var data = 0L
            var shift = 1L
            while (pos < patch.size) {
                val x = patch[pos++].toInt() and 0xFF
                data += (x and 0x7F) * shift
                if ((x and 0x80) != 0) break
                shift = shift shl 7
                data += shift
            }
            return data
        }

        val expectedSourceSize = readVlv().toInt()
        val targetSize = readVlv().toInt().coerceIn(1, 24 * 1024 * 1024)
        val metadataSize = readVlv().toInt().coerceAtLeast(0)
        pos = (pos + metadataSize).coerceAtMost(patch.size)

        // Adaptar automáticamente si la ROM tiene o no cabecera copiadora SMC de 512 bytes
        val source: ByteArray = when {
            rawSourceRom.size == expectedSourceSize -> rawSourceRom
            rawSourceRom.size - 512 == expectedSourceSize && (rawSourceRom.size % 1024 == 512) ->
                rawSourceRom.copyOfRange(512, rawSourceRom.size)
            rawSourceRom.size + 512 == expectedSourceSize ->
                ByteArray(512) + rawSourceRom
            else -> rawSourceRom
        }

        val target = ByteArray(targetSize)
        var outputOffset = 0
        var sourceRelativeOffset = 0
        var targetRelativeOffset = 0
        val footerStart = patch.size - 12

        while (pos < footerStart && outputOffset < targetSize) {
            val actionAndLen = readVlv()
            val mode = (actionAndLen and 3L).toInt()
            var length = ((actionAndLen ushr 2) + 1L).toInt()

            when (mode) {
                0 -> {
                    // SourceRead
                    while (length-- > 0 && outputOffset < targetSize) {
                        target[outputOffset] = if (outputOffset < source.size) source[outputOffset] else 0
                        outputOffset++
                    }
                }
                1 -> {
                    // TargetRead
                    while (length-- > 0 && outputOffset < targetSize && pos < footerStart) {
                        target[outputOffset++] = patch[pos++]
                    }
                }
                2 -> {
                    // SourceCopy
                    val offsetData = readVlv()
                    val negative = (offsetData and 1L) != 0L
                    val absOffset = (offsetData ushr 1).toInt()
                    sourceRelativeOffset += if (negative) -absOffset else absOffset
                    while (length-- > 0 && outputOffset < targetSize) {
                        val b = if (sourceRelativeOffset in source.indices) source[sourceRelativeOffset] else 0
                        target[outputOffset++] = b
                        sourceRelativeOffset++
                    }
                }
                3 -> {
                    // TargetCopy
                    val offsetData = readVlv()
                    val negative = (offsetData and 1L) != 0L
                    val absOffset = (offsetData ushr 1).toInt()
                    targetRelativeOffset += if (negative) -absOffset else absOffset
                    while (length-- > 0 && outputOffset < targetSize) {
                        val b = if (targetRelativeOffset in 0 until outputOffset) target[targetRelativeOffset] else 0
                        target[outputOffset++] = b
                        targetRelativeOffset++
                    }
                }
            }
        }

        return target
    }

    private fun isBpsMagic(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 'B'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'S'.code.toByte() &&
            bytes[3] == '1'.code.toByte()
    }

    private fun detectExtensionFromMagicBytes(bytes: ByteArray): String {
        if (bytes.size >= 5 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'A'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() &&
            bytes[3] == 'C'.code.toByte() &&
            bytes[4] == 'H'.code.toByte()
        ) {
            return "ips"
        }
        if (isBpsMagic(bytes)) {
            return "bps"
        }
        if (bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() &&
            bytes[3] == 4.toByte()
        ) {
            return "zip"
        }
        return "sfc"
    }

    private fun readBytesFromUri(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        return runCatching {
            val inputStream: InputStream? = if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path ?: return null
                FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri)
            }
            inputStream?.use { stream ->
                val out = ByteArrayOutputStream(256 * 1024)
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val r = stream.read(buf)
                    if (r <= 0) break
                    if (total + r > maxBytes) break
                    out.write(buf, 0, r)
                    total += r
                }
                out.toByteArray()
            }
        }.getOrNull()
    }

    private fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "Juego_Importado.sfc" }
    }

    private fun sanitizeFileStem(rawStem: String): String {
        return rawStem
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifBlank { "ROM" }
    }
}
