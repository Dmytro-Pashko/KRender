package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerToolMode
import com.pashkd.krender.engine.tools.texturemanager.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.texturemanager.formatZoomMode
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider

class TextureManagerToolsPanel(
    private val state: TextureManagerState,
    private val operations: TextureManagerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Tools)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Tools, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (ImGui.beginCombo("Mode##texture_manager_tool_mode", state.toolMode.name)) {
            TextureManagerToolMode.entries.forEach { mode ->
                if (ImGui.selectable(mode.name, state.toolMode == mode)) {
                    operations.setToolMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.beginCombo("Zoom##texture_manager_zoom_mode", formatZoomMode(state.preview.zoomMode))) {
            TexturePreviewZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), state.preview.zoomMode == mode)) {
                    operations.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (state.preview.zoomMode == TexturePreviewZoomMode.Custom) {
            if (slider("Custom Zoom##texture_manager_custom_zoom", state.preview::customZoom, 0.05f, 8f, "%.2f", SliderFlag.AlwaysClamp)) {
                operations.setPreviewZoom(state.preview.customZoom)
            }
        }
        if (ImGui.button("Fit##texture_manager_fit")) {
            operations.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("100%##texture_manager_zoom_100")) {
            operations.setZoomMode(TexturePreviewZoomMode.Percent100)
        }
        ImGui.sameLine()
        if (ImGui.button("200%##texture_manager_zoom_200")) {
            operations.setZoomMode(TexturePreviewZoomMode.Percent200)
        }
        if (ImGui.button("Reset Camera##texture_manager_reset_camera")) {
            operations.resetPreviewCamera()
        }

        val checker = booleanArrayOf(state.preview.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##texture_manager_checker", checker)) {
            operations.setShowCheckerboard(checker[0])
        }
        val grid = booleanArrayOf(state.preview.showGrid)
        if (ImGui.checkbox("Grid##texture_manager_grid", grid)) {
            operations.setShowGrid(grid[0])
        }
        val bounds = booleanArrayOf(state.preview.showBounds)
        if (ImGui.checkbox("Bounds##texture_manager_bounds", bounds)) {
            operations.setShowBounds(bounds[0])
        }

        ImGui.separator()
        textLine("Mouse wheel: zoom")
        textLine("RMB drag or Pan mode: pan")
        textLine("LMB on region: select")
        ImGui.end()
    }
}
