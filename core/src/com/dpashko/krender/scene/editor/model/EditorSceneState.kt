package com.dpashko.krender.scene.editor.model

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.dpashko.krender.scene.common.SceneState
import net.mgsx.gltf.scene3d.scene.SceneModel

/**
 * Represents the state of the Editor scene.
 */
class EditorSceneState(
    var isLoading: Boolean = false,
    var drawGrid: Boolean = true,
    var drawAxis: Boolean = true,
    var sceneSize: SceneSize = SceneSize.S_32,

    // TODO(pashko): Should be reworked in order to work with specific WorldObject.
    var models: List<SceneModel> = emptyList(),

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EditorSceneState

        if (isLoading != other.isLoading) return false
        if (drawGrid != other.drawGrid) return false
        if (drawAxis != other.drawAxis) return false
        if (sceneSize != other.sceneSize) return false
        if (models != other.models) return false
        return worldBounds == other.worldBounds
    }

    override fun hashCode(): Int {
        var result = isLoading.hashCode()
        result = 31 * result + drawGrid.hashCode()
        result = 31 * result + drawAxis.hashCode()
        result = 31 * result + sceneSize.hashCode()
        result = 31 * result + models.hashCode()
        result = 31 * result + worldBounds.hashCode()
        return result
    }

    override fun toString(): String {
        return "EditorSceneState(" +
                "isLoading=$isLoading, " +
                "drawGrid=$drawGrid, " +
                "drawAxis=$drawAxis, " +
                "sceneSize=$sceneSize, " +
                "models=$models)"
    }


    enum class SceneSize(val size: Float) {
        S_16(16.0f),
        S_32(32.0f),
        S_64(64.0f);

        override fun toString() = "Size=$size"
    }


}
