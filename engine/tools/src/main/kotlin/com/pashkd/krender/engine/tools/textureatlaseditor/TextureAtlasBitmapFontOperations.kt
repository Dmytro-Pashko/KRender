package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext
import java.io.File

internal class TextureAtlasBitmapFontOperations(
    private val state: TextureAtlasEditorState,
    private val engine: EngineContext,
    private val selectResource: (String?) -> Unit,
    private val writer: BitmapFontWriter = BitmapFontWriter(),
) {
    fun addFontResourceFromPath(fntPath: String) {
        val document = state.project.fontDocuments[fntPath]
        if (document == null || !document.readable) {
            state.statusMessage = "Cannot add font resource: .fnt file not found or not readable."
            return
        }
        val fontName = File(fntPath).nameWithoutExtension
        val id = "resource:font:$fntPath"
        if (state.resources.items.any { it.id == id }) {
            state.statusMessage = "Font resource for '$fontName' already exists."
            return
        }
        val resource = FontAtlasResource(
            id = id,
            name = fontName,
            sourcePath = fntPath,
            documentPath = fntPath,
            pageTexturePaths = document.pages.mapNotNull { it.resolvedPath },
            glyphCount = document.glyphs.size,
            kerningCount = document.kernings.size,
        )
        state.resources.items = state.resources.items + resource
        selectResource(resource.id)
        state.statusMessage = "Added font resource '$fontName' with ${document.glyphs.size} glyphs."
        engine.logger.info(TAG) { "Font resource added id='${resource.id}' name='$fontName' glyphs=${document.glyphs.size}" }
    }

    fun selectFontGlyph(glyphId: Int?) {
        state.fontPreview.selectedGlyphId = glyphId
    }

    fun setFontPreviewPage(pageIndex: Int) {
        state.fontPreview.selectedPageIndex = pageIndex
    }

    fun setFontPreviewTint(
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        state.fontPreview.tintColor = TextureAtlasEditorColor(red, green, blue, alpha)
    }

    fun setFontSampleText(text: String) {
        state.fontPreview.sampleText = text
    }

    fun setFontSampleTextPreviewEnabled(enabled: Boolean) {
        state.fontPreview.showSampleTextPreview = enabled
    }

    fun setFontGlyphFilter(filter: String) {
        state.fontPreview.glyphFilter = filter
    }

    fun exportBitmapFont() {
        val resource = state.selectedResource()
        if (resource !is FontAtlasResource) {
            state.statusMessage = "Select a font resource before exporting."
            return
        }
        val document = state.project.fontDocuments[resource.documentPath]
        if (document == null || !document.readable) {
            state.statusMessage = "Cannot export font: descriptor is not readable."
            return
        }
        val targetPath = state.importExport.targetPath
            .takeIf { it.endsWith(".fnt", ignoreCase = true) }
            ?: defaultFontExportPath(resource)
        val result = writer.write(
            assetRoot = engine.assetRegistry.baseDir(),
            targetPath = targetPath,
            document = document,
            overwrite = state.importExport.saveOverwrite,
        )
        state.importExport.lastExportResult = result
        state.statusMessage = result.message
        if (result.success) {
            state.importExport.targetPath = targetPath
            engine.logger.info(TAG) { "Font exported path='$targetPath' glyphs=${document.glyphs.size}" }
        } else {
            engine.logger.warn(TAG) { "Font export failed path='$targetPath': ${result.message}" }
        }
    }

    private fun defaultFontExportPath(resource: FontAtlasResource): String {
        val sourcePath = resource.documentPath
        val parent = File(sourcePath).parent?.replace('\\', '/')?.let { "$it/" } ?: ""
        val baseName = File(sourcePath).nameWithoutExtension.ifBlank { resource.name }
        return "${parent}${baseName}_export.fnt"
    }

    companion object {
        private const val TAG = "TextureAtlasFontOps"
    }
}
