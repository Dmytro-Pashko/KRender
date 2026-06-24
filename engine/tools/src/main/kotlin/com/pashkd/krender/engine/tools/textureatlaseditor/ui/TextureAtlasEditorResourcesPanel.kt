package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.ColorAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.ImageAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegionSortMode
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasResourceType
import com.pashkd.krender.engine.tools.textureatlaseditor.resolveAtlasPreviewTexturePath
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
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
    private val textureSourceBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        if (!synced) {
            writeBuffer(queryBuffer, state.atlasBrowser.query)
            writeBuffer(textureSourceBuffer, state.importExport.importSourcePath)
            synced = true
        }
        syncTextureSourceBuffer()
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Regions)
        val expanded = beginPanel(TextureAtlasEditorPanelIds.Regions, layout, layoutTracker)
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
            if (ImGui.beginCombo("Sort##texture_atlas_editor_resource_sort", state.atlasBrowser.sortMode.name)) {
                TextureAtlasRegionSortMode.entries.forEach { mode ->
                    if (ImGui.selectable(mode.name, state.atlasBrowser.sortMode == mode)) {
                        state.atlasBrowser.sortMode = mode
                    }
                }
                ImGui.endCombo()
            }
            drawPageSummary(atlas, currentPage)
        }
        drawResourceEditingControls()

        val resources = visibleResources()
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
        textLine("Image Resource Source")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x - 92f)
        if (ImGui.inputText("##texture_atlas_editor_resource_source", textureSourceBuffer)) {
            operations.setImportSourcePath(readBuffer(textureSourceBuffer))
        }
        ImGui.sameLine()
        if (ImGui.button("Open##texture_atlas_editor_resource_source_open")) {
            operations.browseRegionTextureSource()
            writeBuffer(textureSourceBuffer, state.importExport.importSourcePath)
        }
        if (ImGui.button("Add Image Resource##texture_atlas_editor_resource_add")) {
            operations.addImageResourceFromPath()
        }
        ImGui.sameLine()
        if (ImGui.button("Import & Add Image##texture_atlas_editor_resource_import_add")) {
            operations.importAndAddImageResource()
            writeBuffer(textureSourceBuffer, state.importExport.importSourcePath)
        }
        ImGui.sameLine()
        if (state.selectedResource() == null) ImGui.beginDisabled()
        if (ImGui.button("Delete Resource##texture_atlas_editor_resource_delete")) {
            operations.deleteSelectedResource()
        }
        if (state.selectedResource() == null) ImGui.endDisabled()
        ImGui.separator()
    }

    private fun syncTextureSourceBuffer() {
        if (readBuffer(textureSourceBuffer) != state.importExport.importSourcePath) {
            writeBuffer(textureSourceBuffer, state.importExport.importSourcePath)
        }
    }

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
            TextureAtlasRegionSortMode.Index -> compareBy({ it.atlasIndexOrMax() }, { it.name.lowercase() })
            TextureAtlasRegionSortMode.AreaAscending ->
                compareBy<TextureAtlasResource> { resource -> resource.areaOrMax() }.thenBy { it.name.lowercase() }
            TextureAtlasRegionSortMode.AreaDescending ->
                compareByDescending<TextureAtlasResource> { resource -> resource.areaOrMin() }.thenBy { it.name.lowercase() }
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
        val width = page.details["textureWidth"] ?: state.previewInfo.textureWidth.takeIf { state.previewInfo.atlasPageName == pageName && it > 0 }?.toString()
        val height = page.details["textureHeight"] ?: state.previewInfo.textureHeight.takeIf { state.previewInfo.atlasPageName == pageName && it > 0 }?.toString()
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

private fun TextureAtlasResource.areaOrMax(): Int =
    when (this) {
        is ImageAtlasResource -> (sourceWidth ?: Int.MAX_VALUE) * (sourceHeight ?: Int.MAX_VALUE)
        is NinePatchAtlasResource -> (sourceWidth ?: Int.MAX_VALUE) * (sourceHeight ?: Int.MAX_VALUE)
        is ColorAtlasResource -> width * height
        is FontAtlasResource -> Int.MAX_VALUE
    }

private fun TextureAtlasResource.areaOrMin(): Int =
    when (this) {
        is ImageAtlasResource -> (sourceWidth ?: Int.MIN_VALUE) * (sourceHeight ?: Int.MIN_VALUE)
        is NinePatchAtlasResource -> (sourceWidth ?: Int.MIN_VALUE) * (sourceHeight ?: Int.MIN_VALUE)
        is ColorAtlasResource -> width * height
        is FontAtlasResource -> Int.MIN_VALUE
    }

private fun TextureAtlasResource.sizeLabel(): String =
    when (this) {
        is ImageAtlasResource -> sourceWidth?.let { width -> sourceHeight?.let { height -> " [$width x $height]" } }.orEmpty()
        is NinePatchAtlasResource -> sourceWidth?.let { width -> sourceHeight?.let { height -> " [$width x $height]" } }.orEmpty()
        is ColorAtlasResource -> " [$width x $height]"
        is FontAtlasResource -> ""
    }

private fun TextureAtlasResourceType.label(): String =
    when (this) {
        TextureAtlasResourceType.Image -> "Image"
        TextureAtlasResourceType.NinePatch -> "NinePatch"
        TextureAtlasResourceType.Font -> "Font"
        TextureAtlasResourceType.Color -> "Color"
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

private fun beginPanel(
    panelId: String,
    layout: ImGuiPanelLayout,
    tracker: ImGuiLayoutRuntimeTracker,
): Boolean {
    tracker.consumeRestoreLayout(panelId)?.let { restored ->
        ImGui.setNextWindowPos(ImVec2(restored.x, restored.y))
        ImGui.setNextWindowSize(ImVec2(restored.width, restored.height))
    } ?: run {
        ImGui.setNextWindowPos(ImVec2(layout.x, layout.y), imgui.Cond.FirstUseEver)
        ImGui.setNextWindowSize(ImVec2(layout.width, layout.height), imgui.Cond.FirstUseEver)
    }
    val expanded = ImGui.begin("${layout.title}###$panelId")
    tracker.capture(panelId)
    return expanded
}
