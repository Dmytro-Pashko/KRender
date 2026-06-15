package com.pashkd.krender.engine.tools

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.tools.modelviewer.ModelViewerScene

object ToolsModule {
    @JvmStatic
    fun createScene(
        sceneName: String,
        modelPath: String?,
    ): Scene? =
        when (sceneName.lowercase()) {
            "model-viewer" -> ModelViewerScene(modelPath ?: throw missingProperty("krender.model.path", sceneName))
            else -> null
        }

    private fun missingProperty(
        propertyName: String,
        sceneName: String,
    ): IllegalArgumentException =
        IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
}
