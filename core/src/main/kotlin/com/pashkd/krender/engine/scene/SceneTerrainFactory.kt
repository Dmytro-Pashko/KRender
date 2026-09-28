package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainPreviewMode
import com.pashkd.krender.engine.terrain.TerrainPersistence

fun resolveSceneTerrainMaterialLibraryPath(
    terrainPath: String,
    fallbackPath: String,
    files: SceneFileService,
): String =
    TerrainPersistence(files = files).loadDescriptor(terrainPath).materialLibraryPath
        ?.trim()
        ?.replace('\\', '/')
        ?.takeIf(String::isNotBlank)
        ?: fallbackPath

object SceneTerrainFactory {
    fun create(world: SceneWorld, settings: SceneTerrainSettingsDescriptor): Entity? {
        val path = settings.terrainAssetPath?.trim()?.replace('\\', '/')?.takeIf(String::isNotBlank) ?: return null
        val entity = world.createEntity("Scene Terrain")
        entity.transform.position = settings.position.toSceneVec3(Vec3.zero())
        entity.transform.eulerDegrees = settings.rotation.toSceneVec3(Vec3.zero())
        entity.transform.scale = settings.scale.toSceneVec3(Vec3.one())
        entity.add(
            TerrainComponent(
                terrain = AssetRef.terrain(path),
                visible = settings.visible,
                previewMode = TerrainPreviewMode.entries.firstOrNull { it.name == settings.previewMode }
                    ?: TerrainPreviewMode.LayerColor,
                bakedTextureResolution = settings.bakedTextureResolution,
            ),
        )
        return entity
    }
}

internal fun String.toSceneVec3(fallback: Vec3): Vec3 {
    val parts = split(',').map(String::trim).map(String::toFloatOrNull)
    return if (parts.size == 3 && parts.all { it != null }) {
        Vec3(parts[0]!!, parts[1]!!, parts[2]!!)
    } else {
        fallback
    }
}
