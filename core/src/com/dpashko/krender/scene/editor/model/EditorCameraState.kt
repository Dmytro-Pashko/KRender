package com.dpashko.krender.scene.editor.model

import com.badlogic.gdx.math.Vector3

/**
 * Data model that represents Perspective camera state in Editor Scene.
 */
class EditorCameraState(
    val position: Vector3,
    val direction: Vector3,
    val near: Float,
    val far: Float,
    val viewportHeight: Float,
    val viewportWidth: Float


) {
    override fun toString(): String {
        return "CameraState(" +
                "position=$position, " +
                "direction=$direction, " +
                "near=$near, " +
                "far=$far, " +
                "viewportHeight=$viewportHeight, " +
                "viewportWidth=$viewportWidth" +
                ")"
    }
}
