package com.pashkd.krender.android

import android.os.Bundle
import com.badlogic.gdx.Application
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.sceneplayer.ScenePlayerModule

/** Launches the Android application. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLevel = Application.LOG_DEBUG
        initialize(
            GdxEngineApplication(
                initialScene = {
                    ScenePlayerModule.createScene(
                        sceneName = "scene-player",
                        scenePath = "scenes/Untitled_Scene.krscene",
                    ) ?: error("Unable to create Android scene-player route.")
                },
            ),
            AndroidApplicationConfiguration().apply {
                // Configure your application here.
                useImmersiveMode = true // Recommended, but not required.
            },
        )
    }
}
