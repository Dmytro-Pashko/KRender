package com.pashkd.krender.engine.scene

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
data class SceneSettingsDescriptor(
    val activeCameraEntityId: Long? = null,
    val ambientLightEntityId: Long? = null,
)
