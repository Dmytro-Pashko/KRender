package com.dpashko.krender.scene.common

/**
 * The base class for all scenes.
 *
 * @param S the type of scene state used by the scene
 * @param C the type of scene controller used by the scene
 * @param controller the scene controller used by the scene
 */
abstract class BaseScene<S : SceneState, C : SceneController<S>> constructor(
    private val controller: C,
    initialState: S? = null
) : SceneLifecycle {

    override fun update(deltaTime: Float) {
        controller.update(deltaTime)
    }

    override fun destroy() {
        controller.destroy()
    }
}