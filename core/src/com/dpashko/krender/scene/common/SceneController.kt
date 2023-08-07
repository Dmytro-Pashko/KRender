package com.dpashko.krender.scene.common

/**
 * The base interface for scene controllers.
 *
 * @param S the type of scene state used by the controller
 */
interface SceneController<S : SceneState> {

    suspend fun init()

    /**
     * Updates the scene state based on the given delta time.
     *
     * @param deltaTime the elapsed time since the last update
     */
    suspend fun update(deltaTime: Float)

    /**
     * Cleans up any resources used by the controller.
     */
    suspend fun dispose()
}
