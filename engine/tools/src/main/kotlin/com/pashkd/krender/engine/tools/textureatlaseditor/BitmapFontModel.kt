package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File

data class BitmapFontDocument(
    val file: File,
    val info: BitmapFontInfo? = null,
    val common: BitmapFontCommon? = null,
    val pages: List<BitmapFontPage> = emptyList(),
    val glyphs: List<BitmapFontGlyph> = emptyList(),
    val kernings: List<BitmapFontKerning> = emptyList(),
    val diagnostics: List<BitmapFontDiagnostic> = emptyList(),
    val readable: Boolean = true,
)

data class BitmapFontInfo(
    val face: String? = null,
    val size: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val charset: String? = null,
    val unicode: Boolean = false,
    val stretchH: Int? = null,
    val smooth: Boolean = false,
    val aa: Int? = null,
    val padding: List<Int> = emptyList(),
    val spacing: List<Int> = emptyList(),
)

data class BitmapFontCommon(
    val lineHeight: Int,
    val base: Int,
    val scaleW: Int,
    val scaleH: Int,
    val pages: Int,
    val packed: Boolean = false,
)

data class BitmapFontPage(
    val id: Int,
    val file: String,
    val resolvedPath: String? = null,
    val exists: Boolean = false,
)

data class BitmapFontGlyph(
    val id: Int,
    val char: String? = null,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val xAdvance: Int,
    val page: Int,
    val channel: Int,
)

data class BitmapFontKerning(
    val first: Int,
    val second: Int,
    val amount: Int,
)

data class BitmapFontDiagnostic(
    val severity: TextureAtlasEditorDiagnosticSeverity,
    val message: String,
    val source: String? = null,
)

data class FontPreviewState(
    var selectedFontResourceId: String? = null,
    var selectedPageIndex: Int = 0,
    var selectedGlyphId: Int? = null,
    var hoveredGlyphId: Int? = null,
    var sampleText: String = "The quick brown fox jumps over the lazy dog 0123456789",
    var showSampleTextPreview: Boolean = false,
    var glyphFilter: String = "",
    var showGlyphBounds: Boolean = true,
    var tintColor: TextureAtlasEditorColor = TextureAtlasEditorColor(),
)

data class SampleTextLayout(
    val glyphPlacements: List<SampleTextGlyphPlacement> = emptyList(),
    val totalWidth: Int = 0,
    val lineHeight: Int = 0,
    val boundsMinX: Int = 0,
    val boundsMinY: Int = 0,
    val boundsWidth: Int = 0,
    val boundsHeight: Int = 0,
    val missingCodepoints: List<Int> = emptyList(),
)

data class SampleTextGlyphPlacement(
    val glyph: BitmapFontGlyph,
    val x: Int,
    val y: Int,
)
