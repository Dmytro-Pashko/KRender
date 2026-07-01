package com.pashkd.krender.engine.tools.modelviewer

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.ui.editor.*
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorEdit4
import imgui.api.slider
import imgui.dsl
import java.nio.charset.StandardCharsets
import glm_.vec2.Vec2 as ImVec2

private val MODEL_VIEWER_MATERIAL_CHANNEL_MODES =
    listOf(
        MaterialDebugMode.Combined,
        MaterialDebugMode.BaseColor,
        MaterialDebugMode.Normal,
        MaterialDebugMode.Metallic,
        MaterialDebugMode.Roughness,
        MaterialDebugMode.Occlusion,
        MaterialDebugMode.Emission,
        MaterialDebugMode.Alpha,
    )

/**
 * Top-level ModelViewer action/status panel.
 */
class ModelViewerToolbarPanel(
    private val state: ModelViewerState,
    private val operations: ModelViewerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginModelViewerPanel(ModelViewerPanelIds.Toolbar, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        with(dsl) {
            button("Save Layout##model_viewer_save_layout") {
                operations.saveUiLayout()
            }
        }
        tooltipOnHover("Save the current Model Viewer panel positions and sizes.")
        ImGui.sameLine()
        with(dsl) {
            button("Reset Layout##model_viewer_reset_layout") {
                operations.restoreUiLayout()
            }
        }
        tooltipOnHover("Reset all Model Viewer panels to the default layout.")
        ImGui.sameLine()
        with(dsl) {
            button("Reload##model_viewer_reload") {
                operations.requestReload()
            }
        }
        tooltipOnHover("Unload and reload the current model asset from disk.")
        ImGui.sameLine()
        with(dsl) {
            button("Exit##model_viewer_exit") {
                state.exitRequested = true
            }
        }
        tooltipOnHover("Close the Model Viewer window.")

        ImGui.separator()
        textLine("Model: ${state.modelPath}")
        textLine("Status: ${state.statusMessage}")
        state.errorMessage?.let { error -> textLine("Error: $error") }
        textLine("Asset: ${state.loadingStatus}")
        textLine("Camera speed: ${"%.2f".format(state.camera.speed)}")
        ImGui.end()
    }
}

/**
 * Viewport status and display-options panel.
 */
