package com.dpashko.krender.compose

import com.badlogic.gdx.InputProcessor

abstract class ComposeManager {

    abstract val isInitialized: Boolean

    abstract fun init()

    abstract fun getRenderer(): ComposeRenderer

    abstract fun inputProcessor(): InputProcessor

}
