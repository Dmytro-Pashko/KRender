package com.pashkd.krender.lwjgl3

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.EditorToolLauncher

/**
 * Desktop launcher that opens editor tools in separate JVM windows.
 */
class Lwjgl3EditorToolLauncher(
    logger: Logger,
) : EditorToolLauncher {
    private val processLauncher = Lwjgl3JvmProcessLauncher(logger, TAG)

    override fun launchModelViewer(modelPath: String) {
        launch(
            scene = "model-viewer",
            pathProperty = "krender.model.path" to normalizePath(modelPath),
            failureMessage = "Model Viewer launch failed",
        )
    }

    override fun launchAnimationViewer(modelPath: String) {
        launch(
            scene = "animation-viewer",
            pathProperty = "krender.model.path" to normalizePath(modelPath),
            failureMessage = "Animation Viewer launch failed",
        )
    }

    override fun launchTerrainEditor(terrainPath: String) {
        launch(
            scene = "terrain-editor",
            pathProperty = "krender.terrain.path" to normalizePath(terrainPath),
            failureMessage = "Terrain Editor launch failed",
        )
    }

    override fun launchSceneEditorWithScene(scenePath: String) {
        launch(
            scene = "scene-editor",
            pathProperty = "krender.scene.path" to normalizePath(scenePath),
            failureMessage = "Scene Editor launch failed",
        )
    }

    private fun launch(scene: String, pathProperty: Pair<String, String>, failureMessage: String) {
        processLauncher.launch(
            properties = linkedMapOf(
                "krender.scene" to scene,
                pathProperty,
            ),
            failureMessage = failureMessage,
        )
    }

    private fun normalizePath(path: String): String =
        path.trim().replace('\\', '/')

    companion object {
        private const val TAG = "EditorToolLauncher"
    }
}
