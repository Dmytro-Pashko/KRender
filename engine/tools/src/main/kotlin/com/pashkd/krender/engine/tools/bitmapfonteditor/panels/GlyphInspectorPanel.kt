package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.buildFontInspectorData
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.glyphDisplayLabel
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class GlyphInspectorPanel(
    private val state: BitmapFontEditorState,
    private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.Inspector)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.Inspector, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.Inspector, panelLayout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val document = state.document
        if (document == null) {
            ImGui.text("No font loaded.")
            ImGui.end()
            return
        }
        val data = buildFontInspectorData(document)
        ImGui.text("Font Inspector")
        ImGui.separator()
        data.face?.let { ImGui.text("Face: $it") }
        data.size?.let { ImGui.text("Size: $it") }
        if (data.bold) ImGui.text("Bold: yes")
        if (data.italic) ImGui.text("Italic: yes")
        ImGui.text("Line height: ${data.lineHeight}")
        ImGui.text("Base: ${data.base}")
        ImGui.text("Scale: ${data.scaleW} x ${data.scaleH}")
        ImGui.text("Pages: ${data.pageCount}")
        ImGui.text("Glyphs: ${data.glyphCount}")
        ImGui.text("Kernings: ${data.kerningCount}")

        state.glyphSelection.selectedGlyphId?.let { glyphId ->
            document.glyphs.firstOrNull { it.id == glyphId }?.let { glyph ->
                ImGui.separator()
                ImGui.text("Selected Glyph: ${glyphDisplayLabel(glyph)}")
                ImGui.text("Position: ${glyph.x}, ${glyph.y}")
                ImGui.text("Size: ${glyph.width} x ${glyph.height}")
                ImGui.text("Offset: ${glyph.xOffset}, ${glyph.yOffset}")
                ImGui.text("xAdvance: ${glyph.xAdvance}")
                ImGui.text("Page: ${glyph.page}")
                ImGui.text("Channel: ${glyph.channel}")
                val kerningsForGlyph = document.kernings.filter { it.first == glyphId || it.second == glyphId }
                if (kerningsForGlyph.isNotEmpty()) {
                    ImGui.text("Kerning pairs: ${kerningsForGlyph.size}")
                    kerningsForGlyph.take(5).forEach { k ->
                        ImGui.text("  ${k.first} -> ${k.second}: ${k.amount}")
                    }
                }
            }
        }

        if (data.diagnosticCount > 0) {
            ImGui.separator()
            ImGui.text("Diagnostics: ${data.diagnosticCount}")
        }
        ImGui.end()
    }
}
