package com.pashkd.krender.engine.terrain

/**
 * Describes one terrain generator option exposed to the editor UI.
 */
data class TerrainGeneratorOption(
    /** Stable generator id used by the runtime systems. */
    val id: String,
    /** Human-readable generator label shown in ImGui. */
    val label: String,
)

/**
 * Describes one selectable terrain paint layer in the editor UI.
 */
data class TerrainLayerOption(
    /** Stable terrain-layer id. */
    val id: Int,
    /** Human-readable layer name. */
    val name: String,
)

/**
 * Holds mutable state shared between Terrain Editor panels and systems.
 */
data class TerrainEditorState(
    /** Available terrain generators exposed to the UI. */
    val generators: List<TerrainGeneratorOption> = emptyList(),
    /** Currently selected generator id used on regeneration. */
    var selectedGeneratorId: String? = null,
    /** Active brush radius in terrain units. */
    var brushRadius: Float = 3f,
    /** Active brush strength multiplier. */
    var brushStrength: Float = 4f,
    /** Active brush falloff exponent. */
    var brushFalloff: Float = 1f,
    /** Active brush operation mode. */
    var brushMode: TerrainBrushMode = TerrainBrushMode.Raise,
    /** Active terrain layer id used by paint mode. */
    var selectedLayerId: Int? = null,
    /** Requested terrain heightfield resolution. */
    var terrainResolution: Int = 128,
    /** Requested vertex spacing for regenerated terrain. */
    var vertexSpacing: Float = 1f,
    /** Controls whether the world axes helper is rendered. */
    var showAxes: Boolean = false,
    /** Controls whether terrain is rendered in wireframe mode. */
    var wireframeEnabled: Boolean = false,
    /** Current terrain layer list mirrored from runtime terrain data. */
    var layers: List<TerrainLayerOption> = emptyList(),
    /** Current terrain mesh vertex count. */
    var vertices: Int = 0,
    /** Current terrain mesh triangle count. */
    var triangles: Int = 0,
    /** Current terrain width/height label for the debug panel. */
    var terrainSize: String = "0 x 0",
    /** Current hovered terrain world position label for the debug panel. */
    var hoveredTerrainPosition: String = "none",
    /** True when an undo patch is available. */
    var canUndo: Boolean = false,
    /** True when a redo patch is available. */
    var canRedo: Boolean = false,
    /** Label for the next undo action, if available. */
    var undoLabel: String? = null,
    /** Label for the next redo action, if available. */
    var redoLabel: String? = null,
    /** Queues a terrain regeneration on the next update. */
    var regenerateRequested: Boolean = false,
    /** Queues terrain edit undo on the next update. */
    var undoRequested: Boolean = false,
    /** Queues terrain edit redo on the next update. */
    var redoRequested: Boolean = false,
    /** Queues creation of a new paint layer on the next update. */
    var addLayerRequested: Boolean = false,
    /** Queues removal of the selected paint layer on the next update. */
    var removeLayerRequested: Boolean = false,
) {
    /**
     * Returns the currently selected generator option, if any.
     */
    val selectedGenerator: TerrainGeneratorOption?
        get() = generators.firstOrNull { it.id == selectedGeneratorId }
}
