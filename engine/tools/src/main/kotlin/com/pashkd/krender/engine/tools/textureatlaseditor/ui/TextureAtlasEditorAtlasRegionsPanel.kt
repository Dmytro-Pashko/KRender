package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegionSortMode
import com.pashkd.krender.engine.tools.textureatlaseditor.computeRegionMetrics
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.resolveAtlasPreviewTexturePath
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureAtlasEditorAtlasRegionsPanel(
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
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Regions, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Regions, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val atlas = state.selectedAtlasDocument()
        if (atlas == null) {
            ImGui.textUnformatted("Select an atlas to inspect regions.")
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Search")
        ImGui.sameLine()
        ImGui.setNextItemWidth(220f)
        if (ImGui.inputText("##texture_atlas_editor_region_query", queryBuffer)) {
            state.atlasBrowser.query = readBuffer(queryBuffer)
        }
        ImGui.sameLine()
        if (ImGui.button("Clear##texture_atlas_editor_region_query_clear")) {
            state.atlasBrowser.query = ""
            writeBuffer(queryBuffer, "")
        }
        ImGui.separator()

        if (atlas.pages.isNotEmpty()) {
            val currentPage = state.selectedAtlasPageName ?: atlas.pages.first().name
            if (ImGui.beginCombo("Page##texture_atlas_editor_region_page", currentPage)) {
                atlas.pages.forEach { page ->
                    if (ImGui.selectable(page.name, currentPage == page.name)) {
                        operations.selectAtlasPage(page.name)
                    }
                }
                ImGui.endCombo()
            }
            if (ImGui.beginCombo("Sort##texture_atlas_editor_region_sort", state.atlasBrowser.sortMode.name)) {
                TextureAtlasRegionSortMode.entries.forEach { mode ->
                    if (ImGui.selectable(mode.name, state.atlasBrowser.sortMode == mode)) {
                        state.atlasBrowser.sortMode = mode
                    }
                }
                ImGui.endCombo()
            }
            drawPageSummary(atlas, currentPage)
        }
        drawRegionEditingControls()

        val regions = visibleRegions(atlas)
        ImGui.beginChild("texture_atlas_editor_regions_list", ImVec2(0f, 0f), true)
        regions.forEach { region ->
            val selected = state.selectedRegionId == region.id
            val sizeText = region.size?.let { "${it.first} x ${it.second}" } ?: "<unknown>"
            if (ImGui.selectable("${region.id.regionName} [$sizeText]##${region.id.regionName}_${region.id.pageName}", selected)) {
                operations.selectRegion(region.id)
            }
            if (ImGui.isItemHovered() && ImGui.run { imgui.MouseButton.Left.isDoubleClicked }) {
                operations.selectRegion(region.id)
                operations.fitSelectedRegion()
            }
        }
        if (regions.isEmpty()) {
            ImGui.textUnformatted("No atlas regions match the current filter.")
        }
        ImGui.endChild()
        ImGui.end()
    }

    private fun drawRegionEditingControls() {
        textLine("Region Source")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x - 92f)
        if (ImGui.inputText("##texture_atlas_editor_region_source", textureSourceBuffer)) {
            operations.setImportSourcePath(readBuffer(textureSourceBuffer))
        }
        ImGui.sameLine()
        if (ImGui.button("Open##texture_atlas_editor_region_source_open")) {
            operations.browseRegionTextureSource()
            writeBuffer(textureSourceBuffer, state.importExport.importSourcePath)
        }
        if (ImGui.button("Add Region##texture_atlas_editor_region_add")) {
            operations.addRegionSourceFromPath()
        }
        ImGui.sameLine()
        if (ImGui.button("Import & Add##texture_atlas_editor_region_import_add")) {
            operations.importAndAddRegionSource()
            writeBuffer(textureSourceBuffer, state.importExport.importSourcePath)
        }
        ImGui.sameLine()
        if (state.selectedRegionId == null) ImGui.beginDisabled()
        if (ImGui.button("Delete Region##texture_atlas_editor_region_delete")) {
            operations.deleteSelectedRegion()
        }
        if (state.selectedRegionId == null) ImGui.endDisabled()

        if (state.packing.includedTexturePaths.isNotEmpty()) {
            ImGui.separator()
            textLine("Pending texture regions")
            state.packing.includedTexturePaths.sorted().forEach { sourcePath ->
                textLine(sourcePath)
                ImGui.sameLine()
                if (ImGui.button("Remove##texture_atlas_editor_pending_remove_$sourcePath")) {
                    operations.removeAddedRegionSource(sourcePath)
                }
            }
        }
        ImGui.separator()
    }

    private fun syncTextureSourceBuffer() {
        if (readBuffer(textureSourceBuffer) != state.importExport.importSourcePath) {
            writeBuffer(textureSourceBuffer, state.importExport.importSourcePath)
        }
    }

    private fun visibleRegions(atlas: com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasDocument) =
        atlas.regions
            .filter { region ->
                val query = state.atlasBrowser.query
                (state.selectedAtlasPageName == null || region.id.pageName == state.selectedAtlasPageName) &&
                    (
                        query.isBlank() ||
                            region.id.regionName.contains(query, ignoreCase = true) ||
                            region.id.pageName.contains(query, ignoreCase = true)
                    )
            }.sortedWith(regionComparator())

    private fun regionComparator(): Comparator<com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion> =
        when (state.atlasBrowser.sortMode) {
            TextureAtlasRegionSortMode.Name -> compareBy({ it.id.regionName.lowercase() }, { it.index ?: Int.MAX_VALUE })
            TextureAtlasRegionSortMode.Index -> compareBy({ it.index ?: Int.MAX_VALUE }, { it.id.regionName.lowercase() })
            TextureAtlasRegionSortMode.AreaAscending ->
                compareBy<com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion> { region ->
                    region.size?.let { it.first * it.second } ?: Int.MAX_VALUE
                }.thenBy { it.id.regionName.lowercase() }
            TextureAtlasRegionSortMode.AreaDescending ->
                compareByDescending<com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion> { region ->
                    region.size?.let { it.first * it.second } ?: Int.MIN_VALUE
                }.thenBy { it.id.regionName.lowercase() }
        }

    private fun drawPageSummary(
        atlas: com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasDocument,
        pageName: String,
    ) {
        val page = atlas.pages.firstOrNull { candidate -> candidate.name == pageName } ?: return
        val texturePath = page.details["texturePath"] ?: resolveAtlasPreviewTexturePath(atlas.file.path, atlas, pageName) ?: "<unknown>"
        val exists = page.details["textureExists"] == "true"
        val pageRegionCount = atlas.regions.count { region -> region.id.pageName == pageName }
        textLine("Page texture: $texturePath")
        textLine("Page exists: ${if (exists) "yes" else "missing"}")
        val width = page.details["textureWidth"] ?: state.previewInfo.textureWidth.takeIf { state.previewInfo.atlasPageName == pageName && it > 0 }?.toString()
        val height = page.details["textureHeight"] ?: state.previewInfo.textureHeight.takeIf { state.previewInfo.atlasPageName == pageName && it > 0 }?.toString()
        textLine("Page size: ${width ?: "?"} x ${height ?: "?"}")
        textLine("Regions on page: $pageRegionCount")
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
