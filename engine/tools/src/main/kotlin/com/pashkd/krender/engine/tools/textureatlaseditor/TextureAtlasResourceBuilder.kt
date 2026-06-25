package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File

class TextureAtlasResourceBuilder {
    fun rebuild(
        project: TextureAtlasEditorProject,
        previousResources: TextureAtlasResourceState,
    ): TextureAtlasResourceState {
        val atlasPath = project.selectedAtlasPath
        val atlasDocument = atlasPath?.let { path -> project.atlasDocuments[path] }
        val selectedAtlasDirectory = atlasPath?.substringBeforeLast('/', "")
        val carryOverResources =
            previousResources.items.filter { resource ->
                resource !is FontAtlasResource &&
                    resource.atlasRegionIdOrNull() == null &&
                    resource.sourcePathOrNull()?.let { path -> File(path).isFile } != false
            }
        val atlasResources =
            atlasDocument
                ?.regions
                ?.mapIndexedNotNull { index, region ->
                    val sourcePath =
                        resolveAtlasPreviewTexturePath(
                            atlasPath = region.id.atlasPath,
                            atlas = atlasDocument,
                            selectedPageName = region.id.pageName,
                        ) ?: return@mapIndexedNotNull null
                    val size = region.size
                    val xy = region.xy
                    if (size == null || xy == null) return@mapIndexedNotNull null
                    val resourceId = "resource:${region.id.atlasPath}:${region.id.pageName}:${region.id.regionName}:${region.index ?: index}"
                    if (region.split.isNotEmpty() || region.pad.isNotEmpty()) {
                        NinePatchAtlasResource(
                            id = resourceId,
                            name = region.id.regionName,
                            sourcePath = sourcePath,
                            sourceX = xy.first,
                            sourceY = xy.second,
                            sourceWidth = size.first,
                            sourceHeight = size.second,
                            split = region.split,
                            pad = region.pad,
                            atlasRegionId = region.id,
                            atlasIndex = region.index,
                        )
                    } else {
                        ImageAtlasResource(
                            id = resourceId,
                            name = region.id.regionName,
                            sourcePath = sourcePath,
                            sourceX = xy.first,
                            sourceY = xy.second,
                            sourceWidth = size.first,
                            sourceHeight = size.second,
                            atlasRegionId = region.id,
                            atlasIndex = region.index,
                        )
                    }
                }.orEmpty()
        val fontResources =
            project.fontDocuments.entries
                .asSequence()
                .filter { (path, _) ->
                    val fontDirectory = path.substringBeforeLast('/', "")
                    selectedAtlasDirectory != null && fontDirectory == selectedAtlasDirectory
                }.sortedBy { (path, _) -> File(path).nameWithoutExtension.lowercase() }
                .map { (path, document) -> createFontAtlasResource(path, document) }
                .toList()
        val rebuiltItems = atlasResources + fontResources + carryOverResources
        val selectedResourceId =
            previousResources.selectedResourceId?.takeIf { selectedId ->
                rebuiltItems.any { resource -> resource.id == selectedId }
            } ?: rebuiltItems.firstOrNull()?.id
        return TextureAtlasResourceState(
            items = rebuiltItems,
            selectedResourceId = selectedResourceId,
        )
    }
}
