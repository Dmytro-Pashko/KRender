package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPlan
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureAtlasEditorPackingPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Packing)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Packing, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Packing, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val plan = state.selectedPackingPlan()
        if (plan == null) {
            ImGui.textUnformatted("Pack Texture Atlas from the Tools panel to preview planned pages and regions.")
            ImGui.end()
            return
        }

        if (ImGui.beginCombo("Page##texture_atlas_editor_packing_page", state.selectedPackingPage()?.name ?: "<none>")) {
            plan.pages.forEachIndexed { index, page ->
                if (ImGui.selectable(page.name, state.packing.selectedPageIndex == index)) {
                    operations.selectPackingPage(index)
                }
            }
            ImGui.endCombo()
        }
        textLine("Pages: ${plan.pages.size}")
        ImGui.sameLine()
        textLine("Packed: ${plan.packedRegionCount}")
        ImGui.sameLine()
        textLine("Skipped: ${plan.skippedCount}")

        state.selectedPackingPage()?.let { page ->
            drawPagePreview(page)
            ImGui.separator()
            ImGui.beginChild("texture_atlas_editor_packing_regions", ImVec2(0f, 72f), true)
            page.regions.forEach { region ->
                val selected = state.packing.selectedRegionId == region.id
                val label = "${region.displayName} [${region.width}x${region.height}]##${region.id}"
                if (ImGui.selectable(label, selected)) {
                    operations.selectPackingRegion(region.id)
                }
            }
            if (page.regions.isEmpty()) {
                ImGui.textUnformatted("No packed regions on this page.")
            }
            ImGui.endChild()
        }
        if (state.packing.lastResult.diagnostics.isNotEmpty()) {
            ImGui.separator()
            ImGui.beginChild("texture_atlas_editor_packing_diagnostics", ImVec2(0f, 0f), true)
            state.packing.lastResult.diagnostics.forEach { diagnostic ->
                textLine("[${diagnostic.severity.name}] ${diagnostic.message}")
                diagnostic.sourcePath?.let { path -> textLine("  $path") }
            }
            ImGui.endChild()
        }

        ImGui.end()
    }

    private fun drawPagePreview(page: TextureAtlasPackingPage) {
        ImGui.beginChild("texture_atlas_editor_packing_canvas", ImVec2(0f, 90f), true)
        val drawList = ImGui.windowDrawList
        val origin = ImGui.cursorScreenPos
        val avail = ImGui.contentRegionAvail
        val scale = minOf((avail.x - 16f) / page.width.toFloat(), (avail.y - 16f) / page.height.toFloat()).coerceAtLeast(0.05f)
        val pageWidth = page.width * scale
        val pageHeight = page.height * scale
        val pageX = origin.x + 8f
        val pageY = origin.y + 8f
        drawList.addRect(ImVec2(pageX, pageY), ImVec2(pageX + pageWidth, pageY + pageHeight), PageColor, 0f, thickness = 2f)
        page.regions.forEach { region ->
            val minX = pageX + region.x * scale
            val minY = pageY + region.y * scale
            val maxX = minX + region.width * scale
            val maxY = minY + region.height * scale
            val color = if (state.packing.selectedRegionId == region.id) SelectedRegionColor else RegionColor
            drawList.addRectFilled(ImVec2(minX, minY), ImVec2(maxX, maxY), color)
            drawList.addRect(ImVec2(minX, minY), ImVec2(maxX, maxY), RegionOutlineColor, 0f, thickness = 1.5f)
        }
        ImGui.endChild()
    }

    companion object {
        private val PageColor = packImColor(255, 255, 255, 220)
        private val RegionColor = packImColor(77, 184, 255, 120)
        private val SelectedRegionColor = packImColor(255, 184, 77, 180)
        private val RegionOutlineColor = packImColor(255, 255, 255, 200)
    }
}
