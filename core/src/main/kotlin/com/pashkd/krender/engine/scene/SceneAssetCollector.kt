package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef

enum class SceneDependencyKind {
    Model,
    Texture,
    Terrain,
    TerrainMaterialLibrary,
    SkyboxDescriptor,
    SkyboxTexture,
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
    fun collect(
        descriptor: SceneDescriptor,
        resolvedSkybox: SkyboxAssetDescriptor? = null,
    ): SceneDependencyGraph {
        val dependencies = linkedMapOf<Pair<SceneDependencyKind, String>, SceneDependency>()
        val warnings = mutableListOf<String>()
        val activeTerrainEntityId = descriptor.settings.activeTerrainEntityId
        val hasTerrain = descriptor.entities.any { entity -> entity.components.any { component -> component.type == SceneComponentTypes.Terrain } }

        descriptor.entities.forEach { entity ->
            entity.components.forEach { component ->
                when (component.type) {
                    SceneComponentTypes.Model -> component.properties["model"]
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

                    SceneComponentTypes.Terrain -> component.properties["terrain"]
                        .normalizedDependencyPath()
                        ?.let { path ->
                            dependencies.merge(
                                SceneDependency(
                                    kind = SceneDependencyKind.Terrain,
                                    path = path,
                                    requirement = if (entity.id == activeTerrainEntityId) {
                                        SceneDependencyRequirement.Required
                                    } else {
                                        SceneDependencyRequirement.Optional
                                    },
                                    sourceEntityId = entity.id,
                                    sourceComponentType = component.type,
                                    schedulableAsset = AssetRef.terrain(path),
                                ),
                            )
                        }

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

        if (hasTerrain) {
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

        descriptor.settings.environment.skyboxAssetPath
            .normalizedDependencyPath()
            ?.let { path ->
                dependencies.merge(
                    SceneDependency(
                        kind = SceneDependencyKind.SkyboxDescriptor,
                        path = path,
                        requirement = SceneDependencyRequirement.Optional,
                        sourceComponentType = "SceneSettingsDescriptor.environment",
                    ),
                )
            }

        resolvedSkybox?.texturePath
            .normalizedDependencyPath()
            ?.let { path ->
                dependencies.merge(
                    SceneDependency(
                        kind = SceneDependencyKind.SkyboxTexture,
                        path = path,
                        requirement = SceneDependencyRequirement.Optional,
                        sourceComponentType = "SkyboxAssetDescriptor",
                        schedulableAsset = AssetRef.texture(path),
                    ),
                )
            }

        val orderedDependencies = dependencies.values.toList()
        val missing = orderedDependencies.mapNotNull { dependency ->
            if (sceneFiles.exists(dependency.path)) {
                null
            } else {
                MissingSceneDependency(
                    dependency = dependency,
                    message = "Missing ${dependency.kind.name.lowercase()} dependency '${dependency.path}'.",
                )
            }
        }
        return SceneDependencyGraph(
            dependencies = orderedDependencies,
            missing = missing,
            warnings = warnings,
        )
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

private fun String?.normalizedDependencyPath(): String? =
    this
        ?.trim()
        ?.replace('\\', '/')
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { value -> value.equals("null", ignoreCase = true) }
