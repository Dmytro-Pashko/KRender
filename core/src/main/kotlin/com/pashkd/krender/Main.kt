package com.pashkd.krender

import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.game.ModelViewerScene
import com.pashkd.krender.game.TerrainEditorScene
import java.io.File

class Main(
    sceneName: String = defaultScene(),
    modelPath: String = defaultModelPath(),
) : GdxEngineApplication(
    initialScene = {
        when (sceneName.lowercase()) {
            "viewer", "model-viewer", "model_viewer" -> ModelViewerScene(
                model = null,
                availableModels = discoverModelPaths(modelPath).map(AssetRef.Companion::model),
            )

            else -> TerrainEditorScene(
                terrainResolution = defaultTerrainResolution(),
                vertexSpacing = defaultTerrainSpacing(),
            )
        }
    },
) {
    companion object {
        fun defaultScene(): String = System.getProperty("krender.scene", "viewer")
        fun defaultModelPath(): String = System.getProperty("krender.model", "model/m_grass_plant_01.glb")
        fun defaultTerrainResolution(): Int = System.getProperty("krender.terrain.size", "128").toIntOrNull() ?: 128
        fun defaultTerrainSpacing(): Float = System.getProperty("krender.terrain.spacing", "1.0").toFloatOrNull() ?: 1f

        private fun discoverModelPaths(selectedPath: String): List<String> {
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

            if (selectedPath !in discovered) {
                discovered += selectedPath
            }
            return discovered
        }
    }
}
