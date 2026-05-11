package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.DynamicModel
import com.pashkd.krender.engine.api.RuntimeTextureData
import com.pashkd.krender.engine.api.TerrainAsset
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.Material

/**
 * ECS component that references a file-backed terrain asset in a scene.
 */
data class TerrainComponent(
    val terrain: AssetRef<TerrainAsset>,
    var visible: Boolean = true,
    var previewMode: TerrainPreviewMode = TerrainPreviewMode.LayerColor,
    var bakedTextureResolution: Int = 8192,
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
 * ECS component that stores backend-neutral terrain render state.
 *
 * [previewDiffuseTexture] is editor-only data for debug and paint feedback
 * modes. [finalSplatTexture] is the runtime final baked terrain material
 * texture; [materialRevision] tracks changes to that final material state.
 * Render systems bind textures through [com.pashkd.krender.engine.api.MaterialTextureRef]
 * on [material], and pass the matching [RuntimeTextureData] payload with the
 * draw command so the backend can upload it.
 */
data class TerrainRendererComponent(
    var modelId: String,
    var material: Material = Material(),
    var model: DynamicModel? = null,
    /**
     * Editor-only preview texture used by Terrain Editor debug/preview modes.
     */
    var previewDiffuseTexture: RuntimeTextureData? = null,
    /**
     * Runtime final baked terrain material texture generated from terrain layers.
     */
    var finalSplatTexture: RuntimeTextureData? = null,
    var previewMode: TerrainPreviewMode = TerrainPreviewMode.MaterialColor,
    var previewResolution: Int = 0,
    var vertexCount: Int = 0,
    var triangleCount: Int = 0,
    var meshRevision: Long = 0L,
    var materialRevision: Long = 0L,
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
     * Replaces the editor-only material preview texture payload.
     */
    fun replacePreviewDiffuseTexture(texture: RuntimeTextureData?) {
        if (previewDiffuseTexture === texture) return
        previewDiffuseTexture = texture
    }

    /**
     * Replaces the runtime final terrain material texture payload.
     */
    fun replaceFinalSplatTexture(texture: RuntimeTextureData?) {
        if (finalSplatTexture === texture) return
        finalSplatTexture = texture
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
