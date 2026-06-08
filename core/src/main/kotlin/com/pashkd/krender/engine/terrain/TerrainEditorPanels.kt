package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.ProfilerService
import com.pashkd.krender.engine.api.RuntimeStatsService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorButton
import imgui.api.colorEdit4
import imgui.api.slider
import imgui.dsl
import java.nio.charset.StandardCharsets

/**
 * Defines the stable JSON ids used by the Terrain Editor ImGui panels.
 */
object TerrainEditorPanelIds {
    const val Statistics = "sceneStatistics"
    const val Terrain = "terrain"
    const val Brush = "brush"
    const val Layers = "layers"
    const val Control = "control"
    const val Logs = "runtimeLogs"
}

/**
 * Holds the asset path and fallback layouts for Terrain Editor panels.
 */
object TerrainEditorUiLayoutDefaults {
    const val assetPath = "ui/terrain_editor_layout.json"

    val config = ImGuiLayoutConfig(
        panels = mapOf(
            TerrainEditorPanelIds.Statistics to ImGuiPanelLayout(
                title = "Scene Statistics",
                x = 16f,
                y = 16f,
                width = 320f,
                height = 360f,
            ),
            TerrainEditorPanelIds.Terrain to ImGuiPanelLayout(
                title = "Terrain",
                x = 16f,
                y = 392f,
                width = 320f,
                height = 500f,
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
            TerrainEditorPanelIds.Logs to ImGuiPanelLayout(
                title = "Runtime Logs",
                x = 688f,
                y = 16f,
                width = 560f,
                height = 632f,
            ),
        ),
    )
}

/**
 * Presents runtime statistics and profiler timings as a regular scene panel.
 */
class TerrainEditorStatisticsPanel(
    private val runtimeStats: RuntimeStatsService,
    private val profiler: ProfilerService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /**
     * Draws the scene statistics window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TerrainEditorPanelIds.Statistics)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, TerrainEditorPanelIds.Statistics))
        eventLogger.observe(TerrainEditorPanelIds.Statistics, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        runtimeStats.metrics.forEach { metric ->
            ImGui.text("${metric.label}: ${metric.value}")
        }

        runtimeStats.lastCompletedFrame?.let { frame ->
            ImGui.separator()
            ImGui.text("Frame timing")
            ImGui.text("Delta: ${"%.2f".format(frame.deltaSeconds * 1000f)} ms")
            ImGui.text("Fixed updates: ${frame.fixedUpdates}")
        }

        profiler.lastCompletedFrame?.timings?.takeIf(List<*>::isNotEmpty)?.let { timings ->
            ImGui.separator()
            ImGui.text("Profiler")
            timings.forEach { timing ->
                ImGui.text("${timing.name}: ${"%.2f".format(timing.millis)} ms")
            }
        }

        ImGui.end()
    }
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
        val expanded = ImGui.begin(imguiWindowName(layout.title, TerrainEditorPanelIds.Terrain))
        eventLogger.observe(TerrainEditorPanelIds.Terrain, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

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
        val expanded = ImGui.begin(imguiWindowName(layout.title, TerrainEditorPanelIds.Brush))
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
        ImGui.sameLine()
        brushModeButton("Paint", TerrainBrushMode.PaintLayer)

        ImGui.separator()
        ImGui.text("Status")
        ImGui.text("Mode: %s", state.brushMode.name)
        ImGui.text("Hovered: %s", state.hoveredTerrainPosition)

        if (state.brushMode == TerrainBrushMode.Flatten) {
            ImGui.text("Flatten uses the first sampled terrain height as the target.")
        }
        if (state.brushMode == TerrainBrushMode.PaintLayer) {
            ImGui.text("Selected layer: %s", state.selectedLayerId?.toString() ?: "none")
            ImGui.text("Hold Alt to erase")
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
    private val selectedLayerNameBuffer = ByteArray(TEXT_INPUT_BUFFER_SIZE)
    private var bufferedLayerId: Int? = null
    private var nameInputActive: Boolean = false

    /**
     * Draws the layer management window using the configured default layout.
     */
    override fun draw() {
        syncTextBuffers()
        val layout = layoutConfig.panels.getValue(TerrainEditorPanelIds.Layers)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, TerrainEditorPanelIds.Layers))
        eventLogger.observe(TerrainEditorPanelIds.Layers, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Layers list")
        ImGui.text("Layer count: ${state.layers.size} / ${TerrainLayerLimits.MaxLayers}")
        state.layers.forEach { layer ->
            if (ImGui.smallButton("${if (layer.visible) "[x]" else "[ ]"}##visible_${layer.id}")) {
                state.selectedLayerId = layer.id
                state.selectedLayerVisible = !layer.visible
                state.updateLayerVisibilityRequested = true
            }
            ImGui.sameLine()
            if (ImGui.selectable("${formatLayerRow(layer)}##layer_${layer.id}", layer.id == state.selectedLayerId)) {
                state.selectedLayerId = layer.id
            }
            ImGui.sameLine()
            colorButton(
                "Color##color_${layer.id}",
                layer.color.r,
                layer.color.g,
                layer.color.b,
                layer.color.a,
            )
        }

        ImGui.separator()
        ImGui.text("Selected layer")
        ImGui.text("Paint: %s", if (state.brushMode == TerrainBrushMode.PaintLayer) "enabled" else "disabled")
        ImGui.text("Active layer: %s", state.selectedLayerId?.toString() ?: "none")
        if (state.selectedLayerId != null && !state.selectedLayerVisible) {
            ImGui.text("Selected layer is hidden")
        }
        val selectedLayer = state.layers.firstOrNull { it.id == state.selectedLayerId }
        ImGui.beginDisabled(state.selectedLayerId == null)
        if (ImGui.inputText("Name", selectedLayerNameBuffer)) {
            state.selectedLayerName = readBuffer(selectedLayerNameBuffer)
            state.renameLayerRequested = true
        }
        nameInputActive = ImGui.isItemActive
        if (ImGui.checkbox("Visible", state::selectedLayerVisible)) {
            state.updateLayerVisibilityRequested = true
        }
        ImGui.endDisabled()

        ImGui.text("Status: ${state.layerMessage.ifBlank { "none" }}")
        drawLayerActionRow(selectedLayer)

        ImGui.beginDisabled(state.selectedLayerId == null)
        drawMaterialSelector()
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

    private fun formatLayerRow(layer: TerrainLayerOption): String =
        "#${layer.index + 1} ${layer.name}:RGBA(${formatColorChannel(layer.color.r)},${formatColorChannel(layer.color.g)},${formatColorChannel(layer.color.b)},${formatColorChannel(layer.color.a)})|T:${formatTiling(layer.tiling)}|Color"

    private fun drawMaterialSelector() {
        ImGui.separator()
        ImGui.text("Material section:")
        ImGui.text("[Material]")
        if (state.terrainMaterials.isEmpty()) {
            ImGui.text("No terrain materials loaded")
            return
        }

        val selectedMaterial = state.terrainMaterials.firstOrNull { it.id == state.selectedLayerMaterialId }
        ImGui.text("Name: %s", selectedMaterial?.name ?: "Missing")
        ImGui.text("Id: %s", state.selectedLayerMaterialId.ifBlank { "none" })
        ImGui.text("Texture: %s", selectedMaterial?.albedoTexture ?: "none")
        drawMaterialCombo(selectedMaterial)
        if (state.materialMessage.isNotBlank()) {
            ImGui.text("Material status: ${state.materialMessage}")
        }
    }

    private fun drawLayerActionRow(selectedLayer: TerrainLayerOption?) {
        ImGui.beginDisabled(state.layers.size >= TerrainLayerLimits.MaxLayers)
        with(dsl) {
            button("Add") {
                state.addLayerRequested = true
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()

        ImGui.beginDisabled(state.selectedLayerId == null)
        with(dsl) {
            button("Remove") {
                state.removeLayerRequested = true
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()

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
    }

    private fun drawMaterialCombo(selectedMaterial: TerrainMaterialOption?) {
        val preview = selectedMaterial?.name ?: "Select material"
        if (!ImGui.beginCombo("Material##terrain_material_combo", preview)) {
            return
        }

        state.terrainMaterials.forEachIndexed { index, material ->
            if (ImGui.selectable("${material.name}##terrain_material_${material.id}", index == state.selectedLayerMaterialIndex)) {
                state.selectedLayerMaterialIndex = index
                state.selectedLayerMaterialId = material.id
                state.selectedLayerColor = floatArrayOf(
                    material.fallbackColor.r,
                    material.fallbackColor.g,
                    material.fallbackColor.b,
                    material.fallbackColor.a,
                )
                state.selectedLayerTiling = material.defaultTiling
                state.updateLayerMaterialRequested = true
            }
        }
        ImGui.endCombo()
    }

    private fun formatColorChannel(value: Float): String =
        "%.2f".format(value)

    private fun formatTiling(value: Float): String =
        "%.2f".format(value)

    private fun syncTextBuffers() {
        val selectedLayerId = state.selectedLayerId
        if (bufferedLayerId != selectedLayerId) {
            bufferedLayerId = selectedLayerId
            writeBuffer(selectedLayerNameBuffer, state.selectedLayerName)
            return
        }

        if (!nameInputActive && readBuffer(selectedLayerNameBuffer) != state.selectedLayerName) {
            writeBuffer(selectedLayerNameBuffer, state.selectedLayerName)
        }
    }

    private fun readBuffer(buffer: ByteArray): String {
        val length = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
        return String(buffer, 0, length, StandardCharsets.UTF_8)
    }

    private fun writeBuffer(buffer: ByteArray, value: String) {
        buffer.fill(0)
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val length = minOf(bytes.size, buffer.size - 1)
        bytes.copyInto(buffer, endIndex = length)
    }

    companion object {
        private const val TEXT_INPUT_BUFFER_SIZE = 128
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
        val expanded = ImGui.begin(imguiWindowName(layout.title, TerrainEditorPanelIds.Control))
        eventLogger.observe(TerrainEditorPanelIds.Control, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Viewport")
        ImGui.text("Input focus: ${formatInputFocus(state.inputFocus)}")
        ImGui.text("Tab toggles UI/viewport focus")

        ImGui.separator()
        ImGui.text("[Preview Mode]")
        previewModeButton("Layer Color", TerrainPreviewMode.LayerColor)
        previewModeButton("Material Color", TerrainPreviewMode.MaterialColor)
        previewModeButton("Material Texture", TerrainPreviewMode.MaterialTexture)
        previewModeButton("Selected Layer Mask", TerrainPreviewMode.SelectedLayerMask)
        ImGui.text("[Blend Mode]")
        blendModeButton("Weighted Average", TerrainLayerBlendMode.WeightedAverage)
        blendModeButton("Ordered Alpha", TerrainLayerBlendMode.OrderedAlpha)
        blendModeButton("Max Weight", TerrainLayerBlendMode.MaxWeight)
        ImGui.text("Texture preview resolution")
        previewResolutionButton(256)
        ImGui.sameLine()
        previewResolutionButton(512)
        ImGui.sameLine()
        previewResolutionButton(1024)
        ImGui.sameLine()
        previewResolutionButton(4096)
        ImGui.sameLine()
        previewResolutionButton(8192)
        ImGui.text("Preview mode: ${formatPreviewMode(state.terrainPreviewMode)}")
        ImGui.text("Blend mode: ${formatBlendMode(state.layerBlendMode)}")
        ImGui.text("Preview resolution: ${state.materialPreviewResolution}")
        ImGui.text("Last bake: ${formatPreviewTiming(state.lastPreviewBakeTimeMs)}")
        ImGui.text("Average bake: ${formatPreviewTiming(state.averagePreviewBakeTimeMs)} (${state.previewBakeCount})")
        ImGui.text("Cache size: ${state.previewTextureCacheSize}")
        ImGui.text("Cache memory: ${formatByteCount(state.previewTextureCacheMemoryBytes)}")
        ImGui.text("Export path: ${state.materialPreviewExportPath}")
        with(dsl) {
            button("Save preview PNG") {
                state.materialPreviewExportRequested = true
            }
        }
        if (state.terrainPreviewMode == TerrainPreviewMode.MaterialTexture) {
            ImGui.text("Material Texture preview is CPU baked editor preview")
        }
        if (state.terrainPreviewMode == TerrainPreviewMode.SelectedLayerMask) {
            ImGui.text("Mask: ${state.selectedLayerMaskMessage.ifBlank { "No layer selected" }}")
        }
        if (state.materialPreviewMessage.isNotBlank()) {
            ImGui.text("Material preview status: ${state.materialPreviewMessage}")
        }
        if (state.previewMessage.isNotBlank()) {
            ImGui.text("Preview status: ${state.previewMessage}")
        }

        ImGui.separator()
        ImGui.text("Controls")
        ImGui.bulletText("F1 - Raise")
        ImGui.bulletText("F2 - Lower")
        ImGui.bulletText("F3 - Flatten")
        ImGui.bulletText("F4 - Smooth")
        ImGui.bulletText("F5 - Paint selected layer")
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
        ImGui.text("Undo Stack: ${state.undoCount}")
        ImGui.text("Redo Stack: ${state.redoCount}")
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

        ImGui.end()
    }

    private fun blendModeButton(label: String, mode: TerrainLayerBlendMode) {
        if (ImGui.selectable("$label##terrain_blend_$mode", state.layerBlendMode == mode)) {
            state.layerBlendMode = mode
            state.previewSettingsChanged = true
            state.materialPreviewDirty = true
        }
    }

    private fun previewModeButton(label: String, mode: TerrainPreviewMode) {
        if (ImGui.selectable("$label##terrain_preview_$mode", state.terrainPreviewMode == mode)) {
            state.terrainPreviewMode = mode
            state.showLayerColorPreview = mode == TerrainPreviewMode.LayerColor || mode == TerrainPreviewMode.MaterialColor
            state.previewSettingsChanged = true
            state.materialPreviewDirty = true
        }
    }

    private fun previewResolutionButton(resolution: Int) {
        val selected = state.materialPreviewResolution == resolution
        if (selected) {
            ImGui.beginDisabled(true)
        }
        val label = formatPreviewResolution(resolution)
        val clicked = ImGui.smallButton("${if (selected) "[$label]" else label}##terrain_preview_resolution_$resolution")
        if (selected) {
            ImGui.endDisabled()
        }
        if (clicked && !selected) {
            state.materialPreviewResolution = resolution
            state.previewSettingsChanged = true
            state.materialPreviewDirty = true
        }
    }
}

/**
 * Applies the initial ImGui window position and size from layout config.
 */
private fun applyWindowDefaults(layout: ImGuiPanelLayout) {
    ImGui.setNextWindowPos(Vec2(layout.x, layout.y), Cond.FirstUseEver, Vec2())
    ImGui.setNextWindowSize(Vec2(layout.width, layout.height), Cond.FirstUseEver)
}

private fun imguiWindowName(title: String, id: String): String = "$title###$id"

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

private fun formatBoolean(value: Boolean): String = if (value) "yes" else "no"

private fun formatPaintMode(mode: TerrainLayerPaintMode): String =
    when (mode) {
        TerrainLayerPaintMode.Add -> "Add"
        TerrainLayerPaintMode.Erase -> "Erase"
    }

private fun formatPreviewMode(mode: TerrainPreviewMode): String =
    when (mode) {
        TerrainPreviewMode.LayerColor -> "Layer Color"
        TerrainPreviewMode.MaterialColor -> "Material Color"
        TerrainPreviewMode.MaterialTexture -> "Material Texture"
        TerrainPreviewMode.SelectedLayerMask -> "Selected Layer Mask"
    }

private fun formatInputFocus(focus: TerrainEditorInputFocus): String =
    when (focus) {
        TerrainEditorInputFocus.Ui -> "UI"
        TerrainEditorInputFocus.Viewport -> "Viewport"
    }

private fun formatPreviewResolution(resolution: Int): String =
    if (resolution >= 1024 && resolution % 1024 == 0) {
        "${resolution / 1024}K"
    } else {
        resolution.toString()
    }

private fun formatBlendMode(mode: TerrainLayerBlendMode): String =
    when (mode) {
        TerrainLayerBlendMode.WeightedAverage -> "Weighted Average"
        TerrainLayerBlendMode.OrderedAlpha -> "Ordered Alpha"
        TerrainLayerBlendMode.MaxWeight -> "Max Weight"
    }

private fun formatHistoryMemory(bytes: Long): String =
    if (bytes < 1024L) {
        "$bytes B"
    } else {
        "%.1f KB".format(bytes / 1024f)
    }

private fun formatPreviewTiming(milliseconds: Float): String =
    "%.2f ms".format(milliseconds)

private fun formatByteCount(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "%.2f MB".format(bytes / (1024f * 1024f))
    }
