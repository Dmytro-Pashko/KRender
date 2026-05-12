package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.TerrainComponent

enum class SceneValidationSeverity {
    Error,
    Warning,
    Info,
}

enum class SceneValidationIssueCode {
    MissingActiveCamera,
    MissingActiveCameraEntity,
    ActiveCameraWithoutCameraComponent,
    MissingSkyboxPath,
    MissingSkyboxDescriptor,
    MissingSkyboxTexture,
    MissingActiveTerrainEntity,
    ActiveTerrainWithoutTerrainComponent,
    MissingTerrainAsset,
    InvalidTerrainBakeResolution,
    MissingTerrainMaterialLibrary,
    MissingModelAsset,
    DuplicateEntityId,
    BrokenParentReference,
    UnsupportedComponent,
}

data class SceneValidationIssue(
    val severity: SceneValidationSeverity,
    val code: SceneValidationIssueCode,
    val message: String,
    val entityId: Long? = null,
    val assetPath: String? = null,
)

data class SceneValidationReport(
    val issues: List<SceneValidationIssue>,
) {
    val errors: List<SceneValidationIssue>
        get() = issues.filter { it.severity == SceneValidationSeverity.Error }

    val warnings: List<SceneValidationIssue>
        get() = issues.filter { it.severity == SceneValidationSeverity.Warning }

    val isValid: Boolean
        get() = errors.isEmpty()
}

/**
 * Validates scene descriptor references used by the runtime scene pipeline.
 */
