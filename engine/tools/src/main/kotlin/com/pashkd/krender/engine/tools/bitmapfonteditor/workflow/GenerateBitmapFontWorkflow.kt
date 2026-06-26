package com.pashkd.krender.engine.tools.bitmapfonteditor.workflow

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadata
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.charset.CharsetPreset
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.BitmapFontGenerationConfig
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.BitmapFontGenerator
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.FontGenerationDiagnostic
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.FontGenerationDiagnosticSeverity
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.FontPageImageWriter
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnostic
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnosticSeverity
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontPage
import java.io.File

class GenerateBitmapFontWorkflow(
    private val state: BitmapFontEditorState,
    private val engine: EngineContext,
) {
    private val generator = BitmapFontGenerator()

    @Suppress("ReturnCount")
    fun generate() {
        val metadata = state.metadata
        if (metadata == null) {
            state.statusMessage = "No metadata loaded. Create or open a .kfont.json first."
            engine.logger.warn(TAG) { "Generate rejected: no metadata" }
            return
        }

        val validationDiags = validateConfig(metadata)
        if (validationDiags.any { it.severity == FontGenerationDiagnosticSeverity.Error }) {
            state.diagnostics = validationDiags.map { it.toBitmapFontDiagnostic() }
            state.statusMessage = "Generation config has errors. Fix them before generating."
            engine.logger.warn(TAG) { "Generate rejected: validation errors" }
            return
        }

        val assetRoot = engine.assetRegistry.baseDir()
        val sourceAbsolute = File(assetRoot, metadata.sourceFont).absolutePath

        val config =
            BitmapFontGenerationConfig(
                sourceFont = sourceAbsolute,
                sizePx = metadata.generation.sizePx,
                charsetPreset = charsetPresetFromName(metadata.generation.charsetPreset),
                customCharacters = metadata.generation.customCharacters,
                padding = metadata.generation.padding,
                spacing = metadata.generation.spacing,
                pageWidth = metadata.generation.pageWidth,
                pageHeight = metadata.generation.pageHeight,
                antialias = metadata.generation.antialias,
                hinting = metadata.generation.hinting,
            )

        val outputFntPath = metadata.outputFnt.ifBlank { deriveOutputFnt() }
        val outputPageName = File(outputFntPath).nameWithoutExtension + ".png"

        engine.logger.info(TAG) { "Generating bitmap font source='${metadata.sourceFont}' size=${config.sizePx} page=${config.pageWidth}x${config.pageHeight}" }

        val result = generator.generate(config, outputFntPath, outputPageName)

        state.diagnostics = result.diagnostics.map { it.toBitmapFontDiagnostic() }

        if (!result.success || result.document == null) {
            state.statusMessage = "Generation failed. See diagnostics."
            engine.logger.warn(TAG) { "Bitmap font generation failed diagnostics=${result.diagnostics.size}" }
            return
        }

        state.generatedPageRgba = result.pageImageRgba
        state.generatedPageWidth = result.pageWidth
        state.generatedPageHeight = result.pageHeight

        val updatedDocument = writePreviewPage(assetRoot, outputFntPath, outputPageName, result)
        state.document = updatedDocument ?: result.document
        state.dirty = true

        state.statusMessage = "Generated ${result.document.glyphs.size} glyphs on ${result.pageWidth}x${result.pageHeight} page."
        engine.logger.info(TAG) {
            "Bitmap font generated glyphs=${result.document.glyphs.size} page=${result.pageWidth}x${result.pageHeight}"
        }
    }

    private fun validateConfig(metadata: BitmapFontEditorMetadata): List<FontGenerationDiagnostic> {
        val diags = mutableListOf<FontGenerationDiagnostic>()
        if (metadata.sourceFont.isBlank()) {
            diags += FontGenerationDiagnostic(FontGenerationDiagnosticSeverity.Error, "Source font path is empty.")
        } else {
            val assetRoot = engine.assetRegistry.baseDir()
            val sourceFile = File(assetRoot, metadata.sourceFont)
            if (!sourceFile.isFile) {
                diags += FontGenerationDiagnostic(FontGenerationDiagnosticSeverity.Error, "Source font not found: '${metadata.sourceFont}'.")
            }
        }
        if (metadata.generation.sizePx < 8 || metadata.generation.sizePx > 256) {
            diags += FontGenerationDiagnostic(FontGenerationDiagnosticSeverity.Error, "Font size must be 8-256 px.")
        }
        return diags
    }

    private fun deriveOutputFnt(): String {
        val metaPath = state.metadataPath ?: "ui/fonts/generated.fnt"
        val dir = File(metaPath).parent?.replace('\\', '/')?.let { "$it/" } ?: ""
        val baseName = File(metaPath).nameWithoutExtension.removeSuffix(".kfont")
        return "$dir$baseName.fnt"
    }

    @Suppress("ReturnCount")
    private fun writePreviewPage(
        assetRoot: File,
        outputFntPath: String,
        outputPageName: String,
        result: com.pashkd.krender.engine.tools.common.bitmapfont.generator.BitmapFontGenerationResult,
    ): com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument? {
        val pageRgba = result.pageImageRgba ?: return null
        val document = result.document ?: return null
        val fntDir = File(assetRoot, outputFntPath).parentFile ?: assetRoot
        val pngFile = File(fntDir, outputPageName)
        val wrote = FontPageImageWriter.writePng(pageRgba, result.pageWidth, result.pageHeight, pngFile)
        if (!wrote) {
            engine.logger.warn(TAG) { "Failed to write preview page PNG to '${pngFile.path}'" }
            return null
        }
        val resolvedPng = pngFile.absolutePath.replace('\\', '/')
        engine.logger.info(TAG) { "Wrote preview page PNG to '$resolvedPng'" }
        val relPath =
            pngFile.absolutePath
                .removePrefix(assetRoot.absolutePath)
                .removePrefix("/")
                .removePrefix("\\")
                .replace('\\', '/')
        val ref =
            com.pashkd.krender.engine.api.AssetRef
                .texture(relPath)
        if (engine.assets.isLoaded(ref)) {
            engine.assets.unload(ref)
        }
        engine.assets.queue(ref)
        return document.copy(
            pages =
                listOf(
                    BitmapFontPage(
                        id = 0,
                        file = outputPageName,
                        resolvedPath = resolvedPng,
                        exists = true,
                    ),
                ),
        )
    }

    private fun charsetPresetFromName(name: String): CharsetPreset = runCatching { CharsetPreset.valueOf(name) }.getOrDefault(CharsetPreset.ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC)

    companion object {
        private const val TAG = "GenerateBitmapFontWf"
    }
}

private fun FontGenerationDiagnostic.toBitmapFontDiagnostic() =
    BitmapFontDiagnostic(
        severity =
            when (severity) {
                FontGenerationDiagnosticSeverity.Info -> BitmapFontDiagnosticSeverity.Info
                FontGenerationDiagnosticSeverity.Warning -> BitmapFontDiagnosticSeverity.Warning
                FontGenerationDiagnosticSeverity.Error -> BitmapFontDiagnosticSeverity.Error
            },
        message = message,
    )
