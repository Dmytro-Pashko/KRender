package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorEdit4
import imgui.api.slider
import imgui.dsl

/**
 * Defines the stable JSON ids used by the Terrain Editor ImGui panels.
 */
object TerrainEditorPanelIds {
    const val Terrain = "terrain"
    const val Brush = "brush"
    const val Layers = "layers"
    const val Control = "control"
}

/**
 * Holds the asset path and fallback layouts for Terrain Editor panels.
 */
object TerrainEditorUiLayoutDefaults {
    const val assetPath = "ui/terrain_editor_layout.json"

    val config = ImGuiLayoutConfig(
        panels = mapOf(
            TerrainEditorPanelIds.Terrain to ImGuiPanelLayout(
                title = "Terrain",
                x = 16f,
                y = 16f,
                width = 320f,
                height = 520f,
            ),
            TerrainEditorPanelIds.Brush to ImGuiPanelLayout(
                title = "Brush",
                x = 352f,
                y = 16f,
                width = 320f,
                height = 300f,
            ),
            TerrainEditorPanelIds.Layers to ImGuiPanelLayout(
                title = "Layers",
                x = 16f,
                y = 392f,
                width = 320f,
                height = 300f,
            ),
            TerrainEditorPanelIds.Control to ImGuiPanelLayout(
                title = "Control",
                x = 352f,
                y = 348f,
                width = 320f,
                height = 300f,
            ),
        ),
    )
}

/**
 * Presents terrain generation and regeneration controls.
 */
class TerrainEditorTerrainPanel(
    private val state: TerrainEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private var resolutionIndex: Int = DEFAULT_RESOLUTION_INDEX

    /**
     * Draws the terrain settings window using the configured default layout.
     */
    override fun draw() {
        syncResolutionIndex()
        val layout = layoutConfig.panels.getValue(TerrainEditorPanelIds.Terrain)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(layout.title)
        eventLogger.observe(TerrainEditorPanelIds.Terrain, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Generator")
        if (state.generators.isEmpty()) {
            ImGui.text("No generators are available.")
        } else {
            state.generators.forEach { generator ->
                if (ImGui.selectable(generator.label, generator.id == state.selectedGeneratorId)) {
                    state.selectedGeneratorId = generator.id
                }
            }
        }

        ImGui.separator()
        ImGui.text("Mesh")
        if (
            slider(
                "Resolution",
                ::resolutionIndex,
                0,
                RESOLUTION_OPTIONS.lastIndex,
                RESOLUTION_LABELS[resolutionIndex],
                SliderFlag.AlwaysClamp,
            )
        ) {
            state.terrainResolution = RESOLUTION_OPTIONS[resolutionIndex]
        }
        slider("Vertex spacing", state::vertexSpacing, 0.25f, 4f, "%.2f", SliderFlag.AlwaysClamp)

        ImGui.separator()
        ImGui.text("Statistics")
        ImGui.text("Size: %s", state.terrainSize)
        ImGui.text("Vertices: %d", state.vertices)
        ImGui.text("Triangles: %d", state.triangles)

        ImGui.separator()
        ImGui.text("Actions")
        with(dsl) {
            button("Regenerate terrain") {
                state.regenerateRequested = true
            }
        }

        ImGui.separator()
        drawPersistenceControls(state)

        ImGui.end()
    }

    /**
     * Mirrors the state resolution into the local slider index.
     */
    private fun syncResolutionIndex() {
        resolutionIndex = RESOLUTION_OPTIONS.indexOf(state.terrainResolution)
            .takeIf { it >= 0 }
            ?: DEFAULT_RESOLUTION_INDEX
    }

    companion object {
        private val RESOLUTION_OPTIONS = intArrayOf(64, 128, 256)
        private val RESOLUTION_LABELS = arrayOf("64", "128", "256")
        private const val DEFAULT_RESOLUTION_INDEX = 1
    }
}

/**
 * Presents terrain brush settings and mode switches.
 */
class TerrainEditorBrushPanel(
    private val state: TerrainEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the brush settings window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TerrainEditorPanelIds.Brush)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(layout.title)
        eventLogger.observe(TerrainEditorPanelIds.Brush, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Brush")
        slider("Radius", state::brushRadius, 1f, 64f, "%.1f", SliderFlag.AlwaysClamp)
        slider("Strength", state::brushStrength, 0.1f, 32f, "%.1f", SliderFlag.AlwaysClamp)
        slider("Falloff", state::brushFalloff, 1f, 4f, "%.2f", SliderFlag.AlwaysClamp)

        ImGui.separator()
        ImGui.text("Mode")
        brushModeButton("Raise", TerrainBrushMode.Raise)
        ImGui.sameLine()
        brushModeButton("Lower", TerrainBrushMode.Lower)
        ImGui.sameLine()
        brushModeButton("Flatten", TerrainBrushMode.Flatten)
        ImGui.sameLine()
        brushModeButton("Smooth", TerrainBrushMode.Smooth)
        brushModeButton("Paint Layer", TerrainBrushMode.PaintLayer)

        ImGui.separator()
        ImGui.text("Status")
        ImGui.text("Mode: %s", state.brushMode.name)
        ImGui.text("Hovered: %s", state.hoveredTerrainPosition)

        if (state.brushMode == TerrainBrushMode.Flatten) {
            ImGui.text("Flatten uses the first sampled terrain height as the target.")
        }
        if (state.brushMode == TerrainBrushMode.PaintLayer) {
            ImGui.text("Selected layer: %s", state.selectedLayerId?.toString() ?: "none")
        }

        ImGui.end()
    }

    /**
     * Sets the active brush mode when the button is pressed.
     */
    private fun brushModeButton(label: String, mode: TerrainBrushMode) {
        with(dsl) {
            button(label) {
                state.brushMode = mode
            }
        }
    }
}

