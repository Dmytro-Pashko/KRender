package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.io.File

class TextureManagerOperations(
    private val state: TextureManagerState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val packingPlanner: TextureAtlasPackingPlanner = TextureAtlasPackingPlanner(),
    private val importService: TextureManagerImportService = TextureManagerImportService(engine.logger),
    private val descriptorExporter: TextureAtlasDescriptorExporter = TextureAtlasDescriptorExporter(engine.logger, engine.sceneFiles),
) {
    fun openPath(path: String) {
        val normalized = path.trim().replace('\\', '/').ifBlank { null }
        if (normalized != state.currentInputPath) {
            state.clearPreviewSelection()
            state.packing.lastResult = TextureAtlasPackingResult()
            state.packing.selectedPageIndex = 0
            state.packing.selectedRegionSourcePath = null
            engine.logger.info(TAG) {
                "Texture Manager input path changed old='${state.currentInputPath ?: "<none>"}' new='${normalized ?: "<none>"}'; selection reset"
            }
        }
        state.currentInputPath = normalized
        state.pendingPathInput = normalized.orEmpty()
        state.reloadRequested = true
        engine.logger.info(TAG) { "Texture Manager openPath path='${normalized ?: "<none>"}'" }
    }

    fun reload() {
        state.reloadRequested = true
        state.statusMessage = "Reload requested."
    }

    fun selectAsset(assetId: TextureAssetId) {
        val asset = state.project.assets.firstOrNull { it.id == assetId } ?: return
        state.selectedAssetId = assetId
        state.hoveredRegionId = null
        when (asset.kind) {
            TextureManagerAssetKind.Texture -> {
                state.selectedAtlasPageName = null
                state.selectedRegionId = null
            }
            TextureManagerAssetKind.Atlas -> {
                val atlas = state.project.atlasDocuments[asset.path]
                state.selectedAtlasPageName = atlas?.pages?.firstOrNull()?.name
                state.selectedRegionId = atlas?.regions?.firstOrNull()?.id
            }
            else -> Unit
        }
        state.statusMessage = "Selected ${asset.kind.name.lowercase()} '${asset.displayName}'."
        engine.logger.info(TAG) { "Texture Manager selected asset id='${assetId.value}' kind=${asset.kind}" }
    }

    fun selectAtlasPage(pageName: String) {
        state.selectedAtlasPageName = pageName
        val atlas = selectedAtlasDocument()
        state.selectedRegionId =
            atlas?.regions
                ?.firstOrNull { region -> region.id.pageName == pageName }
                ?.id
        state.statusMessage = "Selected atlas page '$pageName'."
        engine.logger.info(TAG) { "Texture Manager selected atlas page='$pageName'" }
    }

    fun selectRegion(regionId: AtlasRegionId?) {
        state.selectedRegionId = regionId
        if (regionId != null) {
            state.selectedAtlasPageName = regionId.pageName
            state.statusMessage = "Selected region '${regionId.regionName}'."
            engine.logger.info(TAG) { "Texture Manager selected region='${regionId.regionName}' page='${regionId.pageName}'" }
        } else {
            state.statusMessage = "Region selection cleared."
        }
    }

    fun setHoveredRegion(regionId: AtlasRegionId?) {
        if (state.hoveredRegionId == regionId) return
        state.hoveredRegionId = regionId
    }

    fun setToolMode(mode: TextureManagerToolMode) {
        state.toolMode = mode
        state.statusMessage = "Tool mode set to ${mode.name}."
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
        state.preview.customZoom = value.coerceIn(0.05f, 8f)
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
                "Texture Manager fitSelectedRegion ignored region='${region.id.regionName}' because canvas or preview dimensions were unavailable"
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
            ).times(0.9f).coerceIn(0.05f, 8f)

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
            "Texture Manager fit selected region='${region.id.regionName}' page='${region.id.pageName}' zoom=$zoom"
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

    fun setShowBounds(enabled: Boolean) {
        state.preview.showBounds = enabled
        state.statusMessage = if (enabled) "Bounds enabled." else "Bounds hidden."
    }

    fun setShowNinePatchGuides(enabled: Boolean) {
        state.preview.showNinePatchGuides = enabled
        state.statusMessage = if (enabled) "Nine-patch guides enabled." else "Nine-patch guides hidden."
    }

    fun setPackingMaxPageWidth(value: Int) {
        state.packing.settings.maxPageWidth = value
    }

    fun setPackingMaxPageHeight(value: Int) {
        state.packing.settings.maxPageHeight = value
    }

    fun setPackingPadding(value: Int) {
        state.packing.settings.padding = value
    }

    fun setPackingAllowRotation(enabled: Boolean) {
        state.packing.settings.allowRotation = enabled
    }

    fun setPackingIncludeNinePatch(enabled: Boolean) {
        state.packing.settings.includeNinePatch = enabled
    }

    fun setImportSourcePath(path: String) {
        state.importExport.importSourcePath = path
    }

    fun setImportTargetDirectory(path: String) {
        state.importExport.importTargetDirectory = path
    }

    fun setImportOverwrite(enabled: Boolean) {
        state.importExport.importOverwrite = enabled
    }

    fun setExportDirectory(path: String) {
        state.importExport.exportDirectory = path
    }

    fun setExportBaseName(name: String) {
        state.importExport.exportBaseName = name
    }

    fun setExportOverwrite(enabled: Boolean) {
        state.importExport.exportOverwrite = enabled
    }

    fun runPackingDryRun() {
        val settings = state.packing.settings.copy()
        val diagnostics = mutableListOf<TextureAtlasPackingDiagnostic>()
        val inputs =
            state.project.assets
                .filter { asset ->
                    asset.kind == TextureManagerAssetKind.Texture &&
                        (state.packing.includedTexturePaths.isEmpty() || asset.path in state.packing.includedTexturePaths)
                }.mapNotNull { asset ->
                    val width = asset.textureInfo?.width
                    val height = asset.textureInfo?.height
                    if (width == null || height == null) {
                        diagnostics +=
                            TextureAtlasPackingDiagnostic(
                                severity = TextureManagerDiagnosticSeverity.Warning,
                                message = "Texture dimensions are unknown and the texture was skipped.",
                                sourcePath = asset.path,
                            )
                        null
                    } else {
                        TextureAtlasPackingInput(
                            sourcePath = asset.path,
                            displayName = asset.displayName,
                            width = width,
                            height = height,
                            isNinePatch = isNinePatchTexturePath(asset.fileName),
                        )
                    }
                }
        engine.logger.info(TAG) {
            "Texture Manager packing dry-run started candidates=${inputs.size} max=${settings.maxPageWidth}x${settings.maxPageHeight} padding=${settings.padding} rotation=${settings.allowRotation} includeNinePatch=${settings.includeNinePatch}"
        }
        val result = packingPlanner.plan(inputs, settings)
        val mergedDiagnostics = diagnostics + result.diagnostics
        state.packing.lastResult = result.copy(diagnostics = mergedDiagnostics)
        state.packing.selectedPageIndex = 0
        state.packing.selectedRegionSourcePath = result.plan?.pages?.firstOrNull()?.regions?.firstOrNull()?.sourcePath
        val pages = result.plan?.pages?.size ?: 0
        val regions = result.plan?.packedRegionCount ?: 0
        val skipped = result.plan?.skippedCount ?: 0
        state.statusMessage = "Packing dry-run produced $pages page(s), $regions region(s), skipped $skipped."
        engine.logger.info(TAG) {
            "Texture Manager packing dry-run completed pages=$pages regions=$regions skipped=$skipped diagnostics=${mergedDiagnostics.size}"
        }
    }

    fun selectPackingPage(index: Int) {
        state.packing.selectedPageIndex = index
        val page = state.packing.lastResult.plan?.pages?.getOrNull(index)
        state.packing.selectedRegionSourcePath = page?.regions?.firstOrNull()?.sourcePath
        state.statusMessage = if (page != null) "Selected packing page '${page.name}'." else "Packing page selection cleared."
    }

    fun selectPackingRegion(sourcePath: String?) {
        state.packing.selectedRegionSourcePath = sourcePath
        val region = state.selectedPackingRegion()
        if (region != null) {
            state.project.assets.firstOrNull { asset -> asset.path == region.sourcePath }?.let { asset ->
                state.selectedAssetId = asset.id
            }
            state.statusMessage = "Selected packed region '${region.displayName}'."
        } else {
            state.statusMessage = "Packed region selection cleared."
        }
    }

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
            val importedPath = result.writtenPaths.firstOrNull()
            if (importedPath != null) {
                openPath(importedPath)
            }
        }
    }

    fun exportAtlasDescriptorDraft() {
        val plan = state.selectedPackingPlan()
        if (plan == null) {
            val result = TextureManagerFileWriteResult(success = false, message = "Run a packing dry-run before exporting an atlas descriptor.")
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

    fun importTexturePlaceholder() = importTexture()

    fun saveMetadataPlaceholder() = placeholder("Save Metadata")

    fun packAtlasPlaceholder() = placeholder("Pack Atlas")

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(TextureManagerUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(TextureManagerUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }

    fun requestExit() {
        engine.requestExit()
    }

    private fun selectedAtlasDocument(): TextureAtlasDocument? {
        val asset = state.project.assets.firstOrNull { it.id == state.selectedAssetId } ?: return null
        if (asset.kind != TextureManagerAssetKind.Atlas) return null
        return state.project.atlasDocuments[asset.path]
    }

    private fun placeholder(action: String) {
        val target = state.currentInputPath ?: state.project.resolvedInputPath ?: "<none>"
        state.statusMessage = "$action is deferred in this MVP."
        engine.logger.info(TAG) { "Texture Manager placeholder action='$action' target='$target'" }
    }

    companion object {
        private const val TAG = "TextureManagerOps"
    }
}

internal fun TextureManagerState.selectedAsset(): TextureManagerAssetDescriptor? = project.assets.firstOrNull { it.id == selectedAssetId }

internal fun TextureManagerState.selectedAtlasDocument(): TextureAtlasDocument? = selectedAsset()?.takeIf { it.kind == TextureManagerAssetKind.Atlas }?.let { asset -> project.atlasDocuments[asset.path] }

internal fun TextureManagerState.selectedNinePatchDocument(): NinePatchDocument? =
    selectedAsset()
        ?.takeIf { asset -> asset.kind == TextureManagerAssetKind.Texture }
        ?.let { asset -> project.ninePatchDocuments[asset.path] }

internal fun TextureManagerState.selectedRegionsForPage(): List<TextureAtlasRegion> =
    selectedAtlasDocument()
        ?.regions
        ?.filter { region -> selectedAtlasPageName == null || region.id.pageName == selectedAtlasPageName }
        .orEmpty()

internal fun TextureManagerState.selectedPackingPlan(): TextureAtlasPackingPlan? = packing.lastResult.plan

internal fun TextureManagerState.selectedPackingPage(): TextureAtlasPackingPage? = packing.lastResult.plan?.pages?.getOrNull(packing.selectedPageIndex)

internal fun TextureManagerState.selectedPackingRegion(): TextureAtlasPackingRegion? =
    selectedPackingPage()
        ?.regions
        ?.firstOrNull { region -> region.sourcePath == packing.selectedRegionSourcePath }

internal fun TextureManagerState.selectedPreviewTexturePath(): String? {
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
        TextureManagerAssetKind.Texture -> asset.path
        TextureManagerAssetKind.Atlas -> resolveAtlasPreviewTexturePath(asset.path, project.atlasDocuments[asset.path], selectedAtlasPageName)
        else -> null
    }
}

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
