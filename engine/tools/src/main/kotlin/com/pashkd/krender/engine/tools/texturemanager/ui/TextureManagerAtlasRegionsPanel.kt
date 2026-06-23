package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.tools.texturemanager.TextureAtlasRegionSortMode
import com.pashkd.krender.engine.tools.texturemanager.computeRegionMetrics
import com.pashkd.krender.engine.tools.texturemanager.selectedAtlasDocument
import com.pashkd.krender.engine.tools.texturemanager.resolveAtlasPreviewTexturePath
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureManagerAtlasRegionsPanel(
    private val state: TextureManagerState,
    private val operations: TextureManagerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        if (!synced) {
            writeBuffer(queryBuffer, state.atlasBrowser.query)
            synced = true
        }
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Regions)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Regions, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Regions, layout.title)
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
        if (ImGui.inputText("##texture_manager_region_query", queryBuffer)) {
            state.atlasBrowser.query = readBuffer(queryBuffer)
        }
        ImGui.sameLine()
        if (ImGui.button("Clear##texture_manager_region_query_clear")) {
            state.atlasBrowser.query = ""
            writeBuffer(queryBuffer, "")
        }
        ImGui.separator()

        if (atlas.pages.isNotEmpty()) {
            val currentPage = state.selectedAtlasPageName ?: atlas.pages.first().name
            if (ImGui.beginCombo("Page##texture_manager_region_page", currentPage)) {
                atlas.pages.forEach { page ->
                    if (ImGui.selectable(page.name, currentPage == page.name)) {
                        operations.selectAtlasPage(page.name)
                    }
                }
                ImGui.endCombo()
            }
            if (ImGui.beginCombo("Sort##texture_manager_region_sort", state.atlasBrowser.sortMode.name)) {
                TextureAtlasRegionSortMode.entries.forEach { mode ->
                    if (ImGui.selectable(mode.name, state.atlasBrowser.sortMode == mode)) {
                        state.atlasBrowser.sortMode = mode
                    }
                }
                ImGui.endCombo()
            }
            drawPageSummary(atlas, currentPage)
        }

        val regions = visibleRegions(atlas)
        ImGui.beginChild("texture_manager_regions_list", ImVec2(0f, 0f), true)
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

    private fun visibleRegions(atlas: com.pashkd.krender.engine.tools.texturemanager.TextureAtlasDocument) =
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

    private fun regionComparator(): Comparator<com.pashkd.krender.engine.tools.texturemanager.TextureAtlasRegion> =
        when (state.atlasBrowser.sortMode) {
            TextureAtlasRegionSortMode.Name -> compareBy({ it.id.regionName.lowercase() }, { it.index ?: Int.MAX_VALUE })
            TextureAtlasRegionSortMode.Index -> compareBy({ it.index ?: Int.MAX_VALUE }, { it.id.regionName.lowercase() })
            TextureAtlasRegionSortMode.AreaAscending ->
                compareBy<com.pashkd.krender.engine.tools.texturemanager.TextureAtlasRegion> { region ->
                    region.size?.let { it.first * it.second } ?: Int.MAX_VALUE
                }.thenBy { it.id.regionName.lowercase() }
            TextureAtlasRegionSortMode.AreaDescending ->
                compareByDescending<com.pashkd.krender.engine.tools.texturemanager.TextureAtlasRegion> { region ->
                    region.size?.let { it.first * it.second } ?: Int.MIN_VALUE
                }.thenBy { it.id.regionName.lowercase() }
        }

    private fun drawPageSummary(
        atlas: com.pashkd.krender.engine.tools.texturemanager.TextureAtlasDocument,
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
