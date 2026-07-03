package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef

enum class SceneDependencyKind {
    Model,
    Texture,
    Terrain,
    TerrainMaterialLibrary,
    EnvironmentManifest,
    Material,
    Shader,
    Audio,
    Script,
    Unknown,
}

enum class SceneDependencyRequirement {
    Required,
    Optional,
}

data class SceneDependency(
    val kind: SceneDependencyKind,
    val path: String,
    val requirement: SceneDependencyRequirement,
    val sourceEntityId: Long? = null,
    val sourceComponentType: String? = null,
    val schedulableAsset: AssetRef<*>? = null,
)

data class MissingSceneDependency(
    val dependency: SceneDependency,
    val message: String,
)

data class SceneDependencyGraph(
    val dependencies: List<SceneDependency>,
    val missing: List<MissingSceneDependency>,
    val warnings: List<String>,
) {
    val schedulableAssets: List<AssetRef<*>>
        get() = dependencies.mapNotNull { it.schedulableAsset }
}

/**
 * Collects normalized external dependencies referenced by a scene descriptor.
 */
class SceneDependencyCollector(
    private val sceneFiles: SceneFileService,
) {
    fun collect(descriptor: SceneDescriptor): SceneDependencyGraph {
        val dependencies = linkedMapOf<Pair<SceneDependencyKind, String>, SceneDependency>()
        val warnings = mutableListOf<String>()
        collectEntityDependencies(descriptor, dependencies, warnings)
        collectTerrainMaterialDependency(descriptor, dependencies)
        collectEnvironmentDependency(descriptor, dependencies)
        val orderedDependencies = dependencies.values.toList()
        val missing = missingDependencies(orderedDependencies)
        return SceneDependencyGraph(
            dependencies = orderedDependencies,
            missing = missing,
            warnings = warnings,
        )
    }

    private fun collectEntityDependencies(
        descriptor: SceneDescriptor,
        dependencies: MutableMap<Pair<SceneDependencyKind, String>, SceneDependency>,
        warnings: MutableList<String>,
    ) {
        val activeTerrainEntityId = descriptor.settings.activeTerrainEntityId
        descriptor.entities.forEach { entity ->
            entity.components.forEach { component ->
                when (component.type) {
                    SceneComponentTypes.Model -> collectModelDependency(entity, component, dependencies)
                    SceneComponentTypes.Terrain -> collectTerrainDependency(entity, component, activeTerrainEntityId, dependencies)
                    SceneComponentTypes.Name,
                    SceneComponentTypes.Transform,
                    SceneComponentTypes.Parent,
                    SceneComponentTypes.Camera,
                    SceneComponentTypes.Light,
                    -> Unit
                    else -> warnings += "Unsupported scene component '${component.type}' on entityId=${entity.id}."
                }
            }
        }
    }

    private fun collectModelDependency(
        entity: EntityDescriptor,
        component: ComponentDescriptor,
        dependencies: MutableMap<Pair<SceneDependencyKind, String>, SceneDependency>,
    ) {
        component.properties["model"]
            .normalizedDependencyPath()
            ?.let { path ->
                dependencies.merge(
                    SceneDependency(
                        kind = SceneDependencyKind.Model,
                        path = path,
                        requirement = SceneDependencyRequirement.Required,
                        sourceEntityId = entity.id,
                        sourceComponentType = component.type,
                        schedulableAsset = AssetRef.model(path),
                    ),
                )
            }
    }

    private fun collectTerrainDependency(
        entity: EntityDescriptor,
        component: ComponentDescriptor,
        activeTerrainEntityId: Long?,
        dependencies: MutableMap<Pair<SceneDependencyKind, String>, SceneDependency>,
    ) {
        component.properties["terrain"]
            .normalizedDependencyPath()
            ?.let { path ->
                dependencies.merge(
                    SceneDependency(
                        kind = SceneDependencyKind.Terrain,
                        path = path,
                        requirement = terrainRequirement(entity.id, activeTerrainEntityId),
                        sourceEntityId = entity.id,
                        sourceComponentType = component.type,
                        schedulableAsset = AssetRef.terrain(path),
                    ),
                )
            }
    }

    private fun collectTerrainMaterialDependency(
        descriptor: SceneDescriptor,
        dependencies: MutableMap<Pair<SceneDependencyKind, String>, SceneDependency>,
    ) {
        if (!descriptor.hasTerrain()) return
        descriptor.settings.terrain.materialLibraryPath
            .normalizedDependencyPath()
            ?.let { path ->
                dependencies.merge(
                    SceneDependency(
                        kind = SceneDependencyKind.TerrainMaterialLibrary,
                        path = path,
                        requirement = SceneDependencyRequirement.Required,
                        sourceComponentType = "SceneSettingsDescriptor.terrain",
                    ),
                )
            }
    }

    private fun collectEnvironmentDependency(
        descriptor: SceneDescriptor,
        dependencies: MutableMap<Pair<SceneDependencyKind, String>, SceneDependency>,
    ) {
        descriptor.settings.environment.environmentAssetPath
            .normalizedDependencyPath()
            ?.let { path ->
                dependencies.merge(
                    SceneDependency(
                        kind = SceneDependencyKind.EnvironmentManifest,
                        path = path,
                        requirement = SceneDependencyRequirement.Optional,
                        sourceComponentType = "SceneSettingsDescriptor.environment",
                    ),
                )
            }
    }

    private fun missingDependencies(dependencies: List<SceneDependency>): List<MissingSceneDependency> =
        dependencies.mapNotNull { dependency ->
            if (sceneFiles.exists(dependency.path)) {
                null
            } else {
                MissingSceneDependency(
                    dependency = dependency,
                    message = "Missing ${dependency.kind.name.lowercase()} dependency '${dependency.path}'.",
                )
            }
        }

    private fun terrainRequirement(
        entityId: Long,
        activeTerrainEntityId: Long?,
    ): SceneDependencyRequirement =
        if (entityId == activeTerrainEntityId) {
            SceneDependencyRequirement.Required
        } else {
            SceneDependencyRequirement.Optional
        }

    private fun MutableMap<Pair<SceneDependencyKind, String>, SceneDependency>.merge(dependency: SceneDependency) {
        val key = dependency.kind to dependency.path
        val existing = this[key]
        if (existing == null) {
            this[key] = dependency
            return
        }
        if (existing.requirement == SceneDependencyRequirement.Required ||
            dependency.requirement == SceneDependencyRequirement.Optional
        ) {
            return
        }
        this[key] = existing.copy(requirement = SceneDependencyRequirement.Required)
    }
}

private fun SceneDescriptor.hasTerrain(): Boolean = entities.any { entity -> entity.components.any { component -> component.type == SceneComponentTypes.Terrain } }

private fun String?.normalizedDependencyPath(): String? =
    this
        ?.trim()
        ?.replace('\\', '/')
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { value -> value.equals("null", ignoreCase = true) }
