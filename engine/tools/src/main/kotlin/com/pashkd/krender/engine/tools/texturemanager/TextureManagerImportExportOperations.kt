package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.EngineContext

class TextureManagerImportExportOperations(
    private val state: TextureManagerState,
    private val engine: EngineContext,
    private val importService: TextureManagerImportService = TextureManagerImportService(engine.logger),
    private val descriptorExporter: TextureAtlasDescriptorExporter = TextureAtlasDescriptorExporter(engine.logger, engine.sceneFiles),
    private val openPath: (String) -> Unit,
) {
    fun importTexture() {
        val assetRoot = engine.assetRegistry.baseDir()
        val result =
            importService.importTexture(
                assetRoot = assetRoot,
                sourcePath = state.importExport.importSourcePath,
                targetDirectory = state.importExport.importTargetDirectory.ifBlank { "textures" },
                overwrite = state.importExport.importOverwrite,
            )
        state.importExport.lastImportResult = result
        state.statusMessage = result.message
        if (result.success) {
            result.writtenPaths.firstOrNull()?.let(openPath)
        }
    }

    fun exportAtlasDescriptorDraft() {
        val plan = state.selectedPackingPlan()
        if (plan == null) {
            val result =
                TextureManagerFileWriteResult(
                    success = false,
                    message = "Run a packing dry-run before exporting an atlas descriptor.",
                )
            state.importExport.lastExportResult = result
            state.statusMessage = result.message
            engine.logger.warn(TAG) { "Texture Manager descriptor export requested without a packing plan" }
            return
        }
        val result =
            descriptorExporter.exportDescriptorDraft(
                assetRoot = engine.assetRegistry.baseDir(),
                exportDirectory = state.importExport.exportDirectory.ifBlank { "atlases" },
                exportBaseName = state.importExport.exportBaseName,
                overwrite = state.importExport.exportOverwrite,
                plan = plan,
            )
        state.importExport.lastExportResult = result
        state.statusMessage = result.message
    }

    fun showImportTextureHelp() {
        state.statusMessage = "Use Tools -> Texture Import to choose the source path and import target before writing files."
        engine.logger.info(TAG) { "Texture Manager toolbar import help requested" }
    }

    companion object {
        private const val TAG = "TextureManagerImportExportOps"
    }
}
