package com.dpashko.krender.scene.common

/**
 * The base interface for scene controllers.
 *
 * @param S the type of scene state used by the controller
 */
interface SceneController<S : SceneState> {

    fun init()

    /**
     * Updates the scene state based on the given delta time.
     *
     * @param deltaTime the elapsed time since the last update
     */
    fun update(deltaTime: Float)

    /**
     * Cleans up any resources used by the controller.
     */
    fun dispose()

    /**
     * Returns the current state of the scene.
     */
    fun getState(): S
}
