package com.pashkd.krender.engine.tools.textureatlaseditor

typealias BitmapFontDocument = com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument
typealias BitmapFontInfo = com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontInfo
typealias BitmapFontCommon = com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontCommon
typealias BitmapFontPage = com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontPage
typealias BitmapFontGlyph = com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontGlyph
typealias BitmapFontKerning = com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontKerning
typealias BitmapFontDiagnostic = com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnostic
typealias SampleTextLayout = com.pashkd.krender.engine.tools.common.bitmapfont.model.SampleTextLayout
typealias SampleTextGlyphPlacement = com.pashkd.krender.engine.tools.common.bitmapfont.model.SampleTextGlyphPlacement

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
