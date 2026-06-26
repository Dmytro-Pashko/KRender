package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontGenerationMetadata
import com.pashkd.krender.engine.tools.common.bitmapfont.charset.CharsetPreset
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
    private val sourceFontBuf = ByteArray(512)
    private val customCharsBuf = ByteArray(512)
    private var buffersSynced = false

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

        val gen = state.metadata?.generation ?: BitmapFontGenerationMetadata()
        if (!buffersSynced) {
            syncBuffer(sourceFontBuf, state.metadata?.sourceFont ?: "")
            syncBuffer(customCharsBuf, gen.customCharacters)
            buffersSynced = true
        }

        ImGui.text("Source Font (.ttf/.otf):")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
        if (ImGui.inputText("##bfe_gen_source_font", sourceFontBuf)) {
            updateMetadata { copy(sourceFont = readBuffer(sourceFontBuf)) }
        }

        val fontSizes = intArrayOf(8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 64, 72, 96, 128)
        if (ImGui.beginCombo("Size (px)##bfe_gen_size", gen.sizePx.toString())) {
            fontSizes.forEach { size ->
                if (ImGui.selectable(size.toString(), gen.sizePx == size)) {
                    updateGeneration { copy(sizePx = size) }
                }
            }
            ImGui.endCombo()
        }

        if (ImGui.beginCombo("Charset##bfe_gen_charset", charsetPresetFromName(gen.charsetPreset).displayName)) {
            CharsetPreset.entries.forEach { preset ->
                if (ImGui.selectable(preset.displayName, gen.charsetPreset == preset.name)) {
                    updateGeneration { copy(charsetPreset = preset.name) }
                }
            }
            ImGui.endCombo()
        }

        if (charsetPresetFromName(gen.charsetPreset) == CharsetPreset.CUSTOM) {
            ImGui.text("Custom Characters:")
            ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
            if (ImGui.inputText("##bfe_gen_custom_chars", customCharsBuf)) {
                updateGeneration { copy(customCharacters = readBuffer(customCharsBuf)) }
            }
        }

        val paddingSizes = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        if (ImGui.beginCombo("Padding##bfe_gen_padding", gen.padding.toString())) {
            paddingSizes.forEach { p ->
                if (ImGui.selectable(p.toString(), gen.padding == p)) {
                    updateGeneration { copy(padding = p) }
                }
            }
            ImGui.endCombo()
        }

        if (ImGui.beginCombo("Spacing##bfe_gen_spacing", gen.spacing.toString())) {
            paddingSizes.forEach { s ->
                if (ImGui.selectable(s.toString(), gen.spacing == s)) {
                    updateGeneration { copy(spacing = s) }
                }
            }
            ImGui.endCombo()
        }

        val pageSizes = intArrayOf(128, 256, 512, 1024, 2048, 4096)
        if (ImGui.beginCombo("Page Width##bfe_gen_pw", gen.pageWidth.toString())) {
            pageSizes.forEach { size ->
                if (ImGui.selectable(size.toString(), gen.pageWidth == size)) {
                    updateGeneration { copy(pageWidth = size) }
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.beginCombo("Page Height##bfe_gen_ph", gen.pageHeight.toString())) {
            pageSizes.forEach { size ->
                if (ImGui.selectable(size.toString(), gen.pageHeight == size)) {
                    updateGeneration { copy(pageHeight = size) }
                }
            }
            ImGui.endCombo()
        }

        ImGui.end()
    }

    private fun updateMetadata(block: com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadata.() -> com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadata) {
        val current = state.metadata ?: com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadata()
        state.metadata = current.block()
        state.dirty = true
    }

    private fun updateGeneration(block: BitmapFontGenerationMetadata.() -> BitmapFontGenerationMetadata) {
        updateMetadata { copy(generation = generation.block()) }
    }

    private fun charsetPresetFromName(name: String): CharsetPreset =
        CharsetPreset.entries.firstOrNull { it.name == name } ?: CharsetPreset.ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC

    private fun syncBuffer(buf: ByteArray, text: String) {
        buf.fill(0)
        val bytes = text.toByteArray(Charsets.UTF_8)
        bytes.copyInto(buf, 0, 0, minOf(bytes.size, buf.size - 1))
    }

    private fun readBuffer(buf: ByteArray): String {
        val nullIndex = buf.indexOf(0)
        return if (nullIndex >= 0) String(buf, 0, nullIndex, Charsets.UTF_8) else String(buf, Charsets.UTF_8)
    }
}
