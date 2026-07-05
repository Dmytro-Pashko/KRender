package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOutputFormat
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import com.pashkd.krender.engine.assets.environment.EnvironmentRoughnessDistribution
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
    private val hdrSkyboxSourceBuffer = ByteArray(512)
    private val hdrSkyboxOutputBuffer = ByteArray(256)
    private val irradianceSourceBuffer = ByteArray(512)
    private val irradianceOutputBuffer = ByteArray(256)
    private val radianceSourceBuffer = ByteArray(512)
    private val radianceOutputBuffer = ByteArray(256)
    private val brdfOutputBuffer = ByteArray(256)
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
        if (ImGui.button("Generate Skybox From HDR/EXR##env_tools_generate_hdr_skybox")) {
            hdrGenerationController.open(HdrEnvironmentGenerationDialog.Skybox)
        }
        tooltipOnHover("Project an equirectangular HDR/EXR source into six cubemap face PNG files.")
        if (ImGui.button("Generate Irradiance##env_tools_generate_irradiance")) {
            hdrGenerationController.open(HdrEnvironmentGenerationDialog.Irradiance)
        }
        if (ImGui.button("Generate Radiance##env_tools_generate_radiance")) {
            hdrGenerationController.open(HdrEnvironmentGenerationDialog.Radiance)
        }
        if (ImGui.button("Generate BRDF LUT##env_tools_generate_brdf_lut")) {
            hdrGenerationController.open(HdrEnvironmentGenerationDialog.BrdfLut)
        }
        if (ImGui.button("Generate All IBL##env_tools_generate_all_ibl")) {
            hdrGenerationController.open(HdrEnvironmentGenerationDialog.AllIbl)
        }
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
            HdrEnvironmentGenerationDialog.Skybox -> drawHdrSkyboxDialog()
            HdrEnvironmentGenerationDialog.Irradiance -> drawIrradianceDialog()
            HdrEnvironmentGenerationDialog.Radiance -> drawRadianceDialog()
            HdrEnvironmentGenerationDialog.BrdfLut -> drawBrdfLutDialog()
            HdrEnvironmentGenerationDialog.AllIbl -> drawAllIblDialog()
            null -> Unit
        }
    }

    private fun drawHdrSkyboxDialog() {
        val dialog = HdrEnvironmentGenerationDialog.Skybox
        val request = state.hdrGenerationState.skyboxRequest
        val availability = hdrGenerationController.availability(dialog)
        ImGui.openPopup("${dialog.title}##env_tools_hdr_skybox_dialog")
        ImGui.setNextWindowSize(Vec2(720f, 460f), Cond.Appearing)
        if (!ImGui.beginPopupModal("${dialog.title}##env_tools_hdr_skybox_dialog")) return

        drawHdrSourceInput(
            label = "Source HDR/EXR file",
            buffer = hdrSkyboxSourceBuffer,
            onChanged = { hdrGenerationController.setSkyboxSourcePath(it) },
        )
        drawPathInput(
            label = "Output directory",
            buffer = hdrSkyboxOutputBuffer,
            onChanged = { request.outputDirectory = it },
        )
        drawResolvedOutputLabel("Resolved output", request.outputDirectory)
        slider("Resolution##env_tools_hdr_skybox_resolution", request::resolution, 64, 4096, "%d", SliderFlag.AlwaysClamp)
        drawOutputFormatCombo("Output format##env_tools_hdr_skybox_format", request.format) { request.format = it }
        slider("Exposure##env_tools_hdr_skybox_exposure", request::exposure, 0.1f, 8f, "%.2f", SliderFlag.AlwaysClamp)
        drawToneMappingCombo("Tone mapping##env_tools_hdr_skybox_tone_mapping", request.toneMapping) { request.toneMapping = it }
        drawOverwritePolicyCombo("Overwrite policy##env_tools_hdr_skybox_overwrite", request.overwritePolicy) { request.overwritePolicy = it }
        drawRememberSourceCheckbox { request.rememberSource = it }
        drawGenerationAvailability(availability)
        drawGenerateCancelRow(
            canGenerate = availability.available && request.sourceHdrPath.isNotBlank(),
            generateLabel = "Generate##env_tools_hdr_skybox_generate",
            missingInputMessage = "Select a source HDR/EXR file.",
            availability = availability,
            onGenerate = hdrGenerationController::generateSkybox,
        )
    }

    private fun drawIrradianceDialog() {
        val dialog = HdrEnvironmentGenerationDialog.Irradiance
        val request = state.hdrGenerationState.irradianceRequest
        val availability = hdrGenerationController.availability(dialog)
        ImGui.openPopup("${dialog.title}##env_tools_hdr_irradiance_dialog")
        ImGui.setNextWindowSize(Vec2(720f, 420f), Cond.Appearing)
        if (!ImGui.beginPopupModal("${dialog.title}##env_tools_hdr_irradiance_dialog")) return

        drawHdrSourceInput(
            label = "Source HDR/EXR file",
            buffer = irradianceSourceBuffer,
            onChanged = { hdrGenerationController.setIrradianceSourcePath(it) },
        )
        drawPathInput(
            label = "Output directory",
            buffer = irradianceOutputBuffer,
            onChanged = { request.outputDirectory = it },
        )
        drawResolvedOutputLabel("Resolved output", request.outputDirectory)
        drawResolutionCombo(
            label = "Resolution##env_tools_hdr_irradiance_resolution",
            selected = request.resolution,
            options = IrradianceResolutionOptions,
        ) { request.resolution = it }
        tooltipOnHover("Choose a preset irradiance face resolution from 64 up to 1024.")
        drawIntOptionCombo(
            label = "Sample count##env_tools_hdr_irradiance_samples",
            selected = request.sampleCount,
            options = SampleCountOptions,
        ) { request.sampleCount = it }
        tooltipOnHover("Choose a preset sample count. Free-form values are intentionally disabled.")
        drawOutputFormatCombo("Output format##env_tools_hdr_irradiance_format", request.format) { request.format = it }
        drawOverwritePolicyCombo("Overwrite policy##env_tools_hdr_irradiance_overwrite", request.overwritePolicy) { request.overwritePolicy = it }
        drawRememberSourceCheckbox { request.rememberSource = it }
        drawGenerationAvailability(availability)
        drawGenerateCancelRow(
            canGenerate = availability.available && request.sourceHdrPath.isNotBlank(),
            generateLabel = "Generate##env_tools_hdr_irradiance_generate",
            missingInputMessage = "Select a source HDR/EXR file.",
            availability = availability,
            onGenerate = hdrGenerationController::generateIrradiance,
        )
    }

    private fun drawRadianceDialog() {
        val dialog = HdrEnvironmentGenerationDialog.Radiance
        val request = state.hdrGenerationState.radianceRequest
        val availability = hdrGenerationController.availability(dialog)
        ImGui.openPopup("${dialog.title}##env_tools_hdr_radiance_dialog")
        ImGui.setNextWindowSize(Vec2(720f, 470f), Cond.Appearing)
        if (!ImGui.beginPopupModal("${dialog.title}##env_tools_hdr_radiance_dialog")) return

        drawHdrSourceInput(
            label = "Source HDR/EXR file",
            buffer = radianceSourceBuffer,
            onChanged = { hdrGenerationController.setRadianceSourcePath(it) },
        )
        drawPathInput(
            label = "Output directory",
            buffer = radianceOutputBuffer,
            onChanged = { request.outputDirectory = it },
        )
        drawResolvedOutputLabel("Resolved output", request.outputDirectory)
        drawResolutionCombo(
            label = "Base resolution##env_tools_hdr_radiance_base_resolution",
            selected = request.baseResolution,
            options = RadianceResolutionOptions,
        ) { request.baseResolution = it }
        tooltipOnHover("Choose a preset radiance base resolution from 64 up to 2048.")
        slider("Mip count##env_tools_hdr_radiance_mip_count", request::mipCount, 1, 12, "%d", SliderFlag.AlwaysClamp)
        drawIntOptionCombo(
            label = "Sample count##env_tools_hdr_radiance_samples",
            selected = request.sampleCount,
            options = SampleCountOptions,
        ) { request.sampleCount = it }
        tooltipOnHover("Choose a preset sample count. Free-form values are intentionally disabled.")
        drawRoughnessDistributionCombo(
            "Roughness distribution##env_tools_hdr_radiance_distribution",
            request.roughnessDistribution,
        ) { request.roughnessDistribution = it }
        drawOutputFormatCombo("Output format##env_tools_hdr_radiance_format", request.format) { request.format = it }
        drawOverwritePolicyCombo("Overwrite policy##env_tools_hdr_radiance_overwrite", request.overwritePolicy) { request.overwritePolicy = it }
        drawRememberSourceCheckbox { request.rememberSource = it }
        drawGenerationAvailability(availability)
        drawGenerateCancelRow(
            canGenerate = availability.available && request.sourceHdrPath.isNotBlank(),
            generateLabel = "Generate##env_tools_hdr_radiance_generate",
            missingInputMessage = "Select a source HDR/EXR file.",
            availability = availability,
            onGenerate = hdrGenerationController::generateRadiance,
        )
    }

    private fun drawBrdfLutDialog() {
        val dialog = HdrEnvironmentGenerationDialog.BrdfLut
        val request = state.hdrGenerationState.brdfLutRequest
        val availability = hdrGenerationController.availability(dialog)
        ImGui.openPopup("${dialog.title}##env_tools_hdr_brdf_dialog")
        ImGui.setNextWindowSize(Vec2(720f, 300f), Cond.Appearing)
        if (!ImGui.beginPopupModal("${dialog.title}##env_tools_hdr_brdf_dialog")) return

        drawPathInput(
            label = "Output path",
            buffer = brdfOutputBuffer,
            onChanged = { request.outputPath = it },
        )
        drawResolvedOutputLabel("Resolved output", request.outputPath)
        slider("Resolution##env_tools_hdr_brdf_resolution", request::resolution, 16, 2048, "%d", SliderFlag.AlwaysClamp)
        slider("Sample count##env_tools_hdr_brdf_samples", request::sampleCount, 16, 4096, "%d", SliderFlag.AlwaysClamp)
        drawOutputFormatCombo("Output format##env_tools_hdr_brdf_format", request.format) { request.format = it }
        drawOverwritePolicyCombo("Overwrite policy##env_tools_hdr_brdf_overwrite", request.overwritePolicy) { request.overwritePolicy = it }
        drawGenerationAvailability(availability)
        drawGenerateCancelRow(
            canGenerate = availability.available,
            generateLabel = "Generate##env_tools_hdr_brdf_generate",
            missingInputMessage = null,
            availability = availability,
            onGenerate = hdrGenerationController::generateBrdfLut,
        )
    }

    private fun drawAllIblDialog() {
        val dialog = HdrEnvironmentGenerationDialog.AllIbl
        val request = state.hdrGenerationState.allIblRequest
        val availability = hdrGenerationController.availability(dialog)
        ImGui.openPopup("${dialog.title}##env_tools_hdr_all_dialog")
        ImGui.setNextWindowSize(Vec2(760f, 620f), Cond.Appearing)
        if (!ImGui.beginPopupModal("${dialog.title}##env_tools_hdr_all_dialog")) return

        drawHdrSourceInput(
            label = "Source HDR/EXR file",
            buffer = allSourceBuffer,
            onChanged = { hdrGenerationController.setAllSourcePath(it) },
        )
        tooltipOnHover("Select the equirectangular HDR/EXR source used for all enabled IBL outputs.")
        drawPathInput(
            label = "Output root",
            buffer = allOutputRootBuffer,
            onChanged = { request.outputRoot = it },
            browseLabel = "Browse...##env_tools_hdr_all_output_root_browse",
            browseTooltip = "Pick any existing file inside the target folder; the parent directory will be used as the output root.",
            onBrowse = {
                val selected = fileDialogService.openFile(OutputRootBrowseFallbackFilters) ?: ""
                if (selected.isNotBlank()) {
                    request.outputRoot = File(selected).parentFile?.path?.replace('\\', '/') ?: request.outputRoot
                }
            },
        )
        drawResolvedOutputLabel("Resolved output", request.outputRoot)
        ImGui.textDisabled("Example: generated/my_environment")
        tooltipOnHover("The generator writes skybox/, irradiance/, radiance/, and brdf_lut.png under this root.")
        drawOverwritePolicyCombo("Overwrite policy##env_tools_hdr_all_overwrite", request.overwritePolicy) { request.overwritePolicy = it }
        tooltipOnHover("Controls whether existing generated files fail, get replaced, or are reused.")
        drawRememberSourceCheckbox { request.rememberSource = it }
        tooltipOnHover("Store the selected HDR/EXR as optional authoring metadata without making it a runtime dependency.")
        ImGui.separator()
        val skyboxEnabled = booleanArrayOf(request.skyboxEnabled)
        if (ImGui.checkbox("Skybox##env_tools_hdr_all_skybox_enabled", skyboxEnabled)) request.skyboxEnabled = skyboxEnabled[0]
        tooltipOnHover("Enable skybox face generation under <output root>/skybox.")
        if (request.skyboxEnabled) {
            drawResolutionCombo(
                label = "Resolution##env_tools_hdr_all_skybox_resolution",
                selected = request.skyboxResolution,
                options = SkyboxResolutionOptions,
            ) { request.skyboxResolution = it }
            tooltipOnHover("Output resolution per cubemap face. Skybox supports 64 up to 4096.")
            slider("Exposure##env_tools_hdr_all_skybox_exposure", request::skyboxExposure, 0.1f, 8f, "%.2f", SliderFlag.AlwaysClamp)
            tooltipOnHover("Scales sampled HDR brightness before tone mapping.")
            drawToneMappingCombo("Tone mapping##env_tools_hdr_all_skybox_tone_mapping", request.skyboxToneMapping) { request.skyboxToneMapping = it }
            tooltipOnHover("Defines how HDR values are compressed into PNG output.")
        }
        val irradianceEnabled = booleanArrayOf(request.irradianceEnabled)
        if (ImGui.checkbox("Irradiance##env_tools_hdr_all_irradiance_enabled", irradianceEnabled)) request.irradianceEnabled = irradianceEnabled[0]
        tooltipOnHover("Enable diffuse IBL cubemap generation under <output root>/irradiance.")
        if (request.irradianceEnabled) {
            drawResolutionCombo(
                label = "Resolution##env_tools_hdr_all_irradiance_resolution",
                selected = request.irradianceResolution,
                options = IrradianceResolutionOptions,
            ) { request.irradianceResolution = it }
            tooltipOnHover("Output resolution per cubemap face. Irradiance supports 64 up to 1024.")
            drawIntOptionCombo(
                label = "Sample count##env_tools_hdr_all_irradiance_samples",
                selected = request.irradianceSampleCount,
                options = SampleCountOptions,
            ) { request.irradianceSampleCount = it }
            tooltipOnHover("Choose a preset sample count for irradiance generation.")
        }
        val radianceEnabled = booleanArrayOf(request.radianceEnabled)
        if (ImGui.checkbox("Radiance##env_tools_hdr_all_radiance_enabled", radianceEnabled)) request.radianceEnabled = radianceEnabled[0]
        tooltipOnHover("Enable specular IBL mip-chain generation under <output root>/radiance.")
        if (request.radianceEnabled) {
            drawResolutionCombo(
                label = "Resolution##env_tools_hdr_all_radiance_base_resolution",
                selected = request.radianceBaseResolution,
                options = RadianceResolutionOptions,
            ) { request.radianceBaseResolution = it }
            tooltipOnHover("Base mip resolution per cubemap face. Lower mips are derived from this size.")
            slider("Mip count##env_tools_hdr_all_radiance_mip_count", request::radianceMipCount, 1, 12, "%d", SliderFlag.AlwaysClamp)
            tooltipOnHover("Number of radiance roughness levels to describe in the output mip chain.")
            drawIntOptionCombo(
                label = "Sample count##env_tools_hdr_all_radiance_samples",
                selected = request.radianceSampleCount,
                options = SampleCountOptions,
            ) { request.radianceSampleCount = it }
            tooltipOnHover("Choose a preset sample count for radiance generation.")
        }
        val brdfEnabled = booleanArrayOf(request.brdfLutEnabled)
        if (ImGui.checkbox("BRDF LUT##env_tools_hdr_all_brdf_enabled", brdfEnabled)) request.brdfLutEnabled = brdfEnabled[0]
        tooltipOnHover("Enable BRDF integration LUT generation at <output root>/brdf_lut.png.")
        if (request.brdfLutEnabled) {
            slider("Resolution##env_tools_hdr_all_brdf_resolution", request::brdfLutResolution, 16, 2048, "%d", SliderFlag.AlwaysClamp)
            tooltipOnHover("Output resolution for the BRDF LUT texture.")
            slider("Sample count##env_tools_hdr_all_brdf_samples", request::brdfLutSampleCount, 16, 4096, "%d", SliderFlag.AlwaysClamp)
            tooltipOnHover("Reserved for future BRDF integration quality control.")
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
        browseLabel: String? = null,
        browseTooltip: String? = null,
        onBrowse: (() -> Unit)? = null,
    ) {
        ImGui.text(label)
        ImGui.sameLine()
        ImGui.pushItemWidth(440f)
        if (ImGui.inputText("##${label}_input", buffer)) {
            onChanged(readBuffer(buffer))
        }
        ImGui.popItemWidth()
        if (browseLabel != null && onBrowse != null) {
            ImGui.sameLine()
            if (ImGui.button(browseLabel)) {
                onBrowse()
            }
            browseTooltip?.let(::tooltipOnHover)
        }
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

    private fun drawOutputFormatCombo(
        label: String,
        selected: EnvironmentIblOutputFormat,
        onChanged: (EnvironmentIblOutputFormat) -> Unit,
    ) {
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(label, selected.name)) {
            EnvironmentIblOutputFormat.entries.forEach { option ->
                if (ImGui.selectable(option.name, option == selected)) {
                    onChanged(option)
                }
            }
            ImGui.endCombo()
        }
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

    private fun drawRoughnessDistributionCombo(
        label: String,
        selected: EnvironmentRoughnessDistribution,
        onChanged: (EnvironmentRoughnessDistribution) -> Unit,
    ) {
        ImGui.setNextItemWidth(200f)
        if (ImGui.beginCombo(label, selected.name)) {
            EnvironmentRoughnessDistribution.entries.forEach { option ->
                if (ImGui.selectable(option.name, option == selected)) {
                    onChanged(option)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawRememberSourceCheckbox(onChanged: (Boolean) -> Unit) {
        val value = booleanArrayOf(false)
        val currentDialog = state.hdrGenerationState.openDialog
        value[0] =
            when (currentDialog) {
                HdrEnvironmentGenerationDialog.Skybox -> state.hdrGenerationState.skyboxRequest.rememberSource
                HdrEnvironmentGenerationDialog.Irradiance -> state.hdrGenerationState.irradianceRequest.rememberSource
                HdrEnvironmentGenerationDialog.Radiance -> state.hdrGenerationState.radianceRequest.rememberSource
                HdrEnvironmentGenerationDialog.AllIbl -> state.hdrGenerationState.allIblRequest.rememberSource
                HdrEnvironmentGenerationDialog.BrdfLut,
                null,
                -> false
            }
        if (ImGui.checkbox("Remember HDR/EXR source in Environment metadata", value)) {
            onChanged(value[0])
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
        syncBuffer(hdrSkyboxSourceBuffer, state.hdrGenerationState.skyboxRequest.sourceHdrPath)
        syncBuffer(hdrSkyboxOutputBuffer, state.hdrGenerationState.skyboxRequest.outputDirectory)
        syncBuffer(irradianceSourceBuffer, state.hdrGenerationState.irradianceRequest.sourceHdrPath)
        syncBuffer(irradianceOutputBuffer, state.hdrGenerationState.irradianceRequest.outputDirectory)
        syncBuffer(radianceSourceBuffer, state.hdrGenerationState.radianceRequest.sourceHdrPath)
        syncBuffer(radianceOutputBuffer, state.hdrGenerationState.radianceRequest.outputDirectory)
        syncBuffer(brdfOutputBuffer, state.hdrGenerationState.brdfLutRequest.outputPath)
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

        private val OutputRootBrowseFallbackFilters =
            listOf(
                FileDialogFilter(
                    "Project Files",
                    listOf("hdr", "exr", "png", "jpg", "jpeg", "webp", "bmp", "json", "txt", "ktx", "glb"),
                ),
            )
    }
}
