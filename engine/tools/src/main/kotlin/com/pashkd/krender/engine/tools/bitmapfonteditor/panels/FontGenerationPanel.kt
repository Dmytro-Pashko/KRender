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

class FontGenerationPanel(
    private val state: BitmapFontEditorState,
    private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.Generation)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.Generation, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.Generation, panelLayout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        ImGui.text("Font Generation")
        ImGui.separator()
        ImGui.text("Generation controls will appear here.")
        ImGui.end()
    }
}
