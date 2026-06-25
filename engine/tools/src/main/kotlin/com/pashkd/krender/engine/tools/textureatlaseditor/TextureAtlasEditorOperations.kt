package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.assets.importing.NoOpFileDialogService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

/**
 * Facade used by ImGui panels to mutate Texture Atlas Editor state.
 *
 * Feature-specific behavior lives in smaller operation classes. Keeping the UI
 * on this facade avoids broad panel churn while preventing packing, font,
 * resource, and Nine-patch workflows from growing into one class again.
 */
class TextureAtlasEditorOperations(
    private val state: TextureAtlasEditorState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    packingPlanner: TextureAtlasPackingPlanner = TextureAtlasPackingPlanner(),
    textureMetadataService: TextureMetadataService = TextureMetadataService(),
    fileDialogService: FileDialogService = NoOpFileDialogService,
    atlasSaveService: TextureAtlasSaveService = NoOpTextureAtlasSaveService,
) {
    private val selectionCoordinator = TextureAtlasEditorSelectionCoordinator(state)
    private val fontOperations =
        TextureAtlasBitmapFontOperations(
            state = state,
            engine = engine,
            selectResource = ::selectResource,
        )
    private val importExportOperations =
        TextureAtlasEditorImportExportOperations(
            state = state,
            engine = engine,
            savePackedFontDescriptors = fontOperations::savePackedFontDescriptors,
            fileDialogService = fileDialogService,
            atlasSaveService = atlasSaveService,
            openPath = ::openPath,
        )
    private val packingOperations =
        TextureAtlasPackingOperations(
            state = state,
            engine = engine,
            packingPlanner = packingPlanner,
            textureMetadataService = textureMetadataService,
        )
    private val resourceOperations =
        TextureAtlasResourceOperations(
            state = state,
            engine = engine,
            selectionCoordinator = selectionCoordinator,
            importTexture = ::importTexture,
            selectResource = ::selectResource,
            textureMetadataService = textureMetadataService,
        )
    private val ninePatchOperations = TextureAtlasNinePatchOperations(state, engine)

    fun openPath(path: String) {
        val normalized = path.trim().replace('\\', '/').ifBlank { null }
        if (normalized != state.currentInputPath && state.hasUnsavedChanges()) {
            state.pendingPathInput = normalized.orEmpty()
            state.statusMessage = "Unsaved atlas changes are still in progress. Save or reset the current draft before opening another atlas."
            engine.logger.warn(TAG) {
                "Texture Atlas Editor blocked openPath old='${state.currentInputPath ?: "<none>"}' new='${normalized ?: "<none>"}' because unsaved changes are present"
            }
            return
        }
        if (normalized != state.currentInputPath) {
            state.clearPreviewSelection()
            state.resources = TextureAtlasResourceState()
            state.packing.lastResult = TextureAtlasPackingResult()
            state.packing.selectedPageIndex = 0
            state.packing.selectedRegionId = null
            engine.logger.info(TAG) {
                "Texture Atlas Editor input path changed old='${state.currentInputPath ?: "<none>"}' new='${normalized ?: "<none>"}'; selection reset"
            }
        }
        state.currentInputPath = normalized
        state.pendingPathInput = normalized.orEmpty()
        if (normalized?.endsWith(".atlas", ignoreCase = true) == true) {
            state.importExport.targetPath = normalized
        }
        state.preview.showPackedAtlasPreview = false
        state.reloadRequested = true
        engine.logger.info(TAG) { "Texture Atlas Editor openPath path='${normalized ?: "<none>"}'" }
    }

    fun reload() {
        if (state.hasUnsavedChanges()) {
            state.statusMessage = "Unsaved atlas changes are still in progress. Save or reset the current draft before reloading."
            engine.logger.warn(TAG) { "Texture Atlas Editor blocked reload because unsaved changes are present" }
            return
        }
        state.reloadRequested = true
        state.statusMessage = "Reload requested."
    }

    fun selectAsset(assetId: TextureAssetId) {
        val asset = state.project.assets.firstOrNull { it.id == assetId } ?: return
        state.selectedAssetId = assetId
        state.hoveredRegionId = null
        when (asset.kind) {
            TextureAtlasEditorAssetKind.Texture -> {
                state.selectedAtlasPageName = null
                state.selectedRegionId = null
                state.resources.selectedResourceId =
                    state.resources.items
                        .firstOrNull { resource -> resource.sourcePathOrNull() == asset.path }
                        ?.id
            }
            TextureAtlasEditorAssetKind.Atlas -> {
                val atlas = state.project.atlasDocuments[asset.path]
                state.selectedAtlasPageName = atlas?.pages?.firstOrNull()?.name
                state.selectedRegionId = atlas?.regions?.firstOrNull()?.id
                selectionCoordinator.syncSelectedResourceFromRegion(state.selectedRegionId)
                state.importExport.targetPath = asset.path
            }
            else -> Unit
        }
        selectionCoordinator.syncSelectedPackingFromCurrentSelection()
        state.statusMessage = "Selected ${asset.kind.name.lowercase()} '${asset.displayName}'."
        engine.logger.info(TAG) { "Texture Atlas Editor selected asset id='${assetId.value}' kind=${asset.kind}" }
    }

    fun selectAtlasPage(pageName: String) {
        state.selectedAtlasPageName = pageName
        val atlas = state.selectedAtlasDocument()
        state.selectedRegionId =
            atlas
                ?.regions
                ?.firstOrNull { region -> region.id.pageName == pageName }
                ?.id
        selectionCoordinator.syncSelectedResourceFromRegion(state.selectedRegionId)
        selectionCoordinator.syncSelectedPackingFromCurrentSelection()
        state.statusMessage = "Selected atlas page '$pageName'."
        engine.logger.info(TAG) { "Texture Atlas Editor selected atlas page='$pageName'" }
    }

    fun selectRegion(regionId: AtlasRegionId?) {
        state.selectedRegionId = regionId
        selectionCoordinator.syncSelectedResourceFromRegion(regionId)
        if (regionId != null) {
            state.selectedAtlasPageName = regionId.pageName
            selectionCoordinator.syncSelectedPackingFromCurrentSelection()
            state.statusMessage = "Selected region '${regionId.regionName}'."
            engine.logger.info(TAG) { "Texture Atlas Editor selected region='${regionId.regionName}' page='${regionId.pageName}'" }
        } else {
            selectionCoordinator.syncSelectedPackingFromCurrentSelection()
            state.statusMessage = "Region selection cleared."
        }
    }

    fun selectResource(resourceId: String?) {
        state.resources.selectedResourceId = resourceId
        val resource = state.selectedResource()
        if (resource !is FontAtlasResource) {
            state.fontPreview.selectedFontResourceId = null
        }
        when (resource) {
            is ImageAtlasResource -> {
                state.selectedAtlasPageName = resource.atlasRegionId?.pageName ?: state.selectedAtlasPageName
                state.selectedRegionId = resource.atlasRegionId
            }
            is NinePatchAtlasResource -> {
                state.selectedAtlasPageName = resource.atlasRegionId?.pageName ?: state.selectedAtlasPageName
                state.selectedRegionId = resource.atlasRegionId
            }
            is FontAtlasResource -> {
                state.fontPreview.selectedFontResourceId = resource.id
                val pageCount =
                    state.project.fontDocuments[resource.documentPath]
                        ?.pages
                        ?.size ?: 0
                state.fontPreview.selectedPageIndex =
                    state.fontPreview.selectedPageIndex
                        .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                state.fontPreview.selectedGlyphId = null
                state.selectedAtlasPageName = resource.atlasRegionId?.pageName ?: state.selectedAtlasPageName
                state.selectedRegionId = resource.atlasRegionId
            }
            else -> {
                state.selectedRegionId = null
            }
        }
        state.importExport.exportResourcePath = resourceOperations.suggestedExportResourcePath(resource)
        selectionCoordinator.syncSelectedPackingFromCurrentSelection()
        if (resource is NinePatchAtlasResource) {
            beginNinePatchEditing(resource.id)
        } else if (state.ninePatchEditor.selectedResourceId != null) {
            state.ninePatchEditor = NinePatchEditorState()
        }
        if (resource != null) {
            state.statusMessage = "Selected ${resource.type.name.lowercase()} resource '${resource.name}'."
            engine.logger.info(TAG) { "Texture Atlas Editor selected resource id='${resource.id}' type=${resource.type}" }
        } else {
            state.statusMessage = "Resource selection cleared."
        }
    }

    fun setHoveredRegion(regionId: AtlasRegionId?) {
        if (state.hoveredRegionId == regionId) return
        state.hoveredRegionId = regionId
    }

    fun setCanvasMode(mode: TextureAtlasCanvasMode) {
        if (mode == TextureAtlasCanvasMode.FontPreview && state.selectedResource() !is FontAtlasResource) {
            state.resources.items
                .firstOrNull { resource -> resource is FontAtlasResource }
                ?.let { resource ->
                    selectResource(resource.id)
                    state.statusMessage = "Previewing font page and glyph bounds."
                    return
                }
        }
        if (state.preview.canvasMode == mode) return
        state.preview.canvasMode = mode
        if (mode != TextureAtlasCanvasMode.TextureAtlas) {
            state.preview.showPackedAtlasPreview = false
        }
        state.statusMessage =
            when (mode) {
                TextureAtlasCanvasMode.TextureAtlas -> "Previewing atlas file pages and regions."
                TextureAtlasCanvasMode.NinePatch -> "Previewing Nine-patch resources."
                TextureAtlasCanvasMode.FontPreview -> "Previewing font page and glyph bounds."
                TextureAtlasCanvasMode.FinalPackedAtlas -> "Previewing the current packed atlas plan."
            }
    }

    fun setPreviewSurfaceMode(mode: TexturePreviewSurfaceMode) {
        if (state.preview.surfaceMode == mode) return
        state.preview.surfaceMode = mode
        state.statusMessage =
            "Canvas viewport mode set to ${
                when (mode) {
                    TexturePreviewSurfaceMode.Actual -> "Actual"
                    TexturePreviewSurfaceMode.Padding -> "Padding"
                    TexturePreviewSurfaceMode.Custom -> "Custom"
                }
            }."
    }

    fun setCustomCanvasWidth(value: Int) {
        state.preview.customCanvasWidth = value.coerceIn(TextureAtlasMinCanvasDimensionPixels, TextureAtlasMaxCanvasDimensionPixels)
    }

    fun setCustomCanvasHeight(value: Int) {
        state.preview.customCanvasHeight = value.coerceIn(TextureAtlasMinCanvasDimensionPixels, TextureAtlasMaxCanvasDimensionPixels)
    }

    fun setShowPackedAtlasPreview(enabled: Boolean) {
        if (state.preview.showPackedAtlasPreview == enabled) return
        state.preview.showPackedAtlasPreview = enabled
        state.statusMessage =
            if (enabled) {
                "Previewing the current packed atlas plan."
            } else {
                "Previewing atlas file pages and regions."
            }
    }

    fun setZoomMode(mode: TexturePreviewZoomMode) {
        state.preview.zoomMode = mode
        when (mode) {
            TexturePreviewZoomMode.Fit -> fitPreview()
            TexturePreviewZoomMode.Percent50 -> setPreviewZoom(0.5f, updateMode = false)
            TexturePreviewZoomMode.Percent100 -> setPreviewZoom(1f, updateMode = false)
            TexturePreviewZoomMode.Percent200 -> setPreviewZoom(2f, updateMode = false)
            TexturePreviewZoomMode.Custom -> state.statusMessage = "Preview zoom set to Custom."
        }
    }

    fun setPreviewZoom(
        value: Float,
        updateMode: Boolean = true,
    ) {
        state.preview.customZoom = value.coerceIn(TextureAtlasMinPreviewZoom, TextureAtlasMaxPreviewZoom)
        state.preview.viewport.zoom = state.preview.customZoom
        if (updateMode) {
            state.preview.zoomMode = TexturePreviewZoomMode.Custom
        }
        state.statusMessage = "Preview zoom set to ${(state.preview.customZoom * 100f).toInt()}%."
    }

    /**
     * Centers and zooms the preview around the selected atlas region when the
     * canvas and preview dimensions are available.
     */
    fun fitSelectedRegion() {
        if (state.isShowingPackedAtlasPreview() || state.preview.canvasMode == TextureAtlasCanvasMode.FinalPackedAtlas) {
            packingOperations.fitSelectedPackedRegion()
            return
        }
        val regionId = state.selectedRegionId
        val atlas = state.selectedAtlasDocument()
        val region = atlas?.regions?.firstOrNull { candidate -> candidate.id == regionId }
        if (region == null || region.xy == null || region.size == null) {
            state.statusMessage = "Select a region with valid bounds to focus it."
            return
        }
        val canvas = state.canvasRect
        val textureWidth = state.previewInfo.textureWidth
        val textureHeight = state.previewInfo.textureHeight
        if (!canvas.isValid || textureWidth <= 0 || textureHeight <= 0) {
            engine.logger.warn(TAG) {
                "Texture Atlas Editor fitSelectedRegion ignored region='${region.id.regionName}' because canvas or preview dimensions were unavailable"
            }
            state.statusMessage = "Preview must be visible before focusing a region."
            return
        }

        val regionWidth = region.size.first.coerceAtLeast(1)
        val regionHeight = region.size.second.coerceAtLeast(1)
        val zoom =
            minOf(
                canvas.width / regionWidth.toFloat(),
                canvas.height / regionHeight.toFloat(),
            ).times(0.9f).coerceIn(TextureAtlasMinPreviewZoom, TextureAtlasMaxPreviewZoom)

        val imageWidth = textureWidth * zoom
        val imageHeight = textureHeight * zoom
        val baseImageX = canvas.x + (canvas.width - imageWidth) * 0.5f
        val baseImageY = canvas.y + (canvas.height - imageHeight) * 0.5f
        val regionCenterX = region.xy.first + regionWidth * 0.5f
        val regionCenterY = region.xy.second + regionHeight * 0.5f
        val desiredCenterX = canvas.x + canvas.width * 0.5f
        val desiredCenterY = canvas.y + canvas.height * 0.5f

        state.preview.customZoom = zoom
        state.preview.viewport.zoom = zoom
        state.preview.zoomMode = TexturePreviewZoomMode.Custom
        state.preview.viewport.panX = desiredCenterX - (baseImageX + regionCenterX * zoom)
        state.preview.viewport.panY = desiredCenterY - (baseImageY + regionCenterY * zoom)
        state.statusMessage = "Focused region '${region.id.regionName}'."
        engine.logger.info(TAG) {
            "Texture Atlas Editor fit selected region='${region.id.regionName}' page='${region.id.pageName}' zoom=$zoom"
        }
    }

    fun panPreview(
        deltaX: Float,
        deltaY: Float,
    ) {
        state.preview.viewport.panX += deltaX
        state.preview.viewport.panY += deltaY
    }

    fun resetPreviewCamera() {
        state.preview.viewport.panX = 0f
        state.preview.viewport.panY = 0f
        state.preview.viewport.zoom = 1f
        state.preview.customZoom = 1f
        if (state.preview.zoomMode == TexturePreviewZoomMode.Custom) {
            state.preview.zoomMode = TexturePreviewZoomMode.Percent100
        }
        state.statusMessage = "Preview camera reset."
    }

    fun fitPreview() {
        state.preview.viewport.panX = 0f
        state.preview.viewport.panY = 0f
        state.preview.zoomMode = TexturePreviewZoomMode.Fit
        state.statusMessage = "Preview fit to canvas."
    }

    fun setShowCheckerboard(enabled: Boolean) {
        state.preview.showCheckerboard = enabled
        state.statusMessage = if (enabled) "Checkerboard enabled." else "Checkerboard hidden."
    }

    fun setShowGrid(enabled: Boolean) {
        state.preview.showGrid = enabled
        state.statusMessage = if (enabled) "Grid enabled." else "Grid hidden."
    }

    fun setGridColor(
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        state.preview.gridColor = TextureAtlasEditorColor(red, green, blue, alpha)
    }

    fun setGridSpacingPixels(value: Int) {
        state.preview.gridSpacingPixels = value.coerceIn(TextureAtlasMinGridSpacingPixels, TextureAtlasMaxGridSpacingPixels)
    }

    fun setShowBounds(enabled: Boolean) {
        state.preview.showBounds = enabled
        state.statusMessage = if (enabled) "Bounds enabled." else "Bounds hidden."
    }

    fun setShowNinePatchGuides(enabled: Boolean) {
        state.preview.showNinePatchGuides = enabled
        state.statusMessage = if (enabled) "Nine-patch guides enabled." else "Nine-patch guides hidden."
    }

    fun setNinePatchPreviewType(type: TextureAtlasNinePatchPreviewType) {
        if (state.preview.ninePatchStretch.previewType == type) return
        state.preview.ninePatchStretch.previewType = type
        state.statusMessage =
            when (type) {
                TextureAtlasNinePatchPreviewType.Source -> "Previewing the NinePatch source texture."
                TextureAtlasNinePatchPreviewType.StretchTest -> "Previewing the stretched NinePatch result."
            }
    }

    fun setNinePatchStretchPreset(preset: TextureAtlasNinePatchStretchPreset) {
        state.preview.ninePatchStretch.preset = preset
        state.statusMessage = "NinePatch stretch preset set to ${preset.name}."
    }

    fun setNinePatchStretchTargetWidth(value: Int) {
        state.preview.ninePatchStretch.targetWidth = value.coerceIn(TextureAtlasMinCanvasDimensionPixels, TextureAtlasMaxCanvasDimensionPixels)
        state.preview.ninePatchStretch.preset = TextureAtlasNinePatchStretchPreset.Custom
    }

    fun setNinePatchStretchTargetHeight(value: Int) {
        state.preview.ninePatchStretch.targetHeight = value.coerceIn(TextureAtlasMinCanvasDimensionPixels, TextureAtlasMaxCanvasDimensionPixels)
        state.preview.ninePatchStretch.preset = TextureAtlasNinePatchStretchPreset.Custom
    }

    fun setShowNinePatchStretchSourceGuides(enabled: Boolean) {
        state.preview.ninePatchStretch.showSourceGuides = enabled
    }

    fun setShowNinePatchStretchDestinationSlices(enabled: Boolean) {
        state.preview.ninePatchStretch.showDestinationSlices = enabled
    }

    fun setShowNinePatchStretchPaddingRect(enabled: Boolean) {
        state.preview.ninePatchStretch.showPaddingRect = enabled
    }

    fun setPackingMaxPageWidth(value: Int) = packingOperations.setPackingMaxPageWidth(value)

    fun setPackingMaxPageHeight(value: Int) = packingOperations.setPackingMaxPageHeight(value)

    fun setPackingPadding(value: Int) = packingOperations.setPackingPadding(value)

    fun setPackingAllowRotation(enabled: Boolean) = packingOperations.setPackingAllowRotation(enabled)

    fun setPackingIncludeNinePatch(enabled: Boolean) = packingOperations.setPackingIncludeNinePatch(enabled)

    fun addImageResourceFromPath(path: String = state.importExport.importSourcePath) = resourceOperations.addImageResourceFromPath(path)

    fun importAndAddImageResource() = resourceOperations.importAndAddImageResource()

    fun deleteSelectedResource() = resourceOperations.deleteSelectedResource()

    fun renameSelectedResource(name: String) = resourceOperations.renameSelectedResource(name)

    fun exportSelectedResourcePng() = resourceOperations.exportSelectedResourcePng()

    fun createNinePatchFromSelectedResource() = resourceOperations.createNinePatchFromSelectedResource()

    fun createBitmapFontPlaceholder() = resourceOperations.createBitmapFontPlaceholder()

    fun setImportSourcePath(path: String) {
        state.importExport.importSourcePath = path
    }

    fun setFontSourcePath(path: String) {
        state.importExport.fontSourcePath = path
    }

    fun setTargetPath(path: String) {
        state.importExport.targetPath = path
    }

    fun setExportResourcePath(path: String) {
        state.importExport.exportResourcePath = path
    }

    fun setImportOverwrite(enabled: Boolean) {
        state.importExport.importOverwrite = enabled
    }

    fun setSaveOverwrite(enabled: Boolean) {
        state.importExport.saveOverwrite = enabled
    }

    fun packTextureAtlas() = packingOperations.packTextureAtlas()

    fun runPackingDryRun() = packTextureAtlas()

    fun selectPackingPage(index: Int) = packingOperations.selectPackingPage(index)

    fun selectPackingRegion(regionId: String?) = packingOperations.selectPackingRegion(regionId)

    fun importTexture() {
        importExportOperations.importTexture()
    }

    fun savePackedAtlas() {
        importExportOperations.savePackedAtlas()
    }

    fun browseImportTexture() {
        importExportOperations.browseImportTexture()
    }

    fun browseRegionTextureSource() {
        importExportOperations.browseTextureSourceOnly()
    }

    fun browseFontDescriptor() {
        importExportOperations.browseFontDescriptor()
    }

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(TextureAtlasEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(TextureAtlasEditorUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }

    fun requestExit() {
        if (state.hasUnsavedChanges()) {
            state.statusMessage = "Unsaved atlas changes are still in progress. Save or reset the current draft before exiting."
            engine.logger.warn(TAG) { "Texture Atlas Editor blocked exit because unsaved changes are present" }
            return
        }
        engine.requestExit()
    }

    fun addFontResourceFromPath(fntPath: String) = fontOperations.addFontResourceFromPath(fntPath)

    fun importFontResourceFromPath(path: String = state.importExport.fontSourcePath) = fontOperations.importFontResourceFromPath(path)

    fun selectFontGlyph(glyphId: Int?) = fontOperations.selectFontGlyph(glyphId)

    fun setFontPreviewPage(pageIndex: Int) = fontOperations.setFontPreviewPage(pageIndex)

    fun setFontPreviewTint(
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) = fontOperations.setFontPreviewTint(red, green, blue, alpha)

    fun setFontSampleText(text: String) = fontOperations.setFontSampleText(text)

    fun setFontSampleTextPreviewEnabled(enabled: Boolean) = fontOperations.setFontSampleTextPreviewEnabled(enabled)

    fun setFontGlyphFilter(filter: String) = fontOperations.setFontGlyphFilter(filter)

    fun exportBitmapFont() = fontOperations.exportBitmapFont()

    fun setPackFontInAtlas(enabled: Boolean) = fontOperations.setPackFontInAtlas(enabled)

    fun beginNinePatchEditing(resourceId: String) = ninePatchOperations.beginNinePatchEditing(resourceId)

    fun updateNinePatchStretchX(
        start: Int,
        length: Int,
    ) = ninePatchOperations.updateNinePatchStretchX(start, length)

    fun updateNinePatchStretchY(
        start: Int,
        length: Int,
    ) = ninePatchOperations.updateNinePatchStretchY(start, length)

    fun updateNinePatchPaddingX(
        start: Int?,
        length: Int?,
    ) = ninePatchOperations.updateNinePatchPaddingX(start, length)

    fun updateNinePatchPaddingY(
        start: Int?,
        length: Int?,
    ) = ninePatchOperations.updateNinePatchPaddingY(start, length)

    fun clearNinePatchPadding() = ninePatchOperations.clearNinePatchPadding()

    fun useFullNinePatchStretch() = ninePatchOperations.useFullNinePatchStretch()

    fun resetNinePatchDraft() = ninePatchOperations.resetNinePatchDraft()

    fun applyNinePatchDraft() = ninePatchOperations.applyNinePatchDraft()

    companion object {
        private const val TAG = "TextureAtlasEditorOps"
    }
}
