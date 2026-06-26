package com.pashkd.krender.engine.tools.common.bitmapfont.io

import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontCommon
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnostic
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnosticSeverity
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontGlyph
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontInfo
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontKerning
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontPage
import java.io.File

class BitmapFontParser {
    fun parse(fntFile: File): BitmapFontDocument {
        val normalizedPath = normalizePath(fntFile.path)
        if (!fntFile.isFile) {
            return errorDocument(fntFile, "Font file not found: '$normalizedPath'.")
        }
        return runCatching {
            val lines = fntFile.readLines(Charsets.UTF_8)
            if (lines.isEmpty()) {
                return errorDocument(fntFile, "Font file is empty.")
            }
            if (isBinaryFnt(lines.first())) {
                return errorDocument(fntFile, "Binary .fnt format is not supported. Use text format.")
            }
            buildDocument(fntFile, lines)
        }.getOrElse { error ->
            errorDocument(fntFile, "Failed to read font file: ${error.message ?: "unknown error"}.")
        }
    }

    private fun buildDocument(
        fntFile: File,
        lines: List<String>,
    ): BitmapFontDocument {
        val diagnostics = mutableListOf<BitmapFontDiagnostic>()
        val fntPath = normalizePath(fntFile.path)
        var info: BitmapFontInfo? = null
        var common: BitmapFontCommon? = null
        val pages = mutableListOf<BitmapFontPage>()
        val glyphs = mutableListOf<BitmapFontGlyph>()
        val kernings = mutableListOf<BitmapFontKerning>()
        val seenGlyphIds = mutableSetOf<Int>()

        lines.forEachIndexed { lineIndex, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed
            val tag = line.substringBefore(' ').substringBefore('\t')
            val attrs = parseAttributes(line.removePrefix(tag).trimStart())

            when (tag) {
                "info" -> info = parseInfo(attrs)
                "common" -> common = parseCommon(attrs, diagnostics, fntPath)
                "page" -> parsePage(attrs, fntFile, diagnostics, fntPath)?.let { pages += it }
                "char" ->
                    parseGlyph(attrs, diagnostics, fntPath, lineIndex)?.let { glyph ->
                        if (!seenGlyphIds.add(glyph.id)) {
                            diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Duplicate glyph id=${glyph.id}.", fntPath)
                        }
                        glyphs += glyph
                    }
                "kerning" -> parseKerning(attrs, diagnostics, fntPath)?.let { kernings += it }
                "chars", "kernings" -> Unit
                else -> Unit
            }
        }

        if (common == null) {
            diagnostics += diag(BitmapFontDiagnosticSeverity.Error, "Missing 'common' block in font descriptor.", fntPath)
        }
        if (pages.isEmpty()) {
            diagnostics += diag(BitmapFontDiagnosticSeverity.Error, "No font pages declared.", fntPath)
        }
        if (glyphs.isEmpty()) {
            diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Font has no glyphs.", fntPath)
        }
        pages.forEach { page ->
            if (!page.exists) {
                diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Page texture '${page.file}' not found.", fntPath)
            }
        }
        val commonBlock = common
        if (commonBlock != null) {
            validateGlyphBounds(glyphs, commonBlock, pages, diagnostics, fntPath)
        }

        val hasErrors = diagnostics.any { it.severity == BitmapFontDiagnosticSeverity.Error }
        return BitmapFontDocument(
            file = fntFile,
            info = info,
            common = common,
            pages = pages,
            glyphs = glyphs.sortedBy { it.id },
            kernings = kernings,
            diagnostics = diagnostics,
            readable = !hasErrors,
        )
    }

