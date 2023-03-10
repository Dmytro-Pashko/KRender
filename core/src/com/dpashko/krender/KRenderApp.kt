/*
 * Property of Medtronic MiniMed.
 */

package com.dpashko.krender

import com.badlogic.gdx.ApplicationAdapter

/**
 *  The main application class that initializes and manages the AppController.
 */
class KRenderApp : ApplicationAdapter() {

    /** The instance of the AppController used to manage the application. */
    private lateinit var appController: ApplicationListener

    override fun create() {
        appController = KRenderAppController()
        appController.create()
    }

    /**
     * Renders the application by calling the AppController's render method.
     */
    override fun render() {
        appController.render()
    }

    /**
     * Disposes of the application by calling the AppController's dispose method.
     */
    override fun dispose() {
        appController.dispose()
    }

    override fun pause() {
        appController.pause()
    }

    override fun resume() {
        appController.resume()
    }
}
