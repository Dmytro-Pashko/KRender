package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

/** Read-only overview of generated resources referenced by the Environment manifest. */
class EnvironmentToolsPanel(
    private val state: EnvironmentEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Tools)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Tools, layout.title)
        if (expanded) {
            state.environment?.let(::drawResources) ?: ImGui.text("No environment loaded.")
        }
        ImGui.end()
    }

    private fun drawResources(environment: EnvironmentAsset) {
        drawSkybox(environment)
        ImGui.separator()
        drawIrradiance(environment)
        ImGui.separator()
        drawRadiance(environment)
        ImGui.separator()
        drawBrdfLut(environment)
    }

    private fun drawSkybox(environment: EnvironmentAsset) {
        ImGui.text("Skybox")
        val skybox = environment.generated.skybox
        if (skybox == null) {
            ImGui.text("  (not defined)")
            return
        }
        ImGui.text("  Layout: ${skybox.layout}")
        ImGui.text("  Resolution: ${skybox.resolution}px")
        ImGui.text("  Format: ${skybox.format}")
        if (skybox.faces.isEmpty()) {
            ImGui.text("  Faces: (none)")
        } else {
            skybox.faces.forEach { (face, path) -> ImGui.text("  $face: $path") }
        }
    }

    private fun drawIrradiance(environment: EnvironmentAsset) {
        ImGui.text("Irradiance")
        val irradiance = environment.generated.irradiance
        if (irradiance == null) {
            ImGui.text("  (not defined)")
            return
        }
        ImGui.text("  Path: ${irradiance.path}")
        ImGui.text("  Resolution: ${irradiance.resolution}px")
        ImGui.text("  Format: ${irradiance.format}")
    }

    private fun drawRadiance(environment: EnvironmentAsset) {
        ImGui.text("Radiance")
        val radiance = environment.generated.radiance
        if (radiance == null) {
            ImGui.text("  (not defined)")
            return
        }
        ImGui.text("  Base Resolution: ${radiance.baseResolution}px")
        ImGui.text("  Mips: ${radiance.mips.size}")
        radiance.mips.forEach { mip ->
            ImGui.text("    Mip ${mip.level}: roughness=%.2f path=%s".format(mip.roughness, mip.path))
        }
    }

    private fun drawBrdfLut(environment: EnvironmentAsset) {
        ImGui.text("BRDF LUT")
        val brdfLut = environment.generated.brdfLut
        if (brdfLut == null) {
            ImGui.text("  (not defined)")
            return
        }
        ImGui.text("  Path: ${brdfLut.path}")
        ImGui.text("  Shared: ${brdfLut.shared}")
    }
}
