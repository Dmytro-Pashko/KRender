package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.SliderFlag
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
                height = 360f,
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
                height = 260f,
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

        ImGui.text("Terrain layers")
        state.layers.forEach { layer ->
            if (ImGui.selectable(layer.name, layer.id == state.selectedLayerId)) {
                state.selectedLayerId = layer.id
            }
        }

        ImGui.separator()
        ImGui.text("Selection")
        ImGui.text("Active layer: %s", state.selectedLayerId?.toString() ?: "none")
        with(dsl) {
            button("Paint selected layer") {
                state.brushMode = TerrainBrushMode.PaintLayer
            }
        }

        ImGui.separator()
        ImGui.text("Actions")
        with(dsl) {
            button("Add layer") {
                state.addLayerRequested = true
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Remove layer") {
                state.removeLayerRequested = true
            }
        }

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
        ImGui.text("History")
        ImGui.text("Undo: %s (%d)", state.undoLabel ?: "none", state.undoCount)
        ImGui.text("Redo: %s (%d)", state.redoLabel ?: "none", state.redoCount)
        ImGui.text("Memory: %s", formatHistoryMemory(state.historyMemoryBytes))
        ImGui.text("Unsaved changes: %s", if (state.hasUnsavedChanges) "yes" else "no")
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
        ImGui.text("--- Undo Stack ---")
        drawHistoryPreview(state.undoPreview)
        ImGui.text("--- Redo Stack ---")
        drawHistoryPreview(state.redoPreview)

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

private fun drawHistoryPreview(preview: List<TerrainEditPatchInfo>) {
    if (preview.isEmpty()) {
        ImGui.text("empty")
        return
    }
    preview.forEach { patch ->
        val samples = patch.heightChanges + patch.layerChanges
        ImGui.text("[%s - %d samples]", patch.label, samples)
    }
}

private fun formatHistoryMemory(bytes: Long): String =
    if (bytes < 1024L) {
        "$bytes B"
    } else {
        "%.1f KB".format(bytes / 1024f)
    }
