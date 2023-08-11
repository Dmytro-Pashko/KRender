package com.dpashko.krender.compose

import com.badlogic.gdx.InputProcessor

/**
 * Manages Compose Scene rendering and handling actions in Desktop implementation
 * backed on Skia OpenGL wrapper.
 */
class DesktopComposeManager : ComposeManager() {

    private lateinit var composeRenderer: DesktopComposeRenderer
    private lateinit var inputProcessor: ComposeInputProcessor
    override var isInitialized = false
    override fun init() {
        if (isInitialized) {
            println("Compose manager already initialize.")
            return
        }
        println("Initialization of Compose manager.")
        composeRenderer = DesktopComposeRenderer(
            GdxCoroutineDispatcher()
        ).apply {
            init()
        }
        inputProcessor = ComposeInputProcessor(composeRenderer.scene)
        println("Compose manager initialized.")
        isInitialized = true
    }

    override fun getRenderer(): ComposeRenderer = composeRenderer

    override fun inputProcessor(): InputProcessor = inputProcessor
}
