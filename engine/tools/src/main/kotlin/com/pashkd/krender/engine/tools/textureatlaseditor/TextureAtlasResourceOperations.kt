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
    private val regionExportService: TextureAtlasRegionExportService = TextureAtlasRegionExportService(),
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

    fun exportSelectedResourcePng() {
        val resource = state.selectedResource()
        if (resource == null) {
            state.statusMessage = "Select a resource before exporting it."
            return
        }
        val atlasFile = state.selectedAtlasDocument()?.file
        val result =
            regionExportService.exportResource(
                assetRoot = engine.assetRegistry.baseDir(),
                atlasFile = atlasFile,
                resource = resource,
                targetPath = state.importExport.exportResourcePath,
            )
        state.importExport.lastExportResult = result
        state.statusMessage = result.message
        if (result.success) {
            state.importExport.exportResourcePath = result.writtenPaths.firstOrNull().orEmpty()
            engine.logger.info(TAG) {
                "Texture Atlas Editor exported resource id='${resource.id}' paths=${result.writtenPaths.joinToString()}"
            }
        } else {
            engine.logger.warn(TAG) { "Texture Atlas Editor failed to export resource id='${resource.id}': ${result.message}" }
        }
    }

    fun createNinePatchFromSelectedResource() {
        val resource = state.selectedResource()
        if (resource == null) {
            state.statusMessage = "Select an image resource before creating a Nine-patch."
            return
        }
        if (resource is NinePatchAtlasResource) {
            state.statusMessage = "Selected resource is already a Nine-patch."
            return
        }
        if (resource !is ImageAtlasResource) {
            state.statusMessage = "Nine-patch creation currently supports image resources and atlas texture regions only."
            return
        }
        val width = resource.sourceWidth ?: textureMetadataService.read(File(resource.sourcePath))?.width
        val height = resource.sourceHeight ?: textureMetadataService.read(File(resource.sourcePath))?.height
        if (width == null || height == null || width <= 0 || height <= 0) {
            state.statusMessage = "Cannot create a Nine-patch because the image size is unknown."
            return
        }
        val ninePatch =
            NinePatchAtlasResource(
                id = resource.id,
                name = resource.name,
                sourcePath = resource.sourcePath,
                sourceX = resource.sourceX,
                sourceY = resource.sourceY,
                sourceWidth = width,
                sourceHeight = height,
                split = listOf(0, 0, 0, 0),
                pad = emptyList(),
                atlasRegionId = resource.atlasRegionId,
                atlasIndex = resource.atlasIndex,
            )
        replaceResource(ninePatch)
        selectResource(ninePatch.id)
        state.statusMessage = "Created Nine-patch resource '${ninePatch.name}'. Adjust split and padding in the inspector."
        engine.logger.info(TAG) { "Texture Atlas Editor converted image resource id='${resource.id}' into NinePatch" }
    }

    fun createBitmapFontPlaceholder() {
        state.statusMessage = "Create Bitmap Font is planned but not implemented yet."
    }

    fun suggestedExportResourcePath(resource: TextureAtlasResource? = state.selectedResource()): String {
        val selected = resource ?: return ""
        val atlasFile = state.selectedAtlasDocument()?.file
        val exportDirectory =
            atlasFile?.parentFile?.resolve("export")
                ?: selected.sourcePathOrNull()?.let(::File)?.parentFile?.resolve("export")
                ?: return ""
        val fileName =
            when (selected) {
                is NinePatchAtlasResource -> "${safeFileStem(selected.name)}.9.png"
                else -> "${safeFileStem(selected.name)}.png"
            }
        return normalizePath(File(exportDirectory, fileName).path)
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

    private fun replaceResource(resource: TextureAtlasResource) {
        state.resources.items =
            state.resources.items.map { item ->
                if (item.id == resource.id) resource else item
            }
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

private fun safeFileStem(name: String): String =
    name
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .ifBlank { "region" }