class ModelViewerViewportPanel(
    private val state: ModelViewerState,
    private val operations: ModelViewerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val gltfPbrRendererOptionsPanel = GltfPbrRendererOptionsPanel(state)
    private val legacyRendererOptionsPanel = LegacyRendererOptionsPanel(state, operations)

    override fun draw() {
        val expanded = beginModelViewerPanel(ModelViewerPanelIds.Viewport, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            state.viewportFocused = false
            ImGui.end()
            return
        }

        ImGui.beginChild("model_viewer_viewport_body", ImVec2(0f, 0f), true)
        val viewportPosition = ImGui.windowPos
        val viewportSize = ImGui.windowSize
        state.viewportOrigin = Vec2(viewportPosition.x, viewportPosition.y)
        state.viewportSize = Vec2(viewportSize.x.coerceAtLeast(0f), viewportSize.y.coerceAtLeast(0f))
        state.viewportFocused = ImGui.isWindowHovered() || ImGui.isWindowFocused()

        ImGui.text("Camera Control")
        textLine("Camera: ${formatPosition(state.camera.position)}")
        textLine("Speed: ${"%.2f".format(state.camera.speed)}${if (state.camera.navigating) " (navigating)" else ""}")
        ImGui.text("RMB look, WASD move, Q/E down/up, wheel speed, Shift faster.")
        with(dsl) {
            button("Reset Camera##model_viewer_reset_camera") {
                operations.resetCamera()
            }
        }
        tooltipOnHover("Reset camera position and rotation to the default preview view.")
        ImGui.sameLine()
        with(dsl) {
            button("Frame Model##model_viewer_frame_model") {
                operations.frameModel()
            }
        }
        tooltipOnHover("Move the camera so the entire model fits into view.")
        slider(
            "Look Sensitivity##model_viewer_camera_sensitivity",
            state.camera::sensitivity,
            MinCameraSensitivity,
            MaxCameraSensitivity,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Mouse-look sensitivity in degrees per pixel. Changes update live.")

        ImGui.separator()
        ImGui.text("Display Mode / Display Options")
        drawDisplayModeCombo()
        ImGui.checkbox("Grid##model_viewer_show_grid", state::showGrid)
        tooltipOnHover("Show the ground reference grid in the viewport.")
        ImGui.sameLine()
        ImGui.checkbox("Axes##model_viewer_show_axes", state::showAxes)
        tooltipOnHover("Show world-space X, Y, and Z axes.")
        ImGui.sameLine()
        ImGui.checkbox("Bounds##model_viewer_show_bounding_box", state::showBoundingBox)
        tooltipOnHover("Show the model bounding box.")
        slider(
            "Grid Size##model_viewer_grid_cell_size",
            state::gridCellSize,
            MinGridCellSize,
            MaxGridCellSize,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Distance between grid lines in world units. Changes update live.")
        slider(
            "Grid Extent##model_viewer_grid_extent",
            state::gridHalfExtentCells,
            MinGridHalfExtentCells,
            MaxGridHalfExtentCells,
            "%d",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("How many grid cells extend from the origin in each direction.")

        ImGui.separator()
        ImGui.text("Renderer Selector")
        drawRendererSelector()

        ImGui.separator()
        ImGui.text("Renderer Options")
        drawRendererOptions()
        ImGui.endChild()
        ImGui.end()
    }

    private fun drawDisplayModeCombo() {
        val expanded = ImGui.beginCombo("Display Mode##model_viewer_display_mode", state.displayMode.name)
        tooltipOnHover("Choose shaded, shaded-with-wireframe, or wireframe display for the model.")
        if (!expanded) return
        ModelViewerDisplayMode.entries.forEach { mode ->
            if (ImGui.selectable("${mode.name}##model_viewer_display_${mode.name}", state.displayMode == mode)) {
                state.displayMode = mode
            }
            tooltipOnHover("Use the ${mode.name} shared viewport display mode.")
        }
        ImGui.endCombo()
    }

    private fun drawRendererSelector() {
        val expanded = ImGui.beginCombo("Renderer##model_viewer_renderer_mode", rendererModeLabel(state.rendererMode))
        tooltipOnHover("Choose which rendering backend is used to draw the model in the viewport.")
        if (expanded) {
            ModelViewerRendererMode.entries.forEach { mode ->
                if (ImGui.selectable(
                        "${rendererModeLabel(mode)}##model_viewer_renderer_$mode",
                        state.rendererMode == mode,
                    )
                ) {
                    state.rendererMode = mode
                }
                tooltipOnHover(
                    if (mode == ModelViewerRendererMode.Pbr) {
                        "Use the glTF / PBR renderer with HDR environment lighting."
                    } else {
                        "Use the LibGDX / Legacy DefaultShader renderer."
                    },
                )
            }
            ImGui.endCombo()
        }
    }

    private fun drawRendererOptions() {
        if (state.rendererMode == ModelViewerRendererMode.LibGdx) {
            legacyRendererOptionsPanel.draw()
            return
        }
        gltfPbrRendererOptionsPanel.draw()
    }

    companion object {
        private const val MinGridHalfExtentCells = 1
        private const val MaxGridHalfExtentCells = 256
        private const val MinGridCellSize = 0.05f
        private const val MaxGridCellSize = 16f
        private const val MinCameraSensitivity = 0.05f
        private const val MaxCameraSensitivity = 1f
    }
}

internal class GltfPbrRendererOptionsPanel(
    private val state: ModelViewerState,
) {
    private val environmentPresetBuffer =
        ByteArray(TEXT_BUFFER_SIZE).also { buffer ->
            writeTextBuffer(buffer, state.pbrEnvironmentPreset)
        }

    fun draw() {
        ImGui.text("Environment / IBL")
        if (ImGui.inputText("Environment Preset##model_viewer_pbr_environment_preset", environmentPresetBuffer)) {
            state.pbrEnvironmentPreset = readTextBuffer(environmentPresetBuffer).ifBlank { DEFAULT_PBR_ENVIRONMENT_PRESET }
        }
        tooltipOnHover(
            "Select the HDR / IBL environment preset name or manifest path. " +
                "The renderer resolves changes live when the value is edited.",
        )
        ImGui.checkbox("Show Skybox##model_viewer_pbr_show_skybox", state::pbrShowSkybox)
        tooltipOnHover("Show the selected HDR environment as the glTF / PBR skybox.")
        slider(
            "Environment Intensity##model_viewer_pbr_environment_intensity",
            state::pbrEnvironmentIntensity,
            0f,
            4f,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Scale the contribution of the IBL environment lighting. Available only in glTF / PBR renderer.")
        slider("Exposure##model_viewer_pbr_exposure", state::pbrExposure, 0.1f, 4f, "%.2f", SliderFlag.AlwaysClamp)
        tooltipOnHover("Adjust overall scene brightness after environment lighting is applied.")
        slider(
            "Environment Rotation##model_viewer_pbr_environment_rotation",
            state::pbrEnvironmentRotationDegrees,
            -180f,
            180f,
            "%.0f deg",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Rotate the HDR environment around the vertical axis in degrees.")

        ImGui.text("Tone / Color Pipeline")
        drawToneMappingCombo()
        ImGui.checkbox("Gamma Correction##model_viewer_pbr_gamma", state::pbrGammaCorrection)
        tooltipOnHover(
            "Apply output gamma correction for more accurate final colors. " +
                "Changing this option recreates renderer shader resources.",
        )
        ImGui.checkbox("sRGB Textures##model_viewer_pbr_srgb", state::pbrSrgbTextures)
        tooltipOnHover(
            "Interpret color textures as sRGB instead of linear textures. " +
                "Changing this option recreates renderer shader resources.",
        )
        ImGui.textDisabled("Tone mapping is retained for a future post-process pass.")

        ImGui.text("Direct Lighting")
        ImGui.checkbox("Directional Light##model_viewer_pbr_directional_enabled", state::pbrDirectionalLightEnabled)
        tooltipOnHover("Enable the main directional light. Available only in glTF / PBR renderer.")
        slider(
            "Directional Intensity##model_viewer_pbr_directional_intensity",
            state::pbrDirectionalLightIntensity,
            0f,
            8f,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Control the brightness of the glTF / PBR directional light.")
        drawColorControl(
            "Directional Color##model_viewer_pbr_directional_color",
            state.pbrDirectionalLightColor,
            "Set the glTF / PBR directional light color. Changes update live.",
        )
        slider(
            "Directional Light Yaw##model_viewer_pbr_light_yaw",
            state::pbrDirectionalLightYawDegrees,
            -180f,
            180f,
            "%.0f deg",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Rotate the directional light around the vertical axis in degrees.")
        slider(
            "Directional Light Pitch##model_viewer_pbr_light_pitch",
            state::pbrDirectionalLightPitchDegrees,
            -89f,
            89f,
            "%.0f deg",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Tilt the directional light up or down in degrees.")

        ImGui.text("Material Debug / Channel View")
        drawMaterialDebugCombo()
        state.pbrWarning?.let { warning -> textLine("Warning: $warning") }
    }

    private fun drawToneMappingCombo() {
        val expanded =
            ImGui.beginCombo("Tone Mapping##model_viewer_pbr_tone_mapping", toneMappingLabel(state.pbrToneMapping))
        tooltipOnHover(
            "Choose how HDR lighting should map to the display. " +
                "Stored now; the current renderer has no live tone-mapping post-process.",
        )
        if (!expanded) return
        PbrToneMapping.entries.forEach { mode ->
            if (ImGui.selectable(
                    "${toneMappingLabel(mode)}##model_viewer_pbr_tone_mapping_$mode",
                    state.pbrToneMapping == mode,
                )
            ) {
                state.pbrToneMapping = mode
            }
            tooltipOnHover("${toneMappingLabel(mode)} tone-mapping preference; not applied live yet.")
        }
        ImGui.endCombo()
    }

    private fun drawMaterialDebugCombo() {
        val expanded =
            ImGui.beginCombo("Material Channel##model_viewer_pbr_material_channel", debugModeLabel(state.debugMode))
        tooltipOnHover("Choose the material result or individual channel visualized in the viewport.")
        if (!expanded) return
        MODEL_VIEWER_MATERIAL_CHANNEL_MODES.forEach { mode ->
            if (ImGui.selectable(
                    "${debugModeLabel(mode)}##model_viewer_pbr_material_channel_$mode",
                    state.debugMode == mode,
                )
            ) {
                state.debugMode = mode
                state.uvCheckerEnabled = false
                state.lastNonUvCheckerDebugMode = mode
            }
            tooltipOnHover(debugModeTooltip(mode))
        }
        ImGui.endCombo()
    }

    companion object {
        private const val TEXT_BUFFER_SIZE = 256
    }
}

internal class LegacyRendererOptionsPanel(
    private val state: ModelViewerState,
    private val operations: ModelViewerOperations,
) {
    fun draw() {
        ImGui.text("Lighting")
        val ambientIntensity = FloatHolder(state.ambientLightIntensity)
        if (
            slider(
                "Ambient Intensity##model_viewer_legacy_ambient_intensity",
                ambientIntensity::value,
                0f,
                3f,
                "%.2f",
                SliderFlag.AlwaysClamp,
            )
        ) {
            operations.setAmbientLightIntensity(ambientIntensity.value)
        }
        tooltipOnHover("Set the strength of ambient light used by the Legacy LibGDX renderer.")
        drawColorControl(
            "Ambient Color##model_viewer_legacy_ambient_color",
            state.legacyAmbientLightColor,
            "Set the ambient light color used by the LibGDX / Legacy renderer.",
        )
        with(dsl) {
            button("Reset Ambient##model_viewer_reset_ambient") {
                operations.resetAmbientLight()
            }
        }
        tooltipOnHover("Reset Legacy ambient intensity to its default value.")
        ImGui.checkbox(
            "Directional Light##model_viewer_legacy_directional_enabled",
            state::legacyDirectionalLightEnabled,
        )
        tooltipOnHover("Enable the main directional light. Available only in LibGDX / Legacy renderer.")
        slider(
            "Directional Intensity##model_viewer_legacy_directional_intensity",
            state::legacyDirectionalLightIntensity,
            0f,
            8f,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Control the brightness of the main Legacy directional light.")
        drawColorControl(
            "Directional Color##model_viewer_legacy_directional_color",
            state.legacyDirectionalLightColor,
            "Set the main directional light color for LibGDX / Legacy rendering.",
        )
        slider(
            "Directional Light Yaw##model_viewer_legacy_light_yaw",
            state::legacyDirectionalLightYawDegrees,
            -180f,
            180f,
            "%.0f deg",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Rotate the Legacy directional light around the vertical axis in degrees.")
        slider(
            "Directional Light Pitch##model_viewer_legacy_light_pitch",
            state::legacyDirectionalLightPitchDegrees,
            -89f,
            89f,
            "%.0f deg",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Tilt the Legacy directional light up or down in degrees.")
    }
}

/**
 * Shows loaded-model metadata summary.
 */
class ModelViewerInfoPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginModelViewerPanel(ModelViewerPanelIds.ModelInfo, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        val info = state.modelInfo
        if (info == null) {
            ImGui.text("Metadata is available after the model finishes loading.")
            ImGui.end()
            return
        }

        textLine("Format: ${info.format}")
        textLine("Nodes: ${info.nodeCount}")
        textLine("Meshes: ${info.meshCount}")
        textLine("Mesh parts: ${info.meshPartCount}")
        textLine("Materials: ${info.materialCount}")
        textLine("Vertices: ${info.vertexCount}")
        textLine("Triangles: ${info.triangleCount}")
        textLine("Vertex channels: ${formatList(info.vertexChannels)}")
        textLine("UV channels: ${formatList(info.uvChannels)}")
        drawInfoList("Texture channels", info.textureChannels.map(::textureChannelLabel))
        textLine("Textures: ${info.textureCount} unique / ${info.textureSlotCount} slots")
        textLine("Rig: skeleton=${if (info.hasSkeleton) "yes" else "no"}, bones=${info.boneCount}, animations=${info.animationCount}")
        textLine("Bone weight channels: ${info.boneWeightChannelCount}")
        drawBounds(info)
        drawWarnings(info)
        ImGui.end()
    }

    private fun drawBounds(info: ModelAssetInfo) {
        ImGui.separator()
        ImGui.text("Bounds")
        val min = info.boundsMin
        val max = info.boundsMax
        if (min != null && max != null) {
            val center =
                Vec3(
                    (min.x + max.x) * 0.5f,
                    (min.y + max.y) * 0.5f,
                    (min.z + max.z) * 0.5f,
                )
            val pivotOffset = Vec3(-center.x, -center.y, -center.z)
            textLine("Min: ${formatPosition(min)}")
            textLine("Max: ${formatPosition(max)}")
            textLine(
                "Size: ${
                    info.size?.let(::formatSize) ?: formatSize(
                        Vec3(
                            max.x - min.x,
                            max.y - min.y,
                            max.z - min.z,
                        ),
                    )
                }",
            )
            textLine("Center: ${formatPosition(center)}")
            textLine("Pivot offset from center: ${formatPosition(pivotOffset)}")
        } else {
            textLine("Size: ${info.size?.let(::formatSize) ?: "unknown"}")
        }
    }

    private fun drawWarnings(info: ModelAssetInfo) {
        val textureSlots = info.materials.flatMap { material -> material.textureSlots }
        val size = info.size
        val warnings =
            buildList {
                if (info.uvChannels.isEmpty()) add("No UV channels.")
                if (info.materialCount <= 0) add("No materials.")
                if (textureSlots.isEmpty()) add("No texture slots.")
                if (textureSlots.isNotEmpty() && info.uvChannels.isEmpty()) add("Texture slots exist but no UV channels were found.")
                if (
                    textureSlots.any { slot -> slot.channel == "normal" } &&
                    info.vertexChannels.none { channel -> channel.equals("Tangent", ignoreCase = true) }
                ) {
                    add("Normal texture exists but no tangent vertex channel was found.")
                }
                if (info.triangleCount >= HighTriangleWarningThreshold) add("High triangle count.")
                if (info.boundsMin == null || info.boundsMax == null || size == null) add("Bounds are missing.")
                if (size != null && size.isNearZero()) add("Model has zero or near-zero size.")
                if (info.meshParts.any { part -> part.materialId == null && part.materialIndex == null }) {
                    add("One or more mesh parts have no material assignment.")
                }
            }
        if (warnings.isEmpty()) return

        ImGui.separator()
        ImGui.text("Warnings")
        warnings.forEach { warning -> ImGui.bulletText(warning) }
    }

    companion object {
        private const val HighTriangleWarningThreshold = 250_000
        private const val NearZeroSize = 0.0001f
    }

    private fun Vec3.isNearZero(): Boolean = x <= NearZeroSize || y <= NearZeroSize || z <= NearZeroSize
}

/**
 * Shows detailed mesh-part metadata.
 */
class ModelViewerMeshPartsPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginModelViewerPanel(ModelViewerPanelIds.MeshParts, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        val info = state.modelInfo
        val meshParts = info?.meshParts.orEmpty()
        if (meshParts.isEmpty()) {
            ImGui.text("No mesh-part metadata available.")
            ImGui.end()
            return
        }

        ImGui.checkbox("Isolate selected mesh part##model_viewer_isolate_mesh_part", state::isolateSelectedMeshPart)
        if (state.isolateSelectedMeshPart && state.selectedMeshPartIndex == null) {
            ImGui.text("Select a mesh part to isolate.")
        }
        ImGui.checkbox(
            "Filter list by selected material##model_viewer_filter_mesh_parts_material",
            state::filterMeshPartsBySelectedMaterial,
        )
        if (state.filterMeshPartsBySelectedMaterial && state.selectedMaterialIndex == null) {
            ImGui.text("Select a material to filter mesh parts.")
        }

        val visibleMeshParts =
            if (state.filterMeshPartsBySelectedMaterial && state.selectedMaterialIndex != null && info != null) {
                val selectedMaterial = info.materials.getOrNull(state.selectedMaterialIndex ?: -1)
                if (selectedMaterial != null) {
                    meshParts.filter { part -> meshPartUsesMaterial(part, selectedMaterial) }
                } else {
                    meshParts
                }
            } else {
                meshParts
            }

        visibleMeshParts.forEach { part ->
            val label = "#${part.index} ${part.partId ?: part.nodeName ?: "Mesh Part"}"
            if (ImGui.selectable(
                    "$label##model_viewer_mesh_part_${part.index}",
                    state.selectedMeshPartIndex == part.index,
                )
            ) {
                state.selectedMeshPartIndex = part.index
                if (info != null) {
                    matchingMaterialIndexForPart(part, info.materials)?.let { materialIndex ->
                        state.selectedMaterialIndex = materialIndex
                    }
                }
            }
        }
        ImGui.separator()
        meshParts.firstOrNull { part -> part.index == state.selectedMeshPartIndex }?.let(::drawMeshPartDetails)
            ?: ImGui.text("Select a mesh part.")
        ImGui.end()
    }

    private fun drawMeshPartDetails(part: ModelMeshPartInfo) {
        ImGui.text("Selected mesh part")
        textLine("Index: ${part.index}")
        textLine("Node: ${part.nodeName ?: "unknown"}")
        textLine("Part: ${part.partId ?: "unknown"}")
        textLine("Material: ${part.materialId ?: "none"}")
        textLine("Material index: ${part.materialIndex?.toString() ?: "unknown"}")
        textLine("Primitive: ${part.primitiveType ?: "unknown"}")
        textLine("Vertices: ${part.vertexCount?.toString() ?: "unknown"}")
        textLine("Triangles: ${part.triangleCount?.toString() ?: "unknown"}")
    }
}

/**
 * Shows detailed material metadata.
 */
class ModelViewerMaterialsPanel(
    private val state: ModelViewerState,
    private val assets: AssetService,
    private val ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginModelViewerPanel(ModelViewerPanelIds.Materials, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        val info = state.modelInfo
        val materials = info?.materials.orEmpty()
        if (materials.isEmpty()) {
            ImGui.text("No material metadata available.")
            ImGui.end()
            return
        }

        materials.forEach { material ->
            val label = "#${material.index} ${material.id ?: "Material"}"
            if (ImGui.selectable(
                    "$label##model_viewer_material_${material.index}",
                    state.selectedMaterialIndex == material.index,
                )
            ) {
                state.selectedMaterialIndex = material.index
            }
        }
        ImGui.separator()
        materials.getOrNull(state.selectedMaterialIndex ?: -1)?.let { material ->
            drawMaterialDetails(material, info?.meshParts.orEmpty())
        }
            ?: ImGui.text("Select a material.")
        ImGui.end()
    }

    private fun drawMaterialDetails(
        material: ModelMaterialInfo,
        meshParts: List<ModelMeshPartInfo>,
    ) {
        val usedBy = meshPartsUsingMaterial(material, meshParts)
        ImGui.text("Selected material")
        textLine("Index: ${material.index}")
        textLine("Id: ${material.id ?: "unknown"}")
        textLine(
            "Base color: ${
                material.baseColor?.let { color ->
                    "%.2f, %.2f, %.2f, %.2f".format(
                        color.r,
                        color.g,
                        color.b,
                        color.a,
                    )
                } ?: "unknown"
            }",
        )
        textLine("Opacity: ${material.opacity?.let { "%.2f".format(it) } ?: "unknown"}")
        textLine("Usage count: ${usedBy.size}")
        textLine(
            "Used by mesh parts: ${
                usedBy.ifEmpty { emptyList() }.joinToString(", ") { part -> "#${part.index}" }.ifBlank { "none" }
            }",
        )
        ImGui.separator()
        ImGui.text("Texture slots")
        if (material.textureSlots.isEmpty()) {
            ImGui.text("No texture slots.")
            return
        }
        material.textureSlots.forEach { slot ->
            drawTextureSlot(slot)
        }
    }

    private fun drawTextureSlot(slot: ModelTextureSlotInfo) {
        ImGui.separator()
        ImGui.text(textureChannelLabel(slot.channel))
        drawTextureSlotPreview(assets, ui, slot)
        textLine("Texture: ${slot.texturePath ?: "unknown"}")
        textLine("UV: ${slot.uvChannel ?: "unknown"}")
        textLine("Material: #${slot.materialIndex} ${slot.materialId ?: "unknown"}")
    }
}

/**
 * Shows model-level texture channel usage without loading or rendering previews.
 */
class ModelViewerTextureChannelsPanel(
    private val state: ModelViewerState,
    private val assets: AssetService,
    private val ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded =
            beginModelViewerPanel(ModelViewerPanelIds.TextureChannels, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        val info = state.modelInfo
        val slots = info?.materials.orEmpty().flatMap { material -> material.textureSlots }
        drawDebugView(info)
        ImGui.separator()
        if (slots.isEmpty()) {
            ImGui.text("No texture channel metadata available.")
            ImGui.separator()
            ImGui.text("Warnings")
            if (info != null && info.materialCount <= 0) {
                ImGui.bulletText("No materials.")
            }
            ImGui.bulletText("No texture slots.")
            if (info != null && info.uvChannels.isEmpty()) {
                ImGui.bulletText("No UV channels.")
            }
            ImGui.end()
            return
        }

        val channels =
            slots
                .map { slot -> slot.channel }
                .distinct()
                .sortedWith(
                    compareBy<String> { channel -> textureChannelSortKey(channel) }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { channel -> channel },
                )

        if (state.selectedTextureChannel !in channels) {
            state.selectedTextureChannel = channels.firstOrNull()
        }

        ImGui.text("Texture Channels")
        channels.forEach { channel ->
            val count = slots.count { slot -> slot.channel == channel }
            val selected = state.selectedTextureChannel == channel
            if (ImGui.selectable(
                    "${textureChannelLabel(channel)}: $count slots##model_viewer_texture_channel_$channel",
                    selected,
                )
            ) {
                state.selectedTextureChannel = channel
            }
            tooltipOnHover(
                "Select ${textureChannelLabel(channel)} texture slots for inspection and preview.",
            )
        }

        ImGui.separator()
        val selectedChannel = state.selectedTextureChannel
        if (selectedChannel == null) {
            ImGui.text("Select a texture channel.")
        } else {
            drawSelectedChannel(selectedChannel, slots)
        }
        drawWarnings(info, slots)
        ImGui.end()
    }

    private fun drawDebugView(info: ModelAssetInfo?) {
        ImGui.text("Channel Display")
        drawDebugModeCombo(info)
        drawDebugCullingCombo()
        drawDebugTextureChannelCombo(info)

        val uvCheckerEnabled = state.uvCheckerEnabled || state.debugMode == MaterialDebugMode.UvChecker
        val uvCheckerToggle = booleanArrayOf(uvCheckerEnabled)
        if (ImGui.checkbox("UV Checker##model_viewer_uv_checker_enabled", uvCheckerToggle)) {
            if (uvCheckerToggle[0]) {
                if (state.debugMode != MaterialDebugMode.UvChecker) {
                    state.lastNonUvCheckerDebugMode = state.debugMode
                }
                state.uvCheckerEnabled = true
                state.debugMode = MaterialDebugMode.UvChecker
            } else {
                state.uvCheckerEnabled = false
                state.debugMode = state.lastNonUvCheckerDebugMode
            }
        }
        tooltipOnHover("Toggle a UV checker texture override to inspect model UV mapping.")
        drawUvCheckerSettings(info)
        state.debugWarning?.let { warning -> textLine("Warning: $warning") }
    }

    private fun drawDebugModeCombo(info: ModelAssetInfo?) {
        val expanded = ImGui.beginCombo("Channel##model_viewer_debug_mode", debugModeLabel(state.debugMode))
        tooltipOnHover("Choose the combined material result or one material channel to visualize.")
        if (!expanded) return
        MODEL_VIEWER_MATERIAL_CHANNEL_MODES.forEach { mode ->
            val available = debugModeAvailable(info, mode)
            if (!available) {
                ImGui.beginDisabled()
            }
            val selected = state.debugMode == mode
            if (ImGui.selectable("${debugModeLabel(mode)}##model_viewer_debug_${mode.name}", selected) && available) {
                state.debugMode = mode
                state.uvCheckerEnabled = mode == MaterialDebugMode.UvChecker
                if (mode != MaterialDebugMode.UvChecker) {
                    state.lastNonUvCheckerDebugMode = mode
                }
                preferredModelViewerTextureChannel(
                    info,
                    mode,
                    selectedMaterialIndex = null,
                )?.let { channel -> state.selectedTextureChannel = channel }
            }
            if (!available) {
                ImGui.endDisabled()
            }
            tooltipOnHover(
                if (available) {
                    debugModeTooltip(mode)
                } else {
                    "${debugModeLabel(mode)} is unavailable because the model does not provide this channel."
                },
            )
        }
        ImGui.endCombo()
    }

    private fun drawDebugCullingCombo() {
        val expanded =
            ImGui.beginCombo("Culling##model_viewer_debug_culling", debugCullingLabel(state.debugCullingMode))
        tooltipOnHover("Choose whether material-debug rendering hides backfaces or draws both sides.")
        if (!expanded) return
        DebugCullingMode.entries.forEach { mode ->
            if (ImGui.selectable(
                    "${debugCullingLabel(mode)}##model_viewer_debug_culling_$mode",
                    state.debugCullingMode == mode,
                )
            ) {
                state.debugCullingMode = mode
            }
            tooltipOnHover(
                if (mode == DebugCullingMode.Backface) {
                    "Hide back-facing triangles in the material-debug view."
                } else {
                    "Draw both front and back faces in the material-debug view."
                },
            )
        }
        ImGui.endCombo()
    }

    private fun drawDebugTextureChannelCombo(info: ModelAssetInfo?) {
        val mode = state.debugMode
        if (!isModelViewerTextureDebugMode(mode)) {
            textLine("Texture Channel: none")
            return
        }
        val channels =
            matchingModelViewerTextureSlots(info, mode, selectedMaterialIndex = null)
                .map { slot -> slot.channel }
                .distinct()
        if (channels.isEmpty()) {
            textLine("Texture Channel: unavailable")
            return
        }
        if (state.selectedTextureChannel !in channels) {
            state.selectedTextureChannel = channels.first()
        }
        val current = state.selectedTextureChannel ?: channels.first()
        val expanded =
            ImGui.beginCombo(
                "Texture Channel##model_viewer_debug_texture_channel",
                textureChannelLabel(current),
            )
        tooltipOnHover("Select which matching source texture slot supplies the active debug channel.")
        if (!expanded) return
        channels.forEach { channel ->
            if (ImGui.selectable(
                    "${textureChannelLabel(channel)}##model_viewer_debug_channel_$channel",
                    current == channel,
                )
            ) {
                state.selectedTextureChannel = channel
            }
            tooltipOnHover("Use ${textureChannelLabel(channel)} as the source for this debug view.")
        }
        ImGui.endCombo()
    }

    private fun drawUvCheckerSettings(info: ModelAssetInfo?) {
        ImGui.text("UV Checker")
        drawUvCheckerTextureCombo()
        drawUvChannelCombo(info)
        slider(
            "Scale##model_viewer_uv_checker_scale",
            state::uvCheckerScale,
            MinUvCheckerScale,
            MaxUvCheckerScale,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        tooltipOnHover("Scale the UV checker tiling multiplier. Changes update live.")
        val uvLabel = "UV${state.uvCheckerUvChannel}"
        if (info != null && info.uvChannels.isNotEmpty() && uvLabel !in info.uvChannels) {
            textLine("Warning: model does not report $uvLabel.")
        }
        val checkerRef = AssetRef.texture(state.uvCheckerTexturePath)
        if (!assets.isLoaded(checkerRef)) {
            textLine("Warning: checker texture is missing or not loaded.")
        }
    }

    private fun drawUvCheckerTextureCombo() {
        val current =
            UV_CHECKER_TEXTURE_OPTIONS.firstOrNull { option ->
                option.texturePath == state.uvCheckerTexturePath
            } ?: UV_CHECKER_TEXTURE_OPTIONS.first { option -> option.texturePath == DEFAULT_UV_CHECKER_TEXTURE }
        if (state.uvCheckerTexturePath !in UV_CHECKER_TEXTURE_OPTIONS.map { option -> option.texturePath }) {
            state.uvCheckerTexturePath = current.texturePath
        }
        val expanded =
            ImGui.beginCombo("Checker Texture##model_viewer_uv_checker_texture", current.resolution.toString())
        tooltipOnHover("Choose the checker texture resolution used for UV inspection.")
        if (!expanded) return
        UV_CHECKER_TEXTURE_OPTIONS.forEach { option ->
            if (ImGui.selectable(
                    "${option.resolution}##model_viewer_uv_checker_texture_${option.resolution}",
                    current == option,
                )
            ) {
                state.uvCheckerTexturePath = option.texturePath
            }
            tooltipOnHover("Use the ${option.resolution} px UV checker texture.")
        }
        ImGui.endCombo()
    }

    private fun drawUvChannelCombo(info: ModelAssetInfo?) {
        val channels =
            info
                ?.uvChannels
                .orEmpty()
                .mapNotNull(::uvChannelIndex)
                .distinct()
                .sorted()
        if (channels.isEmpty()) {
            state.uvCheckerUvChannel = 0
            val expanded = ImGui.beginCombo("UV Channel##model_viewer_uv_checker_channel", "None")
            tooltipOnHover("No UV channel metadata is available for this model.")
            if (expanded) {
                ImGui.selectable("None##model_viewer_uv_checker_channel_none", true)
                tooltipOnHover("The model does not expose a UV channel for the checker.")
                ImGui.endCombo()
            }
            return
        }
        if (state.uvCheckerUvChannel !in channels) {
            state.uvCheckerUvChannel = channels.first()
        }
        val currentLabel = "UV${state.uvCheckerUvChannel}"
        val expanded = ImGui.beginCombo("UV Channel##model_viewer_uv_checker_channel", currentLabel)
        tooltipOnHover("Select the model UV set used by the checker texture.")
        if (!expanded) return
        channels.forEach { channel ->
            val label = "UV$channel"
            if (ImGui.selectable(
                    "$label##model_viewer_uv_checker_channel_$channel",
                    state.uvCheckerUvChannel == channel,
                )
            ) {
                state.uvCheckerUvChannel = channel
            }
            tooltipOnHover("Use UV$channel coordinates for the checker texture.")
        }
        ImGui.endCombo()
    }

    private fun drawSelectedChannel(
        channel: String,
        slots: List<ModelTextureSlotInfo>,
    ) {
        val selectedSlots = slots.filter { slot -> slot.channel == channel }
        ImGui.text("Selected channel: ${textureChannelLabel(channel)}")
        if (selectedSlots.isEmpty()) {
            ImGui.text("No slots use this channel.")
            return
        }
        selectedSlots.forEach { slot ->
            ImGui.bulletText(
                "Material #${slot.materialIndex} ${slot.materialId ?: "unknown"}: ${slot.texturePath ?: "unknown"}",
            )
            drawTextureSlotPreview(assets, ui, slot)
            textLine("UV: ${slot.uvChannel ?: "unknown"}")
        }
    }

    private fun drawWarnings(
        info: ModelAssetInfo?,
        slots: List<ModelTextureSlotInfo>,
    ) {
        val warnings =
            buildList {
                if (info != null && info.materialCount <= 0) add("No materials.")
                if (info != null && info.uvChannels.isEmpty()) add("No UV channels.")
                if (info != null && slots.isNotEmpty() && info.uvChannels.isEmpty()) {
                    add("Texture slots exist but no UV channels were found.")
                }
                if (slots.any { slot -> slot.texturePath.isNullOrBlank() }) {
                    add("One or more texture slots have no stable texture path or id.")
                }
            }
        if (warnings.isEmpty()) return

        ImGui.separator()
        ImGui.text("Warnings")
        warnings.forEach { warning -> ImGui.bulletText(warning) }
    }

    companion object {
        private const val MinUvCheckerScale = 0.01f
        private const val MaxUvCheckerScale = 64f
    }
}

/**
 * Displays loading progress while the active model asset is pending.
 */
class ModelViewerLoadingPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        if (!state.isLoadingModel) return

        val expanded = beginModelViewerPanel(ModelViewerPanelIds.Loading, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Loading model asset")
        textLine(state.modelPath)
        ImGui.separator()
        textLine("Progress: ${"%.0f".format(state.assetProgress * 100f)}%")
        textLine(state.loadingStatus)
        ImGui.end()
    }
}

private fun matchingMaterialIndexForPart(
    part: ModelMeshPartInfo,
    materials: List<ModelMaterialInfo>,
): Int? {
    val materialId = part.materialId
    if (materialId != null) {
        val matchedById = materials.indexOfFirst { material -> material.id == materialId }
        if (matchedById >= 0) return matchedById
    }
    val materialIndex = part.materialIndex ?: return null
    return materialIndex.takeIf { index -> index in materials.indices }
}

private fun meshPartsUsingMaterial(
    material: ModelMaterialInfo,
    meshParts: List<ModelMeshPartInfo>,
): List<ModelMeshPartInfo> = meshParts.filter { part -> meshPartUsesMaterial(part, material) }

private fun meshPartUsesMaterial(
    part: ModelMeshPartInfo,
    material: ModelMaterialInfo,
): Boolean {
    val materialId = material.id
    if (materialId != null && part.materialId == materialId) return true
    return part.materialId == null && part.materialIndex == material.index
}

internal fun beginModelViewerPanel(
    panelId: String,
    layoutConfig: ImGuiLayoutConfig,
    layoutTracker: ImGuiLayoutRuntimeTracker,
    eventLogger: ImGuiWindowEventLogger,
): Boolean {
    val layout = layoutConfig.panels.getValue(panelId)
    val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
    eventLogger.observe(panelId, layout.title)
    return expanded
}

private fun formatPosition(position: Vec3): String = "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)

private fun formatSize(size: Vec3): String = "%.2f x %.2f x %.2f".format(size.x, size.y, size.z)

private fun formatList(values: List<String>): String = values.ifEmpty { listOf("none") }.joinToString(", ")

private fun uvChannelIndex(channel: String): Int? {
    val trimmed = channel.trim()
    return when {
        trimmed.startsWith("UV", ignoreCase = true) -> trimmed.drop(2).toIntOrNull()
        trimmed.startsWith("TEXCOORD_", ignoreCase = true) -> trimmed.substringAfter('_').toIntOrNull()
        else -> trimmed.toIntOrNull()
    }
}

private fun textureChannelLabel(channel: String): String =
    when (channel) {
        "baseColor", "diffuse" -> "Base Color / Diffuse"
        "normal" -> "Normal"
        "emissive" -> "Emissive"
        "occlusion" -> "Occlusion"
        "metallicRoughness" -> "Metallic/Roughness Packed"
        "alpha" -> "Alpha"
        "unknown" -> "Unknown"
        else -> channel.replaceFirstChar { char -> char.uppercaseChar() }
    }

private fun textureChannelSortKey(channel: String): Int =
    when (channel) {
        "baseColor", "diffuse" -> 0
        "normal" -> 1
        "emissive" -> 2
        "occlusion" -> 3
        "metallicRoughness" -> 4
        "alpha" -> 5
        "unknown" -> 100
        else -> 50
    }

private fun debugModeLabel(mode: MaterialDebugMode): String =
    when (mode) {
        MaterialDebugMode.Combined -> "Combined"
        MaterialDebugMode.BaseColor -> "Base Color"
        MaterialDebugMode.Normal -> "Normal"
        MaterialDebugMode.Emission -> "Emissive"
        MaterialDebugMode.Roughness -> "Roughness"
        MaterialDebugMode.Metallic -> "Metallic"
        MaterialDebugMode.MetallicRoughnessPacked -> "Metallic/Roughness Packed"
        MaterialDebugMode.Occlusion -> "Occlusion"
        MaterialDebugMode.Alpha -> "Alpha"
        MaterialDebugMode.UvChecker -> "UV Checker"
    }

private fun debugModeAvailable(
    info: ModelAssetInfo?,
    mode: MaterialDebugMode,
): Boolean =
    when {
        mode == MaterialDebugMode.Combined -> true
        mode == MaterialDebugMode.UvChecker -> info?.uvChannels?.isNotEmpty() != false
        isModelViewerTextureDebugMode(mode) ->
            hasModelViewerTextureChannel(info, mode, selectedMaterialIndex = null)

        else -> true
    }

private fun debugCullingLabel(mode: DebugCullingMode): String =
    when (mode) {
        DebugCullingMode.Backface -> "Backface"
        DebugCullingMode.DoubleSided -> "Double-sided"
    }

private fun rendererModeLabel(mode: ModelViewerRendererMode): String =
    when (mode) {
        ModelViewerRendererMode.LibGdx -> "LibGDX / Legacy"
        ModelViewerRendererMode.Pbr -> "glTF / PBR"
    }

private fun toneMappingLabel(mode: PbrToneMapping): String =
    when (mode) {
        PbrToneMapping.Aces -> "ACES"
        PbrToneMapping.Reinhard -> "Reinhard"
        PbrToneMapping.None -> "None"
    }

private fun drawColorControl(
    label: String,
    color: Color,
    tooltip: String,
) {
    colorEdit4(label, color.r, color.g, color.b, color.a) { r, g, b, a ->
        color.r = r
        color.g = g
        color.b = b
        color.a = a
    }
    tooltipOnHover(tooltip)
}

private fun debugModeTooltip(mode: MaterialDebugMode): String =
    when (mode) {
        MaterialDebugMode.Combined -> "Display the full material result with all supported channels combined."
        MaterialDebugMode.BaseColor -> "Visualize the material base-color texture contribution."
        MaterialDebugMode.Normal -> "Visualize the normal map contribution."
        MaterialDebugMode.Metallic -> "Visualize metallic values used by the material."
        MaterialDebugMode.Roughness -> "Visualize roughness values used by the material."
        MaterialDebugMode.Occlusion -> "Visualize ambient occlusion contribution."
        MaterialDebugMode.Emission -> "Visualize emissive color and texture contribution."
        MaterialDebugMode.Alpha -> "Visualize the alpha channel used for masking or transparency."
        MaterialDebugMode.MetallicRoughnessPacked -> "Visualize the packed metallic and roughness texture."
        MaterialDebugMode.UvChecker -> "Display the UV checker texture override."
    }

private fun tooltipOnHover(value: String) {
    if (ImGui.isItemHovered()) {
        ImGui.setTooltip(value)
    }
}

private fun readTextBuffer(buffer: ByteArray): String {
    val length = buffer.indexOf(0).let { if (it < 0) buffer.size else it }
    return String(buffer, 0, length, StandardCharsets.UTF_8)
}

private fun writeTextBuffer(
    buffer: ByteArray,
    value: String,
) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    bytes.copyInto(buffer, endIndex = minOf(bytes.size, buffer.size - 1))
}

private fun drawInfoList(
    label: String,
    values: List<String>,
) {
    ImGui.text("$label:")
    if (values.isEmpty()) {
        ImGui.bulletText("none")
        return
    }
    values.forEach { value -> ImGui.bulletText(value) }
}

private fun drawTextureSlotPreview(
    assets: AssetService,
    ui: UiService,
    slot: ModelTextureSlotInfo,
) {
    val texturePath = slot.texturePath
    if (texturePath.isNullOrBlank()) {
        ImGui.text("Preview unavailable: no texture id.")
        return
    }

    val handle = assets.texturePreviewHandle(texturePath)
    if (handle == null) {
        ImGui.text("Preview unavailable.")
        return
    }

    if (!ui.drawTexturePreview(handle, TexturePreviewSize, TexturePreviewSize)) {
        ImGui.text("Preview unavailable.")
        return
    }
    ImGui.textUnformatted("Size: ${handle.width} x ${handle.height}")
}

private fun textLine(text: String) {
    ImGui.textUnformatted(text)
}

private class FloatHolder(
    var value: Float,
)

private const val TexturePreviewSize = 100f
