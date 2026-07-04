package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.TextureMetadataReader
import java.io.File

class SkyboxAtlasLayoutResolver {
    fun readSourceSize(
        assetRoot: File,
        path: String,
    ): Pair<Int, Int>? {
        val file = resolveSourceFile(assetRoot, path) ?: return null
        val metadata = TextureMetadataReader.read(file) ?: return null
        return metadata.width to metadata.height
    }

    fun resolveRegions(
        width: Int,
        height: Int,
        preset: SkyboxImportLayoutPreset,
        existing: Map<EnvironmentCubemapFace, SkyboxImportRegion> = emptyMap(),
    ): Map<EnvironmentCubemapFace, SkyboxImportRegion> {
        return when (preset) {
            SkyboxImportLayoutPreset.CubeCross4x3 -> buildCrossLayout(width, height)
            SkyboxImportLayoutPreset.HorizontalRow6x1 -> buildHorizontalRow(width, height)
            SkyboxImportLayoutPreset.VerticalColumn1x6 -> buildVerticalColumn(width, height)
            SkyboxImportLayoutPreset.Custom -> existing.ifEmpty { buildCrossLayout(width, height) }
        }
    }

    private fun buildCrossLayout(
        width: Int,
        height: Int,
    ): Map<EnvironmentCubemapFace, SkyboxImportRegion> {
        val cellWidth = width / 4
        val cellHeight = height / 3
        val cell = minOf(cellWidth, cellHeight).coerceAtLeast(1)
        return mapOf(
            EnvironmentCubemapFace.PosX to SkyboxImportRegion(EnvironmentCubemapFace.PosX, cell * 2, cell, cell, cell),
            EnvironmentCubemapFace.NegX to SkyboxImportRegion(EnvironmentCubemapFace.NegX, 0, cell, cell, cell),
            EnvironmentCubemapFace.PosY to SkyboxImportRegion(EnvironmentCubemapFace.PosY, cell, 0, cell, cell),
            EnvironmentCubemapFace.NegY to SkyboxImportRegion(EnvironmentCubemapFace.NegY, cell, cell * 2, cell, cell),
            EnvironmentCubemapFace.PosZ to SkyboxImportRegion(EnvironmentCubemapFace.PosZ, cell, cell, cell, cell),
            EnvironmentCubemapFace.NegZ to SkyboxImportRegion(EnvironmentCubemapFace.NegZ, cell * 3, cell, cell, cell),
        )
    }

    private fun buildHorizontalRow(
        width: Int,
        height: Int,
    ): Map<EnvironmentCubemapFace, SkyboxImportRegion> {
        val cellWidth = width / 6
        return EnvironmentCubemapFace.ordered.mapIndexed { index, face ->
            face to SkyboxImportRegion(face, cellWidth * index, 0, cellWidth.coerceAtLeast(1), height.coerceAtLeast(1))
        }.toMap()
    }

    private fun buildVerticalColumn(
        width: Int,
        height: Int,
    ): Map<EnvironmentCubemapFace, SkyboxImportRegion> {
        val cellHeight = height / 6
        return EnvironmentCubemapFace.ordered.mapIndexed { index, face ->
            face to SkyboxImportRegion(face, 0, cellHeight * index, width.coerceAtLeast(1), cellHeight.coerceAtLeast(1))
        }.toMap()
    }

    fun resolveSourceFile(
        assetRoot: File,
        path: String,
    ): File? {
        val normalized = path.trim().replace('\\', '/')
        if (normalized.isBlank()) return null
        val direct = File(normalized)
        return if (direct.isAbsolute) direct else File(assetRoot, normalized)
    }
}
