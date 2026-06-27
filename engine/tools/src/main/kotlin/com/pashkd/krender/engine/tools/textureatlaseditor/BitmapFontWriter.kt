package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File

/**
 * Writes text BMFont descriptors without remapping glyph rectangles.
 *
 * Scene2D/Hiero `.fnt` glyph `x`/`y` values are local to the page texture
 * declared by the matching `page` line. The exporter preserves those values so
 * freshly exported fonts can keep their own page PNGs next to the descriptor.
 *
 * Delegates formatting to the common [com.pashkd.krender.engine.tools.common.bitmapfont.io.BitmapFontWriter]
 * and adds atlas-specific path validation.
 */
class BitmapFontWriter {
    private val delegate =
        com.pashkd.krender.engine.tools.common.bitmapfont.io
            .BitmapFontWriter()

    fun write(
        assetRoot: File,
        targetPath: String,
        document: BitmapFontDocument,
        overwrite: Boolean,
        pageFileOverrides: Map<Int, String> = emptyMap(),
    ): TextureAtlasEditorFileWriteResult {
        val targetFile =
            TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, targetPath)
                ?: return failure("Font export target path must stay inside the asset root.")
        val result = delegate.write(targetFile, document, overwrite, pageFileOverrides)
        return TextureAtlasEditorFileWriteResult(
            success = result.success,
            message = result.message,
            writtenPaths = result.writtenPaths,
        )
    }

    private fun failure(message: String): TextureAtlasEditorFileWriteResult = TextureAtlasEditorFileWriteResult(success = false, message = message)
}
