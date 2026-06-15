package com.pashkd.krender

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.game.RuntimeScene

class Main(
    sceneName: String? = configuredSceneName(),
    scenePath: String? = configuredScenePath(),
    runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
    },
    editorToolLauncherFactory: (Logger) -> EditorToolLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
    },
) : GdxEngineApplication(
    initialScene = {
        val requestedScene = sceneName?.trim()?.takeIf(String::isNotBlank) ?: RUNTIME_SCENE
        when (requestedScene.lowercase()) {
            "runtime-scene" ->
                RuntimeScene(
                    scenePath = scenePath ?: throw missingProperty("krender.scene.path", "runtime-scene"),
                )

            else -> throw IllegalArgumentException(
                "Unknown krender.scene '$requestedScene'. Supported scenes in core: runtime-scene.",
            )
        }
    },
    runtimeWindowLauncherFactory = runtimeWindowLauncherFactory,
    editorToolLauncherFactory = editorToolLauncherFactory,
) {
    companion object {
        private const val RUNTIME_SCENE = "runtime-scene"

        fun configuredSceneName(): String? = System.getProperty("krender.scene")?.takeIf(String::isNotBlank)

        fun configuredScenePath(): String? = System.getProperty("krender.scene.path")?.takeIf(String::isNotBlank)

        private fun missingProperty(
            propertyName: String,
            sceneName: String,
        ): IllegalArgumentException =
            IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
    }
}
