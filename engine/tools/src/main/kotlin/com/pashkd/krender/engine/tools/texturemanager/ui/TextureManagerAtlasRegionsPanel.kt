package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.tools.texturemanager.selectedAtlasDocument
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
        }

        ImGui.beginChild("texture_manager_regions_list", ImVec2(0f, 0f), true)
        visibleRegions(atlas).forEach { region ->
            val selected = state.selectedRegionId == region.id
            val sizeText = region.size?.let { "${it.first} x ${it.second}" } ?: "<unknown>"
            if (ImGui.selectable("${region.id.regionName} [$sizeText]##${region.id.regionName}_${region.id.pageName}", selected)) {
                operations.selectRegion(region.id)
            }
        }
        if (visibleRegions(atlas).isEmpty()) {
            ImGui.textUnformatted("No atlas regions match the current filter.")
        }
        ImGui.endChild()
        ImGui.end()
    }

    private fun visibleRegions(atlas: com.pashkd.krender.engine.tools.texturemanager.TextureAtlasDocument) =
        atlas.regions.filter { region ->
            (state.selectedAtlasPageName == null || region.id.pageName == state.selectedAtlasPageName) &&
                (
                    state.atlasBrowser.query.isBlank() ||
                        region.id.regionName.contains(state.atlasBrowser.query, ignoreCase = true)
                )
        }

    companion object {
        private const val BufferSize = 256
    }
}

