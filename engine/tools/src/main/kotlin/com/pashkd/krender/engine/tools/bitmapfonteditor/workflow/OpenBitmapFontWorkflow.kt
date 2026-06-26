package com.pashkd.krender.engine.tools.bitmapfonteditor.workflow

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.io.BitmapFontParser
import java.io.File

class OpenBitmapFontWorkflow(
    private val state: BitmapFontEditorState,
    private val engine: EngineContext,
) {
    private val parser = BitmapFontParser()

    fun openFromPath(fntPath: String) {
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
        if (absoluteFile.isAbsolute && absoluteFile.isFile) return absoluteFile
        return null
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
