package com.pashkd.krender.engine.tools.common.bitmapfont.generator

import com.pashkd.krender.engine.tools.common.bitmapfont.charset.CharsetPreset

enum class BitmapFontRasterizerType {
    AWT,
}

enum class AwtTextAntialiasingMode {
    DEFAULT,
    OFF,
    ON,
    GASP,
    LCD_HRGB,
    LCD_HBGR,
    LCD_VRGB,
    LCD_VBGR,
}

enum class AwtRenderQualityMode {
    DEFAULT,
    QUALITY,
    SPEED,
}

enum class AwtStrokeControlMode {
    DEFAULT,
    NORMALIZE,
    PURE,
}

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
    val rasterizer: BitmapFontRasterizerType = BitmapFontRasterizerType.AWT,
    val textAntialiasing: AwtTextAntialiasingMode = AwtTextAntialiasingMode.ON,
    val fractionalMetrics: Boolean = true,
    val renderQuality: AwtRenderQualityMode = AwtRenderQualityMode.QUALITY,
    val strokeControl: AwtStrokeControlMode = AwtStrokeControlMode.DEFAULT,
) {
    val smoothingEnabled: Boolean
        get() = antialias && textAntialiasing != AwtTextAntialiasingMode.OFF
}

data class FontGenerationDiagnostic(
    val severity: FontGenerationDiagnosticSeverity,
    val message: String,
)

enum class FontGenerationDiagnosticSeverity {
    Info,
    Warning,
    Error,
}
