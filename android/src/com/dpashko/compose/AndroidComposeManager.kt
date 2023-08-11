package com.dpashko.compose

import android.content.Context
import com.badlogic.gdx.InputProcessor
import com.dpashko.krender.compose.ComposeManager
import com.dpashko.krender.compose.ComposeRenderer

class AndroidComposeManager(private val context: Context) : ComposeManager() {

    override val isInitialized: Boolean = false
    internal lateinit var androidComposeRenderer: AndroidComposeRenderer
    private lateinit var androidInputProcessor: AndroidInputProcessor

    override fun init() {
        if (!isInitialized) {
            println("Compose manager already initialized.")
        }
        println("Android Compose manager initialization.")
        androidComposeRenderer = AndroidComposeRenderer(context)
        androidInputProcessor = AndroidInputProcessor()
        println("Compose manager initialized.")
    }

    override fun getRenderer(): ComposeRenderer = androidComposeRenderer

    override fun inputProcessor(): InputProcessor = androidInputProcessor
}