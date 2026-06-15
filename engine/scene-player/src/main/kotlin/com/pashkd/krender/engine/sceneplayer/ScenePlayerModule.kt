package com.pashkd.krender.engine.sceneplayer

import com.pashkd.krender.engine.api.Scene

object ScenePlayerModule {
    @JvmStatic
    fun createScene(
        sceneName: String,
        scenePath: String?,
    ): Scene? =
        when (sceneName.lowercase()) {
            "scene-player", "scene-viewer", "runtime-scene" ->
                ScenePlayerScene(
                    scenePath = scenePath ?: throw missingProperty("krender.scene.path", sceneName),
                )

            else -> null
        }

    private fun missingProperty(
        propertyName: String,
        sceneName: String,
    ): IllegalArgumentException = IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
}
