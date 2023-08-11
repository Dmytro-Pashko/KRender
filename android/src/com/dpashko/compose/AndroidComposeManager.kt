package com.dpashko.compose

import android.content.Context
import com.badlogic.gdx.InputProcessor
import com.dpashko.krender.compose.ComposeManager
import com.dpashko.krender.compose.ComposeRenderer

class AndroidComposeManager(context: Context) : ComposeManager() {

    override val isInitialized: Boolean = true

    override fun init() {}

    internal val androidComposeRenderer = AndroidComposeRenderer(context)
    private val androidInputProcessor = AndroidInputProcessor()

    override fun getRenderer(): ComposeRenderer = androidComposeRenderer

    override fun inputProcessor(): InputProcessor = androidInputProcessor
}