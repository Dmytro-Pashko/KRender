package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

/** Read-only flat summary of the loaded manifest and source metadata. */
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

    private fun drawInspector(env: Environment) {
        val settings = env.settings
        labeledText("ID", env.id)
        labeledText("Name", env.name)
        labeledText("Version", env.schemaVersion.toString())
        labeledText("Manifest", env.manifestPath)
        labeledText("Description", env.description ?: "(none)")
        labeledText("Exposure", "%.2f".format(settings.exposure))
        labeledText("Rotation", "%.1f°".format(settings.rotationDegrees))
        if (settings.backgroundMode == BackgroundMode.Skybox) {
            labeledText("Skybox Intensity", "%.2f".format(settings.skyboxIntensity))
        }
        labeledText("Diffuse Intensity", "%.2f".format(settings.diffuseIntensity))
        labeledText("Specular Intensity", "%.2f".format(settings.specularIntensity))
        labeledText("Background Mode", settings.backgroundMode.displayName)
        settings.backgroundColor?.let { color ->
            labeledText("Background Color", "(%.2f, %.2f, %.2f, %.2f)".format(color.r, color.g, color.b, color.a))
        }
        env.metadata.author?.let { labeledText("Author", it) }
        if (env.metadata.tags.isNotEmpty()) {
            labeledText("Tags", env.metadata.tags.joinToString(", "))
        }
        env.metadata.createdAt?.let { labeledText("Created", it) }
        env.metadata.modifiedAt?.let { labeledText("Modified", it) }
    }

    private fun labeledText(
        label: String,
        value: String,
    ) {
        ImGui.text("$label: $value")
    }
}
