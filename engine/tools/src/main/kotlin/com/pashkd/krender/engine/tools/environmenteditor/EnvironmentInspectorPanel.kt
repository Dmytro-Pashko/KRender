package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class EnvironmentInspectorPanel(
    private val state: EnvironmentEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Inspector)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Inspector, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Inspector, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val env = state.environment
        if (env == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }
        drawInspector(env)
        ImGui.end()
    }

    private fun drawInspector(env: EnvironmentAsset) {
        val settings = env.settings
        labeledText("ID", env.id.path)
        labeledText("Name", env.name)
        labeledText("Type", env.type.name)
        labeledText("Version", env.version.toString())
        labeledText("Manifest", env.manifestPath)
        labeledText("Description", env.description ?: "(none)")
        labeledText("Exposure", "%.2f".format(settings.exposure))
        labeledText("Rotation", "%.1f°".format(settings.rotationDegrees))
        if (settings.backgroundMode == BackgroundMode.Skybox) {
            labeledText("Skybox Intensity", "%.2f".format(settings.skyboxIntensity))
        }
        labeledText("Diffuse Intensity", "%.2f".format(settings.diffuseIntensity))
        labeledText("Specular Intensity", "%.2f".format(settings.specularIntensity))
        labeledText("Background Mode", backgroundModeLabel(settings.backgroundMode))
        settings.backgroundColor?.let { color ->
            labeledText("Background Color", "(%.2f, %.2f, %.2f, %.2f)".format(color.r, color.g, color.b, color.a))
        }
        labeledText("Source Count", env.sources.size.toString())
        env.sources.forEachIndexed { index, source ->
            val prefix = "Source ${index + 1}"
            labeledText("$prefix ID", source.id)
            labeledText("$prefix Format", source.format.toString())
            labeledText("$prefix Role", buildString {
                append(source.role.toString())
                if (source.isDefault) append(" [default]")
            })
            labeledText("$prefix Path", source.path)
            source.resolution?.let { labeledText("$prefix Resolution", it) }
            source.colorSpace?.let { labeledText("$prefix Color Space", it) }
            source.dynamicRange?.let { labeledText("$prefix Dynamic Range", it) }
        }
        env.metadata.author?.let { labeledText("Author", it) }
        if (env.metadata.tags.isNotEmpty()) {
            labeledText("Tags", env.metadata.tags.joinToString(", "))
        }
        env.metadata.createdAt?.let { labeledText("Created", it) }
        env.metadata.modifiedAt?.let { labeledText("Modified", it) }
    }

    private fun backgroundModeLabel(mode: BackgroundMode): String =
        when (mode) {
            BackgroundMode.Skybox -> "Skybox"
            BackgroundMode.SolidColor -> "Solid Color"
            BackgroundMode.Transparent -> "Transparent"
            BackgroundMode.None -> "None"
        }

    private fun labeledText(
        label: String,
        value: String,
    ) {
        ImGui.text("$label: $value")
    }
}
