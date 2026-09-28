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
    MissingEnvironmentAsset,
    MissingActiveTerrainEntity,
    ActiveTerrainWithoutTerrainComponent,
    MissingTerrainAsset,
    InvalidTerrainBakeResolution,
    InvalidTerrainSettings,
    MissingTerrainMaterialLibrary,
    MissingModelAsset,
    DuplicateEntityId,
    BrokenParentReference,
    UnsupportedComponent,
    InvalidComponentProperties,
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
            issues +=
                SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.DuplicateEntityId,
                    message = "Scene '${descriptor.name}' contains duplicate entityId=$entityId.",
                    entityId = entityId,
                )
        }

        descriptor.entities.forEach { entity ->
            if (entity.parentId != null && entity.parentId !in entitiesById) {
                issues +=
                    SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.BrokenParentReference,
                        message = "Scene entityId=${entity.id} references missing parentId=${entity.parentId}.",
                        entityId = entity.id,
                    )
            }
            entity.components.forEach { component ->
                val definition = SceneComponentRegistry.find(component.type)
                if (definition == null) {
                    issues +=
                        SceneValidationIssue(
                            severity = SceneValidationSeverity.Warning,
                            code = SceneValidationIssueCode.UnsupportedComponent,
                            message = "Unsupported scene component '${component.type}' on entityId=${entity.id}.",
                            entityId = entity.id,
                        )
                }
                definition?.validate?.invoke(component.properties)?.let { error ->
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.InvalidComponentProperties,
                        message = "Invalid ${component.type} on entityId=${entity.id}: $error",
                        entityId = entity.id,
                    )
                }
                if (definition?.requiresTransform == true && !entity.hasComponent(SceneComponentTypes.Transform)) {
                    issues += SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.InvalidComponentProperties,
                        message = "${component.type} on entityId=${entity.id} requires Transform.",
                        entityId = entity.id,
                    )
                }
                if (component.type == SceneComponentTypes.Model) {
                    val modelPath = component.properties["model"].normalizedValidationPath()
                    if (modelPath == null) {
                        issues +=
                            SceneValidationIssue(
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
            issues +=
                SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.MissingActiveCamera,
                    message = "Scene '${descriptor.name}' has no active camera.",
                )
        } else {
            val activeCamera = entitiesById[activeCameraEntityId]
            if (activeCamera == null) {
                issues +=
                    SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.MissingActiveCameraEntity,
                        message = "Scene activeCameraEntityId=$activeCameraEntityId does not reference an existing entity.",
                        entityId = activeCameraEntityId,
                    )
            } else if (!activeCamera.hasComponent(SceneComponentTypes.Camera)) {
                issues +=
                    SceneValidationIssue(
                        severity = SceneValidationSeverity.Error,
                        code = SceneValidationIssueCode.ActiveCameraWithoutCameraComponent,
                        message = "Scene activeCameraEntityId=$activeCameraEntityId does not reference an entity with PerspectiveCameraComponent.",
                        entityId = activeCameraEntityId,
                    )
            }
        }

        val terrain = descriptor.settings.terrain
        val hasTerrain = terrain.terrainAssetPath != null
        if (hasTerrain && terrain.terrainAssetPath.normalizedValidationPath() == null) {
            issues += SceneValidationIssue(SceneValidationSeverity.Error, SceneValidationIssueCode.MissingTerrainAsset, "Scene Terrain path is blank.")
        }
        if (hasTerrain && terrain.bakedTextureResolution !in 2..8192) {
            issues += SceneValidationIssue(SceneValidationSeverity.Error, SceneValidationIssueCode.InvalidTerrainBakeResolution, "Invalid Scene Terrain bakedTextureResolution=${terrain.bakedTextureResolution}.")
        }
        if (hasTerrain && terrain.previewMode !in setOf("LayerColor", "MaterialColor", "MaterialTexture")) {
            issues += SceneValidationIssue(SceneValidationSeverity.Error, SceneValidationIssueCode.InvalidTerrainSettings, "Invalid Scene Terrain previewMode='${terrain.previewMode}'.")
        }
        if (hasTerrain && dependencyGraph.dependencies.none { it.kind == SceneDependencyKind.TerrainMaterialLibrary }) {
            issues +=
                SceneValidationIssue(
                    severity = SceneValidationSeverity.Error,
                    code = SceneValidationIssueCode.MissingTerrainMaterialLibrary,
                    message = "Scene '${descriptor.name}' has terrain but no terrain material library path.",
                )
        }

        dependencyGraph.missing.forEach { missing ->
            when (missing.dependency.kind) {
                SceneDependencyKind.Model ->
                    issues +=
                        SceneValidationIssue(
                            severity = SceneValidationSeverity.Error,
                            code = SceneValidationIssueCode.MissingModelAsset,
                            message = missing.message,
                            entityId = missing.dependency.sourceEntityId,
                            assetPath = missing.dependency.path,
                        )

                SceneDependencyKind.Terrain ->
                    if (missing.dependency.requirement == SceneDependencyRequirement.Required) {
                        issues +=
                            SceneValidationIssue(
                                severity = SceneValidationSeverity.Error,
                                code = SceneValidationIssueCode.MissingTerrainAsset,
                                message = missing.message,
                                entityId = missing.dependency.sourceEntityId,
                                assetPath = missing.dependency.path,
                            )
                    }

                SceneDependencyKind.TerrainMaterialLibrary ->
                    issues +=
                        SceneValidationIssue(
                            severity = SceneValidationSeverity.Error,
                            code = SceneValidationIssueCode.MissingTerrainMaterialLibrary,
                            message = missing.message,
                            assetPath = missing.dependency.path,
                        )

                SceneDependencyKind.EnvironmentManifest ->
                    issues +=
                        SceneValidationIssue(
                            severity = SceneValidationSeverity.Warning,
                            code = SceneValidationIssueCode.MissingEnvironmentAsset,
                            message = missing.message,
                            assetPath = missing.dependency.path,
                        )

                else -> Unit
            }
        }

        dependencyGraph.warnings.forEach { warning ->
            if (issues.none { it.code == SceneValidationIssueCode.UnsupportedComponent && it.message == warning }) {
                issues +=
                    SceneValidationIssue(
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

    fun requireActiveCamera(
        world: SceneWorld,
        descriptor: SceneDescriptor,
    ): Entity {
        val activeCameraEntityId =
            descriptor.settings.activeCameraEntityId
                ?: throw IllegalStateException("Runtime scene '${descriptor.name}' has no activeCameraEntityId.")
        val entity =
            world.getEntity(activeCameraEntityId)
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

    fun requireActiveTerrain(
        world: SceneWorld,
        descriptor: SceneDescriptor,
    ): Entity {
        require(descriptor.settings.terrain.terrainAssetPath != null) { "Runtime scene '${descriptor.name}' has no Terrain." }
        val entity = world.query<TerrainComponent>().singleOrNull()
            ?: throw IllegalStateException("Runtime scene must contain exactly one generated Terrain entity.")
        val terrain =
            entity.get<TerrainComponent>()
                ?: throw IllegalStateException("Runtime terrain component missing.")
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

    fun environmentAssetPath(descriptor: SceneDescriptor): String? =
        descriptor.settings.environment.environmentAssetPath
            .normalizedValidationPath()

    private fun EntityDescriptor.hasComponent(type: String): Boolean = components.any { component -> component.type == type }

    private fun EntityDescriptor.component(type: String): ComponentDescriptor? = components.firstOrNull { component -> component.type == type }

}

private fun String?.normalizedValidationPath(): String? =
    this
        ?.trim()
        ?.replace('\\', '/')
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { value -> value.equals("null", ignoreCase = true) }
