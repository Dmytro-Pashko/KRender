package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
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
    private val importSourceBuffer = ByteArray(BufferSize)
    private val importTargetBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        syncBuffersIfNeeded()
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Tools)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Tools, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val packingPlan = drawAtlasPackingSection()
        ImGui.separator()
        drawImportAndSaveSection(packingPlan)
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
        val packingPlan = state.selectedPackingPlan()
        textLine("Input count: ${packingPlan?.inputCount ?: 0}")
        textLine("Packed regions: ${packingPlan?.packedRegionCount ?: 0}")
        textLine("Skipped: ${packingPlan?.skippedCount ?: 0}")
        textLine("Pages: ${packingPlan?.pages?.size ?: 0}")
        textLine("Diagnostics: ${state.packing.lastResult.diagnostics.size}")
        return packingPlan
    }

    private fun drawImportAndSaveSection(packingPlan: com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPlan?) {
        textLine("Texture Import")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x - 90f)
        if (ImGui.inputText("##texture_atlas_editor_import_source", importSourceBuffer)) {
            operations.setImportSourcePath(readBuffer(importSourceBuffer))
        }
        ImGui.sameLine()
        if (ImGui.button("Open##texture_atlas_editor_import_browse")) {
            operations.browseImportTexture()
        }

        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x - 160f)
        if (ImGui.inputText("##texture_atlas_editor_target_path", importTargetBuffer)) {
            operations.setTargetPath(readBuffer(importTargetBuffer))
        }
        ImGui.sameLine()
        if (ImGui.button("Import##texture_atlas_editor_tools_import")) {
            operations.importTexture()
        }
        ImGui.sameLine()
        val canSave = packingPlan?.packedRegionCount?.let { it > 0 } == true
        if (!canSave) ImGui.beginDisabled()
        if (ImGui.button("Save##texture_atlas_editor_save_atlas")) {
            operations.savePackedAtlas()
        }
        if (!canSave) ImGui.endDisabled()

        val importOverwrite = booleanArrayOf(state.importExport.importOverwrite)
        if (ImGui.checkbox("Overwrite Existing Texture##texture_atlas_editor_import_overwrite", importOverwrite)) {
            operations.setImportOverwrite(importOverwrite[0])
        }
        if (!canSave) {
            textLine("Pack the texture atlas with at least one region before saving.")
        }

        state.importExport.lastImportResult?.let { result ->
            textLine(result.message)
            result.writtenPaths.forEach(::textLine)
        }
        state.importExport.lastExportResult?.let { result ->
            textLine(result.message)
            result.writtenPaths.forEach(::textLine)
        }
    }

    private fun syncBuffersIfNeeded() {
        if (!synced || readBuffer(importSourceBuffer) != state.importExport.importSourcePath) {
            writeBuffer(importSourceBuffer, state.importExport.importSourcePath)
        }
        if (!synced || readBuffer(importTargetBuffer) != state.importExport.targetPath) {
            writeBuffer(importTargetBuffer, state.importExport.targetPath)
        }
        synced = true
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
        private const val BufferSize = 1024
        private val PageSizeOptions = intArrayOf(32, 64, 128, 256, 512, 1024, 2048, 4096)
        private val PaddingOptions = intArrayOf(0, 1, 2, 4, 8, 16)
    }
}
