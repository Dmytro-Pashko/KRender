package com.pashkd.krender.engine.tools.common.bitmapfont.generator

import com.pashkd.krender.engine.tools.common.bitmapfont.charset.CharsetBuilder
import com.pashkd.krender.engine.tools.common.bitmapfont.charset.CharsetPreset
import com.pashkd.krender.engine.tools.common.bitmapfont.charset.UnicodeCodePoint
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File

/**
 * KRender AWT-based font rasterizer for desktop bitmap font generation.
 *
 * Uses [java.awt.Font] and [java.awt.Graphics2D] to render individual glyphs
 * into RGBA bitmaps suitable for texture packing. This is a local KRender
 * implementation — not a port of any external library.
 */
class AwtFontRasterizer : FontRasterizer {

    override fun rasterize(config: BitmapFontGenerationConfig): RasterizedFont {
        val diagnostics = mutableListOf<FontGenerationDiagnostic>()

        val awtFont = loadFont(config, diagnostics) ?: return emptyResult(config, diagnostics)
        val codepoints = resolveCodepoints(config)
        val metrics = measureFontMetrics(awtFont)

        val glyphs = mutableListOf<RasterizedGlyph>()
        val missing = mutableListOf<Int>()

        for (cp in codepoints) {
            if (!awtFont.canDisplay(cp.value)) {
                missing += cp.value
                continue
            }
            val rendered = renderSingleGlyph(awtFont, cp, metrics, config)
            if (rendered != null) {
                glyphs += rendered
            }
        }

        if (missing.isNotEmpty()) {
            diagnostics += FontGenerationDiagnostic(
                FontGenerationDiagnosticSeverity.Warning,
                "Font cannot display ${missing.size} requested codepoint(s).",
            )
        }

        return RasterizedFont(
            glyphs = glyphs,
            lineHeight = metrics.lineHeight,
            ascent = metrics.ascent,
            descent = metrics.descent,
            missingCodepoints = missing,
            diagnostics = diagnostics,
        )
    }

    private fun loadFont(
        config: BitmapFontGenerationConfig,
        diagnostics: MutableList<FontGenerationDiagnostic>,
    ): Font? {
        val sourceFile = File(config.sourceFont)
        if (!sourceFile.isFile) {
            diagnostics += FontGenerationDiagnostic(
                FontGenerationDiagnosticSeverity.Error,
                "Source font file not found: '${config.sourceFont}'.",
            )
            return null
        }
        val rawFont = runCatching { Font.createFont(Font.TRUETYPE_FONT, sourceFile) }
            .getOrElse { err ->
                diagnostics += FontGenerationDiagnostic(
                    FontGenerationDiagnosticSeverity.Error,
                    "Cannot load font file: ${err.message ?: "unknown error"}.",
                )
                return null
            }
        return rawFont.deriveFont(Font.PLAIN, config.sizePx.toFloat())
    }

    private fun resolveCodepoints(config: BitmapFontGenerationConfig): List<UnicodeCodePoint> =
        CharsetBuilder.buildCombined(config.charsetPreset, config.customCharacters)

    private data class KRenderFontMetrics(
        val lineHeight: Int,
        val ascent: Int,
        val descent: Int,
    )

    private fun measureFontMetrics(font: Font): KRenderFontMetrics {
        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val probeG = probe.createGraphics()
        probeG.font = font
        val fm = probeG.fontMetrics
        val result = KRenderFontMetrics(
            lineHeight = fm.height,
            ascent = fm.ascent,
            descent = fm.descent,
        )
        probeG.dispose()
        return result
    }

    private fun renderSingleGlyph(
        font: Font,
        cp: UnicodeCodePoint,
        metrics: KRenderFontMetrics,
        config: BitmapFontGenerationConfig,
    ): RasterizedGlyph? {
        val charString = codepointToString(cp.value)

        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val probeG = probe.createGraphics()
        applyRenderingHints(probeG, config)
        probeG.font = font
        val fm = probeG.fontMetrics
        val advance = fm.charWidth(cp.value)
        val stringBounds = fm.getStringBounds(charString, probeG)
        probeG.dispose()

        val glyphW = maxOf(stringBounds.width.toInt(), 1)
        val glyphH = maxOf(stringBounds.height.toInt(), 1)

        if (glyphW <= 0 || glyphH <= 0) {
            return RasterizedGlyph(
                codepoint = cp.value,
                bitmap = ByteArray(0),
                width = 0, height = 0,
                xOffset = 0, yOffset = 0,
                xAdvance = advance,
            )
        }

        val canvas = BufferedImage(glyphW, glyphH, BufferedImage.TYPE_INT_ARGB)
        val canvasG = canvas.createGraphics()
        applyRenderingHints(canvasG, config)
        canvasG.font = font
        canvasG.color = Color.WHITE
        val drawX = -stringBounds.x.toInt()
        val drawY = metrics.ascent
        canvasG.drawString(charString, drawX, drawY)
        canvasG.dispose()

        val rgbaPixels = extractRgbaBytes(canvas, glyphW, glyphH)
        val yOff = stringBounds.y.toInt() + metrics.lineHeight - glyphH

        return RasterizedGlyph(
            codepoint = cp.value,
            bitmap = rgbaPixels,
            width = glyphW,
            height = glyphH,
            xOffset = stringBounds.x.toInt(),
            yOffset = yOff,
            xAdvance = advance,
        )
    }

    private fun applyRenderingHints(g: Graphics2D, config: BitmapFontGenerationConfig) {
        if (config.antialias) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        }
        if (config.hinting) {
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        }
    }

    private fun extractRgbaBytes(image: BufferedImage, w: Int, h: Int): ByteArray {
        val rgba = ByteArray(w * h * 4)
        var idx = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val pixel = image.getRGB(col, row)
                rgba[idx++] = ((pixel ushr 16) and 0xFF).toByte() // R
                rgba[idx++] = ((pixel ushr 8) and 0xFF).toByte()  // G
                rgba[idx++] = (pixel and 0xFF).toByte()            // B
                rgba[idx++] = ((pixel ushr 24) and 0xFF).toByte() // A
            }
        }
        return rgba
    }

    private fun codepointToString(codepoint: Int): String =
        String(Character.toChars(codepoint))

    private fun emptyResult(
        config: BitmapFontGenerationConfig,
        diagnostics: List<FontGenerationDiagnostic>,
    ) = RasterizedFont(
        glyphs = emptyList(),
        lineHeight = config.sizePx,
        ascent = config.sizePx,
        descent = 0,
        diagnostics = diagnostics,
    )
}
