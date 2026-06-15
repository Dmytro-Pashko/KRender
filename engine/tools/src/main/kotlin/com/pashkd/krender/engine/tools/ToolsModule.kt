package com.pashkd.krender.engine.tools

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.tools.animationviewer.AnimationViewerScene
import com.pashkd.krender.engine.tools.modelviewer.ModelViewerScene

object ToolsModule {
    @JvmStatic
    fun createScene(
        sceneName: String,
        modelPath: String?,
        terrainPath: String? = null,
    ): Scene? =
        when (sceneName.lowercase()) {
            "model-viewer" -> ModelViewerScene(modelPath ?: throw missingProperty("krender.model.path", sceneName))
            "animation-viewer" -> AnimationViewerScene(modelPath ?: throw missingProperty("krender.model.path", sceneName))
            "terrain-editor", "terrain-generator" ->
                com.pashkd.krender.engine.tools.terraineditor.TerrainEditorScene(
                    terrainPath ?: throw missingProperty("krender.terrain.path", sceneName),
                )
            else -> null
        }

    private fun missingProperty(
        propertyName: String,
        sceneName: String,
    ): IllegalArgumentException =
        IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
}
