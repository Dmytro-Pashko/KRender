package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.glyphDisplayLabel
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class GlyphListPanel(
    private val state: BitmapFontEditorState,
    private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val filterBuffer = ByteArray(BufferSize)
    private var filterSynced = false

    override fun draw() {
        if (!filterSynced || readBuffer(filterBuffer) != state.glyphSelection.glyphFilter) {
            writeBuffer(filterBuffer, state.glyphSelection.glyphFilter)
            filterSynced = true
        }
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.GlyphList)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.GlyphList, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.GlyphList, panelLayout.title)
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

        ImGui.text("Glyph Filter")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
        if (ImGui.inputText("##bfe_glyph_filter", filterBuffer)) {
            controller.setGlyphFilter(readBuffer(filterBuffer))
        }
        ImGui.separator()
        ImGui.text("Glyphs: ${document.glyphs.size}")

        val filter = state.glyphSelection.glyphFilter.lowercase()
        val filtered =
            if (filter.isBlank()) {
                document.glyphs.take(MaxVisibleGlyphs)
            } else {
                document.glyphs
                    .filter { glyph ->
                        glyph.id.toString().contains(filter) ||
                            glyph.char?.lowercase()?.contains(filter) == true
                    }.take(MaxVisibleGlyphs)
            }

        ImGui.beginChild("bfe_glyph_list_body", ImVec2(0f, 0f), true)
        filtered.forEach { glyph ->
            val label = glyphDisplayLabel(glyph)
            val selected = state.glyphSelection.selectedGlyphId == glyph.id
            if (ImGui.selectable(label, selected)) {
                controller.selectGlyph(glyph.id)
            }
            if (selected && state.pendingScrollToSelectedGlyph) {
                ImGui.setScrollHereY(0.5f)
                state.pendingScrollToSelectedGlyph = false
            }
        }
        if (filtered.isEmpty()) {
            ImGui.text("No glyphs match filter.")
        }
        ImGui.endChild()
        ImGui.end()
    }

    companion object {
        private const val MaxVisibleGlyphs = 500
        private const val BufferSize = 128
    }
}
