package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.activeStyles
import com.pashkd.krender.engine.tools.skin.readBuffer
import com.pashkd.krender.engine.tools.skin.writeBuffer
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
    private val pathBuffer = ByteArray(512)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.Toolbar)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        syncPathBuffer()
        ImGui.textUnformatted("Skin path:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(300f)
        ImGui.inputText("##skin_editor_path", pathBuffer)
        ImGui.newLine()
        with(dsl) {
            button("Reload##skin_editor_reload") { reloadFromToolbarPath() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Discard Edits##skin_editor_discard_edits") { operations.discardInMemoryEdits() }
        }
        ImGui.sameLine()
        val canSaveChanges = state.loadResult.project?.skinFile != null && state.editSession.dirty && state.editSession.changes.isNotEmpty()
        if (!canSaveChanges) ImGui.beginDisabled()
        with(dsl) {
            button("Save Changes##skin_editor_save_changes") { operations.saveChanges() }
        }
        if (!canSaveChanges) ImGui.endDisabled()
        ImGui.sameLine()
        with(dsl) {
            button("Save Panel Layout##skin_editor_save_layout") { operations.saveUiLayout() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset Panel Layout##skin_editor_reset_layout") { operations.restoreUiLayout() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Exit##skin_editor_exit") { operations.requestExit() }
        }
        ImGui.separator()
        ImGui.textUnformatted("Path: ${state.currentInputPath ?: "<none>"}")
        ImGui.textUnformatted("Status: ${state.statusMessage}")
        ImGui.textUnformatted("Loaded skin: ${if (state.loadResult.previewSkinAvailable) "yes" else "no"}")
        ImGui.textUnformatted("Styles: ${state.editSession.activeStyles().size}")
        ImGui.textUnformatted("Resources: ${state.loadResult.resourceIndex.resources.size}")
        ImGui.textUnformatted("Editing: ${if (state.editSession.dirty) "dirty" else "clean"} (${state.editSession.changes.size} pending)")
        ImGui.textUnformatted("Save Changes writes draft style/resource edits to the loaded skin file.")
        ImGui.end()
    }

    private fun reloadFromToolbarPath() {
        val enteredPath = readBuffer(pathBuffer).trim().replace('\\', '/')
        val currentPath = state.currentInputPath.orEmpty()
        if (enteredPath != currentPath) {
            operations.openPath(enteredPath)
        } else {
            operations.requestReload()
        }
    }

    private fun syncPathBuffer() {
        val current = state.pendingPathInput.ifBlank { state.currentInputPath.orEmpty() }
        if (readBuffer(pathBuffer) != current) writeBuffer(pathBuffer, current)
    }
}
