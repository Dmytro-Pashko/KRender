package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.dpashko.krender.scene.common.SceneState

class EditorSceneState(
    var drawGrid: Boolean = true,
    var drawAxis: Boolean = true,
    var gridSize: GridSize = GridSize.S_32,
    var camera: PerspectiveCamera = PerspectiveCamera(
        67f,
        Gdx.graphics.width.toFloat(),
        Gdx.graphics.height.toFloat()
    )
        .apply {
            near = 1f
            far = 128f
            up.set(Vector3.Z)
            position.set(Vector3(2f, 3f, 3f))
            lookAt(Vector3.Zero)
            update()
        },
) : SceneState() {

    override fun getObjectForPersistence(): ByteArray {
        TODO("Not yet implemented")
    }

    enum class GridSize(val size: Float) {
        S_16(16.0f),
        S_32(32.0f),
        S_64(64.0f);

        override fun toString() = "Size=$size"
    }
}
