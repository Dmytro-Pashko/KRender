package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui

/**
 * Displays loading progress while the currently requested model asset is not ready.
 */
class ModelViewerLoadingPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the loading window only while the active model asset is pending.
     */
    override fun draw() {
        if (!state.isLoadingModel) return

        val layout = layoutConfig.panels.getValue(ModelViewerPanelIds.Loading)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, ModelViewerPanelIds.Loading))
        eventLogger.observe(ModelViewerPanelIds.Loading, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Loading model asset")
        ImGui.text("%s", state.loadedModelPath)
        ImGui.separator()
        ImGui.text("Progress: %.0f%%", state.assetProgress * 100f)
        ImGui.text("%s", state.loadingStatus)
        ImGui.end()
    }

    /**
     * Applies the initial ImGui window position and size from layout config.
     */
    private fun applyWindowDefaults(layout: ImGuiPanelLayout) {
        ImGui.setNextWindowPos(Vec2(layout.x, layout.y), Cond.FirstUseEver, Vec2())
        ImGui.setNextWindowSize(Vec2(layout.width, layout.height), Cond.FirstUseEver)
    }
}
