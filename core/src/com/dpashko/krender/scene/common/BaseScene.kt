package com.dpashko.krender.scene.common

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.dpashko.krender.compose.GdxCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The base class for all scenes.
 */
abstract class BaseScene<out C : SceneController<*>, R>(
    protected val controller: C,
    protected val input: InputMultiplexer = InputMultiplexer(),
    protected val dispatcher: CoroutineDispatcher = GdxCoroutineDispatcher(),
) : SceneLifecycle, InputProcessor by input {

    protected val sceneScope = CoroutineScope(dispatcher)

    override fun create() {
        Gdx.input.inputProcessor = this
        sceneScope.launch {
            controller.init()
        }
    }

    override fun resume() {
        Gdx.input.inputProcessor = this
    }

    override fun pause() {
        Gdx.input.inputProcessor = null
    }

    override fun update(deltaTime: Float) {
        sceneScope.launch {
            controller.update(deltaTime)
        }
    }

    override fun dispose() {
        sceneScope.launch {
            controller.dispose()
        }
        sceneScope.cancel()
        Gdx.input.inputProcessor = null
    }
}
