package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.canvas.CanvasRect
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class FontPageCanvasPanel(
    private val state: BitmapFontEditorState,
    private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.Preview)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.Preview, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.Preview, panelLayout.title)
        if (!expanded) {
            state.canvasRect = CanvasRect()
            ImGui.end()
            return
        }
        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        state.canvasRect = CanvasRect(min.x, min.y, size.x.coerceAtLeast(1f), size.y.coerceAtLeast(1f))

        if (state.document == null) {
            ImGui.text("Open a .fnt file or generate a bitmap font to preview.")
        } else {
            ImGui.text("Font page preview placeholder.")
        }
        ImGui.end()
    }
}