object RuntimeSceneValidator {
    fun validate(
        descriptor: SceneDescriptor,
        dependencyGraph: SceneDependencyGraph,
    ): SceneValidationReport {
        val issues = mutableListOf<SceneValidationIssue>()
        val entitiesById = linkedMapOf<Long, EntityDescriptor>()
        val duplicateIds = mutableSetOf<Long>()

        descriptor.entities.forEach { entity ->
            if (entitiesById.put(entity.id, entity) != null) {
                duplicateIds += entity.id
            }
        }
        duplicateIds.forEach { entityId ->
            issues += SceneValidationIssue(
                severity = SceneValidationSeverity.Error,
                code = SceneValidationIssueCode.DuplicateEntityId,
                message = "Scene '${descriptor.name}' contains duplicate entityId=$entityId.",
                entityId = entityId,
            )
        }

        descriptor.entities.forEach { entity ->
            if (entity.parentId != null && entity.parentId !in entitiesById) {
                issues += SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.BrokenParentReference,
                    message = "Scene entityId=${entity.id} references missing parentId=${entity.parentId}.",
                    entityId = entity.id,
                )
            }
            entity.components.forEach { component ->
                if (component.type !in SupportedComponentTypes) {
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Warning,
                        code = SceneValidationIssueCode.UnsupportedComponent,
                        message = "Unsupported scene component '${component.type}' on entityId=${entity.id}.",
                        entityId = entity.id,
                    )
                }
                if (component.type == SceneComponentTypes.Model) {
                    val modelPath = component.properties["model"].normalizedValidationPath()
                    if (modelPath == null) {
                        issues += SceneValidationIssue(
                            severity = SceneValidationSeverity.Error,
                            code = SceneValidationIssueCode.MissingModelAsset,
                            message = "Scene model entityId=${entity.id} has blank model asset path.",
                            entityId = entity.id,
                        )
                    }
                }
            }
        }

        val activeCameraEntityId = descriptor.settings.activeCameraEntityId
        if (activeCameraEntityId == null) {
            issues += SceneValidationIssue(
                severity = SceneValidationSeverity.Error,
                code = SceneValidationIssueCode.MissingActiveCamera,
                message = "Scene '${descriptor.name}' has no active camera.",
            )
        } else {
            val activeCamera = entitiesById[activeCameraEntityId]
            if (activeCamera == null) {
                issues += SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.MissingActiveCameraEntity,
                    message = "Scene activeCameraEntityId=$activeCameraEntityId does not reference an existing entity.",
                    entityId = activeCameraEntityId,
                )
            } else if (!activeCamera.hasComponent(SceneComponentTypes.Camera)) {
                issues += SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.ActiveCameraWithoutCameraComponent,
                    message = "Scene activeCameraEntityId=$activeCameraEntityId does not reference an entity with PerspectiveCameraComponent.",
                    entityId = activeCameraEntityId,
                )
            }
        }

        val activeTerrainEntityId = descriptor.settings.activeTerrainEntityId
        val activeTerrain = activeTerrainEntityId?.let(entitiesById::get)
        if (activeTerrainEntityId != null && activeTerrain == null) {
            issues += SceneValidationIssue(
                severity = SceneValidationSeverity.Error,
                code = SceneValidationIssueCode.MissingActiveTerrainEntity,
                message = "Scene activeTerrainEntityId=$activeTerrainEntityId does not reference an existing entity.",
                entityId = activeTerrainEntityId,
            )
        }
        if (activeTerrain != null) {
            val terrainComponent = activeTerrain.component(SceneComponentTypes.Terrain)
            if (terrainComponent == null) {
                issues += SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.ActiveTerrainWithoutTerrainComponent,
                    message = "Scene activeTerrainEntityId=${activeTerrain.id} does not reference an entity with TerrainComponent.",
                    entityId = activeTerrain.id,
                )
            } else {
                val terrainPath = terrainComponent.properties["terrain"].normalizedValidationPath()
                if (terrainPath == null) {
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.MissingTerrainAsset,
                        message = "Scene terrain entityId=${activeTerrain.id} has blank terrain asset path.",
                        entityId = activeTerrain.id,
                    )
                }
                val bakedTextureResolution = terrainComponent.properties["bakedTextureResolution"]?.trim()?.toIntOrNull()
                    ?: DefaultTerrainBakeResolution
                if (bakedTextureResolution !in 2..8192) {
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.InvalidTerrainBakeResolution,
                        message = "Scene terrain entityId=${activeTerrain.id} has invalid bakedTextureResolution=$bakedTextureResolution.",
                        entityId = activeTerrain.id,
                    )
                }
            }
        }

        val hasTerrain = descriptor.entities.any { entity -> entity.hasComponent(SceneComponentTypes.Terrain) }
        if (hasTerrain && descriptor.settings.terrain.materialLibraryPath.normalizedValidationPath() == null) {
            issues += SceneValidationIssue(
                severity = SceneValidationSeverity.Error,
                code = SceneValidationIssueCode.MissingTerrainMaterialLibrary,
                message = "Scene '${descriptor.name}' has terrain but no terrain material library path.",
            )
        }

        if (descriptor.settings.environment.showSkybox && skyboxPath(descriptor) == null) {
            issues += SceneValidationIssue(
                severity = SceneValidationSeverity.Warning,
                code = SceneValidationIssueCode.MissingSkyboxPath,
                message = "Scene '${descriptor.name}' enables the skybox but has no skybox descriptor path.",
            )
        }

        dependencyGraph.missing.forEach { missing ->
            when (missing.dependency.kind) {
                SceneDependencyKind.Model -> issues += SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.MissingModelAsset,
                    message = missing.message,
                    entityId = missing.dependency.sourceEntityId,
                    assetPath = missing.dependency.path,
                )

                SceneDependencyKind.Terrain -> if (missing.dependency.requirement == SceneDependencyRequirement.Required) {
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.MissingTerrainAsset,
                        message = missing.message,
                        entityId = missing.dependency.sourceEntityId,
                        assetPath = missing.dependency.path,
                    )
                }

                SceneDependencyKind.TerrainMaterialLibrary -> issues += SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.MissingTerrainMaterialLibrary,
                    message = missing.message,
                    assetPath = missing.dependency.path,
                )

                SceneDependencyKind.SkyboxDescriptor -> if (descriptor.settings.environment.showSkybox) {
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Warning,
                        code = SceneValidationIssueCode.MissingSkyboxDescriptor,
                        message = missing.message,
                        assetPath = missing.dependency.path,
                    )
                }

                SceneDependencyKind.SkyboxTexture -> if (descriptor.settings.environment.showSkybox) {
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Warning,
                        code = SceneValidationIssueCode.MissingSkyboxTexture,
                        message = missing.message,
                        assetPath = missing.dependency.path,
                    )
                }

                else -> Unit
            }
        }

        dependencyGraph.warnings.forEach { warning ->
            if (issues.none { it.code == SceneValidationIssueCode.UnsupportedComponent && it.message == warning }) {
                issues += SceneValidationIssue(
                    severity = SceneValidationSeverity.Warning,
                    code = SceneValidationIssueCode.UnsupportedComponent,
                    message = warning,
                )
            }
        }

        return SceneValidationReport(issues = issues)
    }

    fun requireValid(
        descriptor: SceneDescriptor,
        report: SceneValidationReport,
    ) {
        if (report.isValid) return
        val summary = report.errors.joinToString(separator = " | ") { issue -> issue.message }
        throw IllegalStateException("Runtime scene '${descriptor.name}' is invalid: $summary")
    }

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
        val path = terrain.terrain.path.normalizedValidationPath()
        if (path == null) {
            throw IllegalStateException("Runtime terrain entityId=${entity.id} has blank terrain asset path.")
        }
        if (terrain.bakedTextureResolution !in 2..8192) {
            throw IllegalStateException(
                "Runtime terrain entityId=${entity.id} has invalid bakedTextureResolution=${terrain.bakedTextureResolution}.",
            )
        }
        return entity
    }

    fun skyboxPath(descriptor: SceneDescriptor): String? =
        descriptor.settings.environment.skyboxAssetPath.normalizedValidationPath()

    private fun EntityDescriptor.hasComponent(type: String): Boolean =
        components.any { component -> component.type == type }

    private fun EntityDescriptor.component(type: String): ComponentDescriptor? =
        components.firstOrNull { component -> component.type == type }

    private val SupportedComponentTypes = setOf(
        SceneComponentTypes.Name,
        SceneComponentTypes.Transform,
        SceneComponentTypes.Parent,
        SceneComponentTypes.Camera,
        SceneComponentTypes.Light,
        SceneComponentTypes.Model,
        SceneComponentTypes.Terrain,
    )

    private const val DefaultTerrainBakeResolution = 8192
}

private fun String?.normalizedValidationPath(): String? =
    this
        ?.trim()
        ?.replace('\\', '/')
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { value -> value.equals("null", ignoreCase = true) }
