package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class FontDiagnosticsPanel(
    private val state: BitmapFontEditorState,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.Diagnostics)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.Diagnostics, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.Diagnostics, panelLayout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val diagnostics = state.diagnostics
        if (diagnostics.isEmpty()) {
            textLine("No diagnostics.")
        } else {
            textLine("Diagnostics: ${diagnostics.size}")
            ImGui.separator()
            diagnostics.forEach { diag ->
                textLine("[${diag.severity.name}] ${diag.message}")
                diag.source?.let { source -> textLine("  $source") }
            }
        }
        ImGui.end()
    }
}
