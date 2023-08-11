package com.dpashko.krender

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.dpashko.krender.compose.ComposeManager
import com.dpashko.krender.di.DaggerAppComponent
import javax.inject.Inject

/**
 *  The main application class that initializes and manages the AppController.
 */
class AppEntryPoint(private val composeManager: ComposeManager) : ApplicationAdapter() {

    /** The instance of the AppController used to manage the application. */
    @Inject
    lateinit var controller: AppController

    override fun create() {
        DaggerAppComponent.builder()
            .composeManager(composeManager)
            .build()
            .inject(this)

        controller.create()
    }

    /**
     * Renders the application by calling the AppController's render method.
     */
    override fun render() {

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        controller.update(Gdx.graphics.deltaTime)
    }

    override fun resize(width: Int, height: Int) {
        controller.resize(width, height)
    }

    /**
     * Disposes of the application by calling the AppController's dispose method.
     */
    override fun dispose() {
        controller.dispose()
    }

    override fun pause() {
        controller.pause()
    }

    override fun resume() {
        controller.resume()
    }
}
