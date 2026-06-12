package com.pashkd.krender.engine.assets

import java.io.File

/**
 * Lightweight terrain file metadata used by the asset browser.
 */
data class TerrainAssetMetadata(
    val width: Int,
    val height: Int,
    val layerCount: Int,
) {
    val size: String
        get() = "$width x $height"
}

/**
 * Reads terrain descriptor metadata without depending on LibGDX terrain persistence.
 */
object TerrainMetadataReader {
    fun read(file: File): TerrainAssetMetadata? {
        val text = file.readText(Charsets.UTF_8)
        val width = readInt(text, "width") ?: return null
        val height = readInt(text, "height") ?: return null
        val layerCount = readLayerCount(text)
        return TerrainAssetMetadata(width, height, layerCount)
    }

    private fun readInt(
        text: String,
        name: String,
    ): Int? {
        val pattern = Regex("\"${Regex.escape(name)}\"\\s*:\\s*(\\d+)")
        return pattern
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    private fun readLayerCount(text: String): Int {
        val layersBody = readArrayBody(text, "layers") ?: return 0
        return Regex("\"id\"\\s*:").findAll(layersBody).count()
    }

    private fun readArrayBody(
        text: String,
        name: String,
    ): String? {
        val keyMatch = Regex("\"${Regex.escape(name)}\"\\s*:").find(text) ?: return null
        var index = keyMatch.range.last + 1
        while (index < text.length && text[index].isWhitespace()) {
            index += 1
        }
        if (index >= text.length || text[index] != '[') return null

        val start = index + 1
        var depth = 1
        var inString = false
        var escaping = false
        index += 1
        while (index < text.length) {
            val char = text[index]
            when {
                escaping -> escaping = false
                char == '\\' && inString -> escaping = true
                char == '"' -> inString = !inString
                !inString && char == '[' -> depth += 1
                !inString && char == ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return text.substring(start, index)
                    }
                }
            }
            index += 1
        }
        return null
    }
}
