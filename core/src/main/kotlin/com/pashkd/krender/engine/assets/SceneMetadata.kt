package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.scene.EntityDescriptor
import com.pashkd.krender.engine.scene.SceneComponentTypes
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

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
) {
    fun toMetadataMap(): Map<String, String> = buildMap {
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
    }
}

data class SceneAssetBounds(
    val width: Float,
    val height: Float,
    val depth: Float,
) {
    fun formatted(): String =
        String.format(Locale.US, "%.2f x %.2f x %.2f", width, height, depth)
}

private fun formatSceneDecimal(value: Float): String =
    String.format(Locale.US, "%.2f", value)

/**
 * Reads scene metadata without loading the runtime world.
 */
object SceneAssetMetadataReader {
    fun read(file: File, baseDirectory: File): SceneAssetMetadata {
        val descriptor = SceneSerializer.decode(file.readText(StandardCharsets.UTF_8))
        return fromDescriptor(descriptor, baseDirectory)
    }

    internal fun fromDescriptor(descriptor: SceneDescriptor, baseDirectory: File): SceneAssetMetadata {
        val entities = descriptor.entities
        val lightEntities = entities.filter { entity -> entity.hasComponent(SceneComponentTypes.Light) }
        val activeTerrainEntity = descriptor.settings.activeTerrainEntityId?.let { id ->
            entities.firstOrNull { entity -> entity.id == id }
        }
        val activeTerrainPath = activeTerrainEntity
            ?.component(SceneComponentTypes.Terrain)
            ?.properties
            ?.get("terrain")
            ?.normalizeAssetPath()
        val activeTerrainMetadata = activeTerrainPath
            ?.let { path -> File(baseDirectory, path) }
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
            directionalLightCount = lightEntities.count { entity ->
                entity.component(SceneComponentTypes.Light)?.properties?.get("type").equals("Directional", ignoreCase = true)
            },
            pointLightCount = lightEntities.count { entity ->
                entity.component(SceneComponentTypes.Light)?.properties?.get("type").equals("Point", ignoreCase = true)
            },
            modelCount = entities.count { entity -> entity.hasComponent(SceneComponentTypes.Model) },
            terrainCount = entities.count { entity -> entity.hasComponent(SceneComponentTypes.Terrain) },
            sceneBounds = sceneBounds,
            activeCameraName = descriptor.settings.activeCameraEntityId?.let { id ->
                entities.firstOrNull { entity -> entity.id == id }?.name
            },
            activeTerrainName = activeTerrainEntity?.name,
            activeTerrainPath = activeTerrainPath,
            activeTerrainSize = activeTerrainMetadata?.size,
            activeTerrainLayerCount = activeTerrainMetadata?.layerCount,
            activeTerrainBakedResolution = activeTerrainEntity
                ?.component(SceneComponentTypes.Terrain)
                ?.properties
                ?.get("bakedTextureResolution")
                ?.trim()
                ?.toIntOrNull(),
            skyboxPath = descriptor.settings.environment.skyboxAssetPath?.normalizeAssetPath(),
            showSkybox = descriptor.settings.environment.showSkybox,
            environmentIntensity = descriptor.settings.environment.environmentIntensity,
            ambientIntensity = descriptor.settings.lighting.ambientIntensity,
            terrainMaterialLibraryPath = descriptor.settings.terrain.materialLibraryPath.normalizeAssetPath().orEmpty(),
        )
    }

    private fun calculateBounds(entities: List<EntityDescriptor>): SceneAssetBounds? {
        val positions = entities.mapNotNull { entity ->
            entity.component(SceneComponentTypes.Transform)
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

    private fun EntityDescriptor.hasComponent(type: String): Boolean =
        components.any { component -> component.type == type }

    private fun EntityDescriptor.component(type: String) =
        components.firstOrNull { component -> component.type == type }

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
