package com.pashkd.krender.engine.tools.bitmapfonteditor.workflow

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadata
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadataCodec
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.io.BitmapFontParser
import java.io.File

class OpenBitmapFontWorkflow(
    private val state: BitmapFontEditorState,
    private val engine: EngineContext,
) {
    private val parser = BitmapFontParser()

    fun openFromPath(fontPath: String) {
        val normalizedPath = fontPath.trim().replace('\\', '/')
        if (normalizedPath.endsWith(".${BitmapFontEditorMetadata.EXTENSION}", ignoreCase = true)) {
            openMetadata(normalizedPath)
        } else {
            openFnt(normalizedPath)
        }
    }

    @Suppress("ReturnCount")
    private fun openMetadata(metadataPath: String) {
        val assetRoot = engine.assetRegistry.baseDir()
        val metaFile = resolveFile(assetRoot, metadataPath)
        if (metaFile == null || !metaFile.isFile) {
            state.statusMessage = "Metadata file not found: '$metadataPath'."
            engine.logger.warn(TAG) { "Open bitmap font metadata rejected: file not found path='$metadataPath'" }
            return
        }
        val metadata = BitmapFontEditorMetadataCodec.load(metaFile)
        if (metadata == null) {
            state.statusMessage = "Failed to parse metadata: '$metadataPath'."
            engine.logger.warn(TAG) { "Bitmap font metadata parse failed path='$metadataPath'" }
            return
        }
        state.metadata = metadata
        state.metadataPath = metadataPath
        state.resolvedInputPath = metaFile.canonicalPath.replace('\\', '/')
        state.previewTexturePath = null
        state.previewTextureRevision = 0L
        engine.logger.info(TAG) { "Loaded bitmap font metadata path='$metadataPath' sourceFont='${metadata.sourceFont}'" }

        if (metadata.outputFnt.isNotBlank()) {
            val fntFile = resolveFile(assetRoot, metadata.outputFnt)
            if (fntFile != null && fntFile.isFile) {
                openFnt(metadata.outputFnt)
                return
            }
        }
        state.document = null
        state.inputPath = null
        state.resolvedFontPath = null
        state.glyphSelection.selectedGlyphId = null
        state.glyphSelection.hoveredGlyphId = null
        state.selectedPageIndex = 0
        state.statusMessage = "Metadata loaded. Configure generation settings and generate."
    }

    @Suppress("ReturnCount")
    fun openFnt(fntPath: String) {
        val normalizedPath = fntPath.trim().replace('\\', '/')
        if (normalizedPath.isBlank()) {
            state.statusMessage = "No font path specified."
            engine.logger.warn(TAG) { "Open bitmap font rejected: blank path" }
            return
        }
        val assetRoot = engine.assetRegistry.baseDir()
        val fntFile = resolveFile(assetRoot, normalizedPath)
        if (fntFile == null || !fntFile.isFile) {
            state.statusMessage = "Font file not found: '$normalizedPath'."
            engine.logger.warn(TAG) { "Open bitmap font rejected: file not found path='$normalizedPath'" }
            return
        }
        engine.logger.info(TAG) { "Opening bitmap font path='$normalizedPath' resolved='${fntFile.path}'" }
        val document = parser.parse(fntFile)
        state.document = document
        state.inputPath = normalizedPath
        state.resolvedFontPath = fntFile.canonicalPath.replace('\\', '/')
        if (state.metadataPath == null) {
            state.resolvedInputPath = state.resolvedFontPath
        }
        state.previewTexturePath = null
        state.previewTextureRevision = 0L
        state.diagnostics = document.diagnostics
        state.glyphSelection.selectedGlyphId = null
        state.glyphSelection.hoveredGlyphId = null
        state.selectedPageIndex = 0

        if (!document.readable) {
            state.statusMessage = "Font descriptor is not readable."
            engine.logger.warn(TAG) { "Bitmap font not readable path='$normalizedPath' diagnostics=${document.diagnostics.size}" }
            return
        }

        val firstPage = document.pages.firstOrNull()
        if (firstPage != null && firstPage.exists && firstPage.resolvedPath != null) {
            val pageRelativePath = resolvePageAssetPath(assetRoot, firstPage.resolvedPath)
            if (pageRelativePath != null) {
                val ref = AssetRef.texture(pageRelativePath)
                if (!engine.assets.isLoaded(ref)) {
                    engine.assets.queue(ref)
                }
                engine.logger.info(TAG) {
                    "Requested page texture load path='$pageRelativePath' for font='$normalizedPath'"
                }
            }
        }

        state.statusMessage = "Opened '${fntFile.name}': ${document.glyphs.size} glyphs, ${document.pages.size} pages."
        engine.logger.info(TAG) {
            "Bitmap font opened path='$normalizedPath' glyphs=${document.glyphs.size} pages=${document.pages.size} kernings=${document.kernings.size}"
        }
    }

    private fun resolveFile(
        assetRoot: File,
        path: String,
    ): File? {
        val assetFile = File(assetRoot, path)
        if (assetFile.isFile) return assetFile
        val absoluteFile = File(path)
        return absoluteFile.takeIf { it.isAbsolute && it.isFile }
    }

    private fun resolvePageAssetPath(
        assetRoot: File,
        resolvedPath: String,
    ): String? {
        val pageFile = File(resolvedPath)
        val rootPath = assetRoot.canonicalPath.replace('\\', '/')
        val pagePath = pageFile.canonicalPath.replace('\\', '/')
        return if (pagePath.startsWith(rootPath)) {
            pagePath.removePrefix(rootPath).removePrefix("/")
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "OpenBitmapFontWorkflow"
    }
}
