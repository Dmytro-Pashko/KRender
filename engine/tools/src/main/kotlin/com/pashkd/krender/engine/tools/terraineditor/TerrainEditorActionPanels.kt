package com.pashkd.krender.engine.tools.terraineditor

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import com.pashkd.krender.engine.ui.editor.drawImGuiLayoutLockButton
import imgui.ImGui

class TerrainEditorControlPanel(
    private val state: TerrainEditorState,
    private val layout: ImGuiLayoutConfig,
    private val tracker: ImGuiLayoutRuntimeTracker,
    private val events: ImGuiWindowEventLogger,
    private val saveLayout: () -> Unit,
    private val restoreLayout: () -> Unit,
    private val exit: () -> Unit,
) : UiPanel {
    private enum class PendingAction { New, Reload, Regenerate, Exit }
    private var pendingAction: PendingAction? = null

    override fun draw() {
        if (!begin(TerrainEditorPanelIds.Control, layout, tracker, events)) { ImGui.end(); return }
        if (ImGui.button("New Terrain")) request(PendingAction.New)
        ImGui.sameLine()
        if (ImGui.button("Reload")) request(PendingAction.Reload)
        ImGui.sameLine()
        if (ImGui.button("Save")) state.saveTerrainRequested = true
        if (ImGui.button("Regenerate")) request(PendingAction.Regenerate)
        ImGui.sameLine()
        if (ImGui.button("Save Layout")) saveLayout()
        ImGui.sameLine()
        drawImGuiLayoutLockButton(tracker, "terrain_editor")
        if (ImGui.button("Restore to Default")) restoreLayout()
        ImGui.sameLine()
        if (ImGui.button("Exit")) request(PendingAction.Exit)
        ImGui.textUnformatted("Path: ${state.terrainFilePath}")
        ImGui.textUnformatted("Unsaved changes: ${if (state.hasUnsavedChanges) "yes" else "no"}")
        if (state.persistenceMessage.isNotBlank()) ImGui.textUnformatted(state.persistenceMessage)
        if (pendingAction != null) ImGui.openPopup("Discard terrain changes?##terrain_control")
        if (ImGui.beginPopupModal("Discard terrain changes?##terrain_control")) {
            ImGui.textUnformatted("Unsaved terrain changes will be lost.")
            if (ImGui.button("Discard and Continue")) {
                pendingAction?.let(::execute)
                pendingAction = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel")) {
                pendingAction = null
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
        ImGui.end()
    }

    private fun request(action: PendingAction) {
        if (state.hasUnsavedChanges) pendingAction = action else execute(action)
    }

    private fun execute(action: PendingAction) {
        when (action) {
            PendingAction.New -> state.createTerrainRequested = true
            PendingAction.Reload -> state.loadTerrainRequested = true
            PendingAction.Regenerate -> state.regenerateRequested = true
            PendingAction.Exit -> exit()
        }
    }
}

class TerrainEditorControlsPanel(
    private val state: TerrainEditorState,
    private val layout: ImGuiLayoutConfig,
    private val tracker: ImGuiLayoutRuntimeTracker,
    private val events: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        if (!begin(TerrainEditorPanelIds.Controls, layout, tracker, events)) { ImGui.end(); return }
        ImGui.textUnformatted("Input focus: ${state.inputFocus} (Tab switches UI/viewport)")
        listOf(
            "F1 - Raise", "F2 - Lower", "F3 - Flatten", "F4 - Smooth", "F5 - Paint selected layer",
            "Mouse drag - Apply brush", "Ctrl+Z - Undo", "Ctrl+Y / Ctrl+Shift+Z - Redo",
            "Mouse wheel - Brush radius", "Shift+Wheel - Brush strength", "G - Toggle wireframe",
            "W/A/S/D - Pan camera", "R/F - Move camera up/down", "Q/E - Rotate camera",
        ).forEach { ImGui.bulletText(it) }
        ImGui.end()
    }
}

class TerrainEditorHistoryPanel(
    private val state: TerrainEditorState,
    private val layout: ImGuiLayoutConfig,
    private val tracker: ImGuiLayoutRuntimeTracker,
    private val events: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        if (!begin(TerrainEditorPanelIds.History, layout, tracker, events)) { ImGui.end(); return }
        ImGui.textUnformatted("Unsaved changes: ${if (state.hasUnsavedChanges) "yes" else "no"}")
        ImGui.textUnformatted("Undo: ${state.undoCount}  Redo: ${state.redoCount}")
        ImGui.textUnformatted("Next undo: ${state.undoLabel ?: "none"}")
        ImGui.textUnformatted("Next redo: ${state.redoLabel ?: "none"}")
        ImGui.textUnformatted("History memory: ${state.historyMemoryBytes} B")
        ImGui.beginDisabled(!state.canUndo)
        if (ImGui.button("Undo")) state.undoRequested = true
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(!state.canRedo)
        if (ImGui.button("Redo")) state.redoRequested = true
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(!state.canUndo && !state.canRedo)
        if (ImGui.button("Clear History")) state.clearHistoryRequested = true
        ImGui.endDisabled()
        ImGui.end()
    }
}

private fun begin(
    id: String,
    layout: ImGuiLayoutConfig,
    tracker: ImGuiLayoutRuntimeTracker,
    events: ImGuiWindowEventLogger,
): Boolean {
    val panel = layout.panels.getValue(id)
    val expanded = beginImGuiPanel(id, panel, tracker)
    events.observe(id, panel.title)
    return expanded
}
