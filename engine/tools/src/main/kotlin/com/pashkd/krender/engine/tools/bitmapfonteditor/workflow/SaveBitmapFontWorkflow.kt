package com.pashkd.krender.engine.tools.bitmapfonteditor.workflow

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadataCodec
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.FontPageImageWriter
import com.pashkd.krender.engine.tools.common.bitmapfont.io.BitmapFontWriter
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontPage
import java.io.File

class SaveBitmapFontWorkflow(
    private val state: BitmapFontEditorState,
    private val engine: EngineContext,
) {
    private val fntWriter = BitmapFontWriter()

    @Suppress("ReturnCount", "LongMethod")
    fun save() {
        val document = state.document
        val metadata = state.metadata
        if (document == null) {
            state.statusMessage = "Nothing to save. Open or generate a font first."
            engine.logger.warn(TAG) { "Save rejected: no document" }
            return
        }

        val assetRoot = engine.assetRegistry.baseDir()
        val writtenPaths = mutableListOf<String>()

        // Save .fnt
        val fntPath = metadata?.outputFnt?.takeIf { it.isNotBlank() } ?: deriveOutputFnt()
        val fntFile = File(assetRoot, fntPath)
        val pageFileName = fntFile.nameWithoutExtension + ".png"
        val fntResult =
            fntWriter.write(
                targetFile = fntFile,
                document = document,
                overwrite = true,
                pageFileOverrides = mapOf(0 to pageFileName),
            )
        if (!fntResult.success) {
            state.statusMessage = "Failed to save .fnt: ${fntResult.message}"
            engine.logger.warn(TAG) { "Save .fnt failed: ${fntResult.message}" }
            return
        }
        writtenPaths += fntResult.writtenPaths

        // Save .png
        val pageRgba = state.generatedPageRgba
        if (pageRgba != null && state.generatedPageWidth > 0 && state.generatedPageHeight > 0) {
            val pngFile = File(fntFile.parentFile, pageFileName)
            val pngOk = FontPageImageWriter.writePng(pageRgba, state.generatedPageWidth, state.generatedPageHeight, pngFile)
            if (pngOk) {
                writtenPaths += pngFile.path.replace('\\', '/')
            } else {
                state.statusMessage = "Failed to save page PNG."
                engine.logger.warn(TAG) { "Save PNG failed for '${pngFile.path}'" }
                return
            }
        }

        // Save .kfont.json
        if (metadata != null) {
            val metaPath = state.metadataPath
            if (metaPath != null) {
                val metaFile = File(assetRoot, metaPath)
                val updatedMeta =
                    metadata.copy(
                        outputFnt = fntPath,
                        outputPages =
                            listOf(
                                File(fntFile.parentFile, pageFileName)
                                    .path
                                    .removePrefix(assetRoot.path)
                                    .removePrefix("/")
                                    .removePrefix("\\")
                                    .replace('\\', '/'),
                            ),
                    )
                BitmapFontEditorMetadataCodec.save(metaFile, updatedMeta)
                state.metadata = updatedMeta
                writtenPaths += metaPath
                engine.logger.info(TAG) { "Saved metadata path='$metaPath'" }
            }
        }

        state.document =
            document.copy(
                pages =
                    listOf(
                        BitmapFontPage(
                            id = 0,
                            file = pageFileName,
                            resolvedPath = File(fntFile.parentFile, pageFileName).absolutePath.replace('\\', '/'),
                            exists = true,
                        ),
                    ),
            )
        state.previewTexturePath = null
        state.previewTextureRevision = 0L
        state.dirty = false
        state.statusMessage = "Saved: ${writtenPaths.joinToString(", ") { File(it).name }}."
        engine.logger.info(TAG) { "Bitmap font saved paths=${writtenPaths.joinToString()}" }
    }

    private fun deriveOutputFnt(): String {
        val metaPath = state.metadataPath ?: state.inputPath ?: "ui/fonts/generated.fnt"
        val dir = File(metaPath).parent?.replace('\\', '/')?.let { "$it/" } ?: ""
        val baseName = File(metaPath).nameWithoutExtension.removeSuffix(".kfont")
        return "$dir$baseName.fnt"
    }

    companion object {
        private const val TAG = "SaveBitmapFontWorkflow"
    }
}
