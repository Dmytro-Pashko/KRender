package com.pashkd.krender.engine.tools.common.bitmapfont.preview

data class GlyphSelectionState(
    var selectedGlyphId: Int? = null,
    var hoveredGlyphId: Int? = null,
    var glyphFilter: String = "",
)
