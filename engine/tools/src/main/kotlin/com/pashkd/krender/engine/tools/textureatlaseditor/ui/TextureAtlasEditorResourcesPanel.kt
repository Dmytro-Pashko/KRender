package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.ImageAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegionSortMode
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasResourceType
import com.pashkd.krender.engine.tools.textureatlaseditor.resolveAtlasPreviewTexturePath
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureAtlasEditorResourcesPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(BufferSize)
    private val importSourceBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        if (!synced) {
            writeBuffer(queryBuffer, state.atlasBrowser.query)
            writeBuffer(importSourceBuffer, currentImportSourcePath())
            synced = true
        }
        syncImportSourceBuffer()
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Regions)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Regions, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Regions, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val atlas = state.selectedAtlasDocument()
        if (atlas == null) {
            ImGui.textUnformatted("Select an atlas to inspect resources.")
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Search")
        ImGui.sameLine()
        ImGui.setNextItemWidth(220f)
        if (ImGui.inputText("##texture_atlas_editor_resource_query", queryBuffer)) {
            state.atlasBrowser.query = readBuffer(queryBuffer)
        }
        ImGui.sameLine()
        if (ImGui.button("Clear##texture_atlas_editor_resource_query_clear")) {
            state.atlasBrowser.query = ""
            writeBuffer(queryBuffer, "")
        }
        tooltipOnHover("Clears the current resource search filter.")
        ImGui.separator()

        if (atlas.pages.isNotEmpty()) {
            val currentPage = state.selectedAtlasPageName ?: atlas.pages.first().name
            if (ImGui.beginCombo("Page##texture_atlas_editor_resource_page", currentPage)) {
                atlas.pages.forEach { page ->
                    if (ImGui.selectable(page.name, currentPage == page.name)) {
                        operations.selectAtlasPage(page.name)
                    }
                }
                ImGui.endCombo()
            }
            tooltipOnHover("Filters the resource list to atlas items from the selected page.")
            if (ImGui.beginCombo("Sort##texture_atlas_editor_resource_sort", state.atlasBrowser.sortMode.label())) {
                TextureAtlasRegionSortMode.entries.forEach { mode ->
                    if (ImGui.selectable(mode.label(), state.atlasBrowser.sortMode == mode)) {
                        state.atlasBrowser.sortMode = mode
                    }
                }
                ImGui.endCombo()
            }
            tooltipOnHover("Changes how atlas resources are ordered in the list.")
            drawPageSummary(atlas, currentPage)
        }
        drawResourceEditingControls()

        val resources = visibleResources()
        textLine("Resources List")
        ImGui.beginChild("texture_atlas_editor_resources_list", ImVec2(0f, 0f), true)
        resources.forEach { resource ->
            val selected = state.resources.selectedResourceId == resource.id
            val label = "[${resource.type.label()}] ${resource.name}${resource.sizeLabel()}##${resource.id}"
            if (ImGui.selectable(label, selected)) {
                operations.selectResource(resource.id)
            }
            if (ImGui.isItemHovered() && ImGui.run { imgui.MouseButton.Left.isDoubleClicked }) {
                operations.selectResource(resource.id)
                if (resource.atlasRegionIdOrNull() != null) {
                    operations.fitSelectedRegion()
                }
            }
        }
        if (resources.isEmpty()) {
            ImGui.textUnformatted("No atlas resources match the current filter.")
        }
        ImGui.endChild()
        ImGui.end()
    }

    private fun drawResourceEditingControls() {
        ImGui.separator()
        textLine("Import Resource")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x - 92f)
        if (ImGui.inputText("##texture_atlas_editor_import_resource_source", importSourceBuffer)) {
            val path = readBuffer(importSourceBuffer)
            operations.setImportSourcePath(path)
            operations.setFontSourcePath(path)
        }
        ImGui.sameLine()
        if (ImGui.button("Browse##texture_atlas_editor_import_resource_browse")) {
            operations.browseImportResource()
            writeBuffer(importSourceBuffer, currentImportSourcePath())
        }
        if (ImGui.button("Add Existing Asset##texture_atlas_editor_resource_add")) {
            operations.addImageResourceFromPath()
        }
        tooltipOnHover("Adds the selected image file to the atlas resource list without copying it.")
        ImGui.sameLine()
        if (ImGui.button("Import Into Assets and Add##texture_atlas_editor_resource_import_add")) {
            operations.importAndAddImageResource()
            writeBuffer(importSourceBuffer, currentImportSourcePath())
        }
        tooltipOnHover("Copies the selected image into assets and adds it as an atlas resource.")
        if (ImGui.button("Add Font##texture_atlas_editor_resource_add_font")) {
            operations.importFontResourceFromPath()
            writeBuffer(importSourceBuffer, currentImportSourcePath())
        }
        tooltipOnHover("Imports a bitmap font descriptor and adds it to the atlas resources.")
        ImGui.separator()
        val selectedResource = state.selectedResource()
        if (selectedResource == null) ImGui.beginDisabled()
        if (ImGui.button("Delete Resource##texture_atlas_editor_resource_delete")) {
            operations.deleteSelectedResource()
        }
        tooltipOnHover("Removes the selected resource from the atlas resource list.")
        if (selectedResource == null) ImGui.endDisabled()
        ImGui.separator()
    }

    private fun syncImportSourceBuffer() {
        val currentPath = currentImportSourcePath()
        if (readBuffer(importSourceBuffer) != currentPath) {
            writeBuffer(importSourceBuffer, currentPath)
        }
    }

    private fun currentImportSourcePath(): String = state.importExport.fontSourcePath.ifBlank { state.importExport.importSourcePath }

    private fun visibleResources(): List<TextureAtlasResource> =
        state.resources.items
            .filter { resource ->
                val query = state.atlasBrowser.query
                val pageName = resource.atlasRegionIdOrNull()?.pageName
                (state.selectedAtlasPageName == null || pageName == null || pageName == state.selectedAtlasPageName) &&
                    (
                        query.isBlank() ||
                            resource.name.contains(query, ignoreCase = true) ||
                            pageName?.contains(query, ignoreCase = true) == true
                    )
            }.sortedWith(resourceComparator())

    private fun resourceComparator(): Comparator<TextureAtlasResource> =
        when (state.atlasBrowser.sortMode) {
            TextureAtlasRegionSortMode.Name -> compareBy({ it.name.lowercase() }, { it.atlasIndexOrMax() })
            TextureAtlasRegionSortMode.Type -> compareBy<TextureAtlasResource>({ it.type.sortOrder() }, { it.name.lowercase() })
        }

    private fun drawPageSummary(
        atlas: com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasDocument,
        pageName: String,
    ) {
        val page = atlas.pages.firstOrNull { candidate -> candidate.name == pageName } ?: return
        val texturePath = page.details["texturePath"] ?: resolveAtlasPreviewTexturePath(atlas.file.path, atlas, pageName) ?: "<unknown>"
        val exists = page.details["textureExists"] == "true"
        val pageRegionCount = atlas.regions.count { region -> region.id.pageName == pageName }
        val pageResourceCount =
            state.resources.items.count { resource ->
                resource.atlasRegionIdOrNull()?.pageName == pageName
            }
        textLine("Page texture: $texturePath")
        textLine("Page exists: ${if (exists) "yes" else "missing"}")
        val width =
            page.details["textureWidth"] ?: state.previewInfo.textureWidth
                .takeIf { state.previewInfo.atlasPageName == pageName && it > 0 }
                ?.toString()
        val height =
            page.details["textureHeight"] ?: state.previewInfo.textureHeight
                .takeIf { state.previewInfo.atlasPageName == pageName && it > 0 }
                ?.toString()
        textLine("Page size: ${width ?: "?"} x ${height ?: "?"}")
        textLine("Regions on page: $pageRegionCount")
        textLine("Resources on page: $pageResourceCount")
        state.selectedRegionId?.let { regionId ->
            atlas.regions.firstOrNull { region -> region.id == regionId }?.let { region ->
                val metrics = computeRegionMetrics(region, width?.toIntOrNull() ?: 0, height?.toIntOrNull() ?: 0)
                textLine("Selected area: ${metrics.areaPixels ?: 0}px")
            }
        }
        ImGui.separator()
    }

    companion object {
        private const val BufferSize = 256
    }
}

