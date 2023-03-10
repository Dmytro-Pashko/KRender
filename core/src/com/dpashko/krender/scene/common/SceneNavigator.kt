package com.dpashko.krender.scene.common

import java.util.ArrayDeque

/**
 * A navigator that manages the scene stack and handles navigation between scenes.
 *
 * @param T the type of scene managed by this navigator.
 */
class SceneNavigator<T : BaseScene<*, *>> {

    val activeScene: T?
        get() = stack.lastOrNull()

    private val stack = ArrayDeque<T>()

    /**
     * Adds a scene to the top of the scene stack and starts it.
     *
     * @param scene the scene to add to the stack.
     */
    fun pushScene(scene: T) {
        if (!stack.isEmpty()) {
            val currentScene = stack.last()
            currentScene.stop()
        }

        stack.add(scene)
        scene.start()
    }
}
