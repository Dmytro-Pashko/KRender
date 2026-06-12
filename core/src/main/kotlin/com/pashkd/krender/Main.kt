package com.pashkd.krender

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.game.*

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

            "ui-composer" -> UiComposerScene(
                uiScenePath = configuredUiScenePath()
                    ?: throw missingProperty("krender.ui.scene.path", requestedScene),
            )

            "woolboy_sandbox_scene" -> WoolboySandboxScene()

            else -> throw IllegalArgumentException(
                "Unknown krender.scene '$requestedScene'. Supported scenes: asset-browser, scene-editor, runtime-scene, model-viewer, animation-viewer, terrain-editor, ui-composer, woolboy_sandbox_scene.",
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

        /**
         * Reads the `.krui` UiScene path passed to the temporary UI Composer route.
         *
         * This property belongs to editor/tool routing. It lets Asset Browser open UiScene assets
         * in the placeholder composer without adding preview rendering, editing, Skin authoring,
         * drag/drop support, save workflows, or asset-id based references.
         */
        fun configuredUiScenePath(): String? = System.getProperty("krender.ui.scene.path")?.takeIf(String::isNotBlank)

        fun configuredScenePath(): String? = System.getProperty("krender.scene.path")?.takeIf(String::isNotBlank)

        fun configuredSceneNameOverride(): String? =
            System.getProperty("krender.scene.name")?.takeIf(String::isNotBlank)

        private fun missingProperty(propertyName: String, sceneName: String): IllegalArgumentException =
            IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
    }
}
