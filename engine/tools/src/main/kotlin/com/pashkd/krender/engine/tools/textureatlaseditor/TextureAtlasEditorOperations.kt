package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.assets.importing.NoOpFileDialogService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.io.File

class TextureAtlasEditorOperations(
    private val state: TextureAtlasEditorState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val packingPlanner: TextureAtlasPackingPlanner = TextureAtlasPackingPlanner(),
    private val textureMetadataService: TextureMetadataService = TextureMetadataService(),
    fileDialogService: FileDialogService = NoOpFileDialogService,
    atlasSaveService: TextureAtlasSaveService = NoOpTextureAtlasSaveService,
) {
    private val importExportOperations =
        TextureAtlasEditorImportExportOperations(
            state = state,
            engine = engine,
            fileDialogService = fileDialogService,
            atlasSaveService = atlasSaveService,
            openPath = ::openPath,
        )

    fun openPath(path: String) {
        val normalized = path.trim().replace('\\', '/').ifBlank { null }
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
        state.reloadRequested = true
        engine.logger.info(TAG) { "Texture Atlas Editor openPath path='${normalized ?: "<none>"}'" }
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
                syncSelectedResourceFromRegion(state.selectedRegionId)
                state.importExport.targetPath = asset.path
            }
            else -> Unit
        }
        state.statusMessage = "Selected ${asset.kind.name.lowercase()} '${asset.displayName}'."
        engine.logger.info(TAG) { "Texture Atlas Editor selected asset id='${assetId.value}' kind=${asset.kind}" }
    }

    fun selectAtlasPage(pageName: String) {
        state.selectedAtlasPageName = pageName
        val atlas = selectedAtlasDocument()
        state.selectedRegionId =
            atlas?.regions
                ?.firstOrNull { region -> region.id.pageName == pageName }
                ?.id
        syncSelectedResourceFromRegion(state.selectedRegionId)
        state.statusMessage = "Selected atlas page '$pageName'."
        engine.logger.info(TAG) { "Texture Atlas Editor selected atlas page='$pageName'" }
    }

    fun selectRegion(regionId: AtlasRegionId?) {
        state.selectedRegionId = regionId
        syncSelectedResourceFromRegion(regionId)
        if (regionId != null) {
            state.selectedAtlasPageName = regionId.pageName
            state.statusMessage = "Selected region '${regionId.regionName}'."
            engine.logger.info(TAG) { "Texture Atlas Editor selected region='${regionId.regionName}' page='${regionId.pageName}'" }
        } else {
            state.statusMessage = "Region selection cleared."
        }
    }

    fun selectResource(resourceId: String?) {
        state.resources.selectedResourceId = resourceId
        val resource = state.selectedResource()
        when (resource) {
            is ImageAtlasResource -> {
                state.selectedAtlasPageName = resource.atlasRegionId?.pageName ?: state.selectedAtlasPageName
                state.selectedRegionId = resource.atlasRegionId
            }
            is NinePatchAtlasResource -> {
                state.selectedAtlasPageName = resource.atlasRegionId?.pageName ?: state.selectedAtlasPageName
                state.selectedRegionId = resource.atlasRegionId
            }
            else -> {
                state.selectedRegionId = null
            }
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
        if (state.preview.canvasMode == mode) return
        state.preview.canvasMode = mode
        state.statusMessage =
            when (mode) {
                TextureAtlasCanvasMode.TextureAtlas -> "Previewing atlas pages and regions."
                TextureAtlasCanvasMode.NinePatch -> "Previewing Nine-patch resources."
                TextureAtlasCanvasMode.FontPreview -> "Font preview is not implemented yet."
                TextureAtlasCanvasMode.FinalPackedAtlas -> "Previewing the current packed atlas plan."
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

    fun addImageResourceFromPath(path: String = state.importExport.importSourcePath) {
        val sourceFile = resolveTextureSource(path)
        if (sourceFile == null) {
            state.statusMessage = "Choose an existing texture inside the asset root before adding a resource."
            return
        }
        val resource = createResourceFromTextureSource(sourceFile)
        appendResource(resource)
        state.importExport.importSourcePath = localNormalizePath(sourceFile.path)
        state.statusMessage = "Added ${resource.type.name.lowercase()} resource '${resource.name}'."
        engine.logger.info(TAG) { "Texture Atlas Editor added resource id='${resource.id}' source='${resource.sourcePathOrNull() ?: "<none>"}'" }
    }

    fun importAndAddImageResource() {
        val before = state.importExport.lastImportResult
        importTexture()
        val result = state.importExport.lastImportResult
        if (result !== before && result?.success == true) {
            result.writtenPaths.firstOrNull()?.let { path ->
                val normalized = localNormalizePath(path)
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
        syncSelectedRegionFromResource()
        state.statusMessage = "Deleted resource '${resource.name}' from the working atlas."
        engine.logger.info(TAG) {
            "Texture Atlas Editor deleted resource id='${resource.id}' type=${resource.type}"
        }
    }

    fun setImportSourcePath(path: String) {
        state.importExport.importSourcePath = path
    }

    fun setTargetPath(path: String) {
        state.importExport.targetPath = path
    }

    fun setImportOverwrite(enabled: Boolean) {
        state.importExport.importOverwrite = enabled
    }

    fun setSaveOverwrite(enabled: Boolean) {
        state.importExport.saveOverwrite = enabled
    }

    fun packTextureAtlas() {
        val settings = state.packing.settings.copy()
        val diagnostics = mutableListOf<TextureAtlasPackingDiagnostic>()
        val inputs = resourcePackingInputs(diagnostics).ifEmpty { atlasRegionPackingInputs(diagnostics) }
        engine.logger.info(TAG) {
            "Texture Atlas Editor packing started candidates=${inputs.size} max=${settings.maxPageWidth}x${settings.maxPageHeight} padding=${settings.padding} rotation=${settings.allowRotation} includeNinePatch=${settings.includeNinePatch}"
        }
        val result = packingPlanner.plan(inputs, settings)
        val mergedDiagnostics = diagnostics + result.diagnostics
        state.packing.lastResult = result.copy(diagnostics = mergedDiagnostics)
        state.packing.selectedPageIndex = 0
        state.packing.selectedRegionId = result.plan?.pages?.firstOrNull()?.regions?.firstOrNull()?.id
        val pages = result.plan?.pages?.size ?: 0
        val regions = result.plan?.packedRegionCount ?: 0
        val skipped = result.plan?.skippedCount ?: 0
        state.statusMessage = "Packed texture atlas preview produced $pages page(s), $regions region(s), skipped $skipped."
        engine.logger.info(TAG) {
            "Texture Atlas Editor packing completed pages=$pages regions=$regions skipped=$skipped diagnostics=${mergedDiagnostics.size}"
        }
    }

    fun runPackingDryRun() = packTextureAtlas()

    fun selectPackingPage(index: Int) {
        state.packing.selectedPageIndex = index
        val page = state.packing.lastResult.plan?.pages?.getOrNull(index)
        state.packing.selectedRegionId = page?.regions?.firstOrNull()?.id
        state.statusMessage = if (page != null) "Selected packing page '${page.name}'." else "Packing page selection cleared."
    }

    fun selectPackingRegion(regionId: String?) {
        state.packing.selectedRegionId = regionId
        val region = state.selectedPackingRegion()
        if (region != null) {
            state.resources.items.firstOrNull { resource -> resource.sourcePathOrNull() == region.sourcePath && resource.name == region.regionName }?.let { resource ->
                state.resources.selectedResourceId = resource.id
            }
            state.statusMessage = "Selected packed region '${region.displayName}'."
        } else {
            state.statusMessage = "Packed region selection cleared."
        }
    }

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

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(TextureAtlasEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(TextureAtlasEditorUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }

    fun requestExit() {
        engine.requestExit()
    }

    private fun selectedAtlasDocument(): TextureAtlasDocument? {
        val asset = state.project.assets.firstOrNull { it.id == state.selectedAssetId } ?: return null
        if (asset.kind != TextureAtlasEditorAssetKind.Atlas) return null
        return state.project.atlasDocuments[asset.path]
    }

    private fun atlasRegionPackingInputs(diagnostics: MutableList<TextureAtlasPackingDiagnostic>): List<TextureAtlasPackingInput> {
        val selectedAsset = state.selectedAsset()?.takeIf { it.kind == TextureAtlasEditorAssetKind.Atlas }
        val atlas = state.selectedAtlasDocument()
        if (selectedAsset == null || atlas == null) {
            diagnostics +=
                TextureAtlasPackingDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    message = "Open a texture atlas before packing regions.",
                )
            return emptyList()
        }
        return atlas.regions.mapNotNull { region ->
            val xy = region.xy
            val size = region.size
            if (xy == null || size == null) {
                diagnostics +=
                    TextureAtlasPackingDiagnostic(
                        severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                        message = "Atlas region is missing xy or size and was skipped.",
                        sourcePath = "${region.id.pageName}:${region.id.regionName}",
                    )
                return@mapNotNull null
            }
            val sourcePath =
                resolveAtlasPreviewTexturePath(selectedAsset.path, atlas, region.id.pageName)
                    ?: run {
                        diagnostics +=
                            TextureAtlasPackingDiagnostic(
                                severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                                message = "Atlas page texture could not be resolved and its region was skipped.",
                                sourcePath = region.id.pageName,
                            )
                        return@mapNotNull null
                    }
            TextureAtlasPackingInput(
                id = "atlas:${region.id.pageName}:${region.id.regionName}:${region.index ?: -1}",
                sourcePath = sourcePath,
                sourceX = xy.first,
                sourceY = xy.second,
                sourceWidth = size.first,
                sourceHeight = size.second,
                regionName = region.id.regionName,
                displayName = region.id.regionName,
                width = size.first,
                height = size.second,
                split = region.split,
                pad = region.pad,
                index = region.index,
            )
        }
    }

    private fun resourcePackingInputs(diagnostics: MutableList<TextureAtlasPackingDiagnostic>): List<TextureAtlasPackingInput> =
        state.resources.items.mapNotNull { resource ->
            when (resource) {
                is ImageAtlasResource -> imageResourcePackingInput(resource, diagnostics)
                is NinePatchAtlasResource -> ninePatchResourcePackingInput(resource, diagnostics)
                is ColorAtlasResource -> {
                    diagnostics +=
                        TextureAtlasPackingDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                            message = "Color resources are not packable yet.",
                            sourcePath = resource.name,
                        )
                    null
                }
                is FontAtlasResource -> {
                    diagnostics +=
                        TextureAtlasPackingDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                            message = "Font resources are not packable yet.",
                            sourcePath = resource.sourcePath,
                        )
                    null
                }
            }
        }

    private fun imageResourcePackingInput(
        resource: ImageAtlasResource,
        diagnostics: MutableList<TextureAtlasPackingDiagnostic>,
    ): TextureAtlasPackingInput? {
        val file = File(resource.sourcePath)
        val info = textureMetadataService.read(file)
        val width = resource.sourceWidth ?: info?.width
        val height = resource.sourceHeight ?: info?.height
        if (!file.isFile || width == null || height == null) {
            diagnostics +=
                TextureAtlasPackingDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    message = "Image resource is missing or has unknown dimensions and was skipped.",
                    sourcePath = resource.sourcePath,
                )
            return null
        }
        return TextureAtlasPackingInput(
            id = resource.id,
            sourcePath = resource.sourcePath,
            sourceX = resource.sourceX,
            sourceY = resource.sourceY,
            sourceWidth = width,
            sourceHeight = height,
            regionName = resource.name,
            displayName = resource.name,
            width = width,
            height = height,
            index = resource.atlasIndex,
        )
    }

    private fun ninePatchResourcePackingInput(
        resource: NinePatchAtlasResource,
        diagnostics: MutableList<TextureAtlasPackingDiagnostic>,
    ): TextureAtlasPackingInput? {
        val file = File(resource.sourcePath)
        val info = textureMetadataService.read(file)
        val document = state.project.ninePatchDocuments[resource.sourcePath]
        val width = resource.sourceWidth ?: document?.contentWidth ?: info?.width
        val height = resource.sourceHeight ?: document?.contentHeight ?: info?.height
        if (!file.isFile || width == null || height == null) {
            diagnostics +=
                TextureAtlasPackingDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    message = "Nine-patch resource is missing or has unknown dimensions and was skipped.",
                    sourcePath = resource.sourcePath,
                )
            return null
        }
        return TextureAtlasPackingInput(
            id = resource.id,
            sourcePath = resource.sourcePath,
            sourceX = resource.sourceX,
            sourceY = resource.sourceY,
            sourceWidth = width,
            sourceHeight = height,
            regionName = resource.name,
            displayName = resource.name,
            width = width,
            height = height,
            isNinePatch = true,
            split = resource.split.ifEmpty { document?.splitInts().orEmpty() },
            pad = resource.pad.ifEmpty { document?.padInts().orEmpty() },
            index = resource.atlasIndex,
        )
    }

    private fun resolveTextureSource(path: String): File? {
        val trimmed = path.trim().replace('\\', '/')
        if (trimmed.isBlank()) return null
        val assetRoot = engine.assetRegistry.baseDir()
        val file =
            TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, trimmed)
                ?: File(trimmed).takeIf { candidate -> candidate.isAbsolute && candidate.isFile && TextureAtlasEditorPathValidator.isInsideRoot(assetRoot, candidate) }
                ?: return null
        if (!file.isFile || !isSupportedTextureImportFileLocal(file)) return null
        return file
    }

    private fun createResourceFromTextureSource(sourceFile: File): TextureAtlasResource {
        val normalized = localNormalizePath(sourceFile.path)
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

    private fun syncSelectedResourceFromRegion(regionId: AtlasRegionId?) {
        state.resources.selectedResourceId =
            state.resources.items
                .firstOrNull { resource -> resource.atlasRegionIdOrNull() == regionId }
                ?.id
                ?: state.resources.selectedResourceId
    }

    private fun syncSelectedRegionFromResource() {
        val resource = state.selectedResource()
        state.selectedRegionId = resource?.atlasRegionIdOrNull()
        state.selectedAtlasPageName = resource?.atlasRegionIdOrNull()?.pageName ?: state.selectedAtlasPageName
    }

    companion object {
        private const val TAG = "TextureAtlasEditorOps"
    }
}

