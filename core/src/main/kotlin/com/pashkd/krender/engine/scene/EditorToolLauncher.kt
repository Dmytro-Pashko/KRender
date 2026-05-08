package com.pashkd.krender.engine.scene

/**
 * Launches editor tools in an isolated window or process.
 */
interface EditorToolLauncher {
    fun launchModelViewer(modelPath: String)
    fun launchTerrainEditor(terrainPath: String)
    fun launchSceneEditorWithScene(scenePath: String)
}

/**
 * Fallback used by backends that cannot spawn separate editor tool windows.
 */
object UnsupportedEditorToolLauncher : EditorToolLauncher {
    override fun launchModelViewer(modelPath: String) {
        unsupported()
    }

    override fun launchTerrainEditor(terrainPath: String) {
        unsupported()
    }

    override fun launchSceneEditorWithScene(scenePath: String) {
        unsupported()
    }

    private fun unsupported(): Nothing =
        error("Editor tool launching is not supported by this backend.")
}