private fun TextureAtlasResource.typeLabel(): String = type.label()

private fun TextureAtlasResource.atlasRegionIdOrNull() =
    when (this) {
        is ImageAtlasResource -> atlasRegionId
        is NinePatchAtlasResource -> atlasRegionId
        else -> null
    }

private fun TextureAtlasResource.atlasIndexOrMax(): Int =
    when (this) {
        is ImageAtlasResource -> atlasIndex ?: Int.MAX_VALUE
        is NinePatchAtlasResource -> atlasIndex ?: Int.MAX_VALUE
        else -> Int.MAX_VALUE
    }

private fun TextureAtlasResource.sizeLabel(): String =
    when (this) {
        is ImageAtlasResource -> sourceWidth?.let { width -> sourceHeight?.let { height -> " [$width x $height]" } }.orEmpty()
        is NinePatchAtlasResource -> sourceWidth?.let { width -> sourceHeight?.let { height -> " [$width x $height]" } }.orEmpty()
        is FontAtlasResource -> " [${glyphCount}g]"
        else -> ""
    }

private fun TextureAtlasResourceType.label(): String =
    when (this) {
        TextureAtlasResourceType.Image -> "Image"
        TextureAtlasResourceType.NinePatch -> "NinePatch"
        TextureAtlasResourceType.Font -> "Font"
    }

private fun TextureAtlasResourceType.sortOrder(): Int =
    when (this) {
        TextureAtlasResourceType.Image -> 0
        TextureAtlasResourceType.NinePatch -> 1
        TextureAtlasResourceType.Font -> 2
    }

private fun TextureAtlasRegionSortMode.label(): String =
    when (this) {
        TextureAtlasRegionSortMode.Name -> "Name"
        TextureAtlasRegionSortMode.Type -> "Type"
    }

private data class ResourceRegionMetrics(
    val areaPixels: Int? = null,
)

private fun computeRegionMetrics(
    region: TextureAtlasRegion,
    textureWidth: Int,
    textureHeight: Int,
): ResourceRegionMetrics {
    val xy = region.xy
    val size = region.size
    val area = size?.let { dimensions -> dimensions.first * dimensions.second }
    if (xy == null || size == null || textureWidth <= 0 || textureHeight <= 0) {
        return ResourceRegionMetrics(areaPixels = area)
    }
    return ResourceRegionMetrics(areaPixels = area)
}
