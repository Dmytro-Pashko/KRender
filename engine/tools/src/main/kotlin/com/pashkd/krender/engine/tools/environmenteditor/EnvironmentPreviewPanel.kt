package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.assets.environment.ValidationStatus
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui

/**
 * Environment preview panel.
 *
 * MVP Stage A: shows a manifest/resource summary preview. The environment settings
 * and resource availability are displayed in a visual summary form.
 *
 * Stage B (future): connect to a real PBR preview scene with skybox background,
 * roughness ladder, and metallic/dielectric test spheres using the same glTF/PBR
 * backend as Model Viewer.
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
        ImGui.text("Environment Preview")
        ImGui.separator()

        ImGui.text("Name: ${env.name}")
        ImGui.text("Type: ${env.type}")

        ImGui.separator()
        drawSettingsSummary(env)

        ImGui.separator()
        drawResourceAvailability(env)

        ImGui.separator()
        drawPreviewStatus(env)
    }

    private fun drawSettingsSummary(env: EnvironmentAsset) {
        val s = env.settings
        ImGui.text("Settings Summary")
        ImGui.text("  Exposure: %.2f".format(s.exposure))
        ImGui.text("  Rotation: %.1f deg".format(s.rotationDegrees))
        ImGui.text("  Background: ${s.backgroundMode}")
        if (s.backgroundMode == BackgroundMode.Skybox) {
            ImGui.text("  Skybox: ${if (s.skyboxVisible) "visible" else "hidden"}")
            ImGui.text("  Skybox Intensity: %.2f".format(s.skyboxIntensity))
        }
        ImGui.text("  Diffuse: %.2f  Specular: %.2f".format(s.diffuseIntensity, s.specularIntensity))
    }

    private fun drawResourceAvailability(env: EnvironmentAsset) {
        ImGui.text("Resource Availability")
        val gen = env.generated
        val skybox = gen.skybox
        val irradiance = gen.irradiance
        val radiance = gen.radiance
        val brdfLut = gen.brdfLut
        ImGui.text("  Skybox: ${if (skybox != null && skybox.faces.isNotEmpty()) "available (${skybox.faces.size} faces)" else "MISSING"}")
        ImGui.text("  Irradiance: ${if (irradiance != null) "available" else "MISSING"}")
        ImGui.text("  Radiance: ${if (radiance != null && radiance.mips.isNotEmpty()) "available (${radiance.mips.size} mips)" else "MISSING"}")
        ImGui.text("  BRDF LUT: ${if (brdfLut != null) "available" else "MISSING"}")

        val sourcesCount = env.sources.size
        val defaultSource = env.sources.firstOrNull { it.isDefault }
        ImGui.text("  Sources: $sourcesCount")
        if (defaultSource != null) {
            ImGui.text("  Default source: ${defaultSource.id} (${defaultSource.format})")
        }
    }

    private fun drawPreviewStatus(env: EnvironmentAsset) {
        val validation = state.validation
        if (validation != null && validation.status != ValidationStatus.Valid) {
            ImGui.text("Preview may be incomplete: validation status is ${validation.status}")
            ImGui.text("${validation.issues.size} issue(s) — see Diagnostics panel")
        }

        val gen = env.generated
        val hasSkybox = gen.skybox != null && gen.skybox!!.faces.isNotEmpty()
        val hasIrradiance = gen.irradiance != null
        val hasRadiance = gen.radiance != null && gen.radiance!!.mips.isNotEmpty()

        if (!hasSkybox && !hasIrradiance && !hasRadiance) {
            ImGui.separator()
            ImGui.text("PBR preview unavailable: no generated IBL maps.")
            ImGui.text("Use 'Generated Maps' panel to trigger generation.")
        } else {
            ImGui.separator()
            ImGui.text("Real-time PBR preview will be available in a future update.")
            ImGui.text("Connect Environment Editor to the glTF/PBR backend for live preview.")
        }
    }
}
