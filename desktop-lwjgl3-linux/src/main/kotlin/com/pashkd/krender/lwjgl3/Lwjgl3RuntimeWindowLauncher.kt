package com.pashkd.krender.lwjgl3

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher

/**
 * Desktop launcher that starts a second JVM with the same classpath and runtime scene properties.
 */
class Lwjgl3RuntimeWindowLauncher(
    logger: Logger,
    mainClassName: String,
) : RuntimeWindowLauncher {
    private val processLauncher = Lwjgl3JvmProcessLauncher(logger, TAG, mainClassName)

    override fun launchRuntimeScene(scenePath: String) {
        processLauncher.launch(
            properties =
                linkedMapOf(
                    "krender.scene" to "scene-player",
                    "krender.scene.path" to scenePath,
                ),
            failureMessage = "Runtime scene launch failed",
        )
    }

    override fun launchScene(sceneName: String) {
        val normalizedSceneName = sceneName.trim()
        require(normalizedSceneName.isNotBlank()) { "Runtime scene name must not be blank." }
        processLauncher.launch(
            properties =
                linkedMapOf(
                    "krender.scene" to normalizedSceneName,
                ),
            failureMessage = "Named runtime scene launch failed",
        )
    }

    companion object {
        private const val TAG = "RuntimeWindowLauncher"
    }
}
