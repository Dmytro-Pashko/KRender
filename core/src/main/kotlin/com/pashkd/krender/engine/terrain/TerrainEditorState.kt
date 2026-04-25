package com.pashkd.krender.engine.terrain

data class TerrainLayerOption(
    val id: Int,
    val name: String,
)

data class TerrainEditorState(
    var brushRadius: Float = 3f,
    var brushStrength: Float = 4f,
    var brushFalloff: Float = 1f,
    var brushMode: TerrainBrushMode = TerrainBrushMode.Raise,
    var selectedLayerId: Int? = null,
    var terrainResolution: Int = 128,
    var vertexSpacing: Float = 1f,
    var showAxes: Boolean = false,
    var wireframeEnabled: Boolean = false,
    var layers: List<TerrainLayerOption> = emptyList(),
    var vertices: Int = 0,
    var triangles: Int = 0,
    var regenerateRequested: Boolean = false,
    var addLayerRequested: Boolean = false,
    var removeLayerRequested: Boolean = false,
)
