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
            engine.logger.warn(TAG) { "Rejected Texture Atlas Editor add font resource path='$fntPath' because document was unreadable" }
            return
        }
        val resource = createFontAtlasResource(fntPath, document)
        if (state.resources.items.any { it.id == resource.id }) {
            state.statusMessage = "Font resource for '${resource.name}' already exists."
            engine.logger.warn(TAG) { "Rejected Texture Atlas Editor add font resource path='$fntPath' because resource id='${resource.id}' already exists" }
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
            engine.logger.warn(TAG) { "Rejected Texture Atlas Editor import font because source path was blank" }
            return
        }
        val sourceFile = File(normalizedSource)
        if (!sourceFile.isFile || !sourceFile.name.endsWith(".fnt", ignoreCase = true)) {
            state.statusMessage = "Choose a readable .fnt file before adding a font."
            engine.logger.warn(TAG) { "Rejected Texture Atlas Editor import font because source file was invalid path='$normalizedSource'" }
            return
        }
        val atlasPath = state.project.selectedAtlasPath ?: state.currentInputPath?.takeIf { it.endsWith(".atlas", ignoreCase = true) }
        if (atlasPath == null) {
            state.statusMessage = "Open a texture atlas before importing bitmap fonts."
            engine.logger.warn(TAG) { "Rejected Texture Atlas Editor import font path='$normalizedSource' because no atlas was open" }
            return
        }
        val atlasFile =
            TextureAtlasEditorPathValidator.resolveAssetPath(engine.assetRegistry.baseDir(), atlasPath)
                ?: File(atlasPath).takeIf(File::isFile)
                ?: run {
                    state.statusMessage = "The current texture atlas path is not available for font import."
                    engine.logger.warn(TAG) { "Rejected Texture Atlas Editor import font path='$normalizedSource' because atlas path='$atlasPath' could not be resolved" }
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
        val atlasDirectory =
            atlasFile.parentFile ?: run {
                state.statusMessage = "Cannot import bitmap font because the atlas directory is unavailable."
                engine.logger.warn(TAG) { "Rejected Texture Atlas Editor import font path='$normalizedSource' because atlas directory was unavailable" }
                return
            }
        val importTargets = chooseImportTargets(atlasDirectory, sourceFile.nameWithoutExtension, document.pages.size)
        val descriptorOverrides =
            document.pages
                .mapIndexed { index, page ->
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
            engine.logger.warn(TAG) { "Rejected Texture Atlas Editor export font because selected resource was not a font" }
            return
        }
        val document = state.project.fontDocuments[resource.documentPath]
        if (document == null || !document.readable) {
            state.statusMessage = "Cannot export font: descriptor is not readable."
            engine.logger.warn(TAG) { "Rejected Texture Atlas Editor export font resource id='${resource.id}' document='${resource.documentPath}' because descriptor was unreadable" }
            return
        }
        val targetPath =
            state.importExport.targetPath
                .takeIf { it.endsWith(".fnt", ignoreCase = true) }
                ?: defaultFontExportPath(resource)
        val result =
            writer.write(
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

    fun setPackFontInAtlas(enabled: Boolean) {
        val resource = state.selectedResource() as? FontAtlasResource ?: return
        val updated = resource.copy(packInAtlas = enabled)
        state.resources.items =
            state.resources.items.map { item ->
                if (item.id == resource.id) updated else item
            }
        selectResource(updated.id)
        state.dirty = true
        state.statusMessage =
            if (enabled) {
                "Font '${resource.name}' will be packed into the atlas on the next save."
            } else {
                "Font '${resource.name}' will remain external to the atlas."
            }
        engine.logger.info(TAG) {
            "Texture Atlas Editor font pack option changed resource id='${resource.id}' name='${resource.name}' enabled=$enabled"
        }
    }

    fun savePackedFontDescriptors(
        atlasTargetPath: String,
        plan: TextureAtlasPackingPlan,
    ): TextureAtlasEditorFileWriteResult? {
        val assetRoot = engine.assetRegistry.baseDir()
        val atlasFile =
            TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, atlasTargetPath)
                ?: return TextureAtlasEditorFileWriteResult(
                    success = false,
                    message = "Packed font descriptors were not updated because the atlas target path is outside the asset root.",
                )
        val pageNames = buildPackedAtlasPageNames(atlasFile, plan)
        val fontResources =
            state.resources.items
                .filterIsInstance<FontAtlasResource>()
                .filter { resource -> resource.packInAtlas }
        if (fontResources.isEmpty()) {
            return null
        }

        val failures = mutableListOf<String>()
        val writtenPaths = mutableListOf<String>()
        fontResources.forEach { resource ->
            val document = state.project.fontDocuments[resource.documentPath]
            if (document == null || !document.readable) {
                failures += "Descriptor for '${resource.name}' is unreadable."
                engine.logger.warn(TAG) { "Skipped packed font descriptor rewrite resource id='${resource.id}' because descriptor was unreadable" }
                return@forEach
            }
            if (document.pages.size != 1) {
                failures += "Font '${resource.name}' uses multiple pages and cannot be rewritten safely yet."
                engine.logger.warn(TAG) { "Skipped packed font descriptor rewrite resource id='${resource.id}' because pages=${document.pages.size} are unsupported" }
                return@forEach
            }
            val packedRegion =
                plan.pages
                    .flatMap { page -> page.regions }
                    .firstOrNull { region -> region.fontResourceId == resource.id }
            if (packedRegion == null) {
                failures += "Font page for '${resource.name}' was not packed into the atlas."
                engine.logger.warn(TAG) { "Skipped packed font descriptor rewrite resource id='${resource.id}' because no packed region was found" }
                return@forEach
            }
            val packedPage = plan.pages.getOrNull(packedRegion.pageIndex)
            if (packedPage == null) {
                failures += "Packed atlas page for '${resource.name}' could not be resolved."
                engine.logger.warn(TAG) { "Skipped packed font descriptor rewrite resource id='${resource.id}' because packed page index=${packedRegion.pageIndex} was missing" }
                return@forEach
            }
            val packedDescriptorPath = choosePackedDescriptorPath(resource.documentPath)
            val rewritten =
                document.copy(
                    common =
                        document.common?.copy(
                            pages = 1,
                            scaleW = packedPage.width,
                            scaleH = packedPage.height,
                        ),
                    pages =
                        listOf(
                            BitmapFontPage(
                                id = 0,
                                file = pageNames[packedRegion.pageIndex] ?: return@forEach,
                                resolvedPath = normalizePath(File(atlasFile.parentFile, pageNames[packedRegion.pageIndex]!!).path),
                                exists = true,
                            ),
                        ),
                    glyphs =
                        document.glyphs.map { glyph ->
                            glyph.copy(
                                x = glyph.x + packedRegion.x,
                                y = glyph.y + packedRegion.y,
                                page = 0,
                            )
                        },
                )
            val result =
                writer.write(
                    assetRoot = assetRoot,
                    targetPath = packedDescriptorPath,
                    document = rewritten,
                    overwrite = true,
                )
            if (!result.success) {
                failures += "Font '${resource.name}' packed descriptor could not be written: ${result.message}"
                engine.logger.warn(TAG) { "Packed font descriptor write failed resource id='${resource.id}' target='$packedDescriptorPath' message='${result.message}'" }
                return@forEach
            }
            writtenPaths += result.writtenPaths
            engine.logger.info(TAG) {
                "Wrote packed font descriptor resource id='${resource.id}' source='${resource.documentPath}' packed='$packedDescriptorPath' atlasPage='${pageNames[packedRegion.pageIndex]}' glyphs=${document.glyphs.size} offset=${packedRegion.x},${packedRegion.y}"
            }
        }

        return if (failures.isEmpty()) {
            TextureAtlasEditorFileWriteResult(
                success = true,
                message = "Wrote packed bitmap font descriptors.",
                writtenPaths = writtenPaths,
            )
        } else {
            TextureAtlasEditorFileWriteResult(
                success = false,
                message = failures.joinToString(" "),
                writtenPaths = writtenPaths,
            )
        }
    }

    private fun choosePackedDescriptorPath(sourceDocumentPath: String): String {
        val sourceFile = File(sourceDocumentPath)
        val parent = sourceFile.parent?.replace('\\', '/')?.let { "$it/" } ?: ""
        val baseName = sourceFile.nameWithoutExtension.ifBlank { "font" }
        val packedBase = "${baseName}_packed"
        val candidate = "$parent$packedBase.fnt"
        if (!File(candidate).exists()) return candidate
        var suffix = 2
        while (true) {
            val numbered = "$parent${packedBase}_$suffix.fnt"
            if (!File(numbered).exists()) return numbered
            suffix++
        }
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
    packInAtlas: Boolean = false,
): FontAtlasResource {
    val normalizedPath = normalizePath(fntPath)
    val fontName = File(normalizedPath).nameWithoutExtension
    return FontAtlasResource(
        id = "resource:font:$normalizedPath",
        name = fontName,
        sourcePath = normalizedPath,
        documentPath = normalizedPath,
        pageTexturePaths = document.pages.mapNotNull { it.resolvedPath },
        packInAtlas = packInAtlas,
        glyphCount = document.glyphs.size,
        kerningCount = document.kernings.size,
    )
}

private fun sanitizeFontFileStem(name: String): String =
    name
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .ifBlank { "font" }

private fun buildPackedAtlasPageNames(
    atlasFile: File,
    plan: TextureAtlasPackingPlan,
): Map<Int, String> {
    val baseName = atlasFile.nameWithoutExtension.ifBlank { "packed" }
    return plan.pages.associate { page ->
        page.index to "${baseName}_${page.index + 1}.png"
    }
}