/**
 * Presents paint-layer selection and management controls.
 */
class TerrainEditorLayersPanel(
    private val state: TerrainEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the layer management window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TerrainEditorPanelIds.Layers)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(layout.title)
        eventLogger.observe(TerrainEditorPanelIds.Layers, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Terrain layers (max ${TerrainLayerLimits.MaxLayers})")
        ImGui.text("Layer count: ${state.layers.size} / ${TerrainLayerLimits.MaxLayers}")
        state.layers.forEach { layer ->
            if (ImGui.smallButton("${if (layer.visible) "[x]" else "[ ]"}##visible_${layer.id}")) {
                state.selectedLayerId = layer.id
                state.selectedLayerVisible = !layer.visible
                state.updateLayerVisibilityRequested = true
            }
            ImGui.sameLine()
            if (ImGui.selectable("${layer.name}##layer_${layer.id}", layer.id == state.selectedLayerId)) {
                state.selectedLayerId = layer.id
            }
            ImGui.sameLine()
            ImGui.text(
                "RGBA %.2f %.2f %.2f %.2f | Tiling: %.2f",
                layer.color.r,
                layer.color.g,
                layer.color.b,
                layer.color.a,
                layer.tiling,
            )
        }

        ImGui.separator()
        ImGui.text("Selection")
        ImGui.text("Active layer: %s", state.selectedLayerId?.toString() ?: "none")
        if (state.selectedLayerId != null && !state.selectedLayerVisible) {
            ImGui.text("Selected layer is hidden")
        }
        with(dsl) {
            button("Paint selected layer") {
                state.brushMode = TerrainBrushMode.PaintLayer
            }
        }

        ImGui.separator()
        ImGui.text("Actions")
        ImGui.beginDisabled(state.layers.size >= TerrainLayerLimits.MaxLayers)
        with(dsl) {
            button("Add layer") {
                state.addLayerRequested = true
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(state.selectedLayerId == null)
        with(dsl) {
            button("Remove layer") {
                state.removeLayerRequested = true
            }
        }
        ImGui.endDisabled()
        val selectedLayer = state.layers.firstOrNull { it.id == state.selectedLayerId }
        ImGui.beginDisabled(selectedLayer == null || selectedLayer.index == 0)
        with(dsl) {
            button("Move Up") {
                state.moveLayerUpRequested = true
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(selectedLayer == null || selectedLayer.index == state.layers.lastIndex)
        with(dsl) {
            button("Move Down") {
                state.moveLayerDownRequested = true
            }
        }
        ImGui.endDisabled()
        if (state.layerMessage.isNotBlank()) {
            ImGui.text("Status: ${state.layerMessage}")
        }

        ImGui.separator()
        ImGui.text("Selected Layer Details")
        ImGui.beginDisabled(state.selectedLayerId == null)
        if (ImGui.inputText("Name", state::selectedLayerName)) {
            state.renameLayerRequested = true
        }
        if (ImGui.inputText("MaterialId", state::selectedLayerMaterialId)) {
            state.updateLayerMaterialRequested = true
        }
        if (ImGui.checkbox("Visible", state::selectedLayerVisible)) {
            state.updateLayerVisibilityRequested = true
        }
        val color = state.selectedLayerColor
        if (
            colorEdit4(
                "Color",
                color.getOrElse(0) { 1f },
                color.getOrElse(1) { 1f },
                color.getOrElse(2) { 1f },
                color.getOrElse(3) { 1f },
            ) { r, g, b, a ->
                state.selectedLayerColor = floatArrayOf(r, g, b, a)
            }
        ) {
            state.updateLayerColorRequested = true
        }
        if (slider("Tiling", state::selectedLayerTiling, 0.1f, 128f, "%.2f", SliderFlag.AlwaysClamp)) {
            state.updateLayerTilingRequested = true
        }
        ImGui.endDisabled()

        ImGui.end()
    }
}

/**
 * Presents terrain statistics, viewport toggles, and editor hints.
 */
class TerrainEditorControlsPanel(
    private val state: TerrainEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the debug window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TerrainEditorPanelIds.Control)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(layout.title)
        eventLogger.observe(TerrainEditorPanelIds.Control, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Viewport")
        ImGui.checkbox("Draw X/Y/Z axis", state::showAxes)
        ImGui.checkbox("Wireframe", state::wireframeEnabled)

        ImGui.separator()
        ImGui.text("Controls")
        ImGui.bulletText("Mouse drag - Apply brush")
        ImGui.bulletText("Ctrl + Z - Undo")
        ImGui.bulletText("Ctrl + Y / Ctrl + Shift + Z - Redo")
        ImGui.bulletText("Mouse wheel - Brush radius")
        ImGui.bulletText("Shift + Mouse wheel - Brush strength")
        ImGui.bulletText("W/A/S/D - Pan camera")
        ImGui.bulletText("R/F - Move camera up/down")
        ImGui.bulletText("Q/E - Rotate camera")

        ImGui.separator()
        ImGui.text("History")
        ImGui.text("Unsaved changes: ${formatBoolean(state.hasUnsavedChanges)}")
        ImGui.text("Undo actions: ${state.undoCount}")
        ImGui.text("Redo actions: ${state.redoCount}")
        ImGui.text("Next undo: ${state.undoLabel ?: "none"}")
        ImGui.text("Next redo: ${state.redoLabel ?: "none"}")
        ImGui.text("History memory: ${formatHistoryMemory(state.historyMemoryBytes)}")
        ImGui.beginDisabled(!state.canUndo)
        with(dsl) {
            button("Undo") {
                state.undoRequested = true
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(!state.canRedo)
        with(dsl) {
            button("Redo") {
                state.redoRequested = true
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(!state.canUndo && !state.canRedo)
        with(dsl) {
            button("Clear History") {
                state.clearHistoryRequested = true
            }
        }
        ImGui.endDisabled()
        drawHistoryPreview("--- Undo Stack ---", state.undoPreview)
        drawHistoryPreview("--- Redo Stack ---", state.redoPreview)

        ImGui.end()
    }
}

/**
 * Applies the initial ImGui window position and size from layout config.
 */
private fun applyWindowDefaults(layout: ImGuiPanelLayout) {
    ImGui.setNextWindowPos(Vec2(layout.x, layout.y), Cond.FirstUseEver, Vec2())
    ImGui.setNextWindowSize(Vec2(layout.width, layout.height), Cond.FirstUseEver)
}

private fun drawPersistenceControls(state: TerrainEditorState) {
    ImGui.text("Persistence")
    ImGui.text("Path: ${state.terrainFilePath}")
    ImGui.text("File exists: ${formatBoolean(state.terrainFileExists)}")
    ImGui.text("Unsaved changes: ${formatBoolean(state.hasUnsavedChanges)}")
    with(dsl) {
        button("New Terrain") {
            state.createTerrainRequested = true
        }
    }
    ImGui.sameLine()
    with(dsl) {
        button("Save Terrain") {
            state.saveTerrainRequested = true
        }
    }
    ImGui.sameLine()
    ImGui.beginDisabled(state.terrainFilePath.isBlank())
    with(dsl) {
        button("Load Terrain") {
            state.loadTerrainRequested = true
        }
    }
    ImGui.endDisabled()
    if (state.persistenceMessage.isNotBlank()) {
        ImGui.text("Status: ${state.persistenceMessage}")
    }
}

private fun drawHistoryPreview(
    title: String,
    preview: List<TerrainEditPatchInfo>,
) {
    ImGui.text(title)
    if (preview.isEmpty()) {
        ImGui.text("empty")
        return
    }
    preview.forEach { patch ->
        ImGui.text(formatPatchInfo(patch))
    }
}

private fun formatPatchInfo(info: TerrainEditPatchInfo): String {
    val total = info.heightChanges + info.layerChanges
    return "${info.label} | height: ${info.heightChanges} | layers: ${info.layerChanges} | total: $total"
}

private fun formatBoolean(value: Boolean): String = if (value) "yes" else "no"

private fun formatHistoryMemory(bytes: Long): String =
    if (bytes < 1024L) {
        "$bytes B"
    } else {
        "%.1f KB".format(bytes / 1024f)
    }
