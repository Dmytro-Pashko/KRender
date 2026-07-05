package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.importing.FileDialogFilter
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.RadianceMip
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegionSelection
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.formatZoomMode
import com.pashkd.krender.engine.tools.common.texturepreview.hitTestTexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.packColor
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.MouseButton
import imgui.SliderFlag
import imgui.WindowFlag
import imgui.api.slider
import imgui.or
import kotlin.math.abs
import glm_.vec2.Vec2 as ImVec2

class EnvironmentToolsPanel(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentResourcePreviewController,
    private val skyboxImportController: SkyboxAtlasImportController,
    private val fileDialogService: FileDialogService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val skyboxSourceBuffer = ByteArray(512)
    private val skyboxOutputBuffer = ByteArray(256)
    private val regionXBuffer = ByteArray(16)
    private val regionYBuffer = ByteArray(16)
    private val regionWidthBuffer = ByteArray(16)
    private val regionHeightBuffer = ByteArray(16)
    private var sourceBufferSynced = false
    private var outputBufferSynced = false
    private var syncedRegionKey: String? = null
    private var openCreateSkyboxDialog = false
    private var importCanvasClickDragDistance = 0f

    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Tools)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Tools, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Tools, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        syncBuffers()
        state.environment?.let(::drawResources) ?: ImGui.text("No environment loaded.")
        drawCreateSkyboxDialog()
        ImGui.end()
    }

    private fun drawResources(environment: Environment) {
        drawGeneration(environment)
        ImGui.separator()
        drawSkybox(environment)
        ImGui.separator()
        drawRadiance(environment)
        ImGui.separator()
        drawIrradiance(environment)
        ImGui.separator()
        drawBrdf(environment)
    }

    private fun drawGeneration(environment: Environment) {
        ImGui.text("Generation")
        withDisabledButton("Create Skybox##env_tools_create_skybox", enabled = environment.skybox == null) {
            openCreateSkyboxDialog = true
        }
        withDisabledButton("Generate Radiance##env_tools_generate_radiance", enabled = false) {}
        withDisabledButton("Generate Irradiance##env_tools_generate_irradiance", enabled = false) {}
        withDisabledButton("Generate BRDF##env_tools_generate_brdf", enabled = false) {}
    }

    private fun drawSkybox(environment: Environment) {
        ImGui.text("Skybox")
        val skybox = environment.skybox
        if (skybox == null) {
            ImGui.text("Layout: <none>")
            ImGui.text("Resolution: <none>")
            return
        }
        ImGui.text("Layout: ${skybox.layout}")
        ImGui.text("Resolution: ${skybox.resolution}px")
        val faces = EnvironmentCubemapFace.ordered.mapNotNull { face ->
            skybox.faces.entries.firstOrNull { (key, _) -> EnvironmentCubemapFace.fromIdOrAlias(key) == face }?.let { face to it.value }
        }
        if (ImGui.treeNode("Faces(${faces.size})##env_tools_skybox_faces")) {
            faces.forEach { (face, path) ->
                val selected = isSelected(EnvironmentResourceMode.Skybox, face.id)
                if (ImGui.selectable("${face.id}($path)##env_tools_skybox_${face.id}", selected)) {
                    controller.showSkyboxFace(face)
                }
            }
            ImGui.treePop()
        }
        if (ImGui.button("Remove##env_tools_remove_skybox")) {
            controller.removeSkybox()
        }
    }

    private fun drawRadiance(environment: Environment) {
        ImGui.text("Radiance")
        val radiance = environment.radiance
        if (radiance == null || radiance.mips.isEmpty()) {
            ImGui.text("Mips: <none>")
            return
        }
        radiance.mips.sortedBy { it.level }.forEach(::drawRadianceMip)
        if (ImGui.button("Remove##env_tools_remove_radiance")) {
            controller.removeRadiance()
        }
    }

    private fun drawRadianceMip(mip: RadianceMip) {
        if (ImGui.treeNode("Mip ${mip.level}##env_tools_radiance_mip_${mip.level}")) {
            ImGui.text("Roughness: ${"%.3f".format(mip.roughness)}")
            ImGui.text("Path: ${mip.path}")
            inferEnvironmentCubemapFacePaths(
                resourcePath = mip.path,
                directoryStem = directoryStem(mip.path, "radiance_${mip.level}"),
            ).forEach { (face, path) ->
                val selected =
                    state.resourceInspectorState.selectedResourceMode == EnvironmentResourceMode.Radiance &&
                        state.resourceInspectorState.selectedRadianceMip == mip.level &&
                        state.resourceInspectorState.selectedRegionOrFace == face.id
                if (ImGui.selectable("${face.id}($path)##env_tools_radiance_${mip.level}_${face.id}", selected)) {
                    controller.showRadianceFace(mip.level, face)
                }
            }
            ImGui.treePop()
        }
    }

    private fun drawIrradiance(environment: Environment) {
        ImGui.text("Irradiance")
        val irradiance = environment.irradiance
        if (irradiance == null) {
            ImGui.text("Path: <none>")
            ImGui.text("Resolution: <none>")
            return
        }
        ImGui.text("Path: ${irradiance.path}")
        ImGui.text("Resolution: ${irradiance.resolution}px")
        inferEnvironmentCubemapFacePaths(
            resourcePath = irradiance.path,
            directoryStem = directoryStem(irradiance.path, "irradiance"),
        ).forEach { (face, path) ->
            val selected = isSelected(EnvironmentResourceMode.Irradiance, face.id)
            if (ImGui.selectable("${face.id}($path)##env_tools_irradiance_${face.id}", selected)) {
                controller.showIrradianceFace(face)
            }
        }
        if (ImGui.button("Remove##env_tools_remove_irradiance")) {
            controller.removeIrradiance()
        }
    }

    private fun drawBrdf(environment: Environment) {
        ImGui.text("BRDF")
        val brdf = environment.brdfLut
        if (brdf == null) {
            ImGui.text("Path: <none>")
            return
        }
        ImGui.text("Path: ${brdf.path}")
        val selected = state.resourceInspectorState.selectedResourceMode == EnvironmentResourceMode.BrdfLut
        if (ImGui.selectable("brdf(${brdf.path})##env_tools_brdf", selected)) {
            controller.showBrdfLut()
        }
        if (ImGui.button("Remove##env_tools_remove_brdf")) {
            controller.removeBrdfLut()
        }
    }

    private fun drawCreateSkyboxDialog() {
        if (!openCreateSkyboxDialog) return
        ImGui.openPopup("Create Skybox##env_tools_create_skybox_dialog")
        ImGui.setNextWindowSize(Vec2(900f, 860f), Cond.Appearing)
        if (!ImGui.beginPopupModal("Create Skybox##env_tools_create_skybox_dialog")) return

        state.environment?.let { environment ->
            drawCreateSkyboxDialogContent(environment)
        } ?: ImGui.text("No environment loaded.")
        ImGui.endPopup()
    }

    private fun drawCreateSkyboxDialogContent(environment: Environment) {
        ImGui.text("Source")
        ImGui.sameLine()
        ImGui.pushItemWidth(520f)
        if (ImGui.inputText("##env_tools_create_skybox_source", skyboxSourceBuffer)) {
            skyboxImportController.setSourcePath(readBuffer(skyboxSourceBuffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        if (ImGui.button("Browse...##env_tools_create_skybox_browse")) {
            val selected = fileDialogService.openFile(SkyboxSourceFileDialogFilters) ?: ""
            if (selected.isNotBlank()) {
                skyboxImportController.setSourcePath(selected)
                writeBuffer(skyboxSourceBuffer, state.skyboxImportState.sourcePath)
                sourceBufferSynced = true
            }
        }
        ImGui.text("Source texture path")

        val importState = state.skyboxImportState
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo("Layout##env_tools_create_skybox_layout", importState.layoutPreset.name)) {
            SkyboxImportLayoutPreset.entries.forEach { preset ->
                if (ImGui.selectable(preset.name, importState.layoutPreset == preset)) {
                    skyboxImportController.setLayoutPreset(preset)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        if (ImGui.button("Apply Layout##env_tools_create_skybox_apply_layout")) {
            skyboxImportController.refreshDefaultRegions()
        }
        ImGui.pushItemWidth(300f)
        if (ImGui.inputText("##env_tools_create_skybox_output", skyboxOutputBuffer)) {
            skyboxImportController.setOutputDirectory(readBuffer(skyboxOutputBuffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        ImGui.text("Output directory")

        val model = controller.buildImportedSourceModel(environment)
        if (model.diagnostics.isNotEmpty()) {
            ImGui.textWrapped(model.diagnostics.joinToString(" | "))
        }

        ImGui.separator()
        ImGui.text("Faces")
        ImGui.beginChild("##env_tools_create_skybox_faces", ImVec2(180f, 120f), true)
        EnvironmentCubemapFace.ordered.forEach { face ->
            if (ImGui.selectable(face.id, face == state.skyboxImportState.selectedFace)) {
                skyboxImportController.selectFace(face)
                controller.setSelectedItem(face.id)
            }
        }
        ImGui.endChild()
        ImGui.separator()

        drawImportedSourcePreview(model)
        drawSelectedImportRegionEditor(state.skyboxImportState.selectedFace)

        if (ImGui.button("Create##env_tools_create_skybox_commit")) {
            skyboxImportController.importIntoEnvironment()
            openCreateSkyboxDialog = false
            ImGui.closeCurrentPopup()
        }
        tooltipOnHover("Splits the configured source image into six face PNGs and updates environment.skybox.faces. Save still uses the normal Environment save action.")
        ImGui.sameLine()
        if (ImGui.button("Close##env_tools_create_skybox_close_inline")) {
            openCreateSkyboxDialog = false
            ImGui.closeCurrentPopup()
        }
    }

    private fun drawImportedSourcePreview(model: EnvironmentResourcePreviewModel) {
        val previewState = state.resourceInspectorState.resourcePreviewState
        ImGui.setNextItemWidth(160f)
        if (ImGui.beginCombo("Zoom##env_tools_create_skybox_zoom", formatZoomMode(previewState.zoomMode))) {
            com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), previewState.zoomMode == mode)) {
                    controller.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (previewState.zoomMode == com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewZoomMode.Custom) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(120f)
            if (slider("Custom##env_tools_create_skybox_custom_zoom", previewState::customZoom, 0.05f, 25f, "%.2f", SliderFlag.AlwaysClamp)) {
                controller.setPreviewZoom(previewState.customZoom)
            }
        }
        if (ImGui.button("Fit##env_tools_create_skybox_fit")) {
            controller.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset##env_tools_create_skybox_reset")) {
            controller.resetPreviewCamera()
        }
        ImGui.sameLine()
        val showGrid = booleanArrayOf(previewState.showGrid)
        if (ImGui.checkbox("Grid##env_tools_create_skybox_grid", showGrid)) {
            previewState.showGrid = showGrid[0]
        }
        ImGui.sameLine()
        val showChecker = booleanArrayOf(previewState.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##env_tools_create_skybox_checker", showChecker)) {
            previewState.showCheckerboard = showChecker[0]
        }
        ImGui.sameLine()
        val showBounds = booleanArrayOf(previewState.showBounds)
        if (ImGui.checkbox("Bounds##env_tools_create_skybox_bounds", showBounds)) {
            previewState.showBounds = showBounds[0]
        }
        ImGui.separator()

        ImGui.beginChild(
            "environment_tools_create_skybox_canvas",
            ImVec2(0f, 360f),
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
        model.canvasPreviewHandle?.let { handle ->
            ImGui.windowDrawList.addImage(
                handle.id,
                ImVec2(layout.imageX, layout.imageY),
                ImVec2(layout.imageX + layout.imageWidth, layout.imageY + layout.imageHeight),
                ImVec2(handle.u0, handle.v0),
                ImVec2(handle.u1, handle.v1),
            )
        }
        if (previewState.showGrid) {
            TexturePreviewOverlays.drawGrid(layout, spacingPixels = previewState.gridSpacingPixels, color = packPreviewColor(previewState.gridColor))
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
        ImGui.invisibleButton("##environment_tools_create_skybox_canvas_hit", ImVec2(canvasRect.width, canvasRect.height))
        handleImportedSourceCanvasInteraction(model, layout)
        ImGui.endChild()
    }

    private fun handleImportedSourceCanvasInteraction(
        model: EnvironmentResourcePreviewModel,
        layout: com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewViewportLayout,
    ) {
        if (!ImGui.isItemHovered()) {
            controller.setHoveredItem(null)
            return
        }
        val io = ImGui.io
        if (io.keyCtrl && io.mouseWheel != 0f) {
            controller.setPreviewZoom(state.resourceInspectorState.resourcePreviewState.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            controller.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            importCanvasClickDragDistance += abs(io.mouseDelta.x) + abs(io.mouseDelta.y)
        }
        val hovered =
            hitTestTexturePreviewRegion(
                regions = model.items.map(EnvironmentResourceCanvasItem::region),
                layout = layout,
                mouseX = io.mousePos.x,
                mouseY = io.mousePos.y,
            )
        controller.setHoveredItem(hovered?.id)
        if (io.mouseClicked[0] && importCanvasClickDragDistance < ImportCanvasClickDragThreshold) {
            controller.setSelectedItem(hovered?.id)
            hovered?.id?.let(EnvironmentCubemapFace::fromIdOrAlias)?.let(skyboxImportController::selectFace)
        }
        if (io.mouseClicked[0]) {
            importCanvasClickDragDistance = 0f
        }
    }

    private fun drawSelectedImportRegionEditor(face: EnvironmentCubemapFace) {
        val region = state.skyboxImportState.regions[face] ?: return
        syncSelectedRegionBuffers(face, region)
        ImGui.text("Region")
        ImGui.setNextItemWidth(150f)
        if (ImGui.inputText("##env_import_region_x", regionXBuffer)) {
            readBuffer(regionXBuffer).toIntOrNull()?.let { value -> skyboxImportController.updateRegion(face, x = value) }
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(150f)
        if (ImGui.inputText("##env_import_region_y", regionYBuffer)) {
            readBuffer(regionYBuffer).toIntOrNull()?.let { value -> skyboxImportController.updateRegion(face, y = value) }
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(150f)
        if (ImGui.inputText("##env_import_region_width", regionWidthBuffer)) {
            readBuffer(regionWidthBuffer).toIntOrNull()?.let { value -> skyboxImportController.updateRegion(face, width = value) }
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(150f)
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

    private fun syncBuffers() {
        if (!sourceBufferSynced || readBuffer(skyboxSourceBuffer) != state.skyboxImportState.sourcePath) {
            writeBuffer(skyboxSourceBuffer, state.skyboxImportState.sourcePath)
            sourceBufferSynced = true
        }
        if (!outputBufferSynced || readBuffer(skyboxOutputBuffer) != state.skyboxImportState.outputDirectory) {
            writeBuffer(skyboxOutputBuffer, state.skyboxImportState.outputDirectory)
            outputBufferSynced = true
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

    private fun isSelected(
        mode: EnvironmentResourceMode,
        itemId: String,
    ): Boolean =
        state.resourceInspectorState.selectedResourceMode == mode &&
            state.resourceInspectorState.selectedRegionOrFace == itemId

    private fun withDisabledButton(
        label: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        if (!enabled) ImGui.beginDisabled()
        if (ImGui.button(label) && enabled) {
            onClick()
        }
        if (!enabled) ImGui.endDisabled()
    }

    private fun packPreviewColor(color: com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewColor): Int =
        packColor(
            (color.red * 255f).toInt().coerceIn(0, 255),
            (color.green * 255f).toInt().coerceIn(0, 255),
            (color.blue * 255f).toInt().coerceIn(0, 255),
            (color.alpha * 255f).toInt().coerceIn(0, 255),
        )

    private companion object {
        private const val ImportCanvasClickDragThreshold = 6f

        private val SkyboxSourceFileDialogFilters =
            listOf(
                FileDialogFilter("Skybox Source Textures", listOf("png", "jpg", "jpeg", "bmp", "webp")),
            )
    }
}
