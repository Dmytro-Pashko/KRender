package com.pashkd.krender.engine.backend.gdx.scene

import com.badlogic.gdx.Gdx
import com.pashkd.krender.engine.scene.SceneFileService

/**
 * LibGDX-backed scene file access using local project-relative paths.
 */
class GdxSceneFileService : SceneFileService {
    override fun writeText(path: String, text: String) {
        Gdx.files.local(path).writeString(text, false, "UTF-8")
    }

    override fun readText(path: String): String =
        Gdx.files.local(path).readString("UTF-8")

    override fun ensureDirectories(path: String) {
        Gdx.files.local(path).parent()?.mkdirs()
    }
}

