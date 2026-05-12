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
        resolveReadableFile(path).readString("UTF-8")

    override fun ensureDirectories(path: String) {
        Gdx.files.local(path).parent()?.mkdirs()
    }

    override fun exists(path: String): Boolean =
        Gdx.files.local(path).exists() || Gdx.files.internal(path).exists()

    override fun describeReadableSource(path: String): String =
        when {
            Gdx.files.local(path).exists() -> "local"
            Gdx.files.internal(path).exists() -> "internal"
            else -> "missing"
        }

    private fun resolveReadableFile(path: String) =
        Gdx.files.local(path).takeIf { it.exists() }
            ?: Gdx.files.internal(path).takeIf { it.exists() }
            ?: Gdx.files.local(path)
}
