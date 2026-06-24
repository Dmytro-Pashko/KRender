package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class TextureAtlasEditorToolbarPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val pathBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        if (!synced) {
            writeBuffer(pathBuffer, state.pendingPathInput)
            synced = true
        }
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Toolbar)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Path")
        ImGui.sameLine()
        ImGui.setNextItemWidth(300f)
        if (ImGui.inputText("##texture_atlas_editor_path", pathBuffer)) {
            state.pendingPathInput = readBuffer(pathBuffer)
        }
        ImGui.sameLine()
        if (ImGui.button("Open##texture_atlas_editor_open")) {
            operations.openPath(state.pendingPathInput)
        }

        if (ImGui.button("Reload##texture_atlas_editor_reload")) {
            operations.reload()
        }
        ImGui.sameLine()
        if (ImGui.button("Save Layout##texture_atlas_editor_save_layout")) {
            operations.saveUiLayout()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset Layout##texture_atlas_editor_reset_layout")) {
            operations.restoreUiLayout()
        }
        ImGui.sameLine()
        if (ImGui.button("Exit##texture_atlas_editor_exit")) {
            operations.requestExit()
        }

        ImGui.separator()
        wrappedTextLine(state.statusMessage)
        textLine("Input: ${state.currentInputPath ?: "<none>"}")
        state.project.resolvedInputPath?.let { path -> textLine("Resolved: $path") }
        state.project.rootDirectory?.let { root -> textLine("Root: ${root.path.replace('\\', '/')}") }
        ImGui.end()
    }

    companion object {
        private const val BufferSize = 1024
    }
}
