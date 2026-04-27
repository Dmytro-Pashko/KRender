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
    /** Material reference id used by persistence and future terrain material blending. */
    val materialId: String?,
    /** Editor/debug color used by this layer. */
    val color: TerrainLayerColorDescriptor,
    /** Whether this layer contributes to editor/debug visualization. */
    val visible: Boolean,
    /** Per-layer material UV tiling parameter. */
    val tiling: Float,
    /** Current authoring order index. */
    val index: Int,
)

/**
 * Describes one terrain material option exposed to the layer UI.
 */
data class TerrainMaterialOption(
    val id: String,
    val name: String,
    val albedoTexture: String,
    val fallbackColor: TerrainLayerColorDescriptor,
    val defaultTiling: Float,
)

enum class TerrainEditorInputFocus {
    Ui,
    Viewport,
}

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
    /** Determines whether keyboard/mouse input is currently routed to ImGui or terrain tools. */
    var inputFocus: TerrainEditorInputFocus = TerrainEditorInputFocus.Ui,
    /** Controls whether terrain is rendered in wireframe mode. */
    var wireframeEnabled: Boolean = false,
    /** Controls whether terrain uses generated layer color preview vertex colors. */
    var showLayerColorPreview: Boolean = true,
    /** Active terrain material preview mode. */
    var terrainPreviewMode: TerrainPreviewMode = TerrainPreviewMode.MaterialColor,
    /** Color preview blend mode used by terrain mesh generation. */
    var layerBlendMode: TerrainLayerBlendMode = TerrainLayerBlendMode.WeightedAverage,
    /** CPU-baked material texture preview resolution. */
    var materialPreviewResolution: Int = 512,
    /** Queues a CPU material preview bake. */
    var materialPreviewDirty: Boolean = true,
    /** Last CPU material preview bake status shown in the editor UI. */
    var materialPreviewMessage: String = "",
    /** Queues writing the current baked material preview to a PNG file. */
    var materialPreviewExportRequested: Boolean = false,
    /** Queues a terrain mesh rebuild after preview settings changed. */
    var previewSettingsChanged: Boolean = false,
    /** Last terrain preview status message shown in the control panel. */
    var previewMessage: String = "",
    /** Paint behavior for terrain layer weights when Paint Layer brush mode is active. */
    var layerPaintMode: TerrainLayerPaintMode = TerrainLayerPaintMode.Add,
    /** Current terrain layer list mirrored from runtime terrain data. */
    var layers: List<TerrainLayerOption> = emptyList(),
    /** Terrain material definitions available for layer assignment. */
    var terrainMaterials: List<TerrainMaterialOption> = emptyList(),
    /** Editable name mirrored from the selected terrain layer. */
    var selectedLayerName: String = "",
    /** Editable material id mirrored from the selected terrain layer. */
    var selectedLayerMaterialId: String = "",
    /** Selected material option index for the active terrain layer. */
    var selectedLayerMaterialIndex: Int = -1,
    /** Editable RGBA color mirrored from the selected terrain layer. */
    var selectedLayerColor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    /** Editable visibility flag mirrored from the selected terrain layer. */
    var selectedLayerVisible: Boolean = true,
    /** Editable tiling value mirrored from the selected terrain layer. */
    var selectedLayerTiling: Float = 1f,
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
    /** True when editor terrain data changed since the last save/reset point. */
    var hasUnsavedChanges: Boolean = false,
    /** Current terrain history revision used for dirty-state checks. */
    var currentHistoryRevision: Long = 0L,
    /** Last clean terrain history revision used for dirty-state checks. */
    var cleanHistoryRevision: Long = 0L,
    /** Relative local file path used by terrain save/load. */
    var terrainFilePath: String = "terrains/terrain.kterrain.json",
    /** Relative local PNG path used when exporting the baked material preview. */
    var materialPreviewExportPath: String = terrainMaterialPreviewExportPath(terrainFilePath),
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
    /** Queues renaming of the selected paint layer on the next update. */
    var renameLayerRequested: Boolean = false,
    /** Queues material id update of the selected paint layer on the next update. */
    var updateLayerMaterialRequested: Boolean = false,
    /** Queues color update of the selected paint layer on the next update. */
    var updateLayerColorRequested: Boolean = false,
    /** Queues visibility update of the selected paint layer on the next update. */
    var updateLayerVisibilityRequested: Boolean = false,
    /** Queues tiling update of the selected paint layer on the next update. */
    var updateLayerTilingRequested: Boolean = false,
    /** Queues moving the selected paint layer up in authoring order. */
    var moveLayerUpRequested: Boolean = false,
    /** Queues moving the selected paint layer down in authoring order. */
    var moveLayerDownRequested: Boolean = false,
    /** Last terrain layer status message shown in the layer panel. */
    var layerMessage: String = "",
    /** Last terrain material status or warning shown in the layer panel. */
    var materialMessage: String = "",
) {
    /**
     * Returns the currently selected generator option, if any.
     */
    val selectedGenerator: TerrainGeneratorOption?
        get() = generators.firstOrNull { it.id == selectedGeneratorId }
}

fun terrainMaterialPreviewExportPath(terrainFilePath: String): String {
    val leaf = terrainFilePath.substringAfterLast('/').substringAfterLast('\\')
    val terrainName = leaf.substringBeforeLast('.', leaf).trim().takeIf(String::isNotEmpty) ?: "terrain"
    return "terrains/${terrainName}_preview.png"
}
