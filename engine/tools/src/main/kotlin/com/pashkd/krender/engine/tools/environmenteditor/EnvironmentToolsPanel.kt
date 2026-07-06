package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import com.pashkd.krender.engine.assets.environment.EnvironmentToneMapping
import com.pashkd.krender.engine.assets.environment.RadianceMip
import com.pashkd.krender.engine.assets.importing.EnvironmentSourceFileDialogFilters
import com.pashkd.krender.engine.assets.importing.FileDialogFilter
import com.pashkd.krender.engine.assets.importing.FileDialogService
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
import java.io.File
import kotlin.math.abs
import glm_.vec2.Vec2 as ImVec2

class EnvironmentToolsPanel(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentResourcePreviewController,
    private val skyboxImportController: SkyboxAtlasImportController,
    private val hdrGenerationController: HdrEnvironmentGenerationController,
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
    private val allSourceBuffer = ByteArray(512)
    private val allOutputRootBuffer = ByteArray(256)
    private var syncedRegionKey: String? = null
    private var openImportSkyboxDialog = false
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
        drawImportSkyboxDialog()
        drawHdrGenerationDialogs()
        ImGui.end()
    }

    private fun drawResources(environment: Environment) {
        drawGeneration()
        ImGui.separator()
        drawSkybox(environment)
        ImGui.separator()
        drawRadiance(environment)
        ImGui.separator()
        drawIrradiance(environment)
        ImGui.separator()
        drawBrdf(environment)
    }

    private fun drawGeneration() {
        ImGui.text("Generation")
        if (ImGui.button("Import Skybox Atlas##env_tools_import_skybox_atlas")) {
            openImportSkyboxDialog = true
        }
        tooltipOnHover("Split an atlas/cross/row PNG/JPG/JPEG/WEBP/BMP source into six skybox face PNG files.")

        if (ImGui.button("Generate IBL##env_tools_generate_ibl")) {
            hdrGenerationController.open(HdrEnvironmentGenerationDialog.AllIbl)
        }
        tooltipOnHover("Generate skybox, irradiance, and radiance resources from one HDR/EXR source.")

        if (ImGui.button("Import BRDF LUT##env_tools_import_brdf_lut")) {
            val selected = fileDialogService.openFile(BrdfLutFileDialogFilters) ?: ""
            if (selected.isNotBlank()) {
                hdrGenerationController.importBrdfLut(selected)
            }
        }
        tooltipOnHover("Copy an existing BRDF LUT texture into the current environment and update the manifest.")

        state.hdrGenerationState.statusMessage?.let(ImGui::textWrapped)
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

    private fun drawImportSkyboxDialog() {
        if (!openImportSkyboxDialog) return
        ImGui.openPopup("Import Skybox Atlas##env_tools_import_skybox_dialog")
        ImGui.setNextWindowSize(Vec2(900f, 860f), Cond.Appearing)
        if (!ImGui.beginPopupModal("Import Skybox Atlas##env_tools_import_skybox_dialog")) return

        state.environment?.let(::drawImportSkyboxDialogContent) ?: ImGui.text("No environment loaded.")
        ImGui.endPopup()
    }

    private fun drawImportSkyboxDialogContent(environment: Environment) {
        ImGui.text("Source")
        ImGui.sameLine()
        ImGui.pushItemWidth(520f)
        if (ImGui.inputText("##env_tools_import_skybox_source", skyboxSourceBuffer)) {
            skyboxImportController.setSourcePath(readBuffer(skyboxSourceBuffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        if (ImGui.button("Browse...##env_tools_import_skybox_browse")) {
            val selected = fileDialogService.openFile(SkyboxAtlasFileDialogFilters) ?: ""
            if (selected.isNotBlank()) {
                skyboxImportController.setSourcePath(selected)
            }
        }
        ImGui.text("Source atlas/cross/row texture path")

        val importState = state.skyboxImportState
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo("Layout##env_tools_import_skybox_layout", importState.layoutPreset.name)) {
            SkyboxImportLayoutPreset.entries.forEach { preset ->
                if (ImGui.selectable(preset.name, importState.layoutPreset == preset)) {
                    skyboxImportController.setLayoutPreset(preset)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        if (ImGui.button("Apply Layout##env_tools_import_skybox_apply_layout")) {
            skyboxImportController.refreshDefaultRegions()
        }
        ImGui.pushItemWidth(300f)
        if (ImGui.inputText("##env_tools_import_skybox_output", skyboxOutputBuffer)) {
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
        ImGui.beginChild("##env_tools_import_skybox_faces", ImVec2(180f, 120f), true)
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

        if (ImGui.button("Import##env_tools_import_skybox_commit")) {
            skyboxImportController.importIntoEnvironment()
            openImportSkyboxDialog = false
            ImGui.closeCurrentPopup()
        }
        tooltipOnHover("Exports six skybox face PNG files and updates environment.skybox.faces.")
        ImGui.sameLine()
        if (ImGui.button("Cancel##env_tools_import_skybox_cancel")) {
            openImportSkyboxDialog = false
            ImGui.closeCurrentPopup()
        }
    }

    private fun drawHdrGenerationDialogs() {
        when (state.hdrGenerationState.openDialog) {
            HdrEnvironmentGenerationDialog.AllIbl -> drawAllIblDialog()
            null -> Unit
        }
    }

    private fun drawAllIblDialog() {
        val dialog = HdrEnvironmentGenerationDialog.AllIbl
        val request = state.hdrGenerationState.allIblRequest
        val availability = hdrGenerationController.availability(dialog)
        ImGui.openPopup("${dialog.title}##env_tools_hdr_all_dialog")
        ImGui.setNextWindowSize(Vec2(720f, 520f), Cond.Appearing)
        if (!ImGui.beginPopupModal("${dialog.title}##env_tools_hdr_all_dialog")) return

        drawHdrSourceInput(
            label = "Source HDR/EXR file",
            buffer = allSourceBuffer,
            onChanged = hdrGenerationController::setAllSourcePath,
        )
        drawPathInput(
            label = "Output root",
            buffer = allOutputRootBuffer,
            onChanged = { request.outputRoot = it },
        )
        drawResolvedOutputLabel("Resolved output", request.outputRoot)
        drawOverwritePolicyCombo("Overwrite policy##env_tools_hdr_all_overwrite", request.overwritePolicy) {
            request.overwritePolicy = it
        }

        ImGui.separator()
        val skyboxEnabled = booleanArrayOf(request.skyboxEnabled)
        if (ImGui.checkbox("Skybox##env_tools_hdr_all_skybox_enabled", skyboxEnabled)) request.skyboxEnabled = skyboxEnabled[0]
        if (request.skyboxEnabled) {
            drawResolutionCombo(
                label = "Resolution##env_tools_hdr_all_skybox_resolution",
                selected = request.skyboxResolution,
                options = SkyboxResolutionOptions,
            ) { request.skyboxResolution = it }
            slider("Exposure##env_tools_hdr_all_skybox_exposure", request::skyboxExposure, 0.1f, 8f, "%.2f", SliderFlag.AlwaysClamp)
            drawToneMappingCombo("Tone mapping##env_tools_hdr_all_skybox_tone_mapping", request.skyboxToneMapping) {
                request.skyboxToneMapping = it
            }
        }

        ImGui.separator()
        val irradianceEnabled = booleanArrayOf(request.irradianceEnabled)
        if (ImGui.checkbox("Irradiance##env_tools_hdr_all_irradiance_enabled", irradianceEnabled)) {
            request.irradianceEnabled = irradianceEnabled[0]
        }
        if (request.irradianceEnabled) {
            drawResolutionCombo(
                label = "Resolution##env_tools_hdr_all_irradiance_resolution",
                selected = request.irradianceResolution,
                options = IrradianceResolutionOptions,
            ) { request.irradianceResolution = it }
            drawIntOptionCombo(
                label = "Sample count##env_tools_hdr_all_irradiance_samples",
                selected = request.irradianceSampleCount,
                options = SampleCountOptions,
            ) { request.irradianceSampleCount = it }
        }

        ImGui.separator()
        val radianceEnabled = booleanArrayOf(request.radianceEnabled)
        if (ImGui.checkbox("Radiance##env_tools_hdr_all_radiance_enabled", radianceEnabled)) {
            request.radianceEnabled = radianceEnabled[0]
        }
        if (request.radianceEnabled) {
            drawResolutionCombo(
                label = "Base resolution##env_tools_hdr_all_radiance_resolution",
                selected = request.radianceBaseResolution,
                options = RadianceResolutionOptions,
            ) { request.radianceBaseResolution = it }
            slider("Mip count##env_tools_hdr_all_radiance_mip_count", request::radianceMipCount, 1, 12, "%d", SliderFlag.AlwaysClamp)
            drawIntOptionCombo(
                label = "Sample count##env_tools_hdr_all_radiance_samples",
                selected = request.radianceSampleCount,
                options = SampleCountOptions,
            ) { request.radianceSampleCount = it }
        }

        drawGenerationAvailability(availability)
        drawGenerateCancelRow(
            canGenerate = availability.available && request.sourceHdrPath.isNotBlank(),
            generateLabel = "Generate##env_tools_hdr_all_generate",
            missingInputMessage = "Select a source HDR/EXR file.",
            availability = availability,
            onGenerate = hdrGenerationController::generateAll,
        )
    }

    private fun drawHdrSourceInput(
        label: String,
        buffer: ByteArray,
        onChanged: (String) -> Unit,
    ) {
        ImGui.text(label)
        ImGui.sameLine()
        ImGui.pushItemWidth(440f)
        if (ImGui.inputText("##${label}_input", buffer)) {
            onChanged(readBuffer(buffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        if (ImGui.button("Browse...##${label}_browse")) {
            val selected = fileDialogService.openFile(EnvironmentSourceFileDialogFilters) ?: ""
            if (selected.isNotBlank()) {
                onChanged(selected)
            }
        }
    }

    private fun drawPathInput(
        label: String,
        buffer: ByteArray,
        onChanged: (String) -> Unit,
    ) {
        ImGui.text(label)
        ImGui.sameLine()
        ImGui.pushItemWidth(440f)
        if (ImGui.inputText("##${label}_input", buffer)) {
            onChanged(readBuffer(buffer))
        }
        ImGui.popItemWidth()
    }

    private fun drawResolutionCombo(
        label: String,
        selected: Int,
        options: IntArray,
        onChanged: (Int) -> Unit,
    ) {
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(label, selected.toString())) {
            options.forEach { option ->
                if (ImGui.selectable(option.toString(), option == selected)) {
                    onChanged(option)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawIntOptionCombo(
        label: String,
        selected: Int,
        options: IntArray,
        onChanged: (Int) -> Unit,
    ) {
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(label, selected.toString())) {
            options.forEach { option ->
                if (ImGui.selectable(option.toString(), option == selected)) {
                    onChanged(option)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawResolvedOutputLabel(
        label: String,
        path: String,
    ) {
        val manifestPath = state.environment?.manifestPath ?: return
        if (path.isBlank()) return
        val normalized = path.replace('\\', '/')
        val resolved =
            if (File(normalized).isAbsolute) {
                normalized
            } else {
                EnvironmentPathResolver.resolvePath(manifestPath, normalized)
            }
        ImGui.textDisabled("$label: $resolved")
        tooltipOnHover("Resolved relative to the current assets tree and environment manifest location.")
    }

    private fun drawOverwritePolicyCombo(
        label: String,
        selected: EnvironmentIblOverwritePolicy,
        onChanged: (EnvironmentIblOverwritePolicy) -> Unit,
    ) {
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(label, selected.name)) {
            EnvironmentIblOverwritePolicy.entries.forEach { option ->
                if (ImGui.selectable(option.name, option == selected)) {
                    onChanged(option)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawToneMappingCombo(
        label: String,
        selected: EnvironmentToneMapping,
        onChanged: (EnvironmentToneMapping) -> Unit,
    ) {
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(label, selected.name)) {
            EnvironmentToneMapping.entries.forEach { option ->
                if (ImGui.selectable(option.name, option == selected)) {
                    onChanged(option)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawGenerationAvailability(availability: HdrEnvironmentGenerationAvailability) {
        if (availability.reason != null) {
            ImGui.textWrapped(availability.reason)
        }
    }

    private fun drawGenerateCancelRow(
        canGenerate: Boolean,
        generateLabel: String,
        missingInputMessage: String?,
        availability: HdrEnvironmentGenerationAvailability,
        onGenerate: () -> Unit,
    ) {
        if (!canGenerate) ImGui.beginDisabled()
        if (ImGui.button(generateLabel) && canGenerate) {
            onGenerate()
        }
        if (!canGenerate) {
            ImGui.endDisabled()
            tooltipOnHover(availability.reason ?: missingInputMessage.orEmpty())
        }
        ImGui.sameLine()
        if (ImGui.button("Cancel##$generateLabel")) {
            hdrGenerationController.close()
            ImGui.closeCurrentPopup()
        }
    }

    private fun drawImportedSourcePreview(model: EnvironmentResourcePreviewModel) {
        val previewState = state.resourceInspectorState.resourcePreviewState
        ImGui.setNextItemWidth(160f)
        if (ImGui.beginCombo("Zoom##env_tools_import_skybox_zoom", formatZoomMode(previewState.zoomMode))) {
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
            if (slider("Custom##env_tools_import_skybox_custom_zoom", previewState::customZoom, 0.05f, 25f, "%.2f", SliderFlag.AlwaysClamp)) {
                controller.setPreviewZoom(previewState.customZoom)
            }
        }
        if (ImGui.button("Fit##env_tools_import_skybox_fit")) {
            controller.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset##env_tools_import_skybox_reset")) {
            controller.resetPreviewCamera()
        }
        ImGui.sameLine()
        val showGrid = booleanArrayOf(previewState.showGrid)
        if (ImGui.checkbox("Grid##env_tools_import_skybox_grid", showGrid)) {
            previewState.showGrid = showGrid[0]
        }
        ImGui.sameLine()
        val showChecker = booleanArrayOf(previewState.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##env_tools_import_skybox_checker", showChecker)) {
            previewState.showCheckerboard = showChecker[0]
        }
        ImGui.sameLine()
        val showBounds = booleanArrayOf(previewState.showBounds)
        if (ImGui.checkbox("Bounds##env_tools_import_skybox_bounds", showBounds)) {
            previewState.showBounds = showBounds[0]
        }
        ImGui.separator()

        ImGui.beginChild(
            "environment_tools_import_skybox_canvas",
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
        ImGui.invisibleButton("##environment_tools_import_skybox_canvas_hit", ImVec2(canvasRect.width, canvasRect.height))
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
        syncBuffer(skyboxSourceBuffer, state.skyboxImportState.sourcePath)
        syncBuffer(skyboxOutputBuffer, state.skyboxImportState.outputDirectory)
        syncBuffer(allSourceBuffer, state.hdrGenerationState.allIblRequest.sourceHdrPath)
        syncBuffer(allOutputRootBuffer, state.hdrGenerationState.allIblRequest.outputRoot)
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

    private fun syncBuffer(
        buffer: ByteArray,
        value: String,
    ) {
        if (readBuffer(buffer) != value) {
            writeBuffer(buffer, value)
        }
    }

    private fun isSelected(
        mode: EnvironmentResourceMode,
        itemId: String,
    ): Boolean =
        state.resourceInspectorState.selectedResourceMode == mode &&
            state.resourceInspectorState.selectedRegionOrFace == itemId

    private fun packPreviewColor(color: com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewColor): Int =
        packColor(
            (color.red * 255f).toInt().coerceIn(0, 255),
            (color.green * 255f).toInt().coerceIn(0, 255),
            (color.blue * 255f).toInt().coerceIn(0, 255),
            (color.alpha * 255f).toInt().coerceIn(0, 255),
        )

    private companion object {
        private const val ImportCanvasClickDragThreshold = 6f
        private val SkyboxResolutionOptions = intArrayOf(64, 128, 256, 512, 1024, 2048, 4096)
        private val IrradianceResolutionOptions = intArrayOf(64, 128, 256, 512, 1024)
        private val RadianceResolutionOptions = intArrayOf(64, 128, 256, 512, 1024, 2048)
        private val SampleCountOptions = intArrayOf(64, 128, 256, 512, 1024, 2048, 4096)

        private val SkyboxAtlasFileDialogFilters =
            listOf(
                FileDialogFilter("Skybox Atlas Textures", listOf("png", "jpg", "jpeg", "bmp", "webp")),
            )

        private val BrdfLutFileDialogFilters =
            listOf(
                FileDialogFilter("BRDF LUT Textures", listOf("png")),
            )
    }
}
