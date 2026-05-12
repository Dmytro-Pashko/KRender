package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef

/**
 * Groups the file-backed scene dependencies discovered from a [SceneDescriptor].
 *
 * The lists preserve first-seen order and contain no duplicates.
 */
data class SceneAssetCollection(
    /** Typed asset handles that should be scheduled through the asset system. */
    val assetRefs: List<AssetRef<*>>,
    /** Descriptor file paths that must be loaded separately before resolving nested assets. */
    val descriptorPaths: List<String> = emptyList(),
)

/**
 * Collects runtime asset dependencies referenced by a scene descriptor and its resolved skybox.
 *
 * Current collection rules are:
 * - model components contribute `model` paths as model assets
 * - only the active terrain entity contributes its `terrain` path as a terrain asset
 * - the scene environment contributes `skyboxAssetPath` as a descriptor dependency
 * - an optional resolved [SkyboxAssetDescriptor] contributes its texture asset
 *
 * Collected paths are trimmed, normalized to forward slashes, de-duplicated, and returned in
 * first-seen order.
 */
object SceneAssetCollector {
    /**
     * Scans [descriptor] for external dependencies needed by [com.pashkd.krender.game.RuntimeScene].
     *
     * @param descriptor scene data to inspect for model, terrain, and skybox descriptor references
     * @param skybox resolved skybox descriptor referenced by the scene, when already loaded, so its
     * texture file can be included in the returned asset handles
     * @return a de-duplicated dependency snapshot split into schedulable asset handles and
     * descriptor files that must be loaded separately
     */
    fun collect(
        descriptor: SceneDescriptor,
        skybox: SkyboxAssetDescriptor? = null,
    ): SceneAssetCollection {
        val assetRefs = linkedSetOf<AssetRef<*>>()
        val descriptorPaths = linkedSetOf<String>()
        val entitiesById = descriptor.entities.associateBy(EntityDescriptor::id)

        descriptor.entities.forEach { entity ->
            entity.components.forEach { component ->
                when (component.type) {
                    SceneComponentTypes.Model -> component.properties["model"]
                        ?.normalizedAssetPath()
                        ?.takeIf(String::isNotBlank)
                        ?.let { path -> assetRefs += AssetRef.model(path) }
                }
            }
        }

        descriptor.settings.activeTerrainEntityId
            ?.let(entitiesById::get)
            ?.components
            ?.firstOrNull { component -> component.type == SceneComponentTypes.Terrain }
            ?.properties
            ?.get("terrain")
            ?.normalizedAssetPath()
            ?.takeIf(String::isNotBlank)
            ?.let { path -> assetRefs += AssetRef.terrain(path) }

        descriptor.settings.environment.skyboxAssetPath
            ?.normalizedAssetPath()
            ?.takeIf(String::isNotBlank)
            ?.let(descriptorPaths::add)

        skybox?.texturePath
            ?.normalizedAssetPath()
            ?.takeIf(String::isNotBlank)
            ?.let { path -> assetRefs += AssetRef.texture(path) }

        return SceneAssetCollection(
            assetRefs = assetRefs.toList(),
            descriptorPaths = descriptorPaths.toList(),
        )
    }

    /** Normalizes scene-authored asset paths to the forward-slash form used by runtime loaders. */
    private fun String.normalizedAssetPath(): String =
        trim().replace('\\', '/')
}
