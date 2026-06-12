package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.ui.editor.*
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider
import imgui.dsl
import glm_.vec2.Vec2 as ImVec2

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
            button("Persist UI##model_viewer_persist_ui") {
                operations.saveUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset UI to Default##model_viewer_reset_ui") {
                operations.restoreUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset Camera##model_viewer_reset_camera") {
                operations.resetCamera()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Frame Model##model_viewer_frame_model") {
                operations.frameModel()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Exit##model_viewer_exit") {
                state.exitRequested = true
            }
        }

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
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
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

        ImGui.text("Model view")
        ImGui.separator()
        textLine("Camera: ${formatPosition(state.camera.position)}")
        textLine("Speed: ${"%.2f".format(state.camera.speed)}${if (state.camera.navigating) " (navigating)" else ""}")
        ImGui.text("RMB look, WASD move, Q/E down/up, wheel speed, Shift faster.")
        ImGui.separator()

        drawDisplayModeCombo()
        drawRendererControls()
        ImGui.checkbox("Grid##model_viewer_show_grid", state::showGrid)
        ImGui.sameLine()
        ImGui.checkbox("Axes##model_viewer_show_axes", state::showAxes)
        ImGui.sameLine()
        ImGui.checkbox("Bounding Box##model_viewer_show_bounding_box", state::showBoundingBox)
        slider(
            "Grid extent##model_viewer_grid_extent",
            state::gridHalfExtentCells,
            MinGridHalfExtentCells,
            MaxGridHalfExtentCells,
            "%d",
            SliderFlag.AlwaysClamp,
        )
        slider(
            "Cell size##model_viewer_grid_cell_size",
            state::gridCellSize,
            MinGridCellSize,
            MaxGridCellSize,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        ImGui.endChild()
        ImGui.end()
    }

    private fun drawDisplayModeCombo() {
        if (!ImGui.beginCombo("Display Mode##model_viewer_display_mode", state.displayMode.name)) return
        ModelViewerDisplayMode.entries.forEach { mode ->
            if (ImGui.selectable("${mode.name}##model_viewer_display_${mode.name}", state.displayMode == mode)) {
                state.displayMode = mode
            }
        }
        ImGui.endCombo()
    }

    private fun drawRendererControls() {
        ImGui.separator()
        ImGui.text("Renderer")
        if (ImGui.beginCombo("Mode##model_viewer_renderer_mode", rendererModeLabel(state.rendererMode))) {
            ModelViewerRendererMode.entries.forEach { mode ->
                if (ImGui.selectable(
                        "${rendererModeLabel(mode)}##model_viewer_renderer_$mode",
                        state.rendererMode == mode
                    )
                ) {
                    state.rendererMode = mode
                }
            }
            ImGui.endCombo()
        }
        if (state.rendererMode != ModelViewerRendererMode.Pbr) return

        slider("Exposure##model_viewer_pbr_exposure", state::pbrExposure, 0.1f, 4f, "%.2f", SliderFlag.AlwaysClamp)
        slider(
            "Environment Intensity##model_viewer_pbr_environment_intensity",
            state::pbrEnvironmentIntensity,
            0f,
            4f,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        ImGui.checkbox("Show Skybox##model_viewer_pbr_show_skybox", state::pbrShowSkybox)
        ImGui.checkbox("Directional Light##model_viewer_pbr_directional_enabled", state::pbrDirectionalLightEnabled)
        slider(
            "Light Yaw##model_viewer_pbr_light_yaw",
            state::pbrDirectionalLightYawDegrees,
            -180f,
            180f,
            "%.0f",
            SliderFlag.AlwaysClamp,
        )
        slider(
            "Light Pitch##model_viewer_pbr_light_pitch",
            state::pbrDirectionalLightPitchDegrees,
            -89f,
            89f,
            "%.0f",
            SliderFlag.AlwaysClamp,
        )
        state.pbrWarning?.let { warning -> textLine("Warning: $warning") }
    }

    companion object {
        private const val MinGridHalfExtentCells = 1
        private const val MaxGridHalfExtentCells = 256
        private const val MinGridCellSize = 0.05f
        private const val MaxGridCellSize = 16f
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
            textLine("Path: ${state.modelPath}")
            ImGui.end()
            return
        }

        textLine("Path: ${info.path}")
        textLine("Format: ${info.format}")
        textLine("Nodes: ${info.nodeCount}")
        textLine("Meshes: ${info.meshCount}")
        textLine("Mesh parts: ${info.meshPartCount}")
        textLine("Materials: ${info.materialCount}")
        textLine("Vertices: ${info.vertexCount}")
        textLine("Triangles: ${info.triangleCount}")
        textLine("Vertex channels: ${formatList(info.vertexChannels)}")
        textLine("UV channels: ${formatList(info.uvChannels)}")
        textLine("Texture channels: ${formatList(info.textureChannels)}")
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
            val center = Vec3(
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
                            max.z - min.z
                        )
                    )
                }"
            )
            textLine("Center: ${formatPosition(center)}")
            textLine("Pivot offset from center: ${formatPosition(pivotOffset)}")
        } else {
            textLine("Size: ${info.size?.let(::formatSize) ?: "unknown"}")
        }
    }

    private fun drawWarnings(info: ModelAssetInfo) {
        val textureSlots = info.materials.flatMap { material -> material.textureSlots }
        val warnings = buildList {
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
            if (info.boundsMin == null || info.boundsMax == null || info.size == null) add("Bounds are missing.")
            if (info.size != null && info.size.isNearZero()) add("Model has zero or near-zero size.")
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

    private fun Vec3.isNearZero(): Boolean =
        x <= NearZeroSize || y <= NearZeroSize || z <= NearZeroSize
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
            state::filterMeshPartsBySelectedMaterial
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
            val label = "#${part.index} ${part.partId ?: part.meshId ?: part.nodeName ?: "Mesh Part"}"
            if (ImGui.selectable(
                    "$label##model_viewer_mesh_part_${part.index}",
                    state.selectedMeshPartIndex == part.index
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
        textLine("Mesh: ${part.meshId ?: "unknown"}")
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
                    state.selectedMaterialIndex == material.index
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
                        color.a
                    )
                } ?: "unknown"
            }")
        textLine("Opacity: ${material.opacity?.let { "%.2f".format(it) } ?: "unknown"}")
        textLine("Usage count: ${usedBy.size}")
        textLine(
            "Used by mesh parts: ${
                usedBy.ifEmpty { emptyList() }.joinToString(", ") { part -> "#${part.index}" }.ifBlank { "none" }
            }"
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

        val channels = slots
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
                    selected
                )
            ) {
                state.selectedTextureChannel = channel
            }
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
        ImGui.text("Debug View")
        drawDebugModeCombo(info)
        val selectedOnly = booleanArrayOf(state.debugSelectedMaterialOnly)
        if (ImGui.checkbox("Selected material only##model_viewer_debug_selected_material_only", selectedOnly)) {
            state.debugSelectedMaterialOnly = selectedOnly[0]
        }
        if (state.debugSelectedMaterialOnly && state.selectedMaterialIndex == null) {
            textLine("Select a material to limit debug rendering.")
        }
        drawDebugCullingCombo()
        drawDebugTextureChannelCombo(info)

        val uvCheckerEnabled = state.uvCheckerEnabled || state.debugMode == MaterialDebugMode.UvChecker
        val uvCheckerToggle = booleanArrayOf(uvCheckerEnabled)
        if (ImGui.checkbox("UV Checker##model_viewer_uv_checker_enabled", uvCheckerToggle)) {
            state.uvCheckerEnabled = uvCheckerToggle[0]
            state.debugMode = if (state.uvCheckerEnabled) MaterialDebugMode.UvChecker else MaterialDebugMode.None
        }
        drawUvCheckerSettings(info)
        state.debugWarning?.let { warning -> textLine("Warning: $warning") }
    }

    private fun drawDebugModeCombo(info: ModelAssetInfo?) {
        if (!ImGui.beginCombo("Mode##model_viewer_debug_mode", debugModeLabel(state.debugMode))) return
        MaterialDebugMode.entries.forEach { mode ->
            val available = debugModeAvailable(info, mode)
            val label = if (available) debugModeLabel(mode) else "${debugModeLabel(mode)} (unavailable)"
            if (ImGui.selectable("$label##model_viewer_debug_${mode.name}", state.debugMode == mode)) {
                state.debugMode = mode
                state.uvCheckerEnabled = mode == MaterialDebugMode.UvChecker
                preferredModelViewerTextureChannel(
                    info,
                    mode,
                    selectedMaterialIndex = state.selectedMaterialIndex.takeIf { state.debugSelectedMaterialOnly },
                )?.let { channel -> state.selectedTextureChannel = channel }
            }
        }
        ImGui.endCombo()
    }

    private fun drawDebugCullingCombo() {
        if (!ImGui.beginCombo("Culling##model_viewer_debug_culling", debugCullingLabel(state.debugCullingMode))) return
        DebugCullingMode.entries.forEach { mode ->
            if (ImGui.selectable(
                    "${debugCullingLabel(mode)}##model_viewer_debug_culling_$mode",
                    state.debugCullingMode == mode
                )
            ) {
                state.debugCullingMode = mode
            }
        }
        ImGui.endCombo()
    }

    private fun drawDebugTextureChannelCombo(info: ModelAssetInfo?) {
        val mode = state.debugMode
        if (!isModelViewerTextureDebugMode(mode)) {
            textLine("Texture Channel: none")
            return
        }
        val selectedMaterial = state.selectedMaterialIndex.takeIf { state.debugSelectedMaterialOnly }
        val channels = matchingModelViewerTextureSlots(info, mode, selectedMaterialIndex = selectedMaterial)
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
        if (!ImGui.beginCombo(
                "Texture Channel##model_viewer_debug_texture_channel",
                textureChannelLabel(current)
            )
        ) return
        channels.forEach { channel ->
            if (ImGui.selectable(
                    "${textureChannelLabel(channel)}##model_viewer_debug_channel_$channel",
                    current == channel
                )
            ) {
                state.selectedTextureChannel = channel
            }
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
        val current = UV_CHECKER_TEXTURE_OPTIONS.firstOrNull { option ->
            option.texturePath == state.uvCheckerTexturePath
        } ?: UV_CHECKER_TEXTURE_OPTIONS.first { option -> option.texturePath == DEFAULT_UV_CHECKER_TEXTURE }
        if (state.uvCheckerTexturePath !in UV_CHECKER_TEXTURE_OPTIONS.map { option -> option.texturePath }) {
            state.uvCheckerTexturePath = current.texturePath
        }
        if (!ImGui.beginCombo("Checker Texture##model_viewer_uv_checker_texture", current.resolution.toString())) return
        UV_CHECKER_TEXTURE_OPTIONS.forEach { option ->
            if (ImGui.selectable(
                    "${option.resolution}##model_viewer_uv_checker_texture_${option.resolution}",
                    current == option
                )
            ) {
                state.uvCheckerTexturePath = option.texturePath
            }
        }
        ImGui.endCombo()
    }

    private fun drawUvChannelCombo(info: ModelAssetInfo?) {
        val channels = info?.uvChannels.orEmpty()
            .mapNotNull(::uvChannelIndex)
            .distinct()
            .sorted()
        if (channels.isEmpty()) {
            state.uvCheckerUvChannel = 0
            if (ImGui.beginCombo("UV Channel##model_viewer_uv_checker_channel", "None")) {
                ImGui.selectable("None##model_viewer_uv_checker_channel_none", true)
                ImGui.endCombo()
            }
            return
        }
        if (state.uvCheckerUvChannel !in channels) {
            state.uvCheckerUvChannel = channels.first()
        }
        val currentLabel = "UV${state.uvCheckerUvChannel}"
        if (!ImGui.beginCombo("UV Channel##model_viewer_uv_checker_channel", currentLabel)) return
        channels.forEach { channel ->
            val label = "UV$channel"
            if (ImGui.selectable(
                    "$label##model_viewer_uv_checker_channel_$channel",
                    state.uvCheckerUvChannel == channel
                )
            ) {
                state.uvCheckerUvChannel = channel
            }
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
        val warnings = buildList {
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
): List<ModelMeshPartInfo> =
    meshParts.filter { part -> meshPartUsesMaterial(part, material) }

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

private fun formatPosition(position: Vec3): String =
    "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)

private fun formatSize(size: Vec3): String =
    "%.2f x %.2f x %.2f".format(size.x, size.y, size.z)

private fun formatList(values: List<String>): String =
    values.ifEmpty { listOf("none") }.joinToString(", ")

private fun uvChannelIndex(channel: String): Int? {
    val trimmed = channel.trim()
    return when {
        trimmed.startsWith("UV", ignoreCase = true) -> trimmed.drop(2).toIntOrNull()
        trimmed.startsWith("TEXCOORD_", ignoreCase = true) -> trimmed.substringAfter('_').toIntOrNull()
        else -> trimmed.toIntOrNull()
    }
}

private fun textureChannelLabel(channel: String): String = when (channel) {
    "baseColor", "diffuse" -> "Base Color / Diffuse"
    "normal" -> "Normal"
    "emissive" -> "Emissive"
    "occlusion" -> "Occlusion"
    "metallicRoughness" -> "Metallic/Roughness Packed"
    "alpha" -> "Alpha"
    "unknown" -> "Unknown"
    else -> channel.replaceFirstChar { char -> char.uppercaseChar() }
}

private fun textureChannelSortKey(channel: String): Int = when (channel) {
    "baseColor", "diffuse" -> 0
    "normal" -> 1
    "emissive" -> 2
    "occlusion" -> 3
    "metallicRoughness" -> 4
    "alpha" -> 5
    "unknown" -> 100
    else -> 50
}

private fun debugModeLabel(mode: MaterialDebugMode): String = when (mode) {
    MaterialDebugMode.None -> "None"
    MaterialDebugMode.BaseColor -> "Base Color"
    MaterialDebugMode.Normal -> "Normal"
    MaterialDebugMode.Emission -> "Emission"
    MaterialDebugMode.Roughness -> "Roughness"
    MaterialDebugMode.Metallic -> "Metallic"
    MaterialDebugMode.MetallicRoughnessPacked -> "Metallic/Roughness Packed"
    MaterialDebugMode.Occlusion -> "Occlusion"
    MaterialDebugMode.Alpha -> "Alpha"
    MaterialDebugMode.UvChecker -> "UV Checker"
}

private fun debugModeAvailable(info: ModelAssetInfo?, mode: MaterialDebugMode): Boolean = when {
    mode == MaterialDebugMode.None -> true
    mode == MaterialDebugMode.UvChecker -> info?.uvChannels?.isNotEmpty() != false
    isModelViewerTextureDebugMode(mode) ->
        hasModelViewerTextureChannel(info, mode, selectedMaterialIndex = null)

    else -> true
}

private fun debugCullingLabel(mode: DebugCullingMode): String = when (mode) {
    DebugCullingMode.Backface -> "Backface"
    DebugCullingMode.DoubleSided -> "Double-sided"
}

private fun rendererModeLabel(mode: ModelViewerRendererMode): String = when (mode) {
    ModelViewerRendererMode.LibGdx -> "LibGDX (default)"
    ModelViewerRendererMode.Pbr -> "PBR"
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

private const val TexturePreviewSize = 100f
