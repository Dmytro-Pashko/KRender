package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.FontPreviewScales
import com.pashkd.krender.engine.tools.skin.FontPreviewTextHeight
import com.pashkd.krender.engine.tools.skin.MaxInlineResourcePreviewHeight
import com.pashkd.krender.engine.tools.skin.MinInlineResourcePreviewScale
import com.pashkd.krender.engine.tools.skin.ResourceSearchWidth
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewKind
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewZoomMode
import com.pashkd.krender.engine.tools.skin.formatPreviewScale
import com.pashkd.krender.engine.tools.skin.formatResourcePreviewZoom
import com.pashkd.krender.engine.tools.skin.parseResourceColor
import com.pashkd.krender.engine.tools.skin.readBuffer
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
        drawInlineResourcePreview()
    }

    private fun drawInlineResourcePreview() {
        val info = state.resourceVisualPreviewInfo
        when (info.kind) {
            SkinResourceVisualPreviewKind.Texture -> {
                val handle = info.texturePreviewHandle
                if (handle == null) {
                    ImGui.textWrapped("Image preview unavailable.")
                } else {
                    val availableWidth = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
                    val scale =
                        when (state.resourceVisualPreview.zoomMode) {
                            SkinResourceVisualPreviewZoomMode.Fit ->
                                minOf(
                                    availableWidth / handle.width.coerceAtLeast(1).toFloat(),
                                    MaxInlineResourcePreviewHeight / handle.height.coerceAtLeast(1).toFloat(),
                                ).coerceAtLeast(MinInlineResourcePreviewScale)

                            SkinResourceVisualPreviewZoomMode.Percent50 -> 0.5f
                            SkinResourceVisualPreviewZoomMode.Percent100 -> 1f
                            SkinResourceVisualPreviewZoomMode.Percent200 -> 2f
                        }
                    val width = handle.width * scale
                    val height = handle.height * scale
                    if (!ui.drawTexturePreview(handle, width, height)) {
                        ImGui.textWrapped("Image preview unavailable: UI backend rejected the texture handle.")
                    }
                }
            }

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
                button("Reset##skin_editor_resource_preview_zoom_reset") {
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
        if (resource.category == SkinResourceCategory.AtlasRegion) {
            ImGui.textWrapped("Region overlay follows the selected atlas region.")
            return
        }
        val atlasSource = resource.source.takeIf { resource.category == SkinResourceCategory.Atlas } ?: return
        val regions =
            state.loadResult.resourceIndex.atlasRegions
                .filter { region -> region.source == atlasSource }
                .map(SkinResourceInfo::name)
        if (regions.isEmpty()) return

        val selectedName = state.resourceVisualPreview.selectedAtlasRegionName?.takeIf { name -> name in regions }
        val comboLabel = selectedName ?: "None"
        if (ImGui.beginCombo("Region overlay##skin_editor_resource_preview_region", comboLabel)) {
            if (ImGui.selectable("None##skin_editor_resource_preview_region_none", selectedName == null)) {
                operations.selectAtlasRegionPreview(null)
            }
            regions.forEach { regionName ->
                if (ImGui.selectable("$regionName##skin_editor_resource_preview_region_$regionName", regionName == selectedName)) {
                    operations.selectAtlasRegionPreview(regionName)
                }
            }
            ImGui.endCombo()
        }
    }

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
