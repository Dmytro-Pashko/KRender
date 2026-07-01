package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.activeStyles
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.dsl

class SkinEditorToolbarPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.Toolbar)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawToolbarButtons()
        ImGui.separator()
        drawStatusLines()
        ImGui.textUnformatted(
            "Editing: ${if (state.editSession.dirty) "dirty" else "clean"} (${state.editSession.changes.size} pending)",
        )
        ImGui.textUnformatted("Save Changes writes draft style/resource edits to the loaded skin file.")
        ImGui.end()
    }

    private fun drawToolbarButtons() {
        drawToolbarButton("Reload##skin_editor_reload") { operations.requestReload() }
        drawToolbarButton("Discard Edits##skin_editor_discard_edits") { operations.discardInMemoryEdits() }
        drawSaveChangesButton()
        drawToolbarButton("Save Panel Layout##skin_editor_save_layout") { operations.saveUiLayout() }
        drawToolbarButton("Reset Panel Layout##skin_editor_reset_layout") { operations.restoreUiLayout() }
        drawToolbarButton("Exit##skin_editor_exit") { operations.requestExit() }
    }

    private fun drawToolbarButton(
        label: String,
        action: () -> Unit,
    ) {
        with(dsl) {
            button(label) { action() }
        }
        ImGui.sameLine()
    }

    private fun drawSaveChangesButton() {
        val canSaveChanges = state.loadResult.project?.skinFile != null && state.editSession.dirty && state.editSession.changes.isNotEmpty()
        if (!canSaveChanges) ImGui.beginDisabled()
        with(dsl) {
            button("Save Changes##skin_editor_save_changes") { operations.saveChanges() }
        }
        if (!canSaveChanges) ImGui.endDisabled()
        ImGui.sameLine()
    }

    private fun drawStatusLines() {
        ImGui.textUnformatted("Path: ${state.currentInputPath ?: "<none>"}")
        ImGui.textUnformatted("Status: ${state.statusMessage}")
        ImGui.textUnformatted("Loaded skin: ${if (state.loadResult.previewSkinAvailable) "yes" else "no"}")
        ImGui.textUnformatted("Styles: ${state.editSession.activeStyles().size}")
        ImGui.textUnformatted("Resources: ${state.loadResult.resourceIndex.resources.size}")
    }
}
