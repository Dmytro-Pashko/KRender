package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui

/**
 * Read-only inspector panel showing all Environment asset fields.
 */
class EnvironmentInspectorPanel(
    private val state: EnvironmentEditorState,
) : UiPanel {

    override fun draw() {
        if (!ImGui.begin("Inspector")) {
            ImGui.end()
            return
        }
        val env = state.environment
        if (env == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }
        drawIdentity(env)
        drawSettings(env)
        drawSourcesSummary(env)
        drawGeneratedSummary(env)
        drawGenerationSettings(env)
        drawMetadata(env)
        ImGui.end()
    }

    private fun drawIdentity(env: EnvironmentAsset) {
        if (ImGui.collapsingHeader("Identity")) {
            labeledText("ID", env.id.path)
            labeledText("Name", env.name)
            labeledText("Type", env.type.name)
            labeledText("Version", env.version.toString())
            labeledText("Manifest", env.manifestPath)
            labeledText("Description", env.description ?: "(none)")
        }
    }

    private fun drawSettings(env: EnvironmentAsset) {
        if (ImGui.collapsingHeader("Settings")) {
            val s = env.settings
            labeledText("Exposure", "%.2f".format(s.exposure))
            labeledText("Rotation", "%.1f°".format(s.rotationDegrees))
            labeledText("Skybox Visible", s.skyboxVisible.toString())
            labeledText("Skybox Intensity", "%.2f".format(s.skyboxIntensity))
            labeledText("Diffuse Intensity", "%.2f".format(s.diffuseIntensity))
            labeledText("Specular Intensity", "%.2f".format(s.specularIntensity))
            labeledText("Background Mode", s.backgroundMode.name)
            val bg = s.backgroundColor
            if (bg != null) {
                labeledText("Background Color", "(%.2f, %.2f, %.2f, %.2f)".format(bg.r, bg.g, bg.b, bg.a))
            }
        }
    }

    private fun drawSourcesSummary(env: EnvironmentAsset) {
        if (ImGui.collapsingHeader("Sources (${env.sources.size})")) {
            for (src in env.sources) {
                val defaultTag = if (src.isDefault) " [default]" else ""
                ImGui.bulletText("${src.id}: ${src.format} ${src.role}$defaultTag")
                ImGui.text("    Path: ${src.path}")
                src.resolution?.let { ImGui.text("    Resolution: $it") }
                src.colorSpace?.let { ImGui.text("    Color Space: $it") }
                src.dynamicRange?.let { ImGui.text("    Dynamic Range: $it") }
            }
            if (env.sources.isEmpty()) {
                ImGui.text("(no sources)")
            }
        }
    }

    private fun drawGeneratedSummary(env: EnvironmentAsset) {
        if (ImGui.collapsingHeader("Generated Resources")) {
            val gen = env.generated
            val skybox = gen.skybox
            val irradiance = gen.irradiance
            val radiance = gen.radiance
            val brdfLut = gen.brdfLut
            ImGui.text("Skybox: ${if (skybox != null) "${skybox.faces.size} faces, ${skybox.resolution}px" else "(none)"}")
            ImGui.text("Irradiance: ${if (irradiance != null) "${irradiance.resolution}px" else "(none)"}")
            ImGui.text("Radiance: ${if (radiance != null) "${radiance.mips.size} mips, base ${radiance.baseResolution}px" else "(none)"}")
            ImGui.text("BRDF LUT: ${if (brdfLut != null) brdfLut.path else "(none)"}")
        }
    }

    private fun drawGenerationSettings(env: EnvironmentAsset) {
        val gs = env.generation ?: return
        if (ImGui.collapsingHeader("Generation Settings")) {
            labeledText("Source Variant", gs.sourceVariantId)
            labeledText("Generator", gs.generator)
            labeledText("Generator Version", gs.generatorVersion)
            labeledText("Generated At", gs.generatedAt ?: "(never)")
            labeledText("Skybox Resolution", "${gs.skyboxResolution}px")
            labeledText("Irradiance Resolution", "${gs.irradianceResolution}px")
            labeledText("Radiance Resolution", "${gs.radianceResolution}px")
            labeledText("Radiance Mip Count", gs.radianceMipCount.toString())
            labeledText("Output Format", gs.outputFormat)
        }
    }

    private fun drawMetadata(env: EnvironmentAsset) {
        val meta = env.metadata
        if (meta.author == null && meta.tags.isEmpty() && meta.createdAt == null && meta.modifiedAt == null) return
        if (ImGui.collapsingHeader("Metadata")) {
            meta.author?.let { labeledText("Author", it) }
            if (meta.tags.isNotEmpty()) labeledText("Tags", meta.tags.joinToString(", "))
            meta.createdAt?.let { labeledText("Created", it) }
            meta.modifiedAt?.let { labeledText("Modified", it) }
        }
    }

    private fun labeledText(
        label: String,
        value: String,
    ) {
        ImGui.text("$label: $value")
    }
}
