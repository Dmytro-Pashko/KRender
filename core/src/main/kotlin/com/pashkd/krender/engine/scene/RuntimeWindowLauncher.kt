package com.pashkd.krender.engine.scene

/**
 * Launches a saved scene in an isolated runtime window or process.
 */
interface RuntimeWindowLauncher {
    fun launchRuntimeScene(scenePath: String)

    fun launchScene(sceneName: String) {
        error("Named runtime scene launching is not supported by this backend.")
    }
}

/**
 * Fallback used by backends that cannot spawn a separate runtime window.
 */
object UnsupportedRuntimeWindowLauncher : RuntimeWindowLauncher {
    override fun launchRuntimeScene(scenePath: String) {
        error("Runtime window launching is not supported by this backend.")
    }

    override fun launchScene(sceneName: String) {
        error("Named runtime scene launching is not supported by this backend.")
    }
}