internal fun TextureAtlasEditorState.selectedAsset(): TextureAtlasEditorAssetDescriptor? = project.assets.firstOrNull { it.id == selectedAssetId }

internal fun TextureAtlasEditorState.selectedResource(): TextureAtlasResource? =
    resources.items.firstOrNull { resource -> resource.id == resources.selectedResourceId }

internal fun TextureAtlasEditorState.selectedAtlasDocument(): TextureAtlasDocument? = selectedAsset()?.takeIf { it.kind == TextureAtlasEditorAssetKind.Atlas }?.let { asset -> project.atlasDocuments[asset.path] }

internal fun TextureAtlasEditorState.selectedNinePatchDocument(): NinePatchDocument? =
    selectedResource()
        ?.sourcePathOrNull()
        ?.let { previewPath -> project.ninePatchDocuments[previewPath] }
        ?: selectedPreviewTexturePath()?.let { previewPath -> project.ninePatchDocuments[previewPath] }

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

internal fun TextureAtlasEditorState.selectedPreviewTexturePath(): String? {
    when (preview.canvasMode) {
        TextureAtlasCanvasMode.FinalPackedAtlas,
        TextureAtlasCanvasMode.FontPreview,
        -> return null
        TextureAtlasCanvasMode.NinePatch -> {
            selectedResource()?.sourcePathOrNull()?.let { return it }
        }
        TextureAtlasCanvasMode.TextureAtlas -> Unit
    }
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
        else -> null
    }

