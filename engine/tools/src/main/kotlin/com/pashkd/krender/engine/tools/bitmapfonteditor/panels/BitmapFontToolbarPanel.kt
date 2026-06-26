package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class BitmapFontToolbarPanel(
    private val state: BitmapFontEditorState,
    private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.Toolbar)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.Toolbar, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.Toolbar, panelLayout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (ImGui.button("Save Font##bfe_save")) {
            controller.save()
        }
        tooltipOnHover("Writes the generated .fnt, page PNG, and metadata to disk.")
        ImGui.sameLine()
        if (ImGui.button("Reload##bfe_reload")) {
            controller.reload()
        }
        tooltipOnHover("Reloads the currently opened bitmap font or metadata file.")
        ImGui.sameLine()
        if (ImGui.button("Save Layout##bfe_save_layout")) {
            controller.saveUiLayout()
        }
        tooltipOnHover("Saves the current panel layout for this editor.")
        ImGui.sameLine()
        if (ImGui.button("Reset Layout##bfe_reset_layout")) {
            controller.restoreUiLayout()
        }
        tooltipOnHover("Restores the default panel layout.")
        ImGui.sameLine()
        if (ImGui.button("Exit##bfe_exit")) {
            controller.requestExit()
        }
        tooltipOnHover("Closes the Bitmap Font Editor window.")

        ImGui.separator()
        wrappedTextLine(state.statusMessage)
        textLine("Loaded: ${if (state.document?.readable == true || state.metadata != null) "yes" else "no"}")
        textLine("Dirty: ${if (state.dirty) "yes" else "no"}")
        textLine("Path: ${state.metadataPath ?: state.inputPath ?: "<none>"}")
        textLine("Resolved: ${state.resolvedInputPath ?: state.resolvedFontPath ?: "<none>"}")
        textLine("Glyph Count: ${state.document?.glyphs?.size ?: 0}")
        textLine("Pages Count: ${state.document?.pages?.size ?: 0}")
        ImGui.end()
    }
}
