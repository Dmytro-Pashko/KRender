package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPlan
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class TextureAtlasEditorToolsPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Tools)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Tools, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val packingPlan = drawAtlasPackingSection()
        ImGui.separator()
        drawSaveSection(packingPlan)
        ImGui.separator()
        drawFontExportSection()
        ImGui.end()
    }

    private fun drawAtlasPackingSection(): com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPlan? {
        textLine("Texture Atlas Packing")
        drawPageSizeCombo(
            label = "Max Width##texture_atlas_editor_packing_width",
            value = state.packing.settings.maxPageWidth,
            onSelect = operations::setPackingMaxPageWidth,
        )
        drawPageSizeCombo(
            label = "Max Height##texture_atlas_editor_packing_height",
            value = state.packing.settings.maxPageHeight,
            onSelect = operations::setPackingMaxPageHeight,
        )
        drawPaddingCombo()
        val allowRotation = booleanArrayOf(state.packing.settings.allowRotation)
        if (ImGui.checkbox("Allow Rotation##texture_atlas_editor_packing_rotation", allowRotation)) {
            operations.setPackingAllowRotation(allowRotation[0])
        }
        val includeNinePatch = booleanArrayOf(state.packing.settings.includeNinePatch)
        if (ImGui.checkbox("Include Nine-patch##texture_atlas_editor_packing_nine_patch", includeNinePatch)) {
            operations.setPackingIncludeNinePatch(includeNinePatch[0])
        }
        val overwriteExistingAtlas = booleanArrayOf(state.importExport.saveOverwrite)
        if (ImGui.checkbox("Overwrite Existing Atlas##texture_atlas_editor_save_overwrite", overwriteExistingAtlas)) {
            operations.setSaveOverwrite(overwriteExistingAtlas[0])
        }
        if (ImGui.button("Pack Texture Atlas##texture_atlas_editor_packing_run")) {
            operations.packTextureAtlas()
        }
        ImGui.sameLine()
        val canSave = state.selectedPackingPlan()?.packedRegionCount?.let { it > 0 } == true
        if (!canSave) ImGui.beginDisabled()
        if (ImGui.button("Save Texture Atlas##texture_atlas_editor_save_atlas")) {
            operations.savePackedAtlas()
        }
        if (!canSave) ImGui.endDisabled()
        val currentPlan = state.selectedPackingPlan()
        textLine("Input count: ${currentPlan?.inputCount ?: 0}")
        textLine("Packed regions: ${currentPlan?.packedRegionCount ?: 0}")
        textLine("Skipped: ${currentPlan?.skippedCount ?: 0}")
        textLine("Pages: ${currentPlan?.pages?.size ?: 0}")
        textLine("Diagnostics: ${state.packing.lastResult.diagnostics.size}")
        return currentPlan
    }

    private fun drawSaveSection(packingPlan: com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPlan?) {
        if (packingPlan?.packedRegionCount?.let { it > 0 } != true) {
            textLine("Pack the texture atlas with at least one region before saving.")
        } else {
            textLine("Packed atlas is ready to save.")
        }
        state.importExport.lastExportResult?.let { result ->
            textLine(result.message)
            result.writtenPaths.forEach(::textLine)
        }
    }

    private fun drawFontExportSection() {
        val isFontSelected = state.selectedResource() is FontAtlasResource
        textLine("Font Export")
        if (!isFontSelected) {
            textLine("Select a font resource to enable export.")
            textLine("TTF/OTF generation is deferred.")
            return
        }
        if (ImGui.button("Export Bitmap Font##tools_export_font")) {
            operations.exportBitmapFont()
        }
        textLine("TTF/OTF generation is deferred.")
    }

    private fun drawPageSizeCombo(
        label: String,
        value: Int,
        onSelect: (Int) -> Unit,
    ) {
        if (!ImGui.beginCombo(label, value.toString())) return
        PageSizeOptions.forEach { option ->
            if (ImGui.selectable(option.toString(), option == value)) {
                onSelect(option)
            }
        }
        ImGui.endCombo()
    }

    private fun drawPaddingCombo() {
        val value = state.packing.settings.padding
        if (!ImGui.beginCombo("Padding##texture_atlas_editor_packing_padding", value.toString())) return
        PaddingOptions.forEach { option ->
            if (ImGui.selectable(option.toString(), option == value)) {
                operations.setPackingPadding(option)
            }
        }
        ImGui.endCombo()
    }

    companion object {
        private val PageSizeOptions = intArrayOf(32, 64, 128, 256, 512, 1024, 2048, 4096)
        private val PaddingOptions = intArrayOf(0, 1, 2, 4, 8, 16)
    }
}
