package com.fdp.multisnes.data.model

import kotlinx.serialization.Serializable

/**
 * Representa un juego en el catálogo de MULTI SNES BY FDP.
 * Soporta cualquier ROM clásica (.sfc, .smc, .swc, .fig, .zip) sin restricciones.
 */
@Serializable
data class RomGame(
    val id: String,
    val title: String,
    val fullInternalTitle: String = title,
    val sourcePathOrUri: String,
    val zipEntryName: String? = null,
    val crc32Hex: String,
    val enhancementChip: String = "SNES Estándar",
    val isPalRegion: Boolean = false,
    val progressPercent: Int = 0,
    val hasSaveState: Boolean = false,
    val isMarkedCompleted: Boolean = false,
    val totalPlayTimeSeconds: Long = 0L,
    val coverImagePath: String? = null,
    val accentHue: Int = 0
) {
    /**
     * Determina si muestra estrella según el progreso o hito alcanzado (Img 1 y 4):
     * - Estrella dorada al 100% o completado
     * - Estrella verde en progreso avanzado (>= 90%, ej. 96%)
     */
    val starType: StarBadge
        get() = when {
            isMarkedCompleted || progressPercent >= 100 -> StarBadge.GOLD
            progressPercent >= 90 -> StarBadge.GREEN
            else -> StarBadge.NONE
        }
}

enum class StarBadge {
    NONE,
    GREEN,
    GOLD
}
