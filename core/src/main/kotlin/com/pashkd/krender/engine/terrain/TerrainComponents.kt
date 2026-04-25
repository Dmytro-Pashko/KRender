package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.DynamicModel
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.Material

data class TerrainComponent(
    val data: TerrainData,
) : Component {
    var dirty: Boolean = true
        private set

    fun markDirty() {
        dirty = true
    }

    fun clearDirty() {
        dirty = false
    }
}

enum class TerrainDisplayMode {
    Solid,
    Wireframe,
}

data class TerrainRendererComponent(
    val modelId: String,
    var material: Material = Material(),
    var model: DynamicModel? = null,
    var vertexCount: Int = 0,
    var triangleCount: Int = 0,
    var meshRevision: Long = 0L,
) : Component {
    var displayMode: TerrainDisplayMode = if (material.wireframe) {
        TerrainDisplayMode.Wireframe
    } else {
        TerrainDisplayMode.Solid
    }
        private set

    fun toggleDisplayMode() {
        setDisplayMode(
            if (displayMode == TerrainDisplayMode.Solid) {
                TerrainDisplayMode.Wireframe
            } else {
                TerrainDisplayMode.Solid
            },
        )
    }

    fun setDisplayMode(mode: TerrainDisplayMode) {
        displayMode = mode
        material = material.copy(wireframe = mode == TerrainDisplayMode.Wireframe)
    }
}

data class TerrainCameraControllerComponent(
    var panSpeed: Float = 18f,
    var rotationSpeedDegrees: Float = 60f,
    var minDistance: Float = 10f,
    var maxDistance: Float = 180f,
) : Component

data class TerrainHit(
    val worldPosition: Vec3,
    val localX: Float,
    val localZ: Float,
    val sampleX: Int,
    val sampleY: Int,
)
