package com.pashkd.krender.engine.tools.common.bitmapfont.generator

import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontCommon
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontGlyph
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontInfo
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontPage
import java.io.File

data class BitmapFontGenerationResult(
    val document: BitmapFontDocument?,
    val pageImageRgba: ByteArray?,
    val pageWidth: Int,
    val pageHeight: Int,
    val diagnostics: List<FontGenerationDiagnostic>,
    val success: Boolean,
)

class BitmapFontGenerator(
    private val rasterizer: FontRasterizer = AwtFontRasterizer(),
    private val packer: GlyphPacker = SkylineGlyphPacker(),
) {
    @Suppress("ReturnCount")
    fun generate(
        config: BitmapFontGenerationConfig,
        outputFntPath: String,
        outputPageFileName: String,
    ): BitmapFontGenerationResult {
        val allDiagnostics = mutableListOf<FontGenerationDiagnostic>()

        val rasterized = rasterizer.rasterize(config)
        allDiagnostics += rasterized.diagnostics
        if (rasterized.glyphs.isEmpty()) {
            allDiagnostics +=
                FontGenerationDiagnostic(
                    FontGenerationDiagnosticSeverity.Error,
                    "No glyphs were rasterized. Check source font and charset.",
                )
            return failureResult(allDiagnostics)
        }

        val packResult =
            packer.pack(
                glyphs = rasterized.glyphs,
                pageWidth = config.pageWidth,
                pageHeight = config.pageHeight,
                padding = config.padding,
                spacing = config.spacing,
            )
        allDiagnostics += packResult.diagnostics
        if (packResult.overflow) {
            allDiagnostics +=
                FontGenerationDiagnostic(
                    FontGenerationDiagnosticSeverity.Error,
                    "Glyphs do not fit on a ${config.pageWidth}x${config.pageHeight} page. Increase page size or reduce charset/font size.",
                )
            return failureResult(allDiagnostics)
        }

        val pageImage = composePage(rasterized, packResult, config)
        val document =
            buildDocument(
                config = config,
                rasterized = rasterized,
                packResult = packResult,
                outputFntPath = outputFntPath,
                outputPageFileName = outputPageFileName,
            )

        allDiagnostics +=
            FontGenerationDiagnostic(
                FontGenerationDiagnosticSeverity.Info,
                "Generated ${packResult.placements.size} glyphs on ${config.pageWidth}x${config.pageHeight} page.",
            )

        return BitmapFontGenerationResult(
            document = document,
            pageImageRgba = pageImage,
            pageWidth = config.pageWidth,
            pageHeight = config.pageHeight,
            diagnostics = allDiagnostics,
            success = true,
        )
    }

    private fun composePage(
        rasterized: RasterizedFont,
        packResult: GlyphPackingResult,
        config: BitmapFontGenerationConfig,
    ): ByteArray {
        val w = config.pageWidth
        val h = config.pageHeight
        val rgba = ByteArray(w * h * 4)
        val glyphMap = rasterized.glyphs.associateBy { it.codepoint }

        for (placement in packResult.placements) {
            val srcGlyph = glyphMap[placement.codepoint]
            if (srcGlyph == null || srcGlyph.width <= 0 || srcGlyph.height <= 0) continue
            blitGlyph(rgba, w, h, srcGlyph, placement.x, placement.y)
        }
        return rgba
    }

    private fun blitGlyph(
        page: ByteArray,
        pageW: Int,
        pageH: Int,
        glyph: RasterizedGlyph,
        destX: Int,
        destY: Int,
    ) {
        for (row in 0 until glyph.height) {
            val srcRowStart = row * glyph.width * 4
            val dstRow = destY + row
            if (dstRow < 0 || dstRow >= pageH) continue
            for (col in 0 until glyph.width) {
                val dstCol = destX + col
                if (dstCol < 0 || dstCol >= pageW) continue
                val srcIdx = srcRowStart + col * 4
                val dstIdx = (dstRow * pageW + dstCol) * 4
                page[dstIdx] = glyph.bitmap[srcIdx]
                page[dstIdx + 1] = glyph.bitmap[srcIdx + 1]
                page[dstIdx + 2] = glyph.bitmap[srcIdx + 2]
                page[dstIdx + 3] = glyph.bitmap[srcIdx + 3]
            }
        }
    }

    private fun buildDocument(
        config: BitmapFontGenerationConfig,
        rasterized: RasterizedFont,
        packResult: GlyphPackingResult,
        outputFntPath: String,
        outputPageFileName: String,
    ): BitmapFontDocument {
        val fontFile = File(config.sourceFont)
        val faceName = fontFile.nameWithoutExtension

        val info =
            BitmapFontInfo(
                face = faceName,
                size = config.sizePx,
                unicode = true,
                smooth = config.antialias,
                aa = if (config.antialias) 1 else 0,
                padding = listOf(config.padding, config.padding, config.padding, config.padding),
                spacing = listOf(config.spacing, config.spacing),
            )

        val common =
            BitmapFontCommon(
                lineHeight = rasterized.lineHeight,
                base = rasterized.ascent,
                scaleW = config.pageWidth,
                scaleH = config.pageHeight,
                pages = 1,
            )

        val page =
            BitmapFontPage(
                id = 0,
                file = outputPageFileName,
                resolvedPath = null,
                exists = false,
            )

        val glyphs =
            packResult.placements.map { packed ->
                val charStr =
                    if (packed.codepoint in 32..126) {
                        String(Character.toChars(packed.codepoint))
                    } else {
                        null
                    }
                BitmapFontGlyph(
                    id = packed.codepoint,
                    char = charStr,
                    x = packed.x,
                    y = packed.y,
                    width = packed.width,
                    height = packed.height,
                    xOffset = packed.xOffset,
                    yOffset = packed.yOffset,
                    xAdvance = packed.xAdvance,
                    page = 0,
                    channel = 0,
                )
            }

        return BitmapFontDocument(
            file = File(outputFntPath),
            info = info,
            common = common,
            pages = listOf(page),
            glyphs = glyphs,
            readable = true,
        )
    }

    private fun failureResult(diagnostics: List<FontGenerationDiagnostic>) =
        BitmapFontGenerationResult(
            document = null,
            pageImageRgba = null,
            pageWidth = 0,
            pageHeight = 0,
            diagnostics = diagnostics,
            success = false,
        )
}
