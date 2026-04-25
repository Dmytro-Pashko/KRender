package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.ui.UiPanel
import imgui.ImGui

class TerrainEditorControlPanel(
    private val state: TerrainEditorState,
) : UiPanel {
    override fun draw() {
        if (!ImGui.begin("Terrain Controls")) {
            ImGui.end()
            return
        }

        ImGui.text("Viewport")
        ImGui.checkbox("Draw X/Y/Z axis", state::showAxes)
        ImGui.checkbox("Wireframe", state::wireframeEnabled)
        ImGui.end()
    }
}
