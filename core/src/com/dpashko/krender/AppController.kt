package com.dpashko.krender

import com.dpashko.krender.scene.SceneFactory
import com.dpashko.krender.scene.common.BaseScene
import javax.inject.Inject
import javax.inject.Singleton

/**

The interface for application lifecycle listeners.
 */
interface AppController {

    /**

    Called when the application is first created.
     */
    fun create()

    /**

    Called every frame to update and render the application.
     */
    fun update(delta: Float)

    /**

    Called when the application is paused, such as when the user switches to a different app.
     */
    fun pause()

    /**

    Called when the application is resumed, such as when the user returns to the app or screen.
     */
    fun resume()

    /**

    Called when the application is about to be disposed, such as when the user closes the app.
     */
    fun dispose()

    fun resize(width: Int, height: Int)
}

/**
 * Main app controller that initiates app components e.g scenes, and handles navigation between them.
 */
@Singleton
class AppControllerImpl @Inject constructor(
    private val sceneFactory: SceneFactory
) : AppController {

    private lateinit var activeScene: BaseScene<*>

    /**
     * Initialize any app-scoped components here.
     */
    override fun create() {
        println("Started AppController initialization.")
        // Initialize any app-scoped components here

        // Create and start the first scene
        activeScene = sceneFactory.getEntryPointScene().apply {
            create()
        }
        println("AppController initialized.")
    }

    override fun update(delta: Float) {
        activeScene.update(delta)
        activeScene.render()
    }

    /**
     * Pause the current scene.
     */
    override fun pause() {
        activeScene.pause()
    }

    /**
     * Resume the current scene.
     */
    override fun resume() {
        activeScene.resume()
    }

    /**
     * Stop and destroy the current scene and dispose of any app-scoped components.
     */
    override fun dispose() {
        activeScene.dispose()
        println("AppController disposed.")
    }

    override fun resize(width: Int, height: Int) {
        activeScene.resize(width, height)
    }
}
