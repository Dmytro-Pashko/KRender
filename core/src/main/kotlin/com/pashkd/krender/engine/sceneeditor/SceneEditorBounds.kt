package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainDataComponent
import com.pashkd.krender.engine.terrain.TerrainRendererComponent
import kotlin.math.cos
import kotlin.math.sin

data class SceneEditorLocalBounds(
    val min: Vec3,
    val max: Vec3,
)

/**
 * Supplies local-space selection and overlay bounds for editable scene entities.
 *
 * Model asset bounds are delegated to [ModelBoundsService] so this core editor
 * logic never loads assets directly and never depends on a rendering backend.
 */
class SceneEditorBoundsProvider(
    private val modelBoundsService: ModelBoundsService? = null,
) {
    /**
     * Returns local bounds for the entity, preserving stable fallbacks for assets
     * that are not loaded yet and marker-style scene objects.
     */
    fun boundsFor(entity: Entity): SceneEditorLocalBounds? {
        entity.get<TerrainDataComponent>()?.let { return terrainDataBounds(it) }
        entity.get<TerrainRendererComponent>()?.takeIf { it.model?.mesh != null }?.let { return terrainMeshBounds(it) }
        entity.get<ModelComponent>()?.let { model ->
            return modelBoundsService?.boundsFor(model.model) ?: UnitModelBounds
        }
        return when {
            entity.get<TerrainComponent>() != null -> TerrainFallbackBounds
            entity.get<PerspectiveCameraComponent>() != null -> SmallMarkerBounds
            entity.get<LightComponent>() != null -> SmallMarkerBounds
            entity.get<TransformComponent>() != null -> SmallMarkerBounds
            else -> null
        }
    }

    private fun terrainDataBounds(component: TerrainDataComponent): SceneEditorLocalBounds {
        val data = component.data
        val heights = data.heightValues()
        val minY = heights.minOrNull() ?: 0f
        val maxY = heights.maxOrNull() ?: 0f
        return SceneEditorLocalBounds(
            min = Vec3(data.minLocalX, minY, data.minLocalZ),
            max = Vec3(data.minLocalX + data.worldWidth, maxY, data.minLocalZ + data.worldHeight),
        )
    }

    private fun terrainMeshBounds(renderer: TerrainRendererComponent): SceneEditorLocalBounds {
        val positions = renderer.model?.mesh?.positions ?: return TerrainFallbackBounds
        if (positions.size < 3) return TerrainFallbackBounds

        var minX = positions[0]
        var minY = positions[1]
        var minZ = positions[2]
        var maxX = positions[0]
        var maxY = positions[1]
        var maxZ = positions[2]
        var index = 0
        while (index + 2 < positions.size) {
            val x = positions[index]
            val y = positions[index + 1]
            val z = positions[index + 2]
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)
            index += 3
        }
        return SceneEditorLocalBounds(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
    }

    private val UnitModelBounds =
        SceneEditorLocalBounds(
            min = Vec3(-0.5f, -0.5f, -0.5f),
            max = Vec3(0.5f, 0.5f, 0.5f),
        )
    private val TerrainFallbackBounds =
        SceneEditorLocalBounds(
            min = Vec3(-5f, 0f, -5f),
            max = Vec3(5f, 0.25f, 5f),
        )
    private val SmallMarkerBounds =
        SceneEditorLocalBounds(
            min = Vec3(-0.25f, -0.25f, -0.25f),
            max = Vec3(0.25f, 0.25f, 0.25f),
        )
}

/**
 * Resolves cached local-space bounds for model assets without performing loads.
 */
fun interface ModelBoundsService {
    /**
     * Returns cached local model bounds, or null while the asset is unavailable.
     */
    fun boundsFor(model: AssetRef<ModelAsset>): SceneEditorLocalBounds?
}

/**
 * Adapts the shared engine asset service into Scene Editor local bounds.
 */
class AssetServiceModelBoundsService(
    private val assets: AssetService,
) : ModelBoundsService {
    override fun boundsFor(model: AssetRef<ModelAsset>): SceneEditorLocalBounds? =
        assets.modelBounds(model)?.let { bounds ->
            SceneEditorLocalBounds(bounds.min, bounds.max)
        }
}

fun transformedBoundsCorners(
    bounds: SceneEditorLocalBounds,
    transform: TransformComponent,
): List<Vec3> {
    val min = bounds.min
    val max = bounds.max
    return listOf(
        Vec3(min.x, min.y, min.z),
        Vec3(max.x, min.y, min.z),
        Vec3(max.x, max.y, min.z),
        Vec3(min.x, max.y, min.z),
        Vec3(min.x, min.y, max.z),
        Vec3(max.x, min.y, max.z),
        Vec3(max.x, max.y, max.z),
        Vec3(min.x, max.y, max.z),
    ).map { corner ->
        transformLocalPoint(corner, transform)
    }
}

fun transformLocalPoint(
    point: Vec3,
    transform: TransformComponent,
): Vec3 {
    val scaled =
        Vec3(
            point.x * transform.scale.x,
            point.y * transform.scale.y,
            point.z * transform.scale.z,
        )
    val rotated = rotateEulerDegrees(scaled, transform.eulerDegrees)
    return transform.position + rotated
}

private fun rotateEulerDegrees(
    point: Vec3,
    eulerDegrees: Vec3,
): Vec3 {
    val pitch = Math.toRadians(eulerDegrees.x.toDouble()).toFloat()
    val yaw = Math.toRadians(eulerDegrees.y.toDouble()).toFloat()
    val roll = Math.toRadians(eulerDegrees.z.toDouble()).toFloat()

    val cosX = cos(pitch)
    val sinX = sin(pitch)
    val afterX =
        Vec3(
            point.x,
            point.y * cosX - point.z * sinX,
            point.y * sinX + point.z * cosX,
        )

    val cosY = cos(yaw)
    val sinY = sin(yaw)
    val afterY =
        Vec3(
            afterX.x * cosY + afterX.z * sinY,
            afterX.y,
            -afterX.x * sinY + afterX.z * cosY,
        )

    val cosZ = cos(roll)
    val sinZ = sin(roll)
    return Vec3(
        afterY.x * cosZ - afterY.y * sinZ,
        afterY.x * sinZ + afterY.y * cosZ,
        afterY.z,
    )
}
