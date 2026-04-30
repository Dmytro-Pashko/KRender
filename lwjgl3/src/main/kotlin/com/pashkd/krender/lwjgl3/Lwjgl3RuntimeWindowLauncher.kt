package com.pashkd.krender.lwjgl3

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher

/**
 * Desktop launcher that starts a second JVM with the same classpath and runtime scene properties.
 */
class Lwjgl3RuntimeWindowLauncher(
    private val logger: Logger,
) : RuntimeWindowLauncher {
    private val processLauncher = Lwjgl3JvmProcessLauncher(logger, TAG)

    override fun launchRuntimeScene(scenePath: String) {
        processLauncher.launch(
            properties = linkedMapOf(
                "krender.scene" to "runtime-scene",
                "krender.scene.path" to scenePath,
            ),
            failureMessage = "Runtime scene launch failed",
        )
    }

    companion object {
        private const val TAG = "RuntimeWindowLauncher"
    }
}
