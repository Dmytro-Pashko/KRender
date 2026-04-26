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
    /** Number of undo steps currently retained. */
    var undoCount: Int = 0,
    /** Number of redo steps currently retained. */
    var redoCount: Int = 0,
    /** Approximate memory used by terrain edit history. */
    var historyMemoryBytes: Long = 0L,
    /** Limited newest-first undo stack preview for the controls panel. */
    var undoPreview: List<TerrainEditPatchInfo> = emptyList(),
    /** Limited newest-first redo stack preview for the controls panel. */
    var redoPreview: List<TerrainEditPatchInfo> = emptyList(),
    /** True when editor terrain data changed since the last save/reset point. */
    var hasUnsavedChanges: Boolean = false,
    /** Current terrain history revision used for dirty-state checks. */
    var currentHistoryRevision: Long = 0L,
    /** Last clean terrain history revision used for dirty-state checks. */
    var cleanHistoryRevision: Long = 0L,
    /** Relative local file path used by terrain save/load. */
    var terrainFilePath: String = "terrains/terrain.kterrain.json",
    /** Terrain asset display name written into terrain save files. */
    var terrainSaveName: String = "terrain",
    /** True when the configured terrain file currently exists on disk. */
    var terrainFileExists: Boolean = false,
    /** Queues creation of a new editable terrain on the next update. */
    var createTerrainRequested: Boolean = false,
    /** Queues terrain save on the next update. */
    var saveTerrainRequested: Boolean = false,
    /** Queues terrain load on the next update. */
    var loadTerrainRequested: Boolean = false,
    /** Last terrain persistence status message shown in the editor UI. */
    var persistenceMessage: String = "",
    /** True when the last terrain persistence operation failed. */
    var persistenceError: Boolean = false,
    /** Queues a terrain regeneration on the next update. */
    var regenerateRequested: Boolean = false,
    /** Queues terrain edit undo on the next update. */
    var undoRequested: Boolean = false,
    /** Queues terrain edit redo on the next update. */
    var redoRequested: Boolean = false,
    /** Queues clearing terrain edit history on the next update. */
    var clearHistoryRequested: Boolean = false,
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
