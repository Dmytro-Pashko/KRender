package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.assets.importing.FileDialogFilter
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.assets.importing.NoOpFileDialogService
import java.io.File

class TextureAtlasEditorImportExportOperations(
    private val state: TextureAtlasEditorState,
    private val engine: EngineContext,
    private val savePackedFontDescriptors: (String, TextureAtlasPackingPlan) -> TextureAtlasEditorFileWriteResult? = { _, _ -> null },
    private val fileDialogService: FileDialogService = NoOpFileDialogService,
    private val atlasSaveService: TextureAtlasSaveService = NoOpTextureAtlasSaveService,
    private val importService: TextureAtlasEditorImportService = TextureAtlasEditorImportService(engine.logger),
    private val openPath: (String) -> Unit,
) {
    fun browseImportTexture() {
        val selected = fileDialogService.openFile(TextureImportDialogFilters)
        if (selected.isNullOrBlank()) return
        state.importExport.importSourcePath = normalizePath(selected)
        if (state.importExport.targetPath.isBlank() || state.importExport.targetPath.endsWith(".atlas", ignoreCase = true)) {
            state.importExport.targetPath = suggestedImportTargetPath(selected)
        }
        state.statusMessage = "Selected import source '${File(selected).name}'."
        engine.logger.info(TAG) { "Texture Atlas Editor import source selected path='${normalizePath(selected)}'" }
    }

    fun browseTextureSourceOnly() {
        val selected = fileDialogService.openFile(TextureImportDialogFilters)
        if (selected.isNullOrBlank()) return
        state.importExport.importSourcePath = normalizePath(selected)
        state.statusMessage = "Selected region source '${File(selected).name}'."
        engine.logger.info(TAG) { "Texture Atlas Editor region source selected path='${normalizePath(selected)}'" }
    }

    fun browseFontDescriptor() {
        val selected = fileDialogService.openFile(FontImportDialogFilters)
        if (selected.isNullOrBlank()) return
        state.importExport.fontSourcePath = normalizePath(selected)
        state.statusMessage = "Selected bitmap font source '${File(selected).name}'."
        engine.logger.info(TAG) { "Texture Atlas Editor bitmap font source selected path='${normalizePath(selected)}'" }
    }

    fun importTexture() {
        val assetRoot = engine.assetRegistry.baseDir()
        val targetPath =
            state.importExport.targetPath
                .takeUnless { path -> path.isBlank() || path.endsWith(".atlas", ignoreCase = true) }
                ?: suggestedImportTargetPath(state.importExport.importSourcePath)
        state.importExport.targetPath = targetPath
        val result =
            importService.importTexture(
                assetRoot = assetRoot,
                sourcePath = state.importExport.importSourcePath,
                targetPath = targetPath,
                overwrite = state.importExport.importOverwrite,
            )
        state.importExport.lastImportResult = result
        state.statusMessage = result.message
        if (result.success) {
            result.writtenPaths.firstOrNull()?.let { writtenPath ->
                val normalized = normalizePath(writtenPath)
                state.importExport.importSourcePath = normalized
            }
        }
    }

    fun savePackedAtlas() {
        if (state.hasUnappliedNinePatchDraft()) {
            val result =
                TextureAtlasEditorFileWriteResult(
                    success = false,
                    message = "Apply or reset the current NinePatch draft before saving the atlas.",
                )
            state.importExport.lastExportResult = result
            state.statusMessage = result.message
            engine.logger.warn(TAG) { "Texture Atlas Editor atlas save blocked because a NinePatch draft is still pending" }
            return
        }
        val plan = state.selectedPackingPlan()
        if (plan == null) {
            val result =
                TextureAtlasEditorFileWriteResult(
                    success = false,
                    message = "Run a packing dry-run before saving an atlas.",
                )
            state.importExport.lastExportResult = result
            state.statusMessage = result.message
            engine.logger.warn(TAG) { "Texture Atlas Editor atlas save requested without a packing plan" }
            return
        }
        val targetPath =
            state.importExport.targetPath
                .takeIf { path -> path.endsWith(".atlas", ignoreCase = true) }
                ?: defaultAtlasTargetPath()
        val result =
            atlasSaveService.savePackedAtlas(
                assetRoot = engine.assetRegistry.baseDir(),
                targetPath = targetPath,
                overwrite = state.importExport.saveOverwrite,
                plan = plan,
                ninePatchDocuments = state.project.ninePatchDocuments,
            )
        state.importExport.lastExportResult = result
        state.statusMessage = result.message
        if (result.success) {
            val fontRewriteResult = savePackedFontDescriptors(targetPath, plan)
            if (fontRewriteResult != null) {
                state.importExport.lastExportResult = fontRewriteResult
                state.statusMessage =
                    if (fontRewriteResult.success) {
                        "${result.message} ${fontRewriteResult.message}"
                    } else {
                        "${result.message} ${fontRewriteResult.message}"
                    }
            }
            state.dirty = false
            state.importExport.targetPath = targetPath
            openPath(targetPath)
        }
    }

    private fun suggestedImportTargetPath(sourcePath: String): String {
        val atlasDir = atlasRelativeTextureDirectory()
        return normalizePath("$atlasDir${File(sourcePath).name}")
    }

    private fun atlasRelativeTextureDirectory(): String {
        val atlasPath =
            state.currentInputPath
                ?.takeIf { it.endsWith(".atlas", ignoreCase = true) }
        if (atlasPath != null) {
            return File(atlasPath).parent?.replace('\\', '/')?.let { "$it/" }.orEmpty()
        }
        return "textures/"
    }

    private fun defaultAtlasTargetPath(): String =
        state.project.selectedAtlasPath
            ?: state.currentInputPath?.takeIf { it.endsWith(".atlas", ignoreCase = true) }
            ?: "atlases/packed.atlas"

    companion object {
        private const val TAG = "TextureAtlasEditorImportExportOps"

        private val TextureImportDialogFilters =
            listOf(
                FileDialogFilter("Textures", TextureAtlasEditorImportTextureExtensions.toList()),
            )

        private val FontImportDialogFilters =
            listOf(
                FileDialogFilter("Bitmap Fonts", listOf("fnt")),
            )
    }
}
