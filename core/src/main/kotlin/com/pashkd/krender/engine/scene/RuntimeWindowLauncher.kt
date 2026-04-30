package com.pashkd.krender.engine.scene

/**
 * Launches a saved scene in an isolated runtime window or process.
 */
interface RuntimeWindowLauncher {
    fun launchRuntimeScene(scenePath: String)
}

/**
 * Fallback used by backends that cannot spawn a separate runtime window.
 */
object UnsupportedRuntimeWindowLauncher : RuntimeWindowLauncher {
    override fun launchRuntimeScene(scenePath: String) {
        error("Runtime window launching is not supported by this backend.")
    }
}
