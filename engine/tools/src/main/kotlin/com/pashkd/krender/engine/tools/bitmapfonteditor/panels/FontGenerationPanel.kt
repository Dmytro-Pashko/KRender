package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadata
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontGenerationMetadata
import com.pashkd.krender.engine.tools.common.bitmapfont.charset.CharsetPreset
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.AwtRenderQualityMode
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.AwtStrokeControlMode
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.AwtTextAntialiasingMode
import com.pashkd.krender.engine.tools.common.bitmapfont.generator.BitmapFontRasterizerType
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

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun draw() {
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.Generation)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.Generation, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.Generation, panelLayout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val gen = state.metadata?.generation ?: BitmapFontGenerationMetadata()
        if (!buffersSynced) {
            writeBuffer(sourceFontBuf, state.metadata?.sourceFont ?: "")
            writeBuffer(customCharsBuf, gen.customCharacters)
            buffersSynced = true
        }
        if (readBuffer(sourceFontBuf) != (state.metadata?.sourceFont ?: "")) {
            writeBuffer(sourceFontBuf, state.metadata?.sourceFont ?: "")
        }
        if (readBuffer(customCharsBuf) != gen.customCharacters) {
            writeBuffer(customCharsBuf, gen.customCharacters)
        }

        ImGui.text("Source Font (.ttf/.otf)")
        ImGui.setNextItemWidth(300f)
        if (ImGui.inputText("##bfe_gen_source_font", sourceFontBuf)) {
            updateMetadata { copy(sourceFont = readBuffer(sourceFontBuf)) }
        }
        ImGui.sameLine()
        if (ImGui.button("Browse##bfe_gen_source_browse")) {
            controller.browseSourceFont()
        }

        drawIntCombo(
            label = "Size (px)##bfe_gen_size",
            value = gen.sizePx,
            values = intArrayOf(8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 64, 72, 96, 128),
            onSelect = { value -> updateGeneration { copy(sizePx = value) } },
        )

        if (ImGui.beginCombo("Charset##bfe_gen_charset", charsetPresetFromName(gen.charsetPreset).displayName)) {
            CharsetPreset.entries.forEach { preset ->
                if (ImGui.selectable(preset.displayName, gen.charsetPreset == preset.name)) {
                    updateGeneration { copy(charsetPreset = preset.name) }
                }
            }
            ImGui.endCombo()
        }

        if (charsetPresetFromName(gen.charsetPreset) == CharsetPreset.CUSTOM) {
            ImGui.text("Custom Characters")
            ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
            if (ImGui.inputText("##bfe_gen_custom_chars", customCharsBuf)) {
                updateGeneration { copy(customCharacters = readBuffer(customCharsBuf)) }
            }
        }

        drawIntCombo(
            label = "Padding##bfe_gen_padding",
            value = gen.padding,
            values = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            onSelect = { value -> updateGeneration { copy(padding = value) } },
        )
        drawIntCombo(
            label = "Spacing##bfe_gen_spacing",
            value = gen.spacing,
            values = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            onSelect = { value -> updateGeneration { copy(spacing = value) } },
        )

        if (ImGui.beginCombo("Rasterizer##bfe_gen_rasterizer", gen.rasterizer)) {
            BitmapFontRasterizerType.entries.forEach { type ->
                if (ImGui.selectable(type.name, gen.rasterizer == type.name)) {
                    updateGeneration { copy(rasterizer = type.name) }
                }
            }
            ImGui.endCombo()
        }
        tooltipOnHover("Current implementation supports AWT rasterization. The extra options below tune AWT text rendering hints.")

        if (ImGui.beginCombo("Text AA##bfe_gen_text_aa", gen.textAntialiasing)) {
            AwtTextAntialiasingMode.entries.forEach { mode ->
                if (ImGui.selectable(mode.name, gen.textAntialiasing == mode.name)) {
                    updateGeneration { copy(textAntialiasing = mode.name, antialias = mode != AwtTextAntialiasingMode.OFF) }
                }
            }
            ImGui.endCombo()
        }
        tooltipOnHover("Controls AWT text anti-aliasing mode. LCD modes can noticeably change edge sharpness.")

        val fractionalMetrics = booleanArrayOf(gen.fractionalMetrics)
        if (ImGui.checkbox("Fractional Metrics##bfe_gen_fractional_metrics", fractionalMetrics)) {
            updateGeneration { copy(fractionalMetrics = fractionalMetrics[0], hinting = fractionalMetrics[0]) }
        }
        tooltipOnHover("Uses sub-pixel glyph metrics. This changes advances, bounds, and glyph spacing.")

        if (ImGui.beginCombo("Render Quality##bfe_gen_render_quality", gen.renderQuality)) {
            AwtRenderQualityMode.entries.forEach { mode ->
                if (ImGui.selectable(mode.name, gen.renderQuality == mode.name)) {
                    updateGeneration { copy(renderQuality = mode.name) }
                }
            }
            ImGui.endCombo()
        }
        tooltipOnHover("Controls the AWT render-quality hint. Quality can improve consistency, Speed can look rougher.")

        if (ImGui.beginCombo("Stroke Control##bfe_gen_stroke_control", gen.strokeControl)) {
            AwtStrokeControlMode.entries.forEach { mode ->
                if (ImGui.selectable(mode.name, gen.strokeControl == mode.name)) {
                    updateGeneration { copy(strokeControl = mode.name) }
                }
            }
            ImGui.endCombo()
        }
        tooltipOnHover("Controls AWT stroke normalization. It can affect edge snapping and perceived glyph crispness.")

        drawIntCombo(
            label = "Page Width##bfe_gen_pw",
            value = gen.pageWidth,
            values = PageSizes,
            onSelect = { value -> updateGeneration { copy(pageWidth = value) } },
        )
        drawIntCombo(
            label = "Page Height##bfe_gen_ph",
            value = gen.pageHeight,
            values = PageSizes,
            onSelect = { value -> updateGeneration { copy(pageHeight = value) } },
        )

        ImGui.separator()
        if (ImGui.button("Preview##bfe_gen_preview")) {
            controller.preview()
        }
        tooltipOnHover("Generates a transient preview page and shows it in the preview canvas without saving project files.")
        ImGui.sameLine()
        if (ImGui.button("Generate##bfe_gen_run")) {
            controller.generate()
        }
        tooltipOnHover("Generates the working bitmap font document and preview page. Use Save Font to write files explicitly.")
        ImGui.end()
    }

    private fun updateMetadata(block: BitmapFontEditorMetadata.() -> BitmapFontEditorMetadata) {
        val current = state.metadata ?: BitmapFontEditorMetadata()
        state.metadata = current.block()
        state.dirty = true
    }

    private fun updateGeneration(block: BitmapFontGenerationMetadata.() -> BitmapFontGenerationMetadata) {
        updateMetadata { copy(generation = generation.block()) }
    }

    private fun charsetPresetFromName(name: String): CharsetPreset = CharsetPreset.entries.firstOrNull { it.name == name } ?: CharsetPreset.ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC

    private fun drawIntCombo(
        label: String,
        value: Int,
        values: IntArray,
        onSelect: (Int) -> Unit,
    ) {
        if (ImGui.beginCombo(label, value.toString())) {
            values.forEach { option ->
                if (ImGui.selectable(option.toString(), option == value)) {
                    onSelect(option)
                }
            }
            ImGui.endCombo()
        }
    }

    companion object {
        private val PageSizes = intArrayOf(128, 256, 512, 1024, 2048, 4096)
    }
}
