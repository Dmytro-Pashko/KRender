package com.dpashko.krender

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.ScreenUtils
import com.dpashko.krender.di.DaggerAppComponent
import javax.inject.Inject

/**
 *  The main application class that initializes and manages the AppController.
 */
class AppEntryPoint : ApplicationAdapter() {

    /** The instance of the AppController used to manage the application. */
    @Inject
    lateinit var controller: AppController

    override fun create() {
        DaggerAppComponent.builder()
            .build()
            .inject(this)

        controller.create()
    }

    /**
     * Renders the application by calling the AppController's render method.
     */
    override fun render() {
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
