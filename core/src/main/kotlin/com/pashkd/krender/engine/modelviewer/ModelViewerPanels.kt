package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.ModelMaterialInfo
import com.pashkd.krender.engine.api.ModelMeshPartInfo
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import com.pashkd.krender.engine.ui.beginImGuiPanel
import glm_.vec2.Vec2 as ImVec2
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider
import imgui.dsl

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
        ImGui.textWrapped("Model: ${state.modelPath}")
        ImGui.text("Status: ${state.statusMessage}")
        state.errorMessage?.let { error -> ImGui.text("Error: $error") }
        ImGui.text("Asset: ${state.loadingStatus}")
        ImGui.text("Camera speed: ${"%.2f".format(state.camera.speed)}")
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
        ImGui.text("Camera: ${formatPosition(state.camera.position)}")
        ImGui.text("Speed: ${"%.2f".format(state.camera.speed)}${if (state.camera.navigating) " (navigating)" else ""}")
        ImGui.text("RMB look, WASD move, Q/E down/up, wheel speed, Shift faster.")
        ImGui.separator()

        drawDisplayModeCombo()
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
            ImGui.textWrapped("Path: ${state.modelPath}")
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
        textLine("Size: ${info.size?.let(::formatSize) ?: "unknown"}")
        textLine("Vertex channels: ${formatList(info.vertexChannels)}")
        textLine("UV channels: ${formatList(info.uvChannels)}")
        textLine("Texture channels: ${formatList(info.textureChannels)}")
        textLine("Textures: ${info.textureCount} unique / ${info.textureSlotCount} slots")
        textLine("Skeleton: ${if (info.hasSkeleton) "yes" else "no"}")
        textLine("Bones: ${info.boneCount}")
        textLine("Bone weight channels: ${info.boneWeightChannelCount}")
        textLine("Animations: ${info.animationCount}")
        drawWarnings(info)
        ImGui.end()
    }

    private fun drawWarnings(info: com.pashkd.krender.engine.api.ModelAssetInfo) {
        val warnings = buildList {
            if (info.uvChannels.isEmpty()) add("No UV channels.")
            if (info.materialCount <= 0) add("No materials.")
            if (info.textureCount <= 0) add("No textures.")
            if (info.animationCount <= 0) add("No animations.")
            if (info.hasSkeleton && info.animationCount <= 0) add("Skeleton has no animations.")
            if (info.triangleCount >= HighTriangleWarningThreshold) add("High triangle count.")
            if (info.size == null) add("Missing bounds.")
        }
        if (warnings.isEmpty()) return

        ImGui.separator()
        ImGui.text("Warnings")
        warnings.forEach { warning -> ImGui.bulletText(warning) }
    }

    companion object {
        private const val HighTriangleWarningThreshold = 250_000
    }
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

        val meshParts = state.modelInfo?.meshParts.orEmpty()
        if (meshParts.isEmpty()) {
            ImGui.text("No mesh-part metadata available.")
            ImGui.end()
            return
        }

        meshParts.forEach { part ->
            val label = "#${part.index} ${part.partId ?: part.meshId ?: part.nodeName ?: "Mesh Part"}"
            if (ImGui.selectable("$label##model_viewer_mesh_part_${part.index}", state.selectedMeshPartIndex == part.index)) {
                state.selectedMeshPartIndex = part.index
            }
        }
        ImGui.separator()
        meshParts.getOrNull(state.selectedMeshPartIndex ?: -1)?.let(::drawMeshPartDetails)
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

        val materials = state.modelInfo?.materials.orEmpty()
        if (materials.isEmpty()) {
            ImGui.text("No material metadata available.")
            ImGui.end()
            return
        }

        materials.forEach { material ->
            val label = "#${material.index} ${material.id ?: "Material"}"
            if (ImGui.selectable("$label##model_viewer_material_${material.index}", state.selectedMaterialIndex == material.index)) {
                state.selectedMaterialIndex = material.index
            }
        }
        ImGui.separator()
        materials.getOrNull(state.selectedMaterialIndex ?: -1)?.let(::drawMaterialDetails)
            ?: ImGui.text("Select a material.")
        ImGui.end()
    }

    private fun drawMaterialDetails(material: ModelMaterialInfo) {
        ImGui.text("Selected material")
        textLine("Index: ${material.index}")
        textLine("Id: ${material.id ?: "unknown"}")
        textLine("Base color: ${material.baseColor?.let { color -> "%.2f, %.2f, %.2f, %.2f".format(color.r, color.g, color.b, color.a) } ?: "unknown"}")
        textLine("Opacity: ${material.opacity?.let { "%.2f".format(it) } ?: "unknown"}")
        ImGui.separator()
        ImGui.text("Texture slots")
        textLine("Diffuse: ${material.diffuseTexture ?: "empty"}")
        textLine("Normal: ${material.normalTexture ?: "empty"}")
        textLine("Emissive: ${material.emissiveTexture ?: "empty"}")
    }
}

/**
 * Shows animation ids exposed by the loaded model.
 */
class ModelViewerAnimationsPanel(
    private val state: ModelViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginModelViewerPanel(ModelViewerPanelIds.Animations, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        val animations = state.modelInfo?.animationNames.orEmpty()
        if (animations.isEmpty()) {
            ImGui.text("No animations.")
            ImGui.end()
            return
        }

        animations.forEachIndexed { index, animation ->
            if (ImGui.selectable("$animation##model_viewer_animation_$index", state.selectedAnimationIndex == index)) {
                state.selectedAnimationIndex = index
            }
        }
        ImGui.end()
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
        ImGui.textWrapped(state.modelPath)
        ImGui.separator()
        ImGui.text("Progress: %.0f%%", state.assetProgress * 100f)
        ImGui.text("%s", state.loadingStatus)
        ImGui.end()
    }
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

private fun textLine(text: String) {
    ImGui.textWrapped(text)
}