internal fun TextureAtlasResource.sourcePathOrNull(): String? =
    when (this) {
        is ImageAtlasResource -> sourcePath
        is NinePatchAtlasResource -> sourcePath
        is FontAtlasResource -> sourcePath
        is ColorAtlasResource -> null
    }

private fun NinePatchDocument.splitInts(): List<Int> =
    guideSegmentsToAtlasInsets(stretchX.firstOrNull(), stretchY.firstOrNull(), contentWidth, contentHeight)

private fun NinePatchDocument.padInts(): List<Int> =
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

private fun localNormalizePath(path: String): String = path.replace('\\', '/')

private fun isSupportedTextureImportFileLocal(file: File): Boolean =
    file.isFile && file.extension.lowercase() in setOf("png", "bmp", "jpg", "jpeg", "ktx", "webp")

private fun isNinePatchTexturePathLocal(path: String): Boolean = path.endsWith(".9.png", ignoreCase = true)

internal fun resolveAtlasPreviewTexturePath(
    atlasPath: String,
    atlas: TextureAtlasDocument?,
    selectedPageName: String?,
): String? {
    val pageName = selectedPageName ?: atlas?.pages?.firstOrNull()?.name ?: return null
    val atlasFile = File(atlasPath)
    val pageFile = File(pageName)
    if (pageFile.isAbsolute) {
        return localNormalizePath(pageFile.path)
    }
    val parent = atlasFile.parentFile ?: return null
    return localNormalizePath(File(parent, pageName).path)
}
