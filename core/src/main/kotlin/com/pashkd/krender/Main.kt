package com.pashkd.krender

import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.game.ModelViewerScene
import com.pashkd.krender.game.TerrainEditorScene
import java.io.File

class Main(
    sceneName: String = defaultScene(),
    modelPath: String? = defaultModelPath(),
) : GdxEngineApplication(
    initialScene = {
        when (sceneName.lowercase()) {
            "model-viewer" -> ModelViewerScene(
                model = null,
                availableModels = discoverModelPaths(modelPath).map(AssetRef.Companion::model),
            )

            "terrain-generator" -> TerrainEditorScene(
                terrainResolution = defaultTerrainResolution(),
                vertexSpacing = defaultTerrainSpacing(),
                terrainFilePath = defaultTerrainFilePath(),
            )

            else -> {
                System.err.println("Unknown scene '$sceneName', defaulting to model viewer.")
                ModelViewerScene(
                    model = null,
                    availableModels = discoverModelPaths(modelPath).map(AssetRef.Companion::model),
                )

            }
        }
    },
) {
    companion object {
        fun defaultScene(): String = System.getProperty("krender.scene", "terrain-generator")
        fun defaultModelPath(): String? = System.getProperty("krender.model")?.takeIf(String::isNotBlank)
        fun defaultTerrainResolution(): Int = System.getProperty("krender.terrain.size", "128").toIntOrNull() ?: 128
        fun defaultTerrainSpacing(): Float = System.getProperty("krender.terrain.spacing", "1.0").toFloatOrNull() ?: 1f
        fun defaultTerrainFilePath(): String =
            System.getProperty("krender.terrain.path", "terrains/terrain.json")

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
