package com.pashkd.krender.android

import android.os.Bundle
import com.badlogic.gdx.Application
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.pashkd.krender.engine.sceneplayer.ScenePlayerMain

/** Launches the Android application. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLevel = Application.LOG_DEBUG
        initialize(
            ScenePlayerMain(
                sceneName = "scene-player",
                scenePath = "scenes/Untitled_Scene.krscene",
            ),
            AndroidApplicationConfiguration().apply {
                // Configure your application here.
                useImmersiveMode = true // Recommended, but not required.
            },
        )
    }
}
