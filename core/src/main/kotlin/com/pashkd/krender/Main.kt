package com.pashkd.krender

import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.game.AnimationViewerScene
import com.pashkd.krender.game.AssetBrowserScene
import com.pashkd.krender.game.ModelViewerScene
import com.pashkd.krender.game.RuntimeScene
import com.pashkd.krender.game.SceneEditorScene
import com.pashkd.krender.game.TerrainEditorScene
import com.pashkd.krender.game.WoolboySandboxScene

class Main(
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
        val selectedModel = modelPath?.let(AssetRef.Companion::model)
        when (requestedScene.lowercase()) {
            "asset-browser" -> AssetBrowserScene()

            "scene-editor" -> SceneEditorScene(
                scenePath = scenePath,
                initialSceneName = configuredSceneNameOverride(),
            )

            "runtime-scene" -> RuntimeScene(
                scenePath = scenePath ?: throw missingProperty("krender.scene.path", "runtime-scene"),
            )

            "model-viewer" -> ModelViewerScene(
                model = selectedModel ?: throw missingProperty("krender.model.path", "model-viewer"),
            )

            "animation-viewer" -> AnimationViewerScene(
                model = selectedModel ?: throw missingProperty("krender.model.path", "animation-viewer"),
            )

            "terrain-editor", "terrain-generator" -> TerrainEditorScene(
                terrainFilePath = configuredTerrainFilePath()
                    ?: throw missingProperty("krender.terrain.path", requestedScene),
            )

            "woolboy_sandbox_scene" -> WoolboySandboxScene()

            else -> throw IllegalArgumentException(
                "Unknown krender.scene '$requestedScene'. Supported scenes: asset-browser, scene-editor, runtime-scene, model-viewer, animation-viewer, terrain-editor, woolboy_sandbox_scene.",
            )
        }
    },
    runtimeWindowLauncherFactory = runtimeWindowLauncherFactory,
    editorToolLauncherFactory = editorToolLauncherFactory,
) {
    companion object {
        private const val ASSET_BROWSER_SCENE = "woolboy_sandbox_scene"

        fun configuredSceneName(): String? = System.getProperty("krender.scene")?.takeIf(String::isNotBlank)

        fun configuredModelPath(): String? =
            System.getProperty("krender.model.path")?.takeIf(String::isNotBlank)
                ?: System.getProperty("krender.model")?.takeIf(String::isNotBlank)

        fun configuredTerrainFilePath(): String? = System.getProperty("krender.terrain.path")?.takeIf(String::isNotBlank)

        fun configuredScenePath(): String? = System.getProperty("krender.scene.path")?.takeIf(String::isNotBlank)

        fun configuredSceneNameOverride(): String? =
            System.getProperty("krender.scene.name")?.takeIf(String::isNotBlank)

        private fun missingProperty(propertyName: String, sceneName: String): IllegalArgumentException =
            IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
    }
}
