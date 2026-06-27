package com.pashkd.krender.engine.tools.common.bitmapfont.generator

data class RasterizedFont(
    val glyphs: List<RasterizedGlyph>,
    val lineHeight: Int,
    val ascent: Int,
    val descent: Int,
    val missingCodepoints: List<Int> = emptyList(),
    val diagnostics: List<FontGenerationDiagnostic> = emptyList(),
)

interface FontRasterizer {
    fun rasterize(config: BitmapFontGenerationConfig): RasterizedFont
}
