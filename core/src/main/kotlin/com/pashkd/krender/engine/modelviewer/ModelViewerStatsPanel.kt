package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui

/**
 * Shows loaded-model and viewer status information.
 */
class ModelViewerStatsPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the stats window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(ModelViewerPanelIds.ModelInfo)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, ModelViewerPanelIds.ModelInfo))
        eventLogger.observe(ModelViewerPanelIds.ModelInfo, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Loaded model")
        ImGui.text("%s", state.loadedModelPath)
        ImGui.text("Asset state: %s", state.loadingStatus)
        state.errorMessage?.let { error ->
            ImGui.text("Error: %s", error)
        }

        ImGui.separator()
        ImGui.text("Viewer")
        ImGui.text("Selected: %s", state.selectedModelPath)
        ImGui.text("Models: %d", state.availableModels.size)
        ImGui.text("Scale: %.2f", state.modelScale)
        ImGui.text("Wireframe: %s", if (state.wireframeEnabled) "on" else "off")
        ImGui.text("Triangles: %s", state.triangleCount?.toString() ?: if (state.isLoadingModel) "loading" else "none")
        ImGui.text("Camera: %s", state.cameraPosition)

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
