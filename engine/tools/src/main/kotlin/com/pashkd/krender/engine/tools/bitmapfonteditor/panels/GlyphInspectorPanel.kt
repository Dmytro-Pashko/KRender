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
    @Suppress("unused") private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    @Suppress("NestedBlockDepth")
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
            textLine("No font loaded.")
            ImGui.end()
            return
        }
        val data = buildFontInspectorData(document)
        textLine("Font Inspector")
        ImGui.separator()
        data.face?.let { textLine("Face: $it") }
        data.size?.let { textLine("Size: $it") }
        if (data.bold) textLine("Bold: yes")
        if (data.italic) textLine("Italic: yes")
        textLine("Line height: ${data.lineHeight}")
        textLine("Base: ${data.base}")
        textLine("Scale: ${data.scaleW} x ${data.scaleH}")
        textLine("Pages: ${data.pageCount}")
        textLine("Glyphs: ${data.glyphCount}")
        textLine("Kernings: ${data.kerningCount}")

        state.glyphSelection.selectedGlyphId?.let { glyphId ->
            document.glyphs.firstOrNull { it.id == glyphId }?.let { glyph ->
                ImGui.separator()
                textLine("Selected Glyph: ${glyphDisplayLabel(glyph)}")
                textLine("Position: ${glyph.x}, ${glyph.y}")
                textLine("Size: ${glyph.width} x ${glyph.height}")
                textLine("Offset: ${glyph.xOffset}, ${glyph.yOffset}")
                textLine("xAdvance: ${glyph.xAdvance}")
                textLine("Page: ${glyph.page}")
                textLine("Channel: ${glyph.channel}")
                val kerningsForGlyph = document.kernings.filter { it.first == glyphId || it.second == glyphId }
                if (kerningsForGlyph.isNotEmpty()) {
                    textLine("Kerning pairs: ${kerningsForGlyph.size}")
                    kerningsForGlyph.take(5).forEach { k ->
                        textLine("  ${k.first} -> ${k.second}: ${k.amount}")
                    }
                }
            }
        }

        if (data.diagnosticCount > 0) {
            ImGui.separator()
            textLine("Diagnostics: ${data.diagnosticCount}")
        }
        ImGui.end()
    }
}
