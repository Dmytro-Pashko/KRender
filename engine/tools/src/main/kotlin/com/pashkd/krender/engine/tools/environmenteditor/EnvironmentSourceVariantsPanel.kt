package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentSourceVariant
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

/**
 * Panel showing environment source variants in a table/list layout.
 */
class EnvironmentSourceVariantsPanel(
    private val state: EnvironmentEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Sources)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Sources, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Sources, layout.title)
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
        drawVariants(env)
        ImGui.end()
    }

    private fun drawVariants(env: Environment) {
        if (env.sources.isEmpty()) {
            ImGui.text("No source variants defined.")
            return
        }
        ImGui.text("Source Variants (${env.sources.size})")
        ImGui.separator()
        for ((index, src) in env.sources.withIndex()) {
            drawVariant(env, src, index)
            if (index < env.sources.size - 1) ImGui.separator()
        }
    }

    private fun drawVariant(
        env: Environment,
        src: EnvironmentSourceVariant,
        index: Int,
    ) {
        val defaultLabel = if (src.isDefault) " [DEFAULT]" else ""
        ImGui.text("${src.id}$defaultLabel")
        ImGui.text("  Path: ${src.path}")
        ImGui.text("  Format: ${src.format}")
        ImGui.text("  Role: ${src.role}")
        src.resolution?.let { ImGui.text("  Resolution: $it") }
        src.colorSpace?.let { ImGui.text("  Color Space: $it") }
        src.dynamicRange?.let { ImGui.text("  Dynamic Range: $it") }

        if (!src.isDefault) {
            if (ImGui.button("Set as Default##env_src_default_$index")) {
                val updatedSources = env.sources.map { it.copy(isDefault = it.id == src.id) }
                state.updateEnvironment { it.copy(sources = updatedSources) }
            }
            tooltipOnHover("Makes this source variant the default input for the Environment.")
        }
    }
}
