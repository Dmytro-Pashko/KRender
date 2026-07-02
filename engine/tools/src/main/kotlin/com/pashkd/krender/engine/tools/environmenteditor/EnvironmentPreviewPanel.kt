package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewCamera
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewMode
import com.pashkd.krender.engine.ui.editor.UiPanel
import glm_.vec2.Vec2
import imgui.ImGui

/**
 * Environment preview controls and status.
 */
class EnvironmentPreviewPanel(
    private val state: EnvironmentEditorState,
) : UiPanel {
    override fun draw() {
        if (!ImGui.begin("Preview")) {
            ImGui.end()
            return
        }
        val env = state.environment
        if (env == null) {
            drawNoEnvironment()
            ImGui.end()
            return
        }
        drawPreview(env)
        ImGui.end()
    }

    private fun drawNoEnvironment() {
        if (state.loadError != null) {
            ImGui.text("Preview unavailable: manifest failed to load.")
            ImGui.text("Error: ${state.loadError}")
        } else {
            ImGui.text("No environment selected.")
        }
    }

    private fun drawPreview(env: EnvironmentAsset) {
        val preview = state.previewState
        ImGui.text("Preview Mode: ${preview.mode.name}")
        ImGui.separator()
        drawPreviewControls()
        ImGui.separator()
        drawViewportHint()
        ImGui.separator()
        drawPreviewStatus(env)
    }

    private fun drawPreviewControls() {
        val preview = state.previewState
        ImGui.text("Mode")
        ImGui.bulletText(EnvironmentPreviewMode.MaterialSpheres.name)
        ImGui.checkbox("Show Skybox##env_preview_show_skybox", preview::showSkybox)
        ImGui.checkbox("Show Ground##env_preview_show_ground", preview::showGround)
        ImGui.checkbox("Auto Rotate##env_preview_auto_rotate", preview::autoRotate)
        if (ImGui.button("Reset Camera##env_preview_reset_camera")) {
            EnvironmentPreviewCamera.reset(preview)
        }
    }

    private fun drawViewportHint() {
        ImGui.text("Visual Preview")
        ImGui.beginChild("env_preview_visual_area", Vec2(0f, 96f), true)
        ImGui.textWrapped("The Material Spheres preview renders in the main tool scene behind the ImGui panels.")
        ImGui.textWrapped("This keeps the preview visual without adding a separate render-to-texture viewport in this PR step.")
        ImGui.endChild()
    }

    private fun drawPreviewStatus(env: EnvironmentAsset) {
        ImGui.text("Environment: ${env.name}")
        ImGui.text("Type: ${env.type}")
        state.previewState.previewStatusMessage?.let(ImGui::textWrapped)
            ?: ImGui.textWrapped("Preview rig is active. Use the main scene background as the current visual render area.")
    }
}
