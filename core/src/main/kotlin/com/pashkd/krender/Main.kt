package com.pashkd.krender

import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.game.ModelViewerScene
import java.io.File

class Main(
    modelPath: String = defaultModelPath(),
) : GdxEngineApplication(
    initialScene = {
        ModelViewerScene(
            model = null,
            availableModels = discoverModelPaths(modelPath).map(AssetRef.Companion::model),
        )
    },
) {
    companion object {
        fun defaultModelPath(): String = System.getProperty("krender.model", "model/m_grass_plant_01.glb")

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
