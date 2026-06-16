package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Lightweight scene metadata extracted from a `.krscene` file for the asset browser.
 */
data class SceneAssetMetadata(
    val sceneName: String,
    val schemaVersion: Int,
    val entityCount: Int,
    val activeEntityCount: Int,
    val inactiveEntityCount: Int,
    val rootEntityCount: Int,
    val cameraCount: Int,
    val lightCount: Int,
    val directionalLightCount: Int,
    val pointLightCount: Int,
    val modelCount: Int,
    val terrainCount: Int,
    val sceneBounds: SceneAssetBounds?,
    val activeCameraName: String?,
    val activeTerrainName: String?,
    val activeTerrainPath: String?,
    val activeTerrainSize: String?,
    val activeTerrainLayerCount: Int?,
    val activeTerrainBakedResolution: Int?,
    val skyboxPath: String?,
    val showSkybox: Boolean,
    val environmentIntensity: Float,
    val ambientIntensity: Float,
    val terrainMaterialLibraryPath: String,
    val dependencyCount: Int,
    val missingDependencyCount: Int,
    val validationErrorCount: Int,
    val validationWarningCount: Int,
    val validationIssuePreview: List<String>,
) {
    fun toMetadataMap(): Map<String, String> =
        buildMap {
            put("sceneName", sceneName)
            put("sceneSchemaVersion", schemaVersion.toString())
            put("sceneEntityCount", entityCount.toString())
            put("sceneActiveEntityCount", activeEntityCount.toString())
            put("sceneInactiveEntityCount", inactiveEntityCount.toString())
            put("sceneRootEntityCount", rootEntityCount.toString())
            put("sceneCameraCount", cameraCount.toString())
            put("sceneLightCount", lightCount.toString())
            put("sceneDirectionalLightCount", directionalLightCount.toString())
            put("scenePointLightCount", pointLightCount.toString())
            put("sceneModelCount", modelCount.toString())
            put("sceneTerrainCount", terrainCount.toString())
            sceneBounds?.let { put("sceneBounds", it.formatted()) }
            activeCameraName?.let { put("sceneActiveCameraName", it) }
            activeTerrainName?.let { put("sceneActiveTerrainName", it) }
            activeTerrainPath?.let { put("sceneActiveTerrainPath", it) }
            activeTerrainSize?.let { put("sceneTerrainSize", it) }
            activeTerrainLayerCount?.let { put("sceneTerrainLayerCount", it.toString()) }
            activeTerrainBakedResolution?.let { put("sceneTerrainBakedResolution", it.toString()) }
            skyboxPath?.let { put("sceneSkyboxPath", it) }
            put("sceneSkyboxVisible", showSkybox.toString())
            put("sceneEnvironmentIntensity", formatSceneDecimal(environmentIntensity))
            put("sceneAmbientIntensity", formatSceneDecimal(ambientIntensity))
            put("sceneTerrainMaterialLibraryPath", terrainMaterialLibraryPath)
            put("sceneDependencyCount", dependencyCount.toString())
            put("sceneMissingDependencyCount", missingDependencyCount.toString())
            put("sceneValidationErrorCount", validationErrorCount.toString())
            put("sceneValidationWarningCount", validationWarningCount.toString())
            if (validationIssuePreview.isNotEmpty()) {
                put("sceneValidationIssuePreview", validationIssuePreview.joinToString(" | "))
            }
        }
}

data class SceneAssetBounds(
    val width: Float,
    val height: Float,
    val depth: Float,
) {
    fun formatted(): String = String.format(Locale.US, "%.2f x %.2f x %.2f", width, height, depth)
}

private fun formatSceneDecimal(value: Float): String = String.format(Locale.US, "%.2f", value)

/**
 * Reads scene metadata without loading the runtime world.
 */
object SceneAssetMetadataReader {
    fun read(
        file: File,
        baseDirectory: File,
    ): SceneAssetMetadata {
        val descriptor = SceneSerializer.decode(file.readText(StandardCharsets.UTF_8))
        return fromDescriptor(descriptor, baseDirectory)
    }

