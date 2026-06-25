package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.hasUnappliedNinePatchDraft
import com.pashkd.krender.engine.tools.textureatlaseditor.hasUnsavedChanges
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAsset
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
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Toolbar)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (ImGui.button("Reload##texture_atlas_editor_reload")) {
            operations.reload()
        }
        tooltipOnHover("Reloads the current atlas and refreshes the editor state.")
        ImGui.sameLine()
        if (ImGui.button("Save Layout##texture_atlas_editor_save_layout")) {
            operations.saveUiLayout()
        }
        tooltipOnHover("Saves the current panel layout for this editor.")
        ImGui.sameLine()
        if (ImGui.button("Reset Layout##texture_atlas_editor_reset_layout")) {
            operations.restoreUiLayout()
        }
        tooltipOnHover("Restores the default panel layout.")
        ImGui.sameLine()
        if (ImGui.button("Exit##texture_atlas_editor_exit")) {
            operations.requestExit()
        }
        tooltipOnHover("Closes the Texture Atlas Editor window.")

        ImGui.separator()
        wrappedTextLine(state.statusMessage)
        textLine("Dirty: ${if (state.hasUnsavedChanges()) "yes" else "no"}")
        if (state.hasUnappliedNinePatchDraft()) {
            textLine("Pending Draft: Apply or reset the current NinePatch edits before saving or reloading.")
        }
        textLine("Atlas Path: ${state.currentInputPath ?: "<none>"}")
        state.project.resolvedInputPath?.let { path -> textLine("Resolved: $path") }
        state.selectedAsset()?.let { asset ->
            ImGui.separator()
            textLine("File: ${asset.fileName}  |  ${asset.extension.ifBlank { "?" }}  |  ${formatBytes(asset.sizeBytes)}")
            asset.textureInfo?.let { info ->
                textLine("Dimensions: ${info.width ?: "?"} x ${info.height ?: "?"}  Format: ${info.colorFormat ?: "?"}")
            }
        }
        ImGui.end()
    }
}
