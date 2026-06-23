package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.AtlasRegionHitInfo
import com.pashkd.krender.engine.tools.skin.FontPreviewScales
import com.pashkd.krender.engine.tools.skin.FontPreviewTextHeight
import com.pashkd.krender.engine.tools.skin.ResourcePreviewClickDragThreshold
import com.pashkd.krender.engine.tools.skin.ResourcePreviewViewportHeight
import com.pashkd.krender.engine.tools.skin.ResourceSearchWidth
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewKind
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewZoomMode
import com.pashkd.krender.engine.tools.skin.computeResourcePreviewViewportLayout
import com.pashkd.krender.engine.tools.skin.formatPreviewScale
import com.pashkd.krender.engine.tools.skin.formatResourcePreviewZoom
import com.pashkd.krender.engine.tools.skin.parseAtlasRegionHitInfo
import com.pashkd.krender.engine.tools.skin.parseResourceColor
import com.pashkd.krender.engine.tools.skin.readBuffer
import com.pashkd.krender.engine.tools.skin.screenToImageX
import com.pashkd.krender.engine.tools.skin.screenToImageYTopLeft
import com.pashkd.krender.engine.tools.skin.writeBuffer
import com.pashkd.krender.engine.ui.UiService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import glm_.vec2.Vec2 as ImVec2
import imgui.ImGui
import imgui.api.colorButton
import imgui.dsl
import java.io.File
import kotlin.math.hypot

class SkinEditorResourceBrowserPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(256)
    private val fontSampleBuffer = ByteArray(1024)
    private var previewClickPending = false
    private var previewClickDragDistance = 0f

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.ResourceBrowser)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.ResourceBrowser, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.ResourceBrowser, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val resourceIndex = state.loadResult.resourceIndex
        drawFilters()
        if (resourceIndex.resources.isEmpty()) {
            ImGui.textUnformatted("No resources indexed.")
        } else {
            val filteredResources = resourceIndex.resources.filter(::matchesFilters)
            ImGui.textUnformatted("Showing ${filteredResources.size} of ${resourceIndex.resources.size}")
            val categories = state.resourceBrowser.selectedCategory?.let(::listOf) ?: SkinResourceCategory.entries
            categories.forEach { category ->
                val resources = filteredResources.filter { resource -> resource.category == category }
                if (resources.isNotEmpty() && ImGui.treeNode("${category.name} (${resources.size})##skin_editor_resource_category_$category")) {
                    resources.forEach { resource ->
                        val status = if (resource.resolved) "" else " missing"
                        val source = resource.source?.substringBefore('#')?.let(::File)?.name?.takeIf(String::isNotBlank)
                        val sourceLabel = source?.let { " source: $it" }.orEmpty()
                        val label = "${resource.name} [${resource.type}]$status refs: ${resource.referencedBy.size}$sourceLabel"
                        if (ImGui.selectable("$label##skin_editor_resource_${category}_${resource.name}", state.selectedResourceKey == resource.key)) {
                            operations.selectResource(resource)
                        }
                        if (resource.category == SkinResourceCategory.Color) {
                            resourceColor(resource)?.let { color ->
                                ImGui.sameLine()
                                colorButton(
                                    "Color##skin_editor_resource_color_${resource.name}",
                                    color[0],
                                    color[1],
                                    color[2],
                                    color[3],
                                )
                            }
                        }
                    }
                    ImGui.treePop()
                }
            }
        }
        drawResourcePreviewSection()
        ImGui.end()
    }

    private fun drawResourcePreviewSection() {
        ImGui.separator()
        ImGui.textUnformatted("Resource Preview")
        val selectedResource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == state.selectedResourceKey }
        drawResourcePreviewControls(selectedResource)
        ImGui.separator()
        ImGui.textWrapped(state.resourceVisualPreviewInfo.statusMessage)
        state.resourceVisualPreviewInfo.resolvedTexturePath?.let { path ->
            ImGui.textWrapped("Texture: ${File(path).name}")
            ImGui.textUnformatted("Size: ${state.resourceVisualPreviewInfo.textureWidth} x ${state.resourceVisualPreviewInfo.textureHeight}")
        }
        state.resourceVisualPreviewInfo.resolvedFontPath?.let { path ->
            ImGui.textWrapped("Font file: ${File(path).name}")
        }
        state.resourceVisualPreviewInfo.fontPreviewSource?.let { source ->
            ImGui.textWrapped("Font source: $source")
        }
        state.resourceVisualPreviewInfo.colorValue?.let { value ->
            ImGui.textWrapped("Color: $value")
        }
        state.resourceVisualPreviewInfo.atlasPageName?.let { page ->
            ImGui.textWrapped("Atlas page: $page")
        }
        state.resourceVisualPreviewInfo.selectedRegionName?.let { regionName ->
            ImGui.textWrapped("Region overlay: $regionName")
        }
        ImGui.separator()
        drawInlineResourcePreview(selectedResource)
    }

    private fun drawInlineResourcePreview(selectedResource: SkinResourceInfo?) {
        val info = state.resourceVisualPreviewInfo
        when (info.kind) {
            SkinResourceVisualPreviewKind.Texture -> drawInteractiveTexturePreview(selectedResource, info)

            SkinResourceVisualPreviewKind.Color -> {
                val selected = state.selectedResourceKey?.let(state.editSession.resources::get)
                val values = selected?.values
                val color = values?.let(::parseResourceColor)
                if (color != null) {
                    colorButton("Color preview##skin_editor_resource_color_preview", color[0], color[1], color[2], color[3])
                }
            }

            SkinResourceVisualPreviewKind.Font -> {
                val handle = info.texturePreviewHandle
                if (handle == null) {
                    ImGui.textWrapped("Font preview image unavailable.")
                } else {
                    val availableWidth = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
                    val scale = minOf(1f, availableWidth / handle.width.coerceAtLeast(1).toFloat())
                    if (!ui.drawTexturePreview(handle, handle.width * scale, handle.height * scale)) {
                        ImGui.textWrapped("Font preview unavailable: UI backend rejected the texture handle.")
                    }
                }
            }

            SkinResourceVisualPreviewKind.None -> Unit
        }
    }

    private fun drawInteractiveTexturePreview(
        selectedResource: SkinResourceInfo?,
        info: SkinResourceVisualPreviewInfo,
    ) {
        val handle = info.texturePreviewHandle
        if (handle == null) {
            ImGui.textWrapped("Image preview unavailable.")
            return
        }
        val viewportWidth = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
        val viewportHeight = minOf(ResourcePreviewViewportHeight, ImGui.contentRegionAvail.y.coerceAtLeast(160f))
        ImGui.beginChild("skin_editor_resource_preview_viewport", ImVec2(viewportWidth, viewportHeight), true)
        val viewportMin = ImGui.cursorScreenPos
        val viewportContent = ImGui.contentRegionAvail
        operations.syncResourcePreviewViewportContent(buildPreviewContentKey(info))
        val viewportLayout =
            computeResourcePreviewViewportLayout(
                viewportX = viewportMin.x,
                viewportY = viewportMin.y,
                viewportWidth = viewportContent.x.coerceAtLeast(1f),
                viewportHeight = viewportContent.y.coerceAtLeast(1f),
                imageWidth = handle.width,
                imageHeight = handle.height,
                previewState = state.resourceVisualPreview,
            )
        ImGui.windowDrawList.addImage(
            handle.id,
            ImVec2(viewportLayout.imageX, viewportLayout.imageY),
            ImVec2(viewportLayout.imageX + viewportLayout.imageWidth, viewportLayout.imageY + viewportLayout.imageHeight),
            ImVec2(handle.u0, handle.v0),
            ImVec2(handle.u1, handle.v1),
        )
        handleTexturePreviewInteraction(selectedResource, info, viewportLayout)
        ImGui.endChild()
    }

    private fun drawResourcePreviewControls(selectedResource: SkinResourceInfo?) {
        val previewState = state.resourceVisualPreview
        if (selectedResource?.category == SkinResourceCategory.Font) {
            drawFontPreviewControls()
        } else if (selectedResource?.category != SkinResourceCategory.Color) {
            if (ImGui.beginCombo("Zoom##skin_editor_resource_preview_zoom", formatResourcePreviewZoom(previewState.zoomMode))) {
                SkinResourceVisualPreviewZoomMode.entries.forEach { zoomMode ->
                    if (ImGui.selectable("${formatResourcePreviewZoom(zoomMode)}##skin_editor_resource_preview_zoom_$zoomMode", zoomMode == previewState.zoomMode)) {
                        operations.setResourcePreviewZoomMode(zoomMode)
                    }
                }
                ImGui.endCombo()
            }
            ImGui.sameLine()
            with(dsl) {
                button("Fit##skin_editor_resource_preview_zoom_fit") {
                    operations.setResourcePreviewZoomMode(SkinResourceVisualPreviewZoomMode.Fit)
                }
            }
            ImGui.sameLine()
            with(dsl) {
                button("100%##skin_editor_resource_preview_zoom_100") {
                    operations.setResourcePreviewZoomMode(SkinResourceVisualPreviewZoomMode.Percent100)
                }
            }
            ImGui.sameLine()
            with(dsl) {
                button("Reset View##skin_editor_resource_preview_zoom_reset") {
                    operations.resetResourcePreviewZoom()
                }
            }

            val showBounds = booleanArrayOf(previewState.showRegionBounds)
            if (ImGui.checkbox("Show region bounds##skin_editor_resource_preview_bounds", showBounds)) {
                operations.setShowResourceRegionBounds(showBounds[0])
            }
            val showLabels = booleanArrayOf(previewState.showRegionLabels)
            if (ImGui.checkbox("Show region labels##skin_editor_resource_preview_labels", showLabels)) {
                operations.setShowResourceRegionLabels(showLabels[0])
            }
            if (selectedResource?.category == SkinResourceCategory.Atlas || selectedResource?.category == SkinResourceCategory.AtlasRegion) {
                val clickSelect = booleanArrayOf(previewState.viewport.clickSelectRegionEnabled)
                if (ImGui.checkbox("Click to select region##skin_editor_resource_preview_click_select", clickSelect)) {
                    operations.setAtlasClickSelectionEnabled(clickSelect[0])
                }
                ImGui.textWrapped("Ctrl + RMB drag: pan. Ctrl + mouse wheel: zoom. Click region: select.")
            } else {
                ImGui.textWrapped("Ctrl + RMB drag: pan. Ctrl + mouse wheel: zoom.")
            }
        }

        selectedResource?.let { resource ->
            if (resource.category == SkinResourceCategory.Atlas || resource.category == SkinResourceCategory.AtlasRegion) {
                drawAtlasRegionSelector(resource)
            }
            ImGui.textUnformatted("Selected resource: ${resource.category}.${resource.name}")
        } ?: ImGui.textUnformatted("Selected resource: <none>")
    }

    private fun drawFontPreviewControls() {
        val fontPreview = state.resourceVisualPreview.fontPreview
        if (readBuffer(fontSampleBuffer) != fontPreview.sampleText) {
            writeBuffer(fontSampleBuffer, fontPreview.sampleText)
        }
        ImGui.textUnformatted("Preview text:")
        if (ImGui.inputTextMultiline("##skin_editor_font_preview_text", fontSampleBuffer, ImVec2(-1f, FontPreviewTextHeight))) {
            operations.setFontPreviewSampleText(readBuffer(fontSampleBuffer))
        }
        val selectedScale = FontPreviewScales.minBy { scale -> kotlin.math.abs(scale - fontPreview.fontScale) }
        if (ImGui.beginCombo("Font scale##skin_editor_font_preview_scale", formatPreviewScale(selectedScale))) {
            FontPreviewScales.forEach { scale ->
                if (ImGui.selectable("${formatPreviewScale(scale)}##skin_editor_font_preview_scale_$scale", scale == selectedScale)) {
                    operations.setFontPreviewScale(scale)
                }
            }
            ImGui.endCombo()
        }
        val showCyrillic = booleanArrayOf(fontPreview.showCyrillicSample)
        if (ImGui.checkbox("Show Cyrillic##skin_editor_font_preview_uk", showCyrillic)) {
            operations.setShowCyrillicFontSample(showCyrillic[0])
        }
        val showAscii = booleanArrayOf(fontPreview.showAsciiSample)
        if (ImGui.checkbox("Show ASCII##skin_editor_font_preview_ascii", showAscii)) {
            operations.setShowAsciiFontSample(showAscii[0])
        }
        ImGui.textWrapped("Sample text uses the built-in preview block plus Cyrillic and ASCII coverage toggles.")
    }

    private fun drawAtlasRegionSelector(resource: SkinResourceInfo) {
        val atlasSource = resource.source ?: return
        val pageName = activeAtlasPage(resource)
        val regions =
            state.loadResult.resourceIndex.atlasRegions
                .filter { region -> region.source == atlasSource }
                .filter { region -> pageName == null || region.details["page"] == pageName }
                .map(SkinResourceInfo::name)
                .distinct()
                .sorted()
        if (regions.isEmpty()) return

        val selectedName = state.resourceVisualPreview.selectedAtlasRegionName?.takeIf { name -> name in regions }
        val comboLabel = selectedName ?: "None"
        if (ImGui.beginCombo("Region overlay##skin_editor_resource_preview_region", comboLabel)) {
            if (ImGui.selectable("None##skin_editor_resource_preview_region_none", selectedName == null)) {
                operations.selectAtlasRegionByName(null)
            }
            regions.forEach { regionName ->
                if (ImGui.selectable("$regionName##skin_editor_resource_preview_region_$regionName", regionName == selectedName)) {
                    operations.selectAtlasRegionByName(regionName, atlasSource, pageName)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun handleTexturePreviewInteraction(
        selectedResource: SkinResourceInfo?,
        info: SkinResourceVisualPreviewInfo,
        viewportLayout: com.pashkd.krender.engine.tools.skin.ResourcePreviewViewportLayout,
    ) {
        val io = ImGui.io
        val hovered = ImGui.isWindowHovered()

        if (hovered && io.mouseClicked[0]) {
            previewClickPending = true
            previewClickDragDistance = 0f
        }
        if (previewClickPending && io.mouseDown[0]) {
            previewClickDragDistance += hypot(io.mouseDelta.x, io.mouseDelta.y)
        }

        if (hovered && io.keyCtrl && io.mouseDown[1] && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            operations.panResourcePreviewViewport(io.mouseDelta.x, io.mouseDelta.y)
            previewClickPending = false
        }

        if (hovered && io.keyCtrl && io.mouseWheel != 0f) {
            val currentZoom = viewportLayout.effectiveZoom
            val nextZoom = currentZoom * (1f + io.mouseWheel * 0.1f)
            operations.setResourcePreviewViewportZoom(nextZoom)
            previewClickPending = false
        }

        if (previewClickPending && io.mouseReleased[0]) {
            val clickAllowed =
                hovered &&
                    !io.keyCtrl &&
                    previewClickDragDistance <= ResourcePreviewClickDragThreshold &&
                    state.resourceVisualPreview.viewport.clickSelectRegionEnabled
            if (clickAllowed) {
                hitTestAtlasRegion(selectedResource, info, viewportLayout, io.mousePos.x, io.mousePos.y)?.let { hit ->
                    operations.selectResource(hit.resource)
                    operations.selectAtlasRegionByName(hit.resource.name, hit.resource.source, hit.pageName)
                    state.statusMessage = "Selected atlas region '${hit.resource.name}' from preview."
                }
            }
            previewClickPending = false
            previewClickDragDistance = 0f
        }
    }

    private fun hitTestAtlasRegion(
        selectedResource: SkinResourceInfo?,
        info: SkinResourceVisualPreviewInfo,
        viewportLayout: com.pashkd.krender.engine.tools.skin.ResourcePreviewViewportLayout,
        mouseX: Float,
        mouseY: Float,
    ): AtlasRegionHitInfo? {
        selectedResource ?: return null
        if (selectedResource.category != SkinResourceCategory.Atlas && selectedResource.category != SkinResourceCategory.AtlasRegion) return null
        val imageX = screenToImageX(mouseX, viewportLayout)
        val imageY = screenToImageYTopLeft(mouseY, viewportLayout)
        if (imageX < 0f || imageY < 0f || imageX > info.textureWidth || imageY > info.textureHeight) return null
        return activeAtlasRegions(selectedResource)
            .filter { region ->
                imageX >= region.x &&
                    imageX <= region.x + region.width &&
                    imageY >= region.y &&
                    imageY <= region.y + region.height
            }.minWithOrNull(compareBy<AtlasRegionHitInfo>({ it.area }, { it.resource.name }))
    }

    private fun activeAtlasRegions(selectedResource: SkinResourceInfo): List<AtlasRegionHitInfo> {
        val atlasSource = selectedResource.source ?: return emptyList()
        val pageName = activeAtlasPage(selectedResource)
        return state.loadResult.resourceIndex.atlasRegions
            .filter { region -> region.source == atlasSource }
            .filter { region -> pageName == null || region.details["page"] == pageName }
            .mapNotNull(::parseAtlasRegionHitInfo)
    }

    private fun activeAtlasPage(selectedResource: SkinResourceInfo): String? =
        when (selectedResource.category) {
            SkinResourceCategory.AtlasRegion -> selectedResource.details["page"]?.takeIf(String::isNotBlank)
            SkinResourceCategory.Atlas -> state.resourceVisualPreviewInfo.atlasPageName?.takeIf(String::isNotBlank)
            else -> null
        }

    private fun buildPreviewContentKey(info: SkinResourceVisualPreviewInfo): String? =
        info.resolvedTexturePath?.let { texturePath -> "${info.kind}:$texturePath:${info.atlasPageName.orEmpty()}" }

    private fun resourceColor(resource: SkinResourceInfo): FloatArray? {
        val values = state.editSession.resources[resource.key]?.values ?: resource.details
        return parseResourceColor(values)
    }

    private fun drawFilters() {
        if (readBuffer(queryBuffer) != state.resourceBrowser.query) {
            writeBuffer(queryBuffer, state.resourceBrowser.query)
        }
        ImGui.textUnformatted("Search:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.inputText("##skin_editor_resource_search", queryBuffer)) {
            state.resourceBrowser.query = readBuffer(queryBuffer)
        }

        val categoryLabel = state.resourceBrowser.selectedCategory?.name ?: "All categories"
        if (ImGui.beginCombo("Category##skin_editor_resource_category_filter", categoryLabel)) {
            if (ImGui.selectable("All categories##skin_editor_resource_category_all", state.resourceBrowser.selectedCategory == null)) {
                state.resourceBrowser.selectedCategory = null
            }
            SkinResourceCategory.entries.forEach { category ->
                if (ImGui.selectable("${category.name}##skin_editor_resource_category_filter_$category", state.resourceBrowser.selectedCategory == category)) {
                    state.resourceBrowser.selectedCategory = category
                }
            }
            ImGui.endCombo()
        }

        val unresolved = booleanArrayOf(state.resourceBrowser.showOnlyUnresolved)
        if (ImGui.checkbox("Only unresolved##skin_editor_resource_unresolved", unresolved)) {
            state.resourceBrowser.showOnlyUnresolved = unresolved[0]
        }
        val referenced = booleanArrayOf(state.resourceBrowser.showOnlyReferenced)
        if (ImGui.checkbox("Only referenced##skin_editor_resource_referenced", referenced)) {
            state.resourceBrowser.showOnlyReferenced = referenced[0]
        }
        ImGui.separator()
    }

    private fun matchesFilters(resource: SkinResourceInfo): Boolean {
        val browser = state.resourceBrowser
        if (browser.selectedCategory != null && resource.category != browser.selectedCategory) return false
        if (browser.showOnlyUnresolved && resource.resolved) return false
        if (browser.showOnlyReferenced && resource.referencedBy.isEmpty()) return false
        val query = browser.query.trim()
        if (query.isEmpty()) return true
        return sequenceOf(resource.name, resource.type, resource.source.orEmpty())
            .plus(resource.details.asSequence().flatMap { (name, value) -> sequenceOf(name, value) })
            .any { value -> value.contains(query, ignoreCase = true) }
    }
}
