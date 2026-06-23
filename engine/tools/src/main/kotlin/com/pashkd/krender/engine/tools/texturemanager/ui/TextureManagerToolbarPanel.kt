package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class TextureManagerToolbarPanel(
    private val state: TextureManagerState,
    private val operations: TextureManagerOperations,
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
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Toolbar)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Path")
        ImGui.sameLine()
        ImGui.setNextItemWidth(760f)
        if (ImGui.inputText("##texture_manager_path", pathBuffer)) {
            state.pendingPathInput = readBuffer(pathBuffer)
        }
        ImGui.sameLine()
        if (ImGui.button("Open##texture_manager_open")) {
            operations.openPath(state.pendingPathInput)
        }
        ImGui.sameLine()
        if (ImGui.button("Reload##texture_manager_reload")) {
            operations.reload()
        }
        ImGui.sameLine()
        if (ImGui.button("Import Texture##texture_manager_import")) {
            operations.importTexturePlaceholder()
        }
        ImGui.sameLine()
        if (ImGui.button("Save Metadata##texture_manager_save_metadata")) {
            operations.saveMetadataPlaceholder()
        }
        ImGui.sameLine()
        if (ImGui.button("Pack Atlas##texture_manager_pack_atlas")) {
            operations.packAtlasPlaceholder()
        }
        ImGui.sameLine()
        if (ImGui.button("Save Layout##texture_manager_save_layout")) {
            operations.saveUiLayout()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset Layout##texture_manager_reset_layout")) {
            operations.restoreUiLayout()
        }
        ImGui.sameLine()
        if (ImGui.button("Exit##texture_manager_exit")) {
            operations.requestExit()
        }

        ImGui.separator()
        ImGui.textWrapped(state.statusMessage)
        state.project.resolvedInputPath?.let { path -> textLine("Resolved: $path") }
        state.project.rootDirectory?.let { root -> textLine("Root: ${root.path.replace('\\', '/')}") }
        ImGui.end()
    }

    companion object {
        private const val BufferSize = 1024
    }
}
