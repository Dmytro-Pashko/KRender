package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.dpashko.krender.scene.common.SceneState

class EditorSceneState(
    var drawGrid: Boolean = true,
    var drawAxis: Boolean = true,
    var sceneSize: SceneSize = SceneSize.S_32,
    var camera: PerspectiveCamera = PerspectiveCamera(
        67f,
        Gdx.graphics.width.toFloat(),
        Gdx.graphics.height.toFloat()
    )
        .apply {
            near = 0.1f
            far = 128f
            up.set(Vector3.Z)
            position.set(Vector3(5f, 7f, 5f))
            lookAt(Vector3.Zero)
            update()
        },

    // Box that limits world space.
    var worldBounds: BoundingBox = BoundingBox().apply {
        set(
            Vector3(-sceneSize.size, -sceneSize.size, 0f).scl(0.5f),
            Vector3(sceneSize.size, sceneSize.size, sceneSize.size).scl(0.5f)
        )
    },
    var target: Vector3 = Vector3().apply {
        val t = -camera.position.z / camera.direction.z
        set(
            camera.position.x + t * camera.direction.x,
            camera.position.y + t * camera.direction.y,
            0f
        )
    },
) : SceneState() {

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
