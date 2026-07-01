package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.*
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui

/**
 * Panel showing generated environment resources and exposing generation action buttons.
 */
class EnvironmentGeneratedMapsPanel(
    private val state: EnvironmentEditorState,
    private val generationService: EnvironmentGenerationService,
    private val logger: Logger,
) : UiPanel {

    override fun draw() {
        if (!ImGui.begin("Generated Maps")) {
            ImGui.end()
            return
        }
        val env = state.environment
        if (env == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }
        drawSkybox(env)
        drawIrradiance(env)
        drawRadiance(env)
        drawBrdfLut(env)
        ImGui.separator()
        drawGenerationActions(env)
        ImGui.end()
    }

    private fun drawSkybox(env: EnvironmentAsset) {
        ImGui.text("Skybox")
        val skybox = env.generated.skybox
        if (skybox == null) {
            ImGui.text("  (not defined)")
        } else {
            ImGui.text("  Layout: ${skybox.layout}")
            ImGui.text("  Resolution: ${skybox.resolution}px")
            ImGui.text("  Format: ${skybox.format}")
            if (skybox.faces.isEmpty()) {
                ImGui.text("  Faces: (none)")
            } else {
                for ((face, path) in skybox.faces) {
                    ImGui.text("  $face: $path")
                }
            }
        }
        ImGui.separator()
    }

    private fun drawIrradiance(env: EnvironmentAsset) {
        ImGui.text("Irradiance")
        val irradiance = env.generated.irradiance
        if (irradiance == null) {
            ImGui.text("  (not defined)")
        } else {
            ImGui.text("  Path: ${irradiance.path}")
            ImGui.text("  Resolution: ${irradiance.resolution}px")
            ImGui.text("  Format: ${irradiance.format}")
        }
        ImGui.separator()
    }

    private fun drawRadiance(env: EnvironmentAsset) {
        ImGui.text("Radiance")
        val radiance = env.generated.radiance
        if (radiance == null) {
            ImGui.text("  (not defined)")
        } else {
            ImGui.text("  Base Resolution: ${radiance.baseResolution}px")
            ImGui.text("  Mips: ${radiance.mips.size}")
            for (mip in radiance.mips) {
                ImGui.text("    Mip ${mip.level}: roughness=%.2f path=%s".format(mip.roughness, mip.path))
            }
        }
        ImGui.separator()
    }

    private fun drawBrdfLut(env: EnvironmentAsset) {
        ImGui.text("BRDF LUT")
        val brdfLut = env.generated.brdfLut
        if (brdfLut == null) {
            ImGui.text("  (not defined)")
        } else {
            ImGui.text("  Path: ${brdfLut.path}")
            ImGui.text("  Shared: ${brdfLut.shared}")
        }
    }

    private fun drawGenerationActions(env: EnvironmentAsset) {
        ImGui.text("Generation Actions")
        if (ImGui.button("Generate Skybox##env_gen_skybox")) {
            handleResult("Skybox", generationService.generateSkybox(env))
        }
        ImGui.sameLine()
        if (ImGui.button("Generate Irradiance##env_gen_irradiance")) {
            handleResult("Irradiance", generationService.generateIrradiance(env))
        }
        ImGui.sameLine()
        if (ImGui.button("Generate Radiance##env_gen_radiance")) {
            handleResult("Radiance", generationService.generateRadiance(env))
        }
        if (ImGui.button("Generate BRDF LUT##env_gen_brdf")) {
            handleResult("BRDF LUT", generationService.generateBrdfLut(env))
        }
        ImGui.sameLine()
        if (ImGui.button("Generate All##env_gen_all")) {
            handleResult("All", generationService.generateAll(env))
        }
    }

    private fun handleResult(
        name: String,
        result: EnvironmentGenerationResult,
    ) {
        when (result) {
            is EnvironmentGenerationResult.Success -> {
                state.statusMessage = "$name generation completed."
                logger.info(TAG) { "$name generation succeeded" }
            }
            is EnvironmentGenerationResult.Failed -> {
                state.statusMessage = "$name generation failed: ${result.message}"
                logger.error(TAG) { "$name generation failed: ${result.message}" }
            }
            is EnvironmentGenerationResult.NotImplemented -> {
                state.statusMessage = "$name generator is not implemented yet."
                logger.info(TAG) { "$name generator not implemented" }
            }
        }
    }

    companion object {
        private const val TAG = "EnvironmentGeneratedMapsPanel"
    }
}
