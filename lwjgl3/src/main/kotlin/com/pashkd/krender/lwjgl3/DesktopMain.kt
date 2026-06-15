package com.pashkd.krender.lwjgl3

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.tools.ToolsModule
import com.pashkd.krender.game.RuntimeScene

class DesktopMain(
    sceneName: String? = configuredSceneName(),
    modelPath: String? = configuredModelPath(),
    scenePath: String? = configuredScenePath(),
    runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
    },
    editorToolLauncherFactory: (Logger) -> EditorToolLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
    },
) : GdxEngineApplication(
    initialScene = {
        val requestedScene = sceneName?.trim()?.takeIf(String::isNotBlank) ?: ASSET_BROWSER_SCENE
        ToolsModule.createScene(
            sceneName = requestedScene,
            modelPath = modelPath,
            terrainPath = configuredTerrainFilePath(),
            scenePath = scenePath,
            sceneNameOverride = configuredSceneNameOverride(),
            uiScenePath = configuredUiScenePath(),
        ) ?: when (requestedScene.lowercase()) {
            "runtime-scene" ->
                RuntimeScene(
                    scenePath = scenePath ?: throw missingProperty("krender.scene.path", "runtime-scene"),
                )

            else -> throw IllegalArgumentException(
                "Unknown krender.scene '$requestedScene'. Supported scenes: asset-browser, scene-editor, runtime-scene, model-viewer, animation-viewer, terrain-editor, ui-composer.",
            )
        }
    },
    runtimeWindowLauncherFactory = runtimeWindowLauncherFactory,
    editorToolLauncherFactory = editorToolLauncherFactory,
) {
    companion object {
        private const val ASSET_BROWSER_SCENE = "asset-browser"

        fun configuredSceneName(): String? = System.getProperty("krender.scene")?.takeIf(String::isNotBlank)

        fun configuredModelPath(): String? =
            System.getProperty("krender.model.path")?.takeIf(String::isNotBlank)
                ?: System.getProperty("krender.model")?.takeIf(String::isNotBlank)

        fun configuredTerrainFilePath(): String? =
            System.getProperty("krender.terrain.path")?.takeIf(String::isNotBlank)

        fun configuredUiScenePath(): String? = System.getProperty("krender.ui.scene.path")?.takeIf(String::isNotBlank)

        fun configuredScenePath(): String? = System.getProperty("krender.scene.path")?.takeIf(String::isNotBlank)

        fun configuredSceneNameOverride(): String? =
            System.getProperty("krender.scene.name")?.takeIf(String::isNotBlank)

        private fun missingProperty(
            propertyName: String,
            sceneName: String,
        ): IllegalArgumentException =
            IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
    }
}
