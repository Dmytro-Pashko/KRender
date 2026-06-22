package com.pashkd.krender.engine.tools.skin

import java.io.File
import java.nio.charset.StandardCharsets

data class SkinBitmapFontInfo(
    val file: File,
    val face: String? = null,
    val size: String? = null,
    val lineHeight: String? = null,
    val base: String? = null,
    val pages: String? = null,
    val charCount: Int? = null,
    val asciiGlyphCoverage: String? = null,
    val ukrainianGlyphCoverage: String? = null,
    val missingUkrainianGlyphs: String? = null,
    val missingUkrainianGlyphCount: Int? = null,
    val readable: Boolean = true,
)

class SkinBitmapFontParser {
    fun parse(file: File): SkinBitmapFontInfo =
        runCatching {
            val charIds = linkedSetOf<Int>()
            var face: String? = null
            var size: String? = null
            var lineHeight: String? = null
            var base: String? = null
            var pages: String? = null
            var declaredCharCount: Int? = null

            file.readLines(StandardCharsets.UTF_8).forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("info ") -> {
                        val attributes = parseAttributes(trimmed)
                        face = attributes["face"] ?: face
                        size = attributes["size"] ?: size
                    }

                    trimmed.startsWith("common ") -> {
                        val attributes = parseAttributes(trimmed)
                        lineHeight = attributes["lineHeight"] ?: lineHeight
                        base = attributes["base"] ?: base
                        pages = attributes["pages"] ?: pages
                    }

                    trimmed.startsWith("chars ") -> {
                        val attributes = parseAttributes(trimmed)
                        declaredCharCount = attributes["count"]?.toIntOrNull() ?: declaredCharCount
                    }

                    trimmed.startsWith("char ") -> {
                        val attributes = parseAttributes(trimmed)
                        attributes["id"]?.toIntOrNull()?.let(charIds::add)
                    }
                }
            }

            val asciiCovered = AsciiGlyphIds.count(charIds::contains)
            val ukrainianCovered = UkrainianGlyphIds.count(charIds::contains)
            val missingUkrainian =
                UkrainianGlyphIds
                    .filterNot(charIds::contains)
                    .joinToString(" ") { code -> "U+${code.toString(16).uppercase().padStart(4, '0')}" }

            SkinBitmapFontInfo(
                file = file,
                face = face,
                size = size,
                lineHeight = lineHeight,
                base = base,
                pages = pages,
                charCount = declaredCharCount ?: charIds.size.takeIf { it > 0 },
                asciiGlyphCoverage = "$asciiCovered/${AsciiGlyphIds.size}",
                ukrainianGlyphCoverage = "$ukrainianCovered/${UkrainianGlyphIds.size}",
                missingUkrainianGlyphs = missingUkrainian.takeIf(String::isNotBlank),
                missingUkrainianGlyphCount = UkrainianGlyphIds.size - ukrainianCovered,
            )
        }.getOrElse {
            SkinBitmapFontInfo(file = file, readable = false)
        }

    private fun parseAttributes(line: String): Map<String, String> =
        AttributeRegex.findAll(line).associate { match ->
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2]
            key to rawValue.trim('"')
        }

    private companion object {
        private val AttributeRegex = Regex("""(\w+)=(".*?"|[^\s]+)""")
        private val AsciiGlyphIds =
            ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
                .map(Char::code)
        private val UkrainianGlyphIds =
            ("АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯ" +
                "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя")
                .map(Char::code)
    }
}
