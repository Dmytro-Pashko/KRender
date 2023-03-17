package com.dpashko.krender.scene.common

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor

/**
 * The base class for all scenes.
 */
abstract class BaseScene<out C : SceneController<*>, R> constructor(
    protected val controller: C,
    protected val input: InputMultiplexer = InputMultiplexer()
) : SceneLifecycle, InputProcessor by input {

    override fun create() {
        controller.init()
//        Gdx.input.inputProcessor = this
    }

    override fun resume() {
//        Gdx.input.inputProcessor = this
    }

    override fun pause() {
//        Gdx.input.inputProcessor = null
    }

    override fun update(deltaTime: Float) {
        controller.update(deltaTime)
    }

    override fun dispose() {
        controller.dispose()
//        Gdx.input.inputProcessor = null
    }
}
