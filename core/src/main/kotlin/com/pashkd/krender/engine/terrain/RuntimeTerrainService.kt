package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.scene.RuntimeSceneValidator
import com.pashkd.krender.engine.scene.SceneDescriptor

/**
 * Minimal runtime terrain loading surface used by [RuntimeTerrainService].
 *
 * Implementations are expected to resolve a scene-authored terrain asset path into
 * backend-neutral [TerrainData], throwing an exception when the terrain cannot be
 * loaded.
 */
fun interface RuntimeTerrainLoader {
    /** Loads one terrain asset identified by its normalized path. */
    fun load(path: String): TerrainData
}

/**
 * Default runtime loader that delegates to [TerrainRuntimeFactory].
 */
class TerrainRuntimeLoader(
    private val logger: Logger,
    private val persistence: TerrainRuntimePersistence,
) : RuntimeTerrainLoader {
    /** Loads terrain data from the configured runtime persistence backend. */
    override fun load(path: String): TerrainData =
        TerrainRuntimeFactory(
            logger = logger,
            persistence = persistence,
        ).load(path)
}

/**
 * Summary of the active terrain prepared for runtime rendering.
 */
data class RuntimeTerrainSetupResult(
    /** Entity id that received runtime terrain data and renderer components. */
    val entityId: Long,
    /** Stable runtime dynamic-model id assigned to the prepared terrain mesh. */
    val modelId: String,
    /** Normalized terrain asset path that was loaded for the active terrain entity. */
    val terrainPath: String,
    /** Final baked splat/preview texture resolution used by the renderer component. */
    val finalSplatResolution: Int,
)

/**
 * Prepares the scene's active terrain entity for runtime rendering.
 *
 * The service validates the active terrain reference through
 * [RuntimeSceneValidator], loads the referenced [TerrainData], attaches
 * [TerrainDataComponent] and [TerrainRendererComponent] to the active terrain
 * entity, and initializes the renderer material so
 * [RuntimeTerrainMeshSystem] can later build the final runtime mesh and baked
 * texture.
 */
class RuntimeTerrainService(
    private val logger: Logger,
    private val terrainLoader: RuntimeTerrainLoader,
    private val materialBakeService: TerrainMaterialBakeService,
) {
    /**
     * Loads and initializes the active runtime terrain described by [descriptor].
     *
     * Only the entity referenced by `descriptor.settings.activeTerrainEntityId` is
     * modified. Other terrain entities in [world] are left untouched.
     *
     * @return a summary of the prepared runtime terrain entity and generated ids
     * @throws IllegalStateException when the active terrain reference is invalid,
     * its resolution is unsupported, or the underlying terrain asset fails to load
     */
    fun prepareActiveTerrain(
        world: SceneWorld,
        descriptor: SceneDescriptor,
    ): RuntimeTerrainSetupResult {
        val entity = RuntimeSceneValidator.requireActiveTerrain(world, descriptor)
        val terrainComponent = entity.get<TerrainComponent>()
            ?: throw IllegalStateException(
                "Runtime scene activeTerrainEntityId=${descriptor.settings.activeTerrainEntityId} does not reference an entity with TerrainComponent.",
            )
        val terrainPath = terrainComponent.terrain.path.trim().replace('\\', '/')
        val finalSplatResolution = terrainComponent.bakedTextureResolution
        if (finalSplatResolution !in 2..8192) {
            throw IllegalStateException(
                "Runtime terrain entityId=${entity.id} has invalid bakedTextureResolution=$finalSplatResolution.",
            )
        }

        val terrainData = try {
            terrainLoader.load(terrainPath)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Failed to load runtime terrain entityId=${entity.id} path='$terrainPath': ${error.message}",
                error,
            )
        }

        val modelId = "runtime_terrain_${entity.id}_${terrainData.width}x${terrainData.height}"
        val finalTextureId = runtimeTerrainFinalSplatTextureId(
            entityId = entity.id,
            modelId = modelId,
        )
        entity.add(TerrainDataComponent(terrainData))
        entity.add(
            TerrainRendererComponent(
                modelId = modelId,
                material = Material(
                    baseColor = Color.white(),
                    diffuseTextureRef = MaterialTextureRef(
                        id = finalTextureId,
                        channel = "baseColor",
                        uvChannel = 0,
                    ),
                ),
                finalSplatResolution = finalSplatResolution,
            ),
        )
        logger.info(TAG) {
            "Runtime terrain prepared entityId=${entity.id} path='$terrainPath' size=${terrainData.width}x${terrainData.height} " +
                "layers=${terrainData.allLayers().size} finalSplatResolution=$finalSplatResolution"
        }
        return RuntimeTerrainSetupResult(
            entityId = entity.id,
            modelId = modelId,
            terrainPath = terrainPath,
            finalSplatResolution = finalSplatResolution,
        )
    }

    companion object {
        private const val TAG = "RuntimeTerrainService"
    }
}
