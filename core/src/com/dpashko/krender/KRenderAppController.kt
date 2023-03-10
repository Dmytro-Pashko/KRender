package com.dpashko.krender

import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.scene.common.SceneNavigator
import com.dpashko.krender.scene.editor.EditorScene
import com.dpashko.krender.scene.editor.EditorSceneController

/**
 * Main app controller that initiates app components e.g scenes, and handles navigation between them.
 */
class KRenderAppController : ApplicationListener {

    private val navigator: SceneNavigator<BaseScene<*, *>> = SceneNavigator()

    /**
     * Initialize any app-scoped components here.
     */
    override fun create() {
        // Initialize any app-scoped components here

        // Create and start the first scene
        navigator.pushScene(EditorScene(EditorSceneController()))
    }

    /**
     * Update and render the current scene.
     */
    override fun render() {
        navigator.activeScene?.render()
    }

    /**
     * Pause the current scene.
     */
    override fun pause() {
        navigator.activeScene?.pause()
    }

    /**
     * Resume the current scene.
     */
    override fun resume() {
        navigator.activeScene?.resume()
    }

    /**
     * Stop and destroy the current scene and dispose of any app-scoped components.
     */
    override fun dispose() {
        navigator.activeScene?.destroy()
    }
}
