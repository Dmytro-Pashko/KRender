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
 * Presents render toggles, actions, and camera help for the viewer.
 */
class ModelViewerControlsPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the controls window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(ModelViewerPanelIds.Controls)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, ModelViewerPanelIds.Controls))
        eventLogger.observe(ModelViewerPanelIds.Controls, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Render")
        ImGui.checkbox("Wireframe", state::wireframeEnabled)

        ImGui.separator()
        ImGui.text("Actions")
        with(dsl) {
            button("Exit") {
                state.exitRequested = true
            }
        }

        ImGui.separator()
        ImGui.text("Camera")
        ImGui.bulletText("Move the mouse over the viewport to look around")
        ImGui.bulletText("W/A/S/D - Move camera")
        ImGui.bulletText("Ctrl/Shift - Move down or up")
        ImGui.bulletText("F1 - Toggle wireframe")

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
