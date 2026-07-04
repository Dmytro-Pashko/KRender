package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.Environment
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
import imgui.SliderFlag
import imgui.api.slider

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

    private fun drawPreview(env: Environment) {
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
        if (slider("Camera Distance##env_preview_camera_distance", preview::cameraDistance, 2f, 20f, "%.2f", SliderFlag.AlwaysClamp)) {
            state.statusMessage = "Preview camera distance set to ${"%.2f".format(preview.cameraDistance)}."
        }
        tooltipOnHover("Moves the preview camera closer to or farther from the test model without reloading the Environment.")
        if (slider("Yaw##env_preview_camera_yaw", preview::cameraYawDegrees, -180f, 180f, "%.1f", SliderFlag.AlwaysClamp)) {
            state.statusMessage = "Preview camera yaw set to ${"%.1f".format(preview.cameraYawDegrees)} degrees."
        }
        tooltipOnHover("Adjusts the preview camera orbit yaw around the test model.")
        if (slider("Pitch##env_preview_camera_pitch", preview::cameraPitchDegrees, -80f, 80f, "%.1f", SliderFlag.AlwaysClamp)) {
            state.statusMessage = "Preview camera pitch set to ${"%.1f".format(preview.cameraPitchDegrees)} degrees."
        }
        tooltipOnHover("Adjusts the preview camera orbit pitch while keeping the same focus target.")
        if (ImGui.button("Reset Camera##env_preview_reset_camera")) {
            EnvironmentPreviewCamera.reset(preview)
        }
        tooltipOnHover("Restores the default preview camera position and angles.")
    }

    private fun drawPreviewStatus(
        env: Environment,
        availability: EnvironmentPreviewAvailability,
    ) {
        ImGui.text("Environment: ${env.name}")
        ImGui.text("Environment Id: ${env.id}")
        ImGui.text("Type: ${env.type}")
        drawValidationStatus()
        ImGui.separator()
        ImGui.text("Environment Resources")
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
            ImGui.textWrapped("Preview uses the current environment manifest and its referenced IBL resources.")
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