    internal fun fromDescriptor(
        descriptor: SceneDescriptor,
        baseDirectory: File,
    ): SceneAssetMetadata {
        val sceneFiles = DirectorySceneFileService(baseDirectory)
        val resolvedSkybox =
            RuntimeSceneValidator
                .skyboxPath(descriptor)
                ?.let { path ->
                    runCatching {
                        SkyboxAssetService(
                            sceneFiles,
                            MetadataLogger,
                        ).loadRequired(path)
                    }.getOrNull()
                }
        val dependencyGraph = SceneDependencyCollector(sceneFiles).collect(descriptor, resolvedSkybox)
        val validationReport = RuntimeSceneValidator.validate(descriptor, dependencyGraph)
        val entities = descriptor.entities
        val lightEntities = entities.filter { entity -> entity.hasComponent(SceneComponentTypes.Light) }
        val activeTerrainEntity =
            descriptor.settings.activeTerrainEntityId?.let { id ->
                entities.firstOrNull { entity -> entity.id == id }
            }
        val activeTerrainPath =
            activeTerrainEntity
                ?.component(SceneComponentTypes.Terrain)
                ?.properties
                ?.get("terrain")
                ?.normalizeAssetPath()
        val activeTerrainMetadata =
            activeTerrainPath
                ?.let { path -> resolveSceneFile(baseDirectory, path) }
                ?.takeIf(File::isFile)
                ?.let(TerrainMetadataReader::read)
        val sceneBounds = calculateBounds(entities)

        return SceneAssetMetadata(
            sceneName = descriptor.name,
            schemaVersion = descriptor.schemaVersion,
            entityCount = entities.size,
            activeEntityCount = entities.count(EntityDescriptor::active),
            inactiveEntityCount = entities.count { entity -> !entity.active },
            rootEntityCount = entities.count { entity -> entity.parentId == null },
            cameraCount = entities.count { entity -> entity.hasComponent(SceneComponentTypes.Camera) },
            lightCount = lightEntities.size,
            directionalLightCount =
                lightEntities.count { entity ->
                    entity
                        .component(SceneComponentTypes.Light)
                        ?.properties
                        ?.get("type")
                        .equals("Directional", ignoreCase = true)
                },
            pointLightCount =
                lightEntities.count { entity ->
                    entity
                        .component(SceneComponentTypes.Light)
                        ?.properties
                        ?.get("type")
                        .equals("Point", ignoreCase = true)
                },
            modelCount = entities.count { entity -> entity.hasComponent(SceneComponentTypes.Model) },
            terrainCount = entities.count { entity -> entity.hasComponent(SceneComponentTypes.Terrain) },
            sceneBounds = sceneBounds,
            activeCameraName =
                descriptor.settings.activeCameraEntityId?.let { id ->
                    entities.firstOrNull { entity -> entity.id == id }?.name
                },
            activeTerrainName = activeTerrainEntity?.name,
            activeTerrainPath = activeTerrainPath,
            activeTerrainSize = activeTerrainMetadata?.size,
            activeTerrainLayerCount = activeTerrainMetadata?.layerCount,
            activeTerrainBakedResolution =
                activeTerrainEntity
                    ?.component(SceneComponentTypes.Terrain)
                    ?.properties
                    ?.get("bakedTextureResolution")
                    ?.trim()
                    ?.toIntOrNull(),
            skyboxPath =
                descriptor.settings.environment.skyboxAssetPath
                    ?.normalizeAssetPath(),
            showSkybox = descriptor.settings.environment.showSkybox,
            environmentIntensity = descriptor.settings.environment.environmentIntensity,
            ambientIntensity = descriptor.settings.lighting.ambientIntensity,
            terrainMaterialLibraryPath =
                descriptor.settings.terrain.materialLibraryPath
                    .normalizeAssetPath()
                    .orEmpty(),
            dependencyCount = dependencyGraph.dependencies.size,
            missingDependencyCount = dependencyGraph.missing.size,
            validationErrorCount = validationReport.errors.size,
            validationWarningCount = validationReport.warnings.size,
            validationIssuePreview = validationReport.issues.take(3).map { issue -> issue.message },
        )
    }

    private fun resolveSceneFile(
        baseDirectory: File,
        path: String,
    ): File {
        val direct = File(path)
        return if (direct.isAbsolute) direct else File(baseDirectory, path)
    }

    private fun calculateBounds(entities: List<EntityDescriptor>): SceneAssetBounds? {
        val positions =
            entities.mapNotNull { entity ->
                entity
                    .component(SceneComponentTypes.Transform)
                    ?.properties
                    ?.get("position")
                    ?.parseVec3()
            }
        if (positions.isEmpty()) return null

        var minX = positions.first().first
        var minY = positions.first().second
        var minZ = positions.first().third
        var maxX = minX
        var maxY = minY
        var maxZ = minZ
        positions.drop(1).forEach { (x, y, z) ->
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)
        }
        return SceneAssetBounds(
            width = maxX - minX,
            height = maxY - minY,
            depth = maxZ - minZ,
        )
    }

    private fun EntityDescriptor.hasComponent(type: String): Boolean = components.any { component -> component.type == type }

    private fun EntityDescriptor.component(type: String) = components.firstOrNull { component -> component.type == type }

    private fun String.parseVec3(): Triple<Float, Float, Float>? {
        val values = split(',').map { value -> value.trim().toFloatOrNull() }
        if (values.size < 3) return null
        val x = values[0] ?: return null
        val y = values[1] ?: return null
        val z = values[2] ?: return null
        return Triple(x, y, z)
    }

    private fun String?.normalizeAssetPath(): String? =
        this
            ?.trim()
            ?.replace('\\', '/')
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { value -> value.equals("null", ignoreCase = true) }
}

private class DirectorySceneFileService(
    private val baseDirectory: File,
) : SceneFileService {
    override fun writeText(
        path: String,
        text: String,
    ) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(text, StandardCharsets.UTF_8)
    }

    override fun readText(path: String): String = resolve(path).readText(StandardCharsets.UTF_8)

    override fun ensureDirectories(path: String) {
        resolve(path).parentFile?.mkdirs()
    }

    override fun exists(path: String): Boolean = resolve(path).isFile

    override fun describeReadableSource(path: String): String = if (exists(path)) "file" else "missing"

    private fun resolve(path: String): File {
        val normalized = path.trim().replace('\\', '/')
        val file = File(normalized)
        return if (file.isAbsolute) file else File(baseDirectory, normalized)
    }
}

private object MetadataLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) = Unit
}
