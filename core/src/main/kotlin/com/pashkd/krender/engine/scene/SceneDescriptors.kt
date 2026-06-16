package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.Color

object SceneComponentTypes {
    const val Name = "NameComponent"
    const val Transform = "TransformComponent"
    const val Parent = "ParentComponent"
    const val Camera = "PerspectiveCameraComponent"
    const val Light = "LightComponent"
    const val Model = "ModelComponent"
    const val Terrain = "TerrainComponent"
}

/**
 * Serializable scene root shape used by the Scene Editor.
 */
data class SceneDescriptor(
    val schemaVersion: Int = CurrentSchemaVersion,
    val id: String,
    val name: String,
    val entities: List<EntityDescriptor> = emptyList(),
    val settings: SceneSettingsDescriptor = SceneSettingsDescriptor(),
) {
    companion object {
        const val CurrentSchemaVersion = 1
    }
}

/**
 * Serializable entity snapshot with a stable editor id and component list.
 */
data class EntityDescriptor(
    val id: Long,
    val name: String,
    val active: Boolean = true,
    val parentId: Long? = null,
    val components: List<ComponentDescriptor> = emptyList(),
)

/**
 * Serializable component payload keyed by engine/editor component type.
 */
data class ComponentDescriptor(
    val type: String,
    val properties: Map<String, String> = emptyMap(),
)

/**
 * Serializable scene-level settings kept separate from entity data.
 */
data class SceneLightingDescriptor(
    val ambientColor: Color = defaultAmbientLightColor(),
    val ambientIntensity: Float = DefaultAmbientLightIntensity,
)

data class SceneEnvironmentDescriptor(
    val skyboxAssetPath: String? = null,
    val showSkybox: Boolean = true,
    val environmentIntensity: Float = 1f,
)

data class SceneTerrainSettingsDescriptor(
    val materialLibraryPath: String = DefaultTerrainMaterialLibraryPath,
)

data class SceneSettingsDescriptor(
    val activeCameraEntityId: Long? = null,
    val activeTerrainEntityId: Long? = null,
    val lighting: SceneLightingDescriptor = SceneLightingDescriptor(),
    val environment: SceneEnvironmentDescriptor = SceneEnvironmentDescriptor(),
    val terrain: SceneTerrainSettingsDescriptor = SceneTerrainSettingsDescriptor(),
)

fun defaultAmbientLightColor(): Color = Color(0.45f, 0.5f, 0.58f, 1f)

const val DefaultAmbientLightIntensity: Float = 0.55f
const val DefaultTerrainMaterialLibraryPath: String = "materials/terrain_materials.json"
