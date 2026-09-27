package com.pashkd.krender.engine.tools.materialeditor

import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor

data class MaterialDraft(
    var originalId: String?,
    var id: String,
    var name: String,
    var albedoTexture: String,
    var fallbackColor: TerrainLayerColorDescriptor,
    var defaultTiling: Float,
)

class MaterialEditorState(val path: String) {
    val materials = mutableListOf<MaterialDraft>()
    var selectedIndex = 0
    var status = ""
    var dirty = false
    var confirmReload = false
    var confirmExit = false
    var texturePaths: List<String> = emptyList()
    var loadedIds: Set<String> = emptySet()
    var previewError: String? = null
    var previewLoading = false
}
