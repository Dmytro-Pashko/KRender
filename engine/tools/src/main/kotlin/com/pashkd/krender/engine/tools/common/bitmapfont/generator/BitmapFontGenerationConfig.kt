package com.pashkd.krender.engine.tools.common.bitmapfont.generator

import com.pashkd.krender.engine.tools.common.bitmapfont.charset.CharsetPreset

data class BitmapFontGenerationConfig(
    val sourceFont: String = "",
    val sizePx: Int = 24,
    val charsetPreset: CharsetPreset = CharsetPreset.ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC,
    val customCharacters: String = "",
    val padding: Int = 2,
    val spacing: Int = 1,
    val pageWidth: Int = 512,
    val pageHeight: Int = 512,
    val antialias: Boolean = true,
    val hinting: Boolean = true,
)

data class FontGenerationDiagnostic(
    val severity: FontGenerationDiagnosticSeverity,
    val message: String,
)

enum class FontGenerationDiagnosticSeverity {
    Info,
    Warning,
    Error,
}
