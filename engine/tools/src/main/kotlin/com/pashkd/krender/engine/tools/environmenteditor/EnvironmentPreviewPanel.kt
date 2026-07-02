package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.assets.environment.ValidationStatus
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewAvailability
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewCamera
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewController
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

/**
 * Environment preview controls and status.
 */
class EnvironmentPreviewPanel(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentPreviewController,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Preview)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Preview, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Preview, layout.title)
        if (!expanded) {
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
        val availability = controller.availability(env)
        drawPreviewControls()
        ImGui.separator()
        drawPreviewStatus(env, availability)
    }

    private fun drawPreviewControls() {
        val preview = state.previewState
        ImGui.text("Test Models")
        EnvironmentEditorConfig.testModels.forEach { model ->
            val active = if (model.assetPath == controller.previewModel.path) " [active]" else ""
            ImGui.bulletText("${model.displayName}$active")
        }
        tooltipOnHover("Shows the configured Environment preview test models.")
        ImGui.checkbox("Auto Rotate##env_preview_auto_rotate", preview::autoRotate)
        tooltipOnHover("Slowly rotates the preview camera around the scene.")
        if (ImGui.button("Reset Camera##env_preview_reset_camera")) {
            EnvironmentPreviewCamera.reset(preview)
        }
        tooltipOnHover("Restores the default preview camera position and angles.")
    }

    private fun drawPreviewStatus(
        env: EnvironmentAsset,
        availability: EnvironmentPreviewAvailability,
    ) {
        ImGui.text("Environment: ${env.name}")
        ImGui.text("Environment Id: ${env.id.path}")
        ImGui.text("Type: ${env.type}")
        drawValidationStatus()
        ImGui.separator()
        ImGui.text("Generated Resources")
        ImGui.text("Skybox: ${availabilityLabel(availability.hasSkybox)}")
        ImGui.text("Irradiance: ${availabilityLabel(availability.hasIrradiance)}")
        ImGui.text("Radiance: ${availabilityLabel(availability.hasRadiance)}")
        ImGui.text("BRDF LUT: ${availabilityLabel(availability.hasBrdfLut)}")
        ImGui.separator()
        ImGui.text("Background Mode: ${env.settings.backgroundMode.displayName}")
        ImGui.textWrapped("Fallback: ${availability.fallbackMode}")
        ImGui.textWrapped(controller.liveStatusMessage(env))
        availability.warnings.forEach(ImGui::textWrapped)
        if (availability.warnings.isEmpty()) {
            ImGui.textWrapped("Preview uses the current environment manifest and generated IBL maps.")
        }
    }

    private fun drawValidationStatus() {
        val validation = state.validation
        if (validation == null) {
            ImGui.text("Validation: unavailable")
            return
        }
        ImGui.text("Validation: ${validation.status}")
        if (validation.status != ValidationStatus.Valid) {
            ImGui.text("${validation.issues.size} issue(s); see Diagnostics for details.")
        }
    }

    private fun availabilityLabel(value: Boolean): String = if (value) "available" else "missing"
}