    private fun parseInfo(attrs: Map<String, String>): BitmapFontInfo =
        BitmapFontInfo(
            face = attrs["face"],
            size = attrs["size"]?.toIntOrNull(),
            bold = attrs["bold"] == "1",
            italic = attrs["italic"] == "1",
            charset = attrs["charset"],
            unicode = attrs["unicode"] == "1",
            stretchH = attrs["stretchH"]?.toIntOrNull(),
            smooth = attrs["smooth"] == "1",
            aa = attrs["aa"]?.toIntOrNull(),
            padding = attrs["padding"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
            spacing = attrs["spacing"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
        )

    private fun parseCommon(
        attrs: Map<String, String>,
        diagnostics: MutableList<BitmapFontDiagnostic>,
        source: String,
    ): BitmapFontCommon? {
        val lineHeight = attrs["lineHeight"]?.toIntOrNull()
        val base = attrs["base"]?.toIntOrNull()
        val scaleW = attrs["scaleW"]?.toIntOrNull()
        val scaleH = attrs["scaleH"]?.toIntOrNull()
        val pages = attrs["pages"]?.toIntOrNull()
        if (lineHeight == null || base == null || scaleW == null || scaleH == null || pages == null) {
            diagnostics += diag(BitmapFontDiagnosticSeverity.Error, "Incomplete 'common' block.", source)
            return null
        }
        return BitmapFontCommon(
            lineHeight = lineHeight,
            base = base,
            scaleW = scaleW,
            scaleH = scaleH,
            pages = pages,
            packed = attrs["packed"] == "1",
        )
    }

    private fun parsePage(
        attrs: Map<String, String>,
        fntFile: File,
        diagnostics: MutableList<BitmapFontDiagnostic>,
        source: String,
    ): BitmapFontPage? {
        val id = attrs["id"]?.toIntOrNull()
        val fileName = attrs["file"]
        if (id == null || fileName.isNullOrBlank()) {
            diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Malformed page entry.", source)
            return null
        }
        val pageFile = File(fntFile.parentFile, fileName)
        return BitmapFontPage(
            id = id,
            file = fileName,
            resolvedPath = normalizePath(pageFile.path),
            exists = pageFile.isFile,
        )
    }

    private fun parseGlyph(
        attrs: Map<String, String>,
        diagnostics: MutableList<BitmapFontDiagnostic>,
        source: String,
        lineIndex: Int,
    ): BitmapFontGlyph? {
        val id = attrs["id"]?.toIntOrNull()
        if (id == null) {
            diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Glyph missing id at line ${lineIndex + 1}.", source)
            return null
        }
        val charValue = attrs["letter"] ?: attrs["char"] ?: id.toChar().takeIf { it.isDefined() }?.toString()
        return BitmapFontGlyph(
            id = id,
            char = charValue,
            x = attrs["x"]?.toIntOrNull() ?: 0,
            y = attrs["y"]?.toIntOrNull() ?: 0,
            width = attrs["width"]?.toIntOrNull() ?: 0,
            height = attrs["height"]?.toIntOrNull() ?: 0,
            xOffset = attrs["xoffset"]?.toIntOrNull() ?: 0,
            yOffset = attrs["yoffset"]?.toIntOrNull() ?: 0,
            xAdvance = attrs["xadvance"]?.toIntOrNull() ?: 0,
            page = attrs["page"]?.toIntOrNull() ?: 0,
            channel = attrs["chnl"]?.toIntOrNull() ?: 0,
        )
    }

    private fun parseKerning(
        attrs: Map<String, String>,
        diagnostics: MutableList<BitmapFontDiagnostic>,
        source: String,
    ): BitmapFontKerning? {
        val first = attrs["first"]?.toIntOrNull()
        val second = attrs["second"]?.toIntOrNull()
        val amount = attrs["amount"]?.toIntOrNull()
        if (first == null || second == null || amount == null) {
            diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Malformed kerning entry.", source)
            return null
        }
        return BitmapFontKerning(first = first, second = second, amount = amount)
    }

    private fun validateGlyphBounds(
        glyphs: List<BitmapFontGlyph>,
        common: BitmapFontCommon,
        pages: List<BitmapFontPage>,
        diagnostics: MutableList<BitmapFontDiagnostic>,
        source: String,
    ) {
        val pageIds = pages.map { it.id }.toSet()
        glyphs.forEach { glyph ->
            if (glyph.page !in pageIds && pageIds.isNotEmpty()) {
                diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Glyph id=${glyph.id} references missing page ${glyph.page}.", source)
            }
            if (glyph.width > 0 && glyph.height > 0) {
                val right = glyph.x + glyph.width
                val bottom = glyph.y + glyph.height
                if (glyph.x < 0 || glyph.y < 0 || right > common.scaleW || bottom > common.scaleH) {
                    diagnostics += diag(BitmapFontDiagnosticSeverity.Warning, "Glyph id=${glyph.id} is outside page bounds.", source)
                }
            }
        }
    }

    private fun errorDocument(
        file: File,
        message: String,
    ): BitmapFontDocument =
        BitmapFontDocument(
            file = file,
            diagnostics = listOf(diag(BitmapFontDiagnosticSeverity.Error, message, normalizePath(file.path))),
            readable = false,
        )

    private fun diag(
        severity: BitmapFontDiagnosticSeverity,
        message: String,
        source: String? = null,
    ) = BitmapFontDiagnostic(severity = severity, message = message, source = source)

    private fun isBinaryFnt(firstLine: String): Boolean = firstLine.length >= 4 && firstLine[0].code == 0 && firstLine[1].code == 0 && firstLine[2].code == 0

    companion object {
        private val AttrPattern = Regex("""(\w+)=("[^"]*"|\S+)""")

        internal fun parseAttributes(text: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            AttrPattern.findAll(text).forEach { match ->
                val key = match.groupValues[1]
                val rawValue = match.groupValues[2]
                result[key] =
                    if (rawValue.startsWith('"') && rawValue.endsWith('"')) {
                        rawValue.substring(1, rawValue.length - 1)
                    } else {
                        rawValue
                    }
            }
            return result
        }
    }
}

fun layoutSampleText(
    text: String,
    document: BitmapFontDocument,
): SampleTextLayout {
    val common = document.common ?: return SampleTextLayout()
    val glyphMap = document.glyphs.associateBy { it.id }
    val placements = mutableListOf<SampleTextGlyphPlacement>()
    val missing = mutableListOf<Int>()
    var cursorX = 0
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    text.forEach { ch ->
        val codepoint = ch.code
        val glyph = glyphMap[codepoint]
        if (glyph == null) {
            missing += codepoint
            return@forEach
        }
        val glyphX = cursorX + glyph.xOffset
        val glyphY = glyph.yOffset
        placements +=
            SampleTextGlyphPlacement(
                glyph = glyph,
                x = glyphX,
                y = glyphY,
            )
        if (glyph.width > 0 && glyph.height > 0) {
            minX = minOf(minX, glyphX)
            minY = minOf(minY, glyphY)
            maxX = maxOf(maxX, glyphX + glyph.width)
            maxY = maxOf(maxY, glyphY + glyph.height)
        }
        cursorX += glyph.xAdvance
    }
    val hasBounds = minX != Int.MAX_VALUE && minY != Int.MAX_VALUE && maxX != Int.MIN_VALUE && maxY != Int.MIN_VALUE
    return SampleTextLayout(
        glyphPlacements = placements,
        totalWidth = cursorX,
        lineHeight = common.lineHeight,
        boundsMinX = if (hasBounds) minX else 0,
        boundsMinY = if (hasBounds) minY else 0,
        boundsWidth = if (hasBounds) (maxX - minX).coerceAtLeast(0) else 0,
        boundsHeight = if (hasBounds) (maxY - minY).coerceAtLeast(0) else 0,
        missingCodepoints = missing.distinct(),
    )
}

internal fun normalizePath(path: String): String = path.replace('\\', '/')

// Re-export layout types for callers that import from io package
typealias SampleTextLayout = com.pashkd.krender.engine.tools.common.bitmapfont.model.SampleTextLayout
typealias SampleTextGlyphPlacement = com.pashkd.krender.engine.tools.common.bitmapfont.model.SampleTextGlyphPlacement
