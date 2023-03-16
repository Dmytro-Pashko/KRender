package com.dpashko.krender.scene.common

/**
 * The base class for all scenes.
 */
abstract class BaseScene<out C : SceneController<*>, R> constructor(
    protected val controller: C
) : SceneLifecycle {

    override fun create() {
        controller.init()
    }

    override fun update(deltaTime: Float) {
        controller.update(deltaTime)
    }

    override fun dispose() {
        controller.dispose()
    }
}
