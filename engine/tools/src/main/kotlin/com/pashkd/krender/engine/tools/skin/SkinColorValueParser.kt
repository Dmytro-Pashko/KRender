package com.pashkd.krender.engine.tools.skin

/** Backend-neutral parsed color used by editor rows, previews, and validation UI. */
data class SkinColorValue(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
    val displayValue: String,
)

object SkinColorValueParser {
    fun parse(values: Map<String, String>): SkinColorValue? {
        val hex = values["value"] ?: values["hex"]
        if (hex != null) {
            val normalized = hex.removePrefix("#")
            if (normalized.length !in setOf(6, 8) ||
                normalized.any { character -> !character.isDigit() && character.lowercaseChar() !in 'a'..'f' }
            ) {
                return null
            }
            val red = normalized.substring(0, 2).toInt(16) / 255f
            val green = normalized.substring(2, 4).toInt(16) / 255f
            val blue = normalized.substring(4, 6).toInt(16) / 255f
            val alpha = if (normalized.length == 8) normalized.substring(6, 8).toInt(16) / 255f else 1f
            return SkinColorValue(red, green, blue, alpha, "#${normalized.uppercase()}")
        }

        val red = values["r"]?.toFloatOrNull() ?: return null
        val green = values["g"]?.toFloatOrNull() ?: return null
        val blue = values["b"]?.toFloatOrNull() ?: return null
        val alpha = values["a"]?.toFloatOrNull() ?: 1f
        if (listOf(red, green, blue, alpha).any { channel -> channel !in 0f..1f }) return null
        return SkinColorValue(red, green, blue, alpha, "r=$red g=$green b=$blue a=$alpha")
    }
}
