package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegionSelection
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewCursorInfo
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.formatZoomMode
import com.pashkd.krender.engine.tools.common.texturepreview.hitTestTexturePreviewRegion
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.MouseButton
import imgui.SliderFlag
import imgui.WindowFlag
import imgui.api.slider
import imgui.or
import kotlin.math.abs
import glm_.vec2.Vec2 as ImVec2

class EnvironmentResourceInspectorPanel(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentResourcePreviewController,
    private val skyboxImportController: SkyboxAtlasImportController,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val sourcePathBuffer = ByteArray(256)
    private val outputDirectoryBuffer = ByteArray(128)
    private val regionXBuffer = ByteArray(16)
    private val regionYBuffer = ByteArray(16)
    private val regionWidthBuffer = ByteArray(16)
    private val regionHeightBuffer = ByteArray(16)
    private var sourcePathSynced = false
    private var outputDirectorySynced = false
    private var syncedRegionKey: String? = null
    private var clickDragDistance = 0f
    private var cursorText: String = "Cursor: <outside>"

    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Tools)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Tools, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val environment = state.environment
        if (environment == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }

        val model = controller.buildModel(environment)
        drawToolbar(environment, model)
        if (state.resourceInspectorState.selectedResourceMode == EnvironmentResourceMode.ImportedSkyboxSource) {
            ImGui.separator()
            drawSkyboxImportSection()
        }
        ImGui.separator()
        drawSelectionSummary(model)
        ImGui.separator()
        drawCanvas(model)
        ImGui.separator()
        drawSelectedItemDetails(model)
        ImGui.end()
    }

    private fun drawToolbar(
        environment: com.pashkd.krender.engine.assets.environment.Environment,
        model: EnvironmentResourcePreviewModel,
    ) {
        val inspectorState = state.resourceInspectorState
        ImGui.setNextItemWidth(220f)
        if (ImGui.beginCombo("Mode##env_resource_mode", inspectorState.selectedResourceMode.label())) {
            EnvironmentResourceMode.entries.forEach { mode ->
                if (ImGui.selectable(mode.label(), inspectorState.selectedResourceMode == mode)) {
                    controller.setMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (inspectorState.selectedResourceMode == EnvironmentResourceMode.Radiance) {
            val selectedMip =
                environment.radiance
                    ?.mips
                    ?.firstOrNull { it.level == inspectorState.selectedRadianceMip }
                    ?: environment.radiance?.mips?.firstOrNull()
            ImGui.sameLine()
            ImGui.setNextItemWidth(220f)
            if (ImGui.beginCombo("Mip##env_resource_mip", selectedMip?.let { "Mip ${it.level} (roughness ${"%.2f".format(it.roughness)})" } ?: "<none>")) {
                environment.radiance?.mips?.forEach { mip ->
                    if (ImGui.selectable("Mip ${mip.level} (roughness ${"%.2f".format(mip.roughness)})", mip.level == selectedMip?.level)) {
                        controller.setSelectedRadianceMip(mip.level)
                    }
                }
                ImGui.endCombo()
            }
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(160f)
        val previewState = inspectorState.resourcePreviewState
        if (ImGui.beginCombo("Zoom##env_resource_zoom", formatZoomMode(previewState.zoomMode))) {
            TexturePreviewZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), previewState.zoomMode == mode)) {
                    controller.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (previewState.zoomMode == TexturePreviewZoomMode.Custom) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(120f)
            if (slider("Custom##env_resource_custom_zoom", previewState::customZoom, 0.05f, 25f, "%.2f", SliderFlag.AlwaysClamp)) {
                controller.setPreviewZoom(previewState.customZoom)
            }
        }
        if (ImGui.button("Fit##env_resource_fit")) {
            controller.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset##env_resource_reset")) {
            controller.resetPreviewCamera()
        }
        ImGui.sameLine()
        val showGrid = booleanArrayOf(previewState.showGrid)
        if (ImGui.checkbox("Grid##env_resource_grid", showGrid)) {
            previewState.showGrid = showGrid[0]
        }
        ImGui.sameLine()
        val showChecker = booleanArrayOf(previewState.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##env_resource_checker", showChecker)) {
            previewState.showCheckerboard = showChecker[0]
        }
        ImGui.sameLine()
        val showBounds = booleanArrayOf(previewState.showBounds)
        if (ImGui.checkbox("Bounds##env_resource_bounds", showBounds)) {
            previewState.showBounds = showBounds[0]
        }
        if (previewState.showGrid) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(100f)
            slider("Grid##env_resource_grid_size", previewState::gridSpacingPixels, 4, 128, "%d", SliderFlag.AlwaysClamp)
        }
        if (model.diagnostics.isNotEmpty()) {
            ImGui.textWrapped(model.diagnostics.joinToString(" | "))
        }
    }

    private fun drawSkyboxImportSection() {
        syncImportBuffers()
        val importState = state.skyboxImportState
        ImGui.text("Skybox Import")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
        if (ImGui.inputText("##env_skybox_import_source_path", sourcePathBuffer)) {
            skyboxImportController.setSourcePath(readBuffer(sourcePathBuffer))
        }
        ImGui.text("Source texture path")
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo("Layout##env_skybox_import_layout", importState.layoutPreset.name)) {
            SkyboxImportLayoutPreset.entries.forEach { preset ->
                if (ImGui.selectable(preset.name, importState.layoutPreset == preset)) {
                    skyboxImportController.setLayoutPreset(preset)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        if (ImGui.button("Apply Layout##env_skybox_import_apply_layout")) {
            skyboxImportController.refreshDefaultRegions()
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(180f)
        if (ImGui.inputText("##env_skybox_import_output_dir", outputDirectoryBuffer)) {
            skyboxImportController.setOutputDirectory(readBuffer(outputDirectoryBuffer))
        }
        ImGui.text("Output directory")

        val selectedFace = state.skyboxImportState.selectedFace
        ImGui.setNextItemWidth(180f)
        if (ImGui.beginCombo("Face##env_skybox_import_face", selectedFace.id)) {
            EnvironmentCubemapFace.ordered.forEach { face ->
                if (ImGui.selectable(face.id, face == selectedFace)) {
                    skyboxImportController.selectFace(face)
                    controller.setSelectedItem(face.id)
                }
            }
            ImGui.endCombo()
        }
        drawSelectedImportRegionEditor(selectedFace)
        if (ImGui.button("Split And Update Manifest##env_skybox_import_commit")) {
            skyboxImportController.importIntoEnvironment()
        }
        tooltipOnHover("Splits the configured source image into six face PNGs and updates environment.skybox.faces. Save still uses the normal Environment save action.")
    }

    private fun drawSelectionSummary(model: EnvironmentResourcePreviewModel) {
        val selected = model.selectedItem
        ImGui.text("Mode: ${model.mode.label()}")
        ImGui.text(cursorText)
        ImGui.textWrapped(model.statusMessage)
        selected?.let {
            ImGui.text(
                buildString {
                    append("Selected: ${it.label}")
                    it.mipLevel?.let { mip -> append(" | Mip: $mip") }
                    it.roughness?.let { roughness -> append(" | Roughness: ${"%.2f".format(roughness)}") }
                },
            )
        } ?: ImGui.text("Selected: <none>")
    }

    private fun drawCanvas(model: EnvironmentResourcePreviewModel) {
        ImGui.beginChild(
            "environment_resource_canvas",
            ImVec2(0f, 280f),
            true,
            WindowFlag.NoScrollbar or WindowFlag.NoScrollWithMouse,
        )
        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        val canvasRect =
            com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewCanvasRect(
                min.x,
                min.y,
                size.x.coerceAtLeast(1f),
                size.y.coerceAtLeast(1f),
            )
        val previewState = state.resourceInspectorState.resourcePreviewState
        val layout =
            computeTexturePreviewViewportLayout(
                rect = canvasRect,
                textureWidth = model.contentWidth.coerceAtLeast(1),
                textureHeight = model.contentHeight.coerceAtLeast(1),
                previewState = previewState,
            )
        if (previewState.showCheckerboard) {
            TexturePreviewOverlays.drawCheckerboard(layout)
        }
        if (model.drawItemsAsOverlayRegions) {
            model.canvasPreviewHandle?.let { handle ->
                ImGui.windowDrawList.addImage(
                    handle.id,
                    ImVec2(layout.imageX, layout.imageY),
                    ImVec2(layout.imageX + layout.imageWidth, layout.imageY + layout.imageHeight),
                    ImVec2(handle.u0, handle.v0),
                    ImVec2(handle.u1, handle.v1),
                )
            }
        }
        if (previewState.showGrid) {
            TexturePreviewOverlays.drawGrid(layout, spacingPixels = previewState.gridSpacingPixels)
        }
        if (!model.drawItemsAsOverlayRegions) {
            model.items.forEach { item ->
                val rect = com.pashkd.krender.engine.tools.common.texturepreview.textureRegionScreenRect(item.region, layout)
                val handle = item.previewHandle
                if (handle != null) {
                    ImGui.windowDrawList.addImage(
                        handle.id,
                        ImVec2(rect.minX, rect.minY),
                        ImVec2(rect.maxX, rect.maxY),
                        ImVec2(handle.u0, handle.v0),
                        ImVec2(handle.u1, handle.v1),
                    )
                } else {
                    ImGui.windowDrawList.addRect(
                        ImVec2(rect.minX, rect.minY),
                        ImVec2(rect.maxX, rect.maxY),
                        0x99FFFFFF.toInt(),
                        0f,
                        thickness = 1.5f,
                    )
                    ImGui.windowDrawList.addText(ImVec2(rect.minX + 8f, rect.minY + 8f), 0xFFFFFFFF.toInt(), item.label)
                }
            }
        }
        if (previewState.showBounds) {
            TexturePreviewOverlays.drawRegionBounds(
                regions = model.items.map(EnvironmentResourceCanvasItem::region),
                layout = layout,
                selection =
                    TexturePreviewRegionSelection(
                        selectedId = state.resourceInspectorState.selectedRegionOrFace,
                        hoveredId = state.resourceInspectorState.hoveredRegionOrFace,
                    ),
            )
            model.selectedItem?.let { selected ->
                TexturePreviewOverlays.labelRegion(selected.region, layout)
            }
        }

        ImGui.cursorScreenPos = ImVec2(canvasRect.x, canvasRect.y)
        ImGui.invisibleButton("##environment_resource_canvas_hit", ImVec2(canvasRect.width, canvasRect.height))
        handleCanvasInteraction(model, layout)
        ImGui.endChild()
    }

    private fun drawSelectedItemDetails(model: EnvironmentResourcePreviewModel) {
        val selected = model.selectedItem
        if (selected == null) {
            ImGui.text("No selected resource.")
            return
        }
        ImGui.text("Selected Resource")
        ImGui.text("Kind: ${selected.resourceMode.label()}")
        ImGui.text("Face: ${selected.label}")
        selected.mipLevel?.let { ImGui.text("Mip Level: $it") }
        selected.roughness?.let { ImGui.text("Roughness: ${"%.3f".format(it)}") }
        ImGui.text("Manifest Path: ${selected.manifestPath ?: "<none>"}")
        ImGui.text("Resolved Path: ${selected.resolvedPath ?: "<none>"}")
        ImGui.text("Exists: ${if (selected.exists) "yes" else "no"}")
        ImGui.text("Resolution: ${selected.width ?: 0} x ${selected.height ?: 0}")
        ImGui.text("Format: ${selected.format ?: "<unknown>"}")
        if (selected.warnings.isNotEmpty()) {
            ImGui.text("Warnings")
            selected.warnings.forEach(ImGui::bulletText)
        }
    }

    private fun handleCanvasInteraction(
        model: EnvironmentResourcePreviewModel,
        layout: com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewViewportLayout,
    ) {
        if (!ImGui.isItemHovered()) {
            controller.setHoveredItem(null)
            cursorText = "Cursor: <outside>"
            return
        }
        val io = ImGui.io
        if (io.mouseWheel != 0f) {
            controller.setPreviewZoom(state.resourceInspectorState.resourcePreviewState.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            controller.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            clickDragDistance += abs(io.mouseDelta.x) + abs(io.mouseDelta.y)
        }
        val hovered =
            hitTestTexturePreviewRegion(
                regions = model.items.map(EnvironmentResourceCanvasItem::region),
                layout = layout,
                mouseX = io.mousePos.x,
                mouseY = io.mousePos.y,
            )
        controller.setHoveredItem(hovered?.id)
        val cursor =
            computeTexturePreviewCursorInfo(
                screenX = io.mousePos.x,
                screenY = io.mousePos.y,
                layout = layout,
                textureWidth = model.contentWidth.coerceAtLeast(1),
                textureHeight = model.contentHeight.coerceAtLeast(1),
            )
        cursorText =
            if (cursor.isInside) {
                "Cursor: ${cursor.textureX}, ${cursor.textureY}"
            } else {
                "Cursor: <outside>"
            }
        if (io.mouseClicked[0] && clickDragDistance < ClickDragThreshold) {
            controller.setSelectedItem(hovered?.id)
        }
        if (io.mouseClicked[0]) {
            clickDragDistance = 0f
        }
    }

    private companion object {
        private const val ClickDragThreshold = 6f
    }

    private fun drawSelectedImportRegionEditor(face: EnvironmentCubemapFace) {
        val region = state.skyboxImportState.regions[face] ?: return
        syncSelectedRegionBuffers(face, region)
        ImGui.text("Region")
        if (ImGui.inputText("##env_import_region_x", regionXBuffer)) {
            readBuffer(regionXBuffer).toIntOrNull()?.let { value -> skyboxImportController.updateRegion(face, x = value) }
        }
        ImGui.sameLine()
        if (ImGui.inputText("##env_import_region_y", regionYBuffer)) {
            readBuffer(regionYBuffer).toIntOrNull()?.let { value -> skyboxImportController.updateRegion(face, y = value) }
        }
        ImGui.sameLine()
        if (ImGui.inputText("##env_import_region_width", regionWidthBuffer)) {
            readBuffer(regionWidthBuffer).toIntOrNull()?.let { value -> skyboxImportController.updateRegion(face, width = value) }
        }
        ImGui.sameLine()
        if (ImGui.inputText("##env_import_region_height", regionHeightBuffer)) {
            readBuffer(regionHeightBuffer).toIntOrNull()?.let { value -> skyboxImportController.updateRegion(face, height = value) }
        }
        ImGui.text("x / y / width / height")
        ImGui.setNextItemWidth(140f)
        if (ImGui.beginCombo("Rotate##env_import_face_rotate", region.transform.rotateDegrees.toString())) {
            listOf(0, 90, 180, 270).forEach { option ->
                if (ImGui.selectable(option.toString(), option == region.transform.rotateDegrees)) {
                    skyboxImportController.updateTransform(face, rotateDegrees = option)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        val flipX = booleanArrayOf(region.transform.flipX)
        if (ImGui.checkbox("Flip X##env_import_face_flip_x", flipX)) {
            skyboxImportController.updateTransform(face, flipX = flipX[0])
        }
        ImGui.sameLine()
        val flipY = booleanArrayOf(region.transform.flipY)
        if (ImGui.checkbox("Flip Y##env_import_face_flip_y", flipY)) {
            skyboxImportController.updateTransform(face, flipY = flipY[0])
        }
    }

    private fun syncImportBuffers() {
        if (!sourcePathSynced || readBuffer(sourcePathBuffer) != state.skyboxImportState.sourcePath) {
            writeBuffer(sourcePathBuffer, state.skyboxImportState.sourcePath)
            sourcePathSynced = true
        }
        if (!outputDirectorySynced || readBuffer(outputDirectoryBuffer) != state.skyboxImportState.outputDirectory) {
            writeBuffer(outputDirectoryBuffer, state.skyboxImportState.outputDirectory)
            outputDirectorySynced = true
        }
    }

    private fun syncSelectedRegionBuffers(
        face: EnvironmentCubemapFace,
        region: SkyboxImportRegion,
    ) {
        if (syncedRegionKey == face.id &&
            readBuffer(regionXBuffer) == region.x.toString() &&
            readBuffer(regionYBuffer) == region.y.toString() &&
            readBuffer(regionWidthBuffer) == region.width.toString() &&
            readBuffer(regionHeightBuffer) == region.height.toString()
        ) {
            return
        }
        writeBuffer(regionXBuffer, region.x.toString())
        writeBuffer(regionYBuffer, region.y.toString())
        writeBuffer(regionWidthBuffer, region.width.toString())
        writeBuffer(regionHeightBuffer, region.height.toString())
        syncedRegionKey = face.id
    }
}
