package com.pashkd.krender.engine.tools.common.bitmapfont.generator

data class RasterizedGlyph(
    val codepoint: Int,
    val bitmap: ByteArray,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val xAdvance: Int,
)

data class GlyphPackingResult(
    val placements: List<PackedGlyph>,
    val pageWidth: Int,
    val pageHeight: Int,
    val overflow: Boolean = false,
    val diagnostics: List<FontGenerationDiagnostic> = emptyList(),
)

data class PackedGlyph(
    val codepoint: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val xAdvance: Int,
)

interface GlyphPacker {
    fun pack(
        glyphs: List<RasterizedGlyph>,
        pageWidth: Int,
        pageHeight: Int,
        padding: Int,
        spacing: Int,
    ): GlyphPackingResult
}

class SkylineGlyphPacker : GlyphPacker {
    override fun pack(
        glyphs: List<RasterizedGlyph>,
        pageWidth: Int,
        pageHeight: Int,
        padding: Int,
        spacing: Int,
    ): GlyphPackingResult {
        val sorted = glyphs.sortedByDescending { it.height }
        val skyline = IntArray(pageWidth)
        val placements = mutableListOf<PackedGlyph>()
        val diagnostics = mutableListOf<FontGenerationDiagnostic>()
        var overflow = false
        val pad = padding
        val space = spacing

        for (glyph in sorted) {
            val w = glyph.width + pad * 2
            val h = glyph.height + pad * 2
            if (w <= 0 || h <= 0) {
                placements += PackedGlyph(
                    codepoint = glyph.codepoint,
                    x = 0, y = 0, width = 0, height = 0,
                    xOffset = glyph.xOffset, yOffset = glyph.yOffset, xAdvance = glyph.xAdvance,
                )
                continue
            }
            val totalW = w + space
            if (totalW > pageWidth) {
                diagnostics += FontGenerationDiagnostic(
                    FontGenerationDiagnosticSeverity.Error,
                    "Glyph U+${glyph.codepoint.toString(16).uppercase().padStart(4, '0')} is wider than the page.",
                )
                overflow = true
                continue
            }
            var bestX = -1
            var bestY = Int.MAX_VALUE
            for (x in 0..(pageWidth - totalW)) {
                var maxY = 0
                for (i in x until x + totalW) {
                    if (skyline[i] > maxY) maxY = skyline[i]
                }
                if (maxY + h + space <= pageHeight && maxY < bestY) {
                    bestY = maxY
                    bestX = x
                }
            }
            if (bestX < 0) {
                diagnostics += FontGenerationDiagnostic(
                    FontGenerationDiagnosticSeverity.Error,
                    "Glyph U+${glyph.codepoint.toString(16).uppercase().padStart(4, '0')} does not fit on page.",
                )
                overflow = true
                continue
            }
            val placeX = bestX + pad
            val placeY = bestY + pad
            for (i in bestX until bestX + totalW) {
                skyline[i] = bestY + h + space
            }
            placements += PackedGlyph(
                codepoint = glyph.codepoint,
                x = placeX, y = placeY,
                width = glyph.width, height = glyph.height,
                xOffset = glyph.xOffset, yOffset = glyph.yOffset, xAdvance = glyph.xAdvance,
            )
        }

        return GlyphPackingResult(
            placements = placements.sortedBy { it.codepoint },
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            overflow = overflow,
            diagnostics = diagnostics,
        )
    }
}
