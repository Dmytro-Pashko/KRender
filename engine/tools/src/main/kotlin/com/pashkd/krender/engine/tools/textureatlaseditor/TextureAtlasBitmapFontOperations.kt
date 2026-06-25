package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        val resource = createFontAtlasResource(fntPath, document)
        if (state.resources.items.any { it.id == resource.id }) {
            state.statusMessage = "Font resource for '${resource.name}' already exists."
            return
        }
        state.resources.items = state.resources.items + resource
        selectResource(resource.id)
        state.statusMessage = "Added font resource '${resource.name}' with ${document.glyphs.size} glyphs."
        engine.logger.info(TAG) { "Font resource added id='${resource.id}' name='${resource.name}' glyphs=${document.glyphs.size}" }
    }

    fun importFontResourceFromPath(fntPath: String) {
        val normalizedSource = fntPath.trim().replace('\\', '/')
        if (normalizedSource.isBlank()) {
            state.statusMessage = "Choose a .fnt file before adding a font."
            return
        }
        val sourceFile = File(normalizedSource)
        if (!sourceFile.isFile || !sourceFile.name.endsWith(".fnt", ignoreCase = true)) {
            state.statusMessage = "Choose a readable .fnt file before adding a font."
            return
        }
        val atlasPath = state.project.selectedAtlasPath ?: state.currentInputPath?.takeIf { it.endsWith(".atlas", ignoreCase = true) }
        if (atlasPath == null) {
            state.statusMessage = "Open a texture atlas before importing bitmap fonts."
            return
        }
        val atlasFile =
            TextureAtlasEditorPathValidator.resolveAssetPath(engine.assetRegistry.baseDir(), atlasPath)
                ?: File(atlasPath).takeIf(File::isFile)
                ?: run {
                    state.statusMessage = "The current texture atlas path is not available for font import."
                    return
                }
        val document = BitmapFontParser().parse(sourceFile)
        if (!document.readable) {
            state.statusMessage = "Cannot import bitmap font: the descriptor is not readable."
            engine.logger.warn(TAG) { "Bitmap font import rejected because descriptor was unreadable path='$normalizedSource'" }
            return
        }
        if (document.pages.isEmpty() || document.pages.any { it.resolvedPath.isNullOrBlank() || it.exists.not() }) {
            state.statusMessage = "Cannot import bitmap font: every page texture declared by the descriptor must exist."
            engine.logger.warn(TAG) { "Bitmap font import rejected because one or more pages were missing path='$normalizedSource'" }
            return
        }
        val atlasDirectory = atlasFile.parentFile ?: run {
            state.statusMessage = "Cannot import bitmap font because the atlas directory is unavailable."
            return
        }
        val importTargets = chooseImportTargets(atlasDirectory, sourceFile.nameWithoutExtension, document.pages.size)
        val descriptorOverrides =
            document.pages.mapIndexed { index, page ->
                page.id to importTargets.pageFiles[index].name
            }.toMap()
        runCatching {
            importTargets.pageFiles.forEachIndexed { index, targetFile ->
                val sourcePage = document.pages[index].resolvedPath?.let(::File) ?: error("Missing page source.")
                Files.copy(sourcePage.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
            }
            val writeResult =
                writer.write(
                    assetRoot = engine.assetRegistry.baseDir(),
                    targetPath = normalizePath(importTargets.fntFile.path),
                    document = document,
                    overwrite = false,
                    pageFileOverrides = descriptorOverrides,
                )
            if (!writeResult.success) {
                error(writeResult.message)
            }
            val importedDocument = BitmapFontParser().parse(importTargets.fntFile)
            if (!importedDocument.readable) {
                error("Imported font descriptor could not be reloaded.")
            }
            state.project =
                state.project.copy(
                    fontDocuments = state.project.fontDocuments + (normalizePath(importTargets.fntFile.path) to importedDocument),
                )
            val resource = createFontAtlasResource(normalizePath(importTargets.fntFile.path), importedDocument)
            state.resources.items = state.resources.items.filterNot { it.id == resource.id } + resource
            selectResource(resource.id)
            state.importExport.fontSourcePath = normalizePath(importTargets.fntFile.path)
            state.dirty = true
            state.statusMessage = "Imported bitmap font '${resource.name}' next to the atlas."
            engine.logger.info(TAG) {
                "Bitmap font imported source='$normalizedSource' target='${normalizePath(importTargets.fntFile.path)}' pages=${importTargets.pageFiles.size}"
            }
        }.onFailure { error ->
            importTargets.pageFiles.forEach { file -> if (file.isFile) file.delete() }
            if (importTargets.fntFile.isFile) {
                importTargets.fntFile.delete()
            }
            state.statusMessage = "Bitmap font import failed: ${error.message ?: "unknown error"}."
            engine.logger.warn(TAG, error) { "Bitmap font import failed source='$normalizedSource': ${error.message}" }
        }
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

    private fun chooseImportTargets(
        atlasDirectory: File,
        requestedBaseName: String,
        pageCount: Int,
    ): ImportedFontTargets {
        var suffix = 1
        while (true) {
            val baseName =
                if (suffix == 1) {
                    sanitizeFontFileStem(requestedBaseName)
                } else {
                    "${sanitizeFontFileStem(requestedBaseName)}_$suffix"
                }
            val fntFile = File(atlasDirectory, "$baseName.fnt")
            val pageFiles =
                List(pageCount) { index ->
                    val pageName =
                        if (index == 0) {
                            "$baseName.png"
                        } else {
                            "${baseName}_$index.png"
                        }
                    File(atlasDirectory, pageName)
                }
            if (!fntFile.exists() && pageFiles.none(File::exists)) {
                return ImportedFontTargets(fntFile = fntFile, pageFiles = pageFiles)
            }
            suffix++
        }
    }

    private data class ImportedFontTargets(
        val fntFile: File,
        val pageFiles: List<File>,
    )

    companion object {
        private const val TAG = "TextureAtlasFontOps"
    }
}

internal fun createFontAtlasResource(
    fntPath: String,
    document: BitmapFontDocument,
): FontAtlasResource {
    val normalizedPath = normalizePath(fntPath)
    val fontName = File(normalizedPath).nameWithoutExtension
    return FontAtlasResource(
        id = "resource:font:$normalizedPath",
        name = fontName,
        sourcePath = normalizedPath,
        documentPath = normalizedPath,
        pageTexturePaths = document.pages.mapNotNull { it.resolvedPath },
        glyphCount = document.glyphs.size,
        kerningCount = document.kernings.size,
    )
}

private fun sanitizeFontFileStem(name: String): String =
    name
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .ifBlank { "font" }
