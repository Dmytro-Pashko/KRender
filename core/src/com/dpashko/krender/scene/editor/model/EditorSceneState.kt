package com.dpashko.krender.scene.editor.model

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.dpashko.krender.scene.common.SceneState

/**
 * Represents the state of the Editor scene.
 */
class EditorSceneState(
    var drawGrid: Boolean = true,
    var drawAxis: Boolean = true,
    var sceneSize: SceneSize = SceneSize.S_32,

    // Box that limits world space.
    var worldBounds: BoundingBox = BoundingBox().apply {
        set(
            Vector3(-sceneSize.size, -sceneSize.size, 0f).scl(0.5f),
            Vector3(sceneSize.size, sceneSize.size, sceneSize.size).scl(0.5f)
        )
    },
) : SceneState() {

    /**
     * Returns a byte array representing the object for persistence.
     */
    override fun getObjectForPersistence(): ByteArray {
        return ByteArray(0)
    }

    enum class SceneSize(val size: Float) {
        S_16(16.0f),
        S_32(32.0f),
        S_64(64.0f);

        override fun toString() = "Size=$size"
    }
}
