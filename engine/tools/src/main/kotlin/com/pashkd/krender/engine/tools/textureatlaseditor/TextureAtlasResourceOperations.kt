package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext
import java.io.File

internal class TextureAtlasResourceOperations(
    private val state: TextureAtlasEditorState,
    private val engine: EngineContext,
    private val selectionCoordinator: TextureAtlasEditorSelectionCoordinator,
    private val importTexture: () -> Unit,
    private val selectResource: (String?) -> Unit,
    private val textureMetadataService: TextureMetadataService = TextureMetadataService(),
) {
    fun addImageResourceFromPath(path: String = state.importExport.importSourcePath) {
        val sourceFile = resolveTextureSource(path)
        if (sourceFile == null) {
            state.statusMessage = "Choose an existing texture inside the asset root before adding a resource."
            return
        }
        val resource = createResourceFromTextureSource(sourceFile)
        appendResource(resource)
        state.importExport.importSourcePath = normalizePath(sourceFile.path)
        state.statusMessage = "Added ${resource.type.name.lowercase()} resource '${resource.name}'."
        engine.logger.info(TAG) { "Texture Atlas Editor added resource id='${resource.id}' source='${resource.sourcePathOrNull() ?: "<none>"}'" }
    }

    fun importAndAddImageResource() {
        val before = state.importExport.lastImportResult
        importTexture()
        val result = state.importExport.lastImportResult
        if (result !== before && result?.success == true) {
            result.writtenPaths.firstOrNull()?.let { path ->
                val normalized = normalizePath(path)
                val resource = createResourceFromTextureSource(File(normalized))
                appendResource(resource)
                state.importExport.importSourcePath = normalized
                state.statusMessage = "Imported and added ${resource.type.name.lowercase()} resource '${resource.name}'."
            }
        }
    }

    fun deleteSelectedResource() {
        val resource = state.selectedResource()
        if (resource == null) {
            state.statusMessage = "Select a resource before deleting it."
            return
        }
        val regionId = resource.atlasRegionIdOrNull()
        val selectedAsset = state.selectedAsset()?.takeIf { it.kind == TextureAtlasEditorAssetKind.Atlas }
        if (regionId != null && selectedAsset != null) {
            val atlas = state.selectedAtlasDocument()
            if (atlas != null) {
                val updatedRegions = atlas.regions.filterNot { region -> region.id == regionId }
                if (updatedRegions.size != atlas.regions.size) {
                    state.project =
                        state.project.copy(
                            atlasDocuments = state.project.atlasDocuments + (selectedAsset.path to atlas.copy(regions = updatedRegions)),
                        )
                }
            }
        }
        state.resources.items = state.resources.items.filterNot { item -> item.id == resource.id }
        state.resources.selectedResourceId =
            state.resources.items
                .firstOrNull()
                ?.id
        selectionCoordinator.syncSelectedRegionFromResource()
        state.statusMessage = "Deleted resource '${resource.name}' from the working atlas draft. Pack and Save to write changes."
        engine.logger.info(TAG) {
            "Texture Atlas Editor deleted resource id='${resource.id}' type=${resource.type}"
        }
    }

    private fun resolveTextureSource(path: String): File? {
        val trimmed = path.trim().replace('\\', '/')
        if (trimmed.isBlank()) return null
        val assetRoot = engine.assetRegistry.baseDir()
        val file =
            TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, trimmed)
                ?: File(trimmed).takeIf { candidate -> candidate.isAbsolute && candidate.isFile && TextureAtlasEditorPathValidator.isInsideRoot(assetRoot, candidate) }
                ?: return null
        if (!file.isFile || !isSupportedTextureImportFile(file)) return null
        return file
    }

    private fun createResourceFromTextureSource(sourceFile: File): TextureAtlasResource {
        val normalized = normalizePath(sourceFile.path)
        val ninePatch = state.project.ninePatchDocuments[normalized]
        val info = textureMetadataService.read(sourceFile)
        val baseName =
            sourceFile.name
                .removeSuffix(".9.png")
                .substringBeforeLast('.', sourceFile.nameWithoutExtension)
                .ifBlank { sourceFile.nameWithoutExtension }
        return if (ninePatch != null || isNinePatchTexturePathLocal(sourceFile.name)) {
            NinePatchAtlasResource(
                id = uniqueResourceId("resource:ninepatch:$normalized"),
                name = uniqueResourceName(baseName),
                sourcePath = normalized,
                sourceX = if (ninePatch != null) 1 else 0,
                sourceY = if (ninePatch != null) 1 else 0,
                sourceWidth = ninePatch?.contentWidth ?: info?.width,
                sourceHeight = ninePatch?.contentHeight ?: info?.height,
                split = ninePatch?.splitInts().orEmpty(),
                pad = ninePatch?.padInts().orEmpty(),
            )
        } else {
            ImageAtlasResource(
                id = uniqueResourceId("resource:image:$normalized"),
                name = uniqueResourceName(baseName),
                sourcePath = normalized,
                sourceWidth = info?.width,
                sourceHeight = info?.height,
            )
        }
    }

    private fun appendResource(resource: TextureAtlasResource) {
        state.resources.items = state.resources.items + resource
        selectResource(resource.id)
    }

    private fun uniqueResourceId(baseId: String): String {
        if (state.resources.items.none { resource -> resource.id == baseId }) return baseId
        var index = 2
        while (true) {
            val candidate = "$baseId#$index"
            if (state.resources.items.none { resource -> resource.id == candidate }) {
                return candidate
            }
            index++
        }
    }

    private fun uniqueResourceName(baseName: String): String {
        if (state.resources.items.none { resource -> resource.name == baseName }) return baseName
        var index = 2
        while (true) {
            val candidate = "$baseName $index"
            if (state.resources.items.none { resource -> resource.name == candidate }) {
                return candidate
            }
            index++
        }
    }

    companion object {
        private const val TAG = "TextureAtlasResourceOps"
    }
}
