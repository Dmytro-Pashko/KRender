package com.pashkd.krender.engine.terrain

import com.badlogic.gdx.graphics.Texture
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.DynamicModel
import com.pashkd.krender.engine.api.TerrainAsset
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.Material

/**
 * ECS component that references a file-backed terrain asset in a scene.
 */
data class TerrainComponent(
    val terrain: AssetRef<TerrainAsset>,
    var visible: Boolean = true,
) : Component

/**
 * ECS component that owns editable terrain data inside the Terrain Editor.
 */
data class TerrainDataComponent(
    var data: TerrainData,
) : Component {
    var dirty: Boolean = true
        private set

    /**
     * Marks the terrain mesh representation as stale after data changes.
     */
    fun markDirty() {
        dirty = true
    }

    /**
     * Clears the dirty flag after render mesh synchronization.
     */
    fun clearDirty() {
        dirty = false
    }
}

/**
 * Terrain viewport display mode.
 */
enum class TerrainDisplayMode {
    Solid,
    Wireframe,
}

/**
 * ECS component that stores terrain render state generated from terrain data or assets.
 */
data class TerrainRendererComponent(
    var modelId: String,
    var material: Material = Material(),
    var model: DynamicModel? = null,
    var previewDiffuseTexture: Texture? = null,
    var previewMode: TerrainPreviewMode = TerrainPreviewMode.MaterialColor,
    var previewResolution: Int = 0,
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

    /**
     * Switches between solid and wireframe terrain rendering.
     */
    fun toggleDisplayMode() {
        setDisplayMode(
            if (displayMode == TerrainDisplayMode.Solid) {
                TerrainDisplayMode.Wireframe
            } else {
                TerrainDisplayMode.Solid
            },
        )
    }

    /**
     * Sets terrain display mode and mirrors it into the material wireframe flag.
     */
    fun setDisplayMode(mode: TerrainDisplayMode) {
        displayMode = mode
        material = material.copy(wireframe = mode == TerrainDisplayMode.Wireframe)
    }

    /**
     * Replaces the editor-only material preview texture and releases the previous one.
     */
    fun replacePreviewDiffuseTexture(texture: Texture?) {
        if (previewDiffuseTexture === texture) return
        previewDiffuseTexture?.dispose()
        previewDiffuseTexture = texture
    }
}

/**
 * Terrain editor camera settings for pan, vertical movement, and target rotation.
 */
data class TerrainCameraControllerComponent(
    var panSpeed: Float = 18f,
    var rotationSpeedDegrees: Float = 60f,
    var minDistance: Float = 10f,
    var maxDistance: Float = 180f,
) : Component

/**
 * Result of terrain picking in world and terrain-local coordinates.
 */
data class TerrainHit(
    val worldPosition: Vec3,
    val localX: Float,
    val localZ: Float,
    val sampleX: Int,
    val sampleY: Int,
)
