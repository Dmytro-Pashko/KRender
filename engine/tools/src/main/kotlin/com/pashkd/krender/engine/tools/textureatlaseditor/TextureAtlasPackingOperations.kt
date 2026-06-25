package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext
import java.io.File

internal class TextureAtlasPackingOperations(
    private val state: TextureAtlasEditorState,
    private val engine: EngineContext,
    private val packingPlanner: TextureAtlasPackingPlanner = TextureAtlasPackingPlanner(),
    private val textureMetadataService: TextureMetadataService = TextureMetadataService(),
) {
    fun setPackingMaxPageWidth(value: Int) {
        engine.logger.info(TAG) { "Texture Atlas Editor packing maxPageWidth changed old=${state.packing.settings.maxPageWidth} new=$value" }
        state.packing.settings.maxPageWidth = value
    }

    fun setPackingMaxPageHeight(value: Int) {
        engine.logger.info(TAG) { "Texture Atlas Editor packing maxPageHeight changed old=${state.packing.settings.maxPageHeight} new=$value" }
        state.packing.settings.maxPageHeight = value
    }

    fun setPackingPadding(value: Int) {
        engine.logger.info(TAG) { "Texture Atlas Editor packing padding changed old=${state.packing.settings.padding} new=$value" }
        state.packing.settings.padding = value
    }

    fun setPackingAllowRotation(enabled: Boolean) {
        engine.logger.info(TAG) { "Texture Atlas Editor packing allowRotation changed old=${state.packing.settings.allowRotation} new=$enabled" }
        state.packing.settings.allowRotation = enabled
    }

    fun setPackingIncludeNinePatch(enabled: Boolean) {
        engine.logger.info(TAG) { "Texture Atlas Editor packing includeNinePatch changed old=${state.packing.settings.includeNinePatch} new=$enabled" }
        state.packing.settings.includeNinePatch = enabled
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
            state.resources.items
                .firstOrNull { resource -> resource.sourcePathOrNull() == region.sourcePath && resource.name == region.regionName }
                ?.let { resource ->
                    state.resources.selectedResourceId = resource.id
                    state.selectedRegionId = resource.atlasRegionIdOrNull()
                    state.selectedAtlasPageName = resource.atlasRegionIdOrNull()?.pageName ?: state.selectedAtlasPageName
                }
            state.statusMessage = "Selected packed region '${region.displayName}'."
        } else {
            state.statusMessage = "Packed region selection cleared."
        }
    }

    fun fitSelectedPackedRegion() {
        val region = state.selectedPackingRegion()
        if (region == null) {
            state.statusMessage = "Select a packed region to focus it."
            return
        }
        val canvas = state.canvasRect
        val textureWidth = state.previewInfo.textureWidth
        val textureHeight = state.previewInfo.textureHeight
        if (!canvas.isValid || textureWidth <= 0 || textureHeight <= 0) {
            engine.logger.warn(TAG) {
                "Texture Atlas Editor fitSelectedPackedRegion ignored region='${region.displayName}' because canvas or packed preview dimensions were unavailable"
            }
            state.statusMessage = "Preview must be visible before focusing a packed region."
            return
        }

        val regionWidth = region.width.coerceAtLeast(1)
        val regionHeight = region.height.coerceAtLeast(1)
        val zoom =
            minOf(
                canvas.width / regionWidth.toFloat(),
                canvas.height / regionHeight.toFloat(),
            ).times(0.9f).coerceIn(TextureAtlasMinPreviewZoom, TextureAtlasMaxPreviewZoom)

        val imageWidth = textureWidth * zoom
        val imageHeight = textureHeight * zoom
        val baseImageX = canvas.x + (canvas.width - imageWidth) * 0.5f
        val baseImageY = canvas.y + (canvas.height - imageHeight) * 0.5f
        val regionCenterX = region.x + regionWidth * 0.5f
        val regionCenterY = region.y + regionHeight * 0.5f
        val desiredCenterX = canvas.x + canvas.width * 0.5f
        val desiredCenterY = canvas.y + canvas.height * 0.5f

        state.preview.customZoom = zoom
        state.preview.viewport.zoom = zoom
        state.preview.zoomMode = TexturePreviewZoomMode.Custom
        state.preview.viewport.panX = desiredCenterX - (baseImageX + regionCenterX * zoom)
        state.preview.viewport.panY = desiredCenterY - (baseImageY + regionCenterY * zoom)
        state.statusMessage = "Focused packed region '${region.displayName}'."
        engine.logger.info(TAG) {
            "Texture Atlas Editor fit packed region='${region.displayName}' page=${region.pageIndex} zoom=$zoom"
        }
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

    /**
     * Converts editable draft resources into packable image slices.
     */
    private fun resourcePackingInputs(diagnostics: MutableList<TextureAtlasPackingDiagnostic>): List<TextureAtlasPackingInput> =
        state.resources.items.flatMap { resource ->
            when (resource) {
                is ImageAtlasResource -> listOfNotNull(imageResourcePackingInput(resource, diagnostics))
                is NinePatchAtlasResource -> listOfNotNull(ninePatchResourcePackingInput(resource, diagnostics))
                is ColorAtlasResource -> {
                    diagnostics +=
                        TextureAtlasPackingDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                            message = "Color resources are not packable yet.",
                            sourcePath = resource.name,
                        )
                    emptyList()
                }
                is FontAtlasResource -> fontResourcePackingInputs(resource, diagnostics)
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

    private fun fontResourcePackingInputs(
        resource: FontAtlasResource,
        diagnostics: MutableList<TextureAtlasPackingDiagnostic>,
    ): List<TextureAtlasPackingInput> {
        if (!resource.packInAtlas) {
            return emptyList()
        }
        val document = state.project.fontDocuments[resource.documentPath]
        if (document == null || !document.readable) {
            diagnostics +=
                TextureAtlasPackingDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    message = "Packed font was skipped because its descriptor could not be read.",
                    sourcePath = resource.documentPath,
                )
            return emptyList()
        }
        if (document.pages.size != 1) {
            diagnostics +=
                TextureAtlasPackingDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    message = "Packed font rewrite currently supports single-page bitmap fonts only. The font was skipped.",
                    sourcePath = resource.documentPath,
                )
            return emptyList()
        }
        val page = document.pages.first()
        val pagePath = page.resolvedPath
        if (pagePath.isNullOrBlank()) {
            diagnostics +=
                TextureAtlasPackingDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    message = "Packed font was skipped because its page texture path could not be resolved.",
                    sourcePath = resource.documentPath,
                )
            return emptyList()
        }
        val pageFile = File(pagePath)
        val pageInfo = textureMetadataService.read(pageFile)
        val width = pageInfo?.width
        val height = pageInfo?.height
        if (!pageFile.isFile || width == null || height == null) {
            diagnostics +=
                TextureAtlasPackingDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    message = "Packed font was skipped because its page texture is missing or unreadable.",
                    sourcePath = pagePath,
                )
            return emptyList()
        }
        return listOf(
            TextureAtlasPackingInput(
                id = "${resource.id}:page:${page.id}",
                sourcePath = pagePath,
                sourceWidth = width,
                sourceHeight = height,
                regionName = resource.name,
                displayName = resource.name,
                width = width,
                height = height,
                fontResourceId = resource.id,
                fontDocumentPath = resource.documentPath,
                fontPageId = page.id,
            ),
        )
    }

    companion object {
        private const val TAG = "TextureAtlasPackingOps"
    }
}
