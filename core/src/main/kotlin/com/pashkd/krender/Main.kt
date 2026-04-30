package com.pashkd.krender

import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.game.AssetBrowserScene
import com.pashkd.krender.game.ModelViewerScene
import com.pashkd.krender.game.RuntimeScene
import com.pashkd.krender.game.SceneEditorScene
import com.pashkd.krender.game.TerrainEditorScene
import java.io.File

class Main(
    sceneName: String = defaultScene(),
    modelPath: String? = defaultModelPath(),
    runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
    },
    editorToolLauncherFactory: (Logger) -> EditorToolLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
    },
) : GdxEngineApplication(
    initialScene = {
        val selectedModel = modelPath?.let(AssetRef.Companion::model)
        when (sceneName.lowercase()) {
            "asset-browser" -> AssetBrowserScene()

            "scene-editor" -> SceneEditorScene(
                scenePath = defaultScenePath(),
                initialSceneName = defaultSceneName(),
                initialModelPlacementPath = defaultSceneModelPath(),
            )

            "runtime-scene" -> RuntimeScene(scenePath = defaultScenePath())

            "model-viewer" -> ModelViewerScene(
                model = selectedModel,
                availableModels = discoverModelPaths(modelPath).map(AssetRef.Companion::model),
            )

            "terrain-editor", "terrain-generator" -> TerrainEditorScene(
                terrainResolution = defaultTerrainResolution(),
                vertexSpacing = defaultTerrainSpacing(),
                terrainFilePath = defaultTerrainFilePath(),
            )

            else -> {
                System.err.println("Unknown scene '$sceneName', defaulting to model viewer.")
                ModelViewerScene(
                    model = selectedModel,
                    availableModels = discoverModelPaths(modelPath).map(AssetRef.Companion::model),
                )

            }
        }
    },
    runtimeWindowLauncherFactory = runtimeWindowLauncherFactory,
    editorToolLauncherFactory = editorToolLauncherFactory,
) {
    companion object {
        fun defaultScene(): String = System.getProperty("krender.scene", "asset-browser")
        fun defaultModelPath(): String? =
            System.getProperty("krender.model.path")?.takeIf(String::isNotBlank)
                ?: System.getProperty("krender.model")?.takeIf(String::isNotBlank)
        fun defaultTerrainResolution(): Int = System.getProperty("krender.terrain.size", "128").toIntOrNull() ?: 128
        fun defaultTerrainSpacing(): Float = System.getProperty("krender.terrain.spacing", "1.0").toFloatOrNull() ?: 1f
        fun defaultTerrainFilePath(): String =
            System.getProperty("krender.terrain.path", "terrains/terrain_01.json")
        fun defaultScenePath(): String? = System.getProperty("krender.scene.path")?.takeIf(String::isNotBlank)
        fun defaultSceneModelPath(): String? = System.getProperty("krender.scene.modelPath")?.takeIf(String::isNotBlank)
        fun defaultSceneName(): String = System.getProperty("krender.scene.name", "Untitled Scene")

        private fun discoverModelPaths(selectedPath: String?): List<String> {
            val supportedExtensions = setOf("glb", "gltf", "g3db", "g3dj", "obj", "fbx")
            val discovered = File("model")
                .listFiles()
                ?.asSequence()
                ?.filter { it.isFile }
                ?.filter { it.extension.lowercase() in supportedExtensions }
                ?.map { "model/${it.name}" }
                ?.sorted()
                ?.toMutableList()
                ?: mutableListOf()

            if (!selectedPath.isNullOrBlank() && selectedPath !in discovered) {
                discovered += selectedPath
            }
            return discovered
        }
    }
}
