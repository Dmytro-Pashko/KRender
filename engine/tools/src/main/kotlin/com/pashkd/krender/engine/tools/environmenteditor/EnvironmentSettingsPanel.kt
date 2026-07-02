package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

/** Hosts editable runtime and background settings for the loaded Environment. */
class EnvironmentSettingsPanel(
    private val state: EnvironmentEditorState,
    logger: Logger,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val editor = EnvironmentSettingsEditor(state, logger)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Settings)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Settings, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Settings, layout.title)
        if (expanded) {
            state.environment?.let(editor::draw) ?: ImGui.text("No environment loaded.")
        }
        ImGui.end()
    }
}
