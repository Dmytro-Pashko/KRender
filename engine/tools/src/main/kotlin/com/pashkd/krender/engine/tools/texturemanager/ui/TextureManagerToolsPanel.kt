package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerToolMode
import com.pashkd.krender.engine.tools.texturemanager.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.texturemanager.formatZoomMode
import com.pashkd.krender.engine.tools.texturemanager.selectedPackingPlan
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider

class TextureManagerToolsPanel(
    private val state: TextureManagerState,
    private val operations: TextureManagerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val importSourceBuffer = ByteArray(BufferSize)
    private val importTargetBuffer = ByteArray(BufferSize)
    private val exportDirectoryBuffer = ByteArray(BufferSize)
    private val exportBaseNameBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        syncBuffersIfNeeded()
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Tools)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Tools, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawPreviewTools()
        ImGui.separator()
        val packingPlan = drawAtlasPackingSection()
        ImGui.separator()
        drawTextureImportSection()
        ImGui.separator()
        drawAtlasDescriptorExportSection(packingPlan)

        ImGui.separator()
        textLine("Mouse wheel: zoom")
        textLine("RMB drag or Pan mode: pan")
        textLine("LMB on region: select")
        ImGui.end()
    }

    private fun drawPreviewTools() {
        if (ImGui.beginCombo("Mode##texture_manager_tool_mode", state.toolMode.name)) {
            TextureManagerToolMode.entries.forEach { mode ->
                if (ImGui.selectable(mode.name, state.toolMode == mode)) {
                    operations.setToolMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.beginCombo("Zoom##texture_manager_zoom_mode", formatZoomMode(state.preview.zoomMode))) {
            TexturePreviewZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), state.preview.zoomMode == mode)) {
                    operations.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (state.preview.zoomMode == TexturePreviewZoomMode.Custom) {
            if (slider("Custom Zoom##texture_manager_custom_zoom", state.preview::customZoom, 0.05f, 8f, "%.2f", SliderFlag.AlwaysClamp)) {
                operations.setPreviewZoom(state.preview.customZoom)
            }
        }
        if (ImGui.button("Fit##texture_manager_fit")) {
            operations.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("100%##texture_manager_zoom_100")) {
            operations.setZoomMode(TexturePreviewZoomMode.Percent100)
        }
        ImGui.sameLine()
        if (ImGui.button("200%##texture_manager_zoom_200")) {
            operations.setZoomMode(TexturePreviewZoomMode.Percent200)
        }
        if (ImGui.button("Reset Camera##texture_manager_reset_camera")) {
            operations.resetPreviewCamera()
        }
        if (ImGui.button("Focus Selected Region##texture_manager_focus_region")) {
            operations.fitSelectedRegion()
        }

        val checker = booleanArrayOf(state.preview.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##texture_manager_checker", checker)) {
            operations.setShowCheckerboard(checker[0])
        }
        val grid = booleanArrayOf(state.preview.showGrid)
        if (ImGui.checkbox("Grid##texture_manager_grid", grid)) {
            operations.setShowGrid(grid[0])
        }
        val bounds = booleanArrayOf(state.preview.showBounds)
        if (ImGui.checkbox("Bounds##texture_manager_bounds", bounds)) {
            operations.setShowBounds(bounds[0])
        }
        val ninePatchGuides = booleanArrayOf(state.preview.showNinePatchGuides)
        if (ImGui.checkbox("Show Nine-patch Guides##texture_manager_nine_patch_guides", ninePatchGuides)) {
            operations.setShowNinePatchGuides(ninePatchGuides[0])
        }
    }

    private fun drawAtlasPackingSection(): com.pashkd.krender.engine.tools.texturemanager.TextureAtlasPackingPlan? {
        textLine("Atlas Packing Draft")
        drawPageSizeCombo(
            label = "Max Width##texture_manager_packing_width",
            value = state.packing.settings.maxPageWidth,
            onSelect = operations::setPackingMaxPageWidth,
        )
        drawPageSizeCombo(
            label = "Max Height##texture_manager_packing_height",
            value = state.packing.settings.maxPageHeight,
            onSelect = operations::setPackingMaxPageHeight,
        )
        drawPaddingCombo()
        val allowRotation = booleanArrayOf(state.packing.settings.allowRotation)
        if (ImGui.checkbox("Allow Rotation##texture_manager_packing_rotation", allowRotation)) {
            operations.setPackingAllowRotation(allowRotation[0])
        }
        val includeNinePatch = booleanArrayOf(state.packing.settings.includeNinePatch)
        if (ImGui.checkbox("Include Nine-patch##texture_manager_packing_nine_patch", includeNinePatch)) {
            operations.setPackingIncludeNinePatch(includeNinePatch[0])
        }
        if (ImGui.button("Run Packing Dry-run##texture_manager_packing_run")) {
            operations.runPackingDryRun()
        }
        val packingPlan = state.selectedPackingPlan()
        textLine("Input count: ${packingPlan?.inputCount ?: 0}")
        textLine("Packed regions: ${packingPlan?.packedRegionCount ?: 0}")
        textLine("Skipped: ${packingPlan?.skippedCount ?: 0}")
        textLine("Pages: ${packingPlan?.pages?.size ?: 0}")
        textLine("Diagnostics: ${state.packing.lastResult.diagnostics.size}")
        return packingPlan
    }

    private fun drawTextureImportSection() {
        textLine("Texture Import")
        ImGui.textUnformatted("Source Path")
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputText("##texture_manager_import_source", importSourceBuffer)) {
            operations.setImportSourcePath(readBuffer(importSourceBuffer))
        }
        ImGui.textUnformatted("Target Directory")
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputText("##texture_manager_import_target_dir", importTargetBuffer)) {
            operations.setImportTargetDirectory(readBuffer(importTargetBuffer))
        }
        val importOverwrite = booleanArrayOf(state.importExport.importOverwrite)
        if (ImGui.checkbox("Overwrite Existing Texture##texture_manager_import_overwrite", importOverwrite)) {
            operations.setImportOverwrite(importOverwrite[0])
        }
        if (ImGui.button("Import Texture##texture_manager_tools_import")) {
            operations.importTexture()
        }
        state.importExport.lastImportResult?.let { result ->
            textLine(result.message)
            result.writtenPaths.forEach(::textLine)
        }
    }

    private fun drawAtlasDescriptorExportSection(packingPlan: com.pashkd.krender.engine.tools.texturemanager.TextureAtlasPackingPlan?) {
        textLine("Atlas Descriptor Export")
        ImGui.textUnformatted("Export Directory")
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputText("##texture_manager_export_dir", exportDirectoryBuffer)) {
            operations.setExportDirectory(readBuffer(exportDirectoryBuffer))
        }
        ImGui.textUnformatted("Export Base Name")
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputText("##texture_manager_export_base_name", exportBaseNameBuffer)) {
            operations.setExportBaseName(readBuffer(exportBaseNameBuffer))
        }
        val exportOverwrite = booleanArrayOf(state.importExport.exportOverwrite)
        if (ImGui.checkbox("Overwrite Existing Descriptor##texture_manager_export_overwrite", exportOverwrite)) {
            operations.setExportOverwrite(exportOverwrite[0])
        }
        val canExport = packingPlan?.packedRegionCount?.let { it > 0 } == true
        if (!canExport) ImGui.beginDisabled()
        if (ImGui.button("Export Atlas Descriptor Draft##texture_manager_export_descriptor")) {
            operations.exportAtlasDescriptorDraft()
        }
        if (!canExport) ImGui.endDisabled()
        if (!canExport) {
            textLine("Run a packing dry-run with at least one packed region before exporting.")
        }
        state.importExport.lastExportResult?.let { result ->
            textLine(result.message)
            result.writtenPaths.forEach(::textLine)
        }
    }

    private fun syncBuffersIfNeeded() {
        if (synced) return
        writeBuffer(importSourceBuffer, state.importExport.importSourcePath)
        writeBuffer(importTargetBuffer, state.importExport.importTargetDirectory)
        writeBuffer(exportDirectoryBuffer, state.importExport.exportDirectory)
        writeBuffer(exportBaseNameBuffer, state.importExport.exportBaseName)
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
        if (!ImGui.beginCombo("Padding##texture_manager_packing_padding", value.toString())) return
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
