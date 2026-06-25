package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File

internal const val TextureAtlasMinPreviewZoom = 0.05f
internal const val TextureAtlasMaxPreviewZoom = 30f
internal const val TextureAtlasMinGridSpacingPixels = 1
internal const val TextureAtlasMaxGridSpacingPixels = 512
internal const val TextureAtlasMinCanvasDimensionPixels = 1
internal const val TextureAtlasMaxCanvasDimensionPixels = 8192

internal fun TextureAtlasEditorState.isShowingPackedAtlasPreview(): Boolean =
    preview.canvasMode == TextureAtlasCanvasMode.TextureAtlas && preview.showPackedAtlasPreview

internal fun TextureAtlasEditorState.hasUnappliedNinePatchDraft(): Boolean = ninePatchEditor.dirty

internal fun TextureAtlasEditorState.hasUnsavedChanges(): Boolean = dirty || hasUnappliedNinePatchDraft()

internal fun TextureAtlasEditorState.selectedAsset(): TextureAtlasEditorAssetDescriptor? = project.assets.firstOrNull { it.id == selectedAssetId }

internal fun TextureAtlasEditorState.selectedResource(): TextureAtlasResource? =
    resources.items.firstOrNull { resource -> resource.id == resources.selectedResourceId }

internal fun TextureAtlasEditorState.selectedAtlasDocument(): TextureAtlasDocument? =
    selectedAsset()
        ?.takeIf { it.kind == TextureAtlasEditorAssetKind.Atlas }
        ?.let { asset -> project.atlasDocuments[asset.path] }

internal fun TextureAtlasEditorState.selectedNinePatchDocument(): NinePatchDocument? =
    selectedResource()
        ?.sourcePathOrNull()
        ?.let { previewPath -> project.ninePatchDocuments[previewPath] }
        ?: selectedPreviewTexturePath()?.let { previewPath -> project.ninePatchDocuments[previewPath] }

internal fun TextureAtlasEditorState.selectedFontDocument(): BitmapFontDocument? {
    val resource = selectedResource() as? FontAtlasResource ?: return null
    return project.fontDocuments[resource.documentPath]
}

internal fun TextureAtlasEditorState.selectedFontPageTexturePath(): String? {
    val resource = selectedResource() as? FontAtlasResource ?: return null
    val document = project.fontDocuments[resource.documentPath] ?: return resource.pageTexturePaths.firstOrNull()
    val selectedPage = document.pages.getOrNull(fontPreview.selectedPageIndex) ?: document.pages.firstOrNull()
    return selectedPage?.resolvedPath ?: resource.pageTexturePaths.getOrNull(fontPreview.selectedPageIndex) ?: resource.pageTexturePaths.firstOrNull()
}

internal fun TextureAtlasEditorState.selectedAtlasNinePatchRegion(): TextureAtlasRegion? =
    selectedAtlasDocument()
        ?.regions
        ?.firstOrNull { region ->
            region.id == (selectedResource()?.atlasRegionIdOrNull() ?: selectedRegionId) && (region.split.isNotEmpty() || region.pad.isNotEmpty())
        }

internal fun TextureAtlasEditorState.selectedRegionsForPage(): List<TextureAtlasRegion> =
    selectedAtlasDocument()
        ?.regions
        ?.filter { region -> selectedAtlasPageName == null || region.id.pageName == selectedAtlasPageName }
        .orEmpty()

internal fun TextureAtlasEditorState.selectedPackingPlan(): TextureAtlasPackingPlan? = packing.lastResult.plan

internal fun TextureAtlasEditorState.selectedPackingPage(): TextureAtlasPackingPage? = packing.lastResult.plan?.pages?.getOrNull(packing.selectedPageIndex)

internal fun TextureAtlasEditorState.selectedPackingRegion(): TextureAtlasPackingRegion? =
    selectedPackingPage()
        ?.regions
        ?.firstOrNull { region -> region.id == packing.selectedRegionId }

