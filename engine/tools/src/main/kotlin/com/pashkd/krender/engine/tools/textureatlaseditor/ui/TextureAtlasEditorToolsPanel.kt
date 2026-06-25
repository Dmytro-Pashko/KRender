package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPlan
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
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
    private val exportPathBuffer = ByteArray(512)
    private var synced = false

    override fun draw() {
        if (!synced) {
            writeBuffer(exportPathBuffer, state.importExport.exportResourcePath)
            synced = true
        }
        syncExportPathBuffer()
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Tools)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Tools, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val packingPlan = drawAtlasPackingSection()
        ImGui.separator()
        drawExportResourceSection()
        ImGui.separator()
        drawAtlasInfoSection()
        ImGui.separator()
        drawSaveSection(packingPlan)
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

    private fun drawExportResourceSection() {
        textLine("Export Resource")
        textLine("Export Path")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
        if (ImGui.inputText("##texture_atlas_editor_export_resource_path", exportPathBuffer)) {
            operations.setExportResourcePath(readBuffer(exportPathBuffer))
        }
        val canExport = state.selectedResource() != null
        if (!canExport) ImGui.beginDisabled()
        if (ImGui.button("Export Resource##texture_atlas_editor_export_resource")) {
            operations.exportSelectedResourcePng()
            writeBuffer(exportPathBuffer, state.importExport.exportResourcePath)
        }
        if (!canExport) ImGui.endDisabled()
    }

    private fun drawAtlasInfoSection() {
        val atlas = state.selectedAtlasDocument() ?: return
        textLine("Texture Atlas Info")
        textLine("Pages: ${atlas.pages.size}")
        textLine("Regions: ${atlas.regions.size}")
        state.selectedAtlasPageName?.let { pageName ->
            textLine("Selected page: $pageName")
            atlas.pages.firstOrNull { page -> page.name == pageName }?.details?.forEach { (key, value) ->
                textLine("$key: $value")
            }
        }
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

    private fun syncExportPathBuffer() {
        if (readBuffer(exportPathBuffer) != state.importExport.exportResourcePath) {
            writeBuffer(exportPathBuffer, state.importExport.exportResourcePath)
        }
    }

    companion object {
        private val PageSizeOptions = intArrayOf(32, 64, 128, 256, 512, 1024, 2048, 4096)
        private val PaddingOptions = intArrayOf(0, 1, 2, 4, 8, 16)
    }
}
