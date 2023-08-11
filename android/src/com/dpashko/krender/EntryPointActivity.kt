package com.dpashko.krender

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.dpashko.compose.AndroidComposeManager

class EntryPointActivity : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        super.onCreate(savedInstanceState)

        // Initializes compose components.
        val composeManager = AndroidComposeManager(this)
        initialize(AppEntryPoint(composeManager), AndroidApplicationConfiguration())

        val graphics = Gdx.graphics as AndroidGraphics
        val surfaceView = graphics.view
        val composeView = composeManager.androidComposeRenderer.composeView

        // Creates FrameLayout that renders compose scene over LibGDX surfaceView.
        setContentView(FrameLayout(this).apply {
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            addView(surfaceView)
            addView(composeView)
        })
    }
}