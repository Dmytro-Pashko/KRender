package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

/** Primary file/session controls and current manifest status. */
class EnvironmentEditorControlPanel(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentEditorController,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Control)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Control, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Control, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (ImGui.button("Save##environment_editor_save")) {
            controller.save()
        }
        tooltipOnHover("Saves the current Environment manifest to disk.")
        ImGui.sameLine()
        if (ImGui.button("Revert##environment_editor_revert")) {
            controller.revert()
        }
        tooltipOnHover("Restores the Environment state from the last saved manifest.")
        ImGui.sameLine()
        if (ImGui.button("Reload##environment_editor_reload")) {
            controller.reload()
        }
        tooltipOnHover("Reloads the Environment file and refreshes validation state.")
        ImGui.sameLine()
        if (ImGui.button("Persist UI##environment_editor_persist_ui")) {
            controller.saveUiLayout()
        }
        tooltipOnHover("Saves the current panel layout for Environment Editor.")
        ImGui.sameLine()
        if (ImGui.button("Reset UI##environment_editor_reset_ui")) {
            controller.restoreUiLayout()
        }
        tooltipOnHover("Restores the default Environment Editor panel layout.")
        ImGui.sameLine()
        if (ImGui.button("Exit##environment_editor_exit")) {
            controller.requestExit()
        }
        tooltipOnHover("Closes the Environment Editor window.")

        ImGui.separator()
        val info = controller.fileInfo()
        textLine("Path: ${info.displayPath}")
        textLine("Resolved: ${info.resolvedPath ?: "<unresolved>"}")
        textLine("Size: ${formatByteCount(info.sizeBytes)}")
        textLine("Dirty: ${if (state.dirty) "yes" else "no"}")
        state.statusMessage?.let { textLine("Status: $it") }
        state.loadError?.let { textLine("Error: $it") }
        ImGui.end()
    }

    private fun textLine(value: String) {
        ImGui.textUnformatted(value)
    }

    private fun formatByteCount(bytes: Long?): String {
        val value = bytes ?: return "unknown"
        return when {
            value < 1024L -> "$value B"
            value < 1024L * 1024L -> "%.1f KB".format(value / 1024f)
            else -> "%.2f MB".format(value / (1024f * 1024f))
        }
    }
}