internal fun TextureAtlasEditorState.selectedPreviewSlice(): TextureAtlasEditorPreviewSlice? {
    return when (preview.canvasMode) {
        TextureAtlasCanvasMode.NinePatch -> {
            val resource = selectedResource() as? NinePatchAtlasResource ?: return null
            val width = resource.sourceWidth ?: selectedNinePatchDocument()?.contentWidth ?: return null
            val height = resource.sourceHeight ?: selectedNinePatchDocument()?.contentHeight ?: return null
            if (width <= 0 || height <= 0) return null
            TextureAtlasEditorPreviewSlice(
                sourceX = resource.sourceX,
                sourceY = resource.sourceY,
                width = width,
                height = height,
            )
        }
        TextureAtlasCanvasMode.FontPreview -> {
            null
        }
        else -> null
    }
}

internal fun TextureAtlasEditorState.selectedPreviewTexturePath(): String? {
    when (preview.canvasMode) {
        TextureAtlasCanvasMode.FinalPackedAtlas -> return null
        TextureAtlasCanvasMode.FontPreview -> {
            val fontResource = selectedResource() as? FontAtlasResource
            if (fontResource != null) {
                selectedFontPageTexturePath()?.let { return it }
                fontResource.atlasTexturePath?.let { return it }
            }
            return null
        }
        TextureAtlasCanvasMode.NinePatch -> {
            selectedResource()?.sourcePathOrNull()?.let { return it }
        }
        TextureAtlasCanvasMode.TextureAtlas -> Unit
    }
    if (isShowingPackedAtlasPreview()) return null
    val asset = selectedAsset()
    if (asset == null) {
        project.selectedTexturePath?.let { return it }
        val atlasPath = project.selectedAtlasPath ?: return null
        return resolveAtlasPreviewTexturePath(
            atlasPath = atlasPath,
            atlas = project.atlasDocuments[atlasPath],
            selectedPageName = selectedAtlasPageName,
        )
    }
    return when (asset.kind) {
        TextureAtlasEditorAssetKind.Texture -> asset.path
        TextureAtlasEditorAssetKind.Atlas -> resolveAtlasPreviewTexturePath(asset.path, project.atlasDocuments[asset.path], selectedAtlasPageName)
        else -> null
    }
}

internal fun TextureAtlasResource.atlasRegionIdOrNull(): AtlasRegionId? =
    when (this) {
        is ImageAtlasResource -> atlasRegionId
        is NinePatchAtlasResource -> atlasRegionId
        is FontAtlasResource -> atlasRegionId
        else -> null
    }

internal fun TextureAtlasResource.sourcePathOrNull(): String? =
    when (this) {
        is ImageAtlasResource -> sourcePath
        is NinePatchAtlasResource -> sourcePath
        is FontAtlasResource -> sourcePath
    }

internal fun NinePatchDocument.splitInts(): List<Int> =
    guideSegmentsToAtlasInsets(stretchX.firstOrNull(), stretchY.firstOrNull(), contentWidth, contentHeight)

internal fun NinePatchDocument.padInts(): List<Int> =
    guideSegmentsToAtlasInsets(paddingX, paddingY, contentWidth, contentHeight)

private fun guideSegmentsToAtlasInsets(
    horizontal: NinePatchSegment?,
    vertical: NinePatchSegment?,
    contentWidth: Int,
    contentHeight: Int,
): List<Int> {
    val horizontalSegment = horizontal ?: return emptyList()
    val verticalSegment = vertical ?: return emptyList()
    return listOf(
        horizontalSegment.start,
        contentWidth - (horizontalSegment.start + horizontalSegment.length),
        verticalSegment.start,
        contentHeight - (verticalSegment.start + verticalSegment.length),
    )
}

internal fun isNinePatchTexturePathLocal(path: String): Boolean = path.endsWith(".9.png", ignoreCase = true)

internal fun resolveAtlasPreviewTexturePath(
    atlasPath: String,
    atlas: TextureAtlasDocument?,
    selectedPageName: String?,
): String? {
    val pageName = selectedPageName ?: atlas?.pages?.firstOrNull()?.name ?: return null
    val atlasFile = File(atlasPath)
    val pageFile = File(pageName)
    if (pageFile.isAbsolute) {
        return normalizePath(pageFile.path)
    }
    val parent = atlasFile.parentFile ?: return null
    return normalizePath(File(parent, pageName).path)
}
