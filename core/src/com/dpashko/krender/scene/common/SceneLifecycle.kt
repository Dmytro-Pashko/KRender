package com.dpashko.krender.scene.common

/**
 * This interface defines the lifecycle methods of a scene.
 */
interface SceneLifecycle {

    /**
     * This method is called when the scene is being created.
     */
    fun create()

    /**
     * This method is called when the scene is starting.
     */
    fun start()

    /**
     * This method is called every frame to update the scene.
     *
     * @param deltaTime the time elapsed since the last frame, in seconds
     */
    fun update(deltaTime: Float)

    /**
     * This method is called every frame to render the scene.
     */
    fun render()

    /**
     * This method is called when the scene is being paused.
     */
    fun pause()

    /**
     * This method is called when the scene is being resumed.
     */
    fun resume()

    /**
     * This method is called when the scene is being stopped.
     */
    fun stop()

    /**
     * This method is called when the scene is being destroyed.
     */
    fun dispose()

    fun resize(width: Int, height: Int)
}
