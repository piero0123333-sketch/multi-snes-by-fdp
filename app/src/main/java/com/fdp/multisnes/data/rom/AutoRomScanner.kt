package com.fdp.multisnes.data.rom

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.fdp.multisnes.data.model.RomGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Motor del botón "BUSCAR JUEGOS (AUTO)" (Imágenes 1 y 3).
 * Escanea automáticamente carpetas comunes del teléfono y árboles SAF autorizados
 * encontrando cualquier ROM (.sfc, .smc, .swc, .fig, .zip) y asociando carátulas locales si existen.
 */
class AutoRomScanner(private val context: Context) {

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")

    suspend fun scanAllSources(safTreeUris: List<String>): List<RomGame> = withContext(Dispatchers.IO) {
        val discovered = LinkedHashMap<String, RomGame>()

        // 1. Escanear carpeta interna dedicada de la app + directorios públicos habituales
        val candidateDirs = buildList {
            add(File(context.filesDir, "roms").apply { mkdirs() })
            context.getExternalFilesDir("roms")?.let { add(it.apply { mkdirs() }) }

            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                add(File(extRoot, "Download"))
                add(File(extRoot, "ROMs"))
                add(File(extRoot, "Roms"))
                add(File(extRoot, "SNES"))
                add(File(extRoot, "Snes"))
                add(File(extRoot, "Games"))
            }
        }

        for (dir in candidateDirs) {
            if (dir.exists() && dir.canRead()) {
                scanFileDirectoryRecursive(dir, depth = 0, maxDepth = 4, outMap = discovered)
            }
        }

        // 2. Escanear carpetas SAF seleccionadas por el usuario (Android 11+ Scoped Storage)
        for (uriStr in safTreeUris) {
            val treeUri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: continue
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            scanDocumentTreeRecursive(rootDoc, depth = 0, maxDepth = 4, outMap = discovered)
        }

        discovered.values.sortedBy { it.title }
    }

    private fun scanFileDirectoryRecursive(
        dir: File,
        depth: Int,
        maxDepth: Int,
        outMap: MutableMap<String, RomGame>
    ) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return

        val siblingImages = children
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in imageExtensions }
            .associateBy { it.nameWithoutExtension.lowercase(Locale.ROOT) }

        for (child in children) {
            if (child.isDirectory && !child.name.startsWith(".")) {
                scanFileDirectoryRecursive(child, depth + 1, maxDepth, outMap)
            } else if (child.isFile) {
                val ext = child.extension.lowercase(Locale.ROOT)
                if (ext in SnesRomHeaderParser.SUPPORTED_ROM_EXTENSIONS ||
                    ext in SnesRomHeaderParser.SUPPORTED_ARCHIVE_EXTENSIONS
                ) {
                    val payload = SnesRomHeaderParser.loadRomPayload(context, child.absolutePath) ?: continue
                    val meta = payload.metadata
                    val baseLower = child.nameWithoutExtension.lowercase(Locale.ROOT)
                    val coverFile = siblingImages[baseLower]

                    val game = RomGame(
                        id = "${meta.crc32Hex}_${child.name.hashCode()}",
                        title = meta.displayTitle,
                        fullInternalTitle = meta.internalHeaderTitle,
                        sourcePathOrUri = child.absolutePath,
                        zipEntryName = meta.zipEntryName,
                        crc32Hex = meta.crc32Hex,
                        enhancementChip = meta.enhancementChip,
                        isPalRegion = meta.isPalRegion,
                        coverImagePath = coverFile?.absolutePath,
                        accentHue = abs(meta.crc32Hex.hashCode()) % 360
                    )
                    outMap.putIfAbsent(meta.crc32Hex, game)
                }
            }
        }
    }

    private fun scanDocumentTreeRecursive(
        folder: DocumentFile,
        depth: Int,
        maxDepth: Int,
        outMap: MutableMap<String, RomGame>
    ) {
        if (depth > maxDepth) return
        val files = folder.listFiles()

        val siblingImages = files
            .filter { it.isFile && it.name != null }
            .filter { doc ->
                val ext = doc.name!!.substringAfterLast('.', "").lowercase(Locale.ROOT)
                ext in imageExtensions
            }
            .associateBy { it.name!!.substringBeforeLast('.').lowercase(Locale.ROOT) }

        for (doc in files) {
            val name = doc.name ?: continue
            if (doc.isDirectory && !name.startsWith(".")) {
                scanDocumentTreeRecursive(doc, depth + 1, maxDepth, outMap)
            } else if (doc.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext in SnesRomHeaderParser.SUPPORTED_ROM_EXTENSIONS ||
                    ext in SnesRomHeaderParser.SUPPORTED_ARCHIVE_EXTENSIONS
                ) {
                    val uriStr = doc.uri.toString()
                    val payload = SnesRomHeaderParser.loadRomPayload(context, uriStr) ?: continue
                    val meta = payload.metadata
                    val baseLower = name.substringBeforeLast('.').lowercase(Locale.ROOT)
                    val coverUri = siblingImages[baseLower]?.uri?.toString()

                    val game = RomGame(
                        id = "${meta.crc32Hex}_${name.hashCode()}",
                        title = meta.displayTitle,
                        fullInternalTitle = meta.internalHeaderTitle,
                        sourcePathOrUri = uriStr,
                        zipEntryName = meta.zipEntryName,
                        crc32Hex = meta.crc32Hex,
                        enhancementChip = meta.enhancementChip,
                        isPalRegion = meta.isPalRegion,
                        coverImagePath = coverUri,
                        accentHue = abs(meta.crc32Hex.hashCode()) % 360
                    )
                    outMap.putIfAbsent(meta.crc32Hex, game)
                }
            }
        }
    }
}
