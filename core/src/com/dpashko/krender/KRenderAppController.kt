package com.dpashko.krender

import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.scene.common.SceneNavigator
import com.dpashko.krender.scene.editor.EditorScene
import com.dpashko.krender.scene.editor.EditorSceneController
import com.dpashko.krender.scene.editor.EditorSceneState

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
        val controller = EditorSceneController(EditorSceneState())
        val entryScene = EditorScene(controller).apply {
            create()
        }
        navigator.pushScene(entryScene)
    }

    /**
     * Update and render the current scene.
     */
    override fun render(delta: Float) {
        navigator.activeScene?.let {
            it.update(delta)
            it.render()
        }
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

    override fun resize(width: Int, height: Int) {
        navigator.activeScene?.resize(width, height)
    }
}
