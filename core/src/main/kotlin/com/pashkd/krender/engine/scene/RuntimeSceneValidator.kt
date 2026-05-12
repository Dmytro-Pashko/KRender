package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.TerrainComponent

/**
 * Validates scene descriptor references that runtime loading depends on.
 *
 * These helpers fail fast with descriptive [IllegalStateException] messages when a
 * scene points at missing entities, missing required components, or invalid asset
 * settings that would prevent [com.pashkd.krender.game.RuntimeScene] from showing.
 */
object RuntimeSceneValidator {
    /**
     * Resolves the entity referenced by `settings.activeCameraEntityId`.
     *
     * The returned entity must exist in [world] and contain a
     * [PerspectiveCameraComponent].
     *
     * @throws IllegalStateException when the active camera id is missing, points to
     * a missing entity, or references an entity without a camera component
     */
    fun requireActiveCamera(world: SceneWorld, descriptor: SceneDescriptor): Entity {
        val activeCameraEntityId = descriptor.settings.activeCameraEntityId
            ?: throw IllegalStateException("Runtime scene '${descriptor.name}' has no activeCameraEntityId.")
        val entity = world.getEntity(activeCameraEntityId)
            ?: throw IllegalStateException(
                "Runtime scene activeCameraEntityId=$activeCameraEntityId does not reference an existing entity.",
            )
        if (entity.get<PerspectiveCameraComponent>() == null) {
            throw IllegalStateException(
                "Runtime scene activeCameraEntityId=$activeCameraEntityId does not reference an entity with PerspectiveCameraComponent.",
            )
        }
        return entity
    }

    /**
     * Resolves the entity referenced by `settings.activeTerrainEntityId`.
     *
     * The returned entity must exist in [world], contain a [TerrainComponent], use a
     * non-blank terrain asset path after trimming and slash normalization, and keep
     * `bakedTextureResolution` within the supported runtime range of `2..8192`.
     *
     * @throws IllegalStateException when any of those runtime terrain requirements
     * are not satisfied
     */
    fun requireActiveTerrain(world: SceneWorld, descriptor: SceneDescriptor): Entity {
        val activeTerrainEntityId = descriptor.settings.activeTerrainEntityId
            ?: throw IllegalStateException("Runtime scene '${descriptor.name}' has no activeTerrainEntityId.")
        val entity = world.getEntity(activeTerrainEntityId)
            ?: throw IllegalStateException(
                "Runtime scene activeTerrainEntityId=$activeTerrainEntityId does not reference an existing entity.",
            )
        val terrain = entity.get<TerrainComponent>()
            ?: throw IllegalStateException(
                "Runtime scene activeTerrainEntityId=$activeTerrainEntityId does not reference an entity with TerrainComponent.",
            )
        val path = terrain.terrain.path.trim().replace('\\', '/')
        if (path.isBlank()) {
            throw IllegalStateException("Runtime terrain entityId=${entity.id} has blank terrain asset path.")
        }
        if (terrain.bakedTextureResolution !in 2..8192) {
            throw IllegalStateException(
                "Runtime terrain entityId=${entity.id} has invalid bakedTextureResolution=${terrain.bakedTextureResolution}.",
            )
        }
        return entity
    }

    /**
     * Returns the normalized skybox descriptor path referenced by the scene.
     *
     * Whitespace is trimmed and backslashes are converted to forward slashes before
     * the path is returned.
     *
     * @throws IllegalStateException when `environment.skyboxAssetPath` is missing or blank
     */
    fun requireSkyboxPath(descriptor: SceneDescriptor): String =
        descriptor.settings.environment.skyboxAssetPath
            ?.trim()
            ?.replace('\\', '/')
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Runtime scene '${descriptor.name}' has no environment.skyboxAssetPath.")
}
