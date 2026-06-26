package com.pashkd.krender.engine.scene

/**
 * Launches editor tools in an isolated window or process.
 */
interface EditorToolLauncher {
    fun launchModelViewer(modelPath: String)

    fun launchAnimationViewer(modelPath: String)

    fun launchTerrainEditor(terrainPath: String)

    fun launchSceneEditorWithScene(scenePath: String)

    fun launchSkinEditor(skinPath: String?)

    fun launchTextureAtlasEditor(atlasPath: String)

    /**
     * Opens a `.krui` UiScene in the UI Composer route.
     *
     * This is editor/tool routing only: it gives Asset Browser a stable launch target for UiScene
     * assets. The launched tool supports validation, Scene2D preview, hierarchy/inspector editing,
     * undo/redo, and save workflows, while current limitations such as no drag/drop authoring,
     * no Skin editing, and no asset-id references remain intentional.
     */
    fun launchUiComposer(uiScenePath: String)

    fun launchBitmapFontEditor(fontPath: String?)
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

    override fun launchSkinEditor(skinPath: String?) {
        unsupported()
    }

    override fun launchTextureAtlasEditor(atlasPath: String) {
        unsupported()
    }

    override fun launchUiComposer(uiScenePath: String) {
        unsupported()
    }

    override fun launchBitmapFontEditor(fontPath: String?) {
        unsupported()
    }

    private fun unsupported(): Nothing = error("Editor tool launching is not supported by this backend.")
}
