package com.pashkd.krender.engine.scene

/**
 * Launches editor tools in an isolated window or process.
 */
interface EditorToolLauncher {
    fun launchModelViewer(modelPath: String)

    fun launchAnimationViewer(modelPath: String)

    fun launchTerrainEditor(terrainPath: String)

    fun launchSceneEditorWithScene(scenePath: String)

    /**
     * Opens a `.krui` UiScene in the temporary UI Composer route.
     *
     * This is editor/tool routing only: it gives Asset Browser a stable launch target for UiScene
     * assets before the real composer editor exists. The launched placeholder does not render previews,
     * edit hierarchy/inspector data, show bounds overlays, edit Skins, support drag/drop, save files,
     * or introduce asset-id references.
     */
    fun launchUiComposer(uiScenePath: String)
}

/**
 * Fallback used by backends that cannot spawn separate editor tool windows.
 */
object UnsupportedEditorToolLauncher : EditorToolLauncher {
    override fun launchModelViewer(modelPath: String) {
        unsupported()
    }

    override fun launchAnimationViewer(modelPath: String) {
        unsupported()
    }

    override fun launchTerrainEditor(terrainPath: String) {
        unsupported()
    }

    override fun launchSceneEditorWithScene(scenePath: String) {
        unsupported()
    }

    override fun launchUiComposer(uiScenePath: String) {
        unsupported()
    }

    private fun unsupported(): Nothing = error("Editor tool launching is not supported by this backend.")
}
