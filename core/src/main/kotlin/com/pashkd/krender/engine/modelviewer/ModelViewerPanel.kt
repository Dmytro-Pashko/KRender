package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.dsl

/**
 * Renders the model list and selection actions for the viewer.
 */
class ModelViewerPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the list window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(ModelViewerPanelIds.ModelList)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(layout.title)
        eventLogger.observe(ModelViewerPanelIds.ModelList, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Available models")
        if (state.availableModels.isEmpty()) {
            ImGui.text("No models discovered.")
        } else {
            state.availableModels.forEachIndexed { index, model ->
                if (ImGui.selectable(model.path, index == state.selectedModelIndex)) {
                    state.selectedModelIndex = index
                }
            }
        }

        ImGui.separator()
        ImGui.text("Selection")
        ImGui.text(state.selectedModelPath)
        with(dsl) {
            button("Load selected") {
                state.loadSelectedModelRequested = true
            }
        }

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
