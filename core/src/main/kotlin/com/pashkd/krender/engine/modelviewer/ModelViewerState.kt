package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.ModelAsset

/**
 * Holds mutable scene state shared between ModelViewer panels and systems.
 */
data class ModelViewerState(
    val availableModels: List<AssetRef<ModelAsset>>,
    var selectedModelIndex: Int = 0,
    var loadedModel: AssetRef<ModelAsset>? = null,
    val modelScale: Float = 1f,
    var wireframeEnabled: Boolean = false,
    var loadSelectedModelRequested: Boolean = false,
    var exitRequested: Boolean = false,
    var assetLoaded: Boolean = false,
    var assetProgress: Float = 1f,
    var loadingStatus: String = "No model loaded",
    var errorMessage: String? = null,
    var cameraPosition: String = "0.00, 0.00, 0.00",
    var triangleCount: Int? = null,
) {
    /**
     * Returns the model currently highlighted in the list panel.
     */
    val selectedModel: AssetRef<ModelAsset>?
        get() = availableModels.getOrNull(selectedModelIndex)

    /**
     * Exposes the selected model path in a UI-friendly format.
     */
    val selectedModelPath: String
        get() = selectedModel?.path ?: "none"

    /**
     * Exposes the loaded model path in a UI-friendly format.
     */
    val loadedModelPath: String
        get() = loadedModel?.path ?: "none"

    /**
     * Returns whether the active model asset is still loading.
     */
    val isLoadingModel: Boolean
        get() = loadedModel != null && !assetLoaded
}
