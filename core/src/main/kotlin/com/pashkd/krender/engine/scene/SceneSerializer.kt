package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentAssets
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.serialization.*
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainPreviewMode
import kotlinx.serialization.json.*
import java.util.*

/**
 * Converts runtime scene worlds to and from the `.krscene` descriptor format.
 */
object SceneSerializer : KRenderSerializer<SceneDescriptor> {
    private const val DocumentName = "Scene descriptor"
    private val json = KRenderJson.Pretty

    fun toDescriptor(
        world: SceneWorld,
        sceneName: String,
        existingDescriptor: SceneDescriptor? = null,
        includeEntity: (Entity) -> Boolean = { true },
    ): SceneDescriptor {
        val entities =
            world
                .all()
                .filter(includeEntity)
                .filterNot { entity -> entity.get<LightComponent>()?.type == LightType.Ambient }
                .map { entity -> toEntityDescriptor(entity, existingDescriptor?.entities?.firstOrNull { it.id == entity.id }) }
        val existingSettings = existingDescriptor?.settings
        val activeCameraEntityId =
            existingSettings
                ?.activeCameraEntityId
                ?.takeIf { id -> entities.any { it.id == id } }
                ?: world
                    .all()
                    .firstOrNull { entity -> includeEntity(entity) && entity.get<PerspectiveCameraComponent>() != null }
                    ?.id
        return SceneDescriptor(
            schemaVersion = SceneDescriptor.CurrentSchemaVersion,
            id = existingDescriptor?.id ?: generateSceneId(),
            name = sceneName,
            entities = entities,
            settings =
                SceneSettingsDescriptor(
                    activeCameraEntityId = activeCameraEntityId,
                    lighting =
                        existingSettings?.lighting?.copy(
                            ambientColor = existingSettings.lighting.ambientColor.copy(),
                        ) ?: SceneLightingDescriptor(),
                    environment = existingSettings?.environment ?: SceneEnvironmentDescriptor(),
                    terrain = existingSettings?.terrain ?: SceneTerrainSettingsDescriptor(),
                ),
        )
    }

    override fun encode(value: SceneDescriptor): String = json.encodeToString(JsonObject.serializer(), value.toJsonObject())

    override fun decode(json: String): SceneDescriptor {
        val root =
            this.json.parseToJsonElement(json) as? JsonObject
                ?: throw IllegalArgumentException("Scene descriptor root must be a JSON object")
        val schemaVersion = root.intOrDefault("schemaVersion", 1)
        require(schemaVersion in 1..SceneDescriptor.CurrentSchemaVersion) { "Unsupported scene schemaVersion=$schemaVersion" }
        val entities = readEntities(root["entities"])
        val settings = readSettings(root["settings"])
        require(schemaVersion == 1 || entities.none { it.hasComponent(SceneComponentTypes.Terrain) }) {
            "Schema v2 Terrain must be stored in settings.terrain."
        }
        val migrated = if (schemaVersion == 1) migrateTerrain(entities, settings, (root["settings"] as? JsonObject)?.longOrNull("activeTerrainEntityId")) else entities to settings
        return SceneDescriptor(
            schemaVersion = SceneDescriptor.CurrentSchemaVersion,
            id = root.requiredString("id", DocumentName),
            name = root.stringOrDefault("name", "Untitled Scene"),
            entities = migrated.first,
            settings = migrated.second,
        )
    }

    private fun migrateTerrain(entities: List<EntityDescriptor>, settings: SceneSettingsDescriptor, activeId: Long?): Pair<List<EntityDescriptor>, SceneSettingsDescriptor> {
        val terrainEntities = entities.filter { entity -> entity.hasComponent(SceneComponentTypes.Terrain) }
        if (terrainEntities.isEmpty()) return entities to settings
        require(terrainEntities.size == 1) { "Legacy scene has ${terrainEntities.size} Terrain entities; migrate them manually to one scene Terrain." }
        val terrainEntity = terrainEntities.single()
        require(activeId == null || activeId == terrainEntity.id) { "Legacy activeTerrainEntityId=$activeId does not identify the scene Terrain." }
        val component = terrainEntity.components.first { it.type == SceneComponentTypes.Terrain }
        val transform = terrainEntity.components.firstOrNull { it.type == SceneComponentTypes.Transform }?.properties.orEmpty()
        val terrain = settings.terrain.copy(
            terrainAssetPath = component.properties["terrain"],
            visible = component.properties["visible"]?.toBooleanStrictOrNull() ?: true,
            previewMode = component.properties["previewMode"] ?: "LayerColor",
            bakedTextureResolution = component.properties["bakedTextureResolution"]?.toIntOrNull() ?: 8192,
            position = transform["position"] ?: "0.0,0.0,0.0",
            rotation = transform["rotation"] ?: "0.0,0.0,0.0",
            scale = transform["scale"] ?: "1.0,1.0,1.0",
        )
        val hasChildren = entities.any { it.parentId == terrainEntity.id }
        val remaining = terrainEntity.components.filterNot { it.type == SceneComponentTypes.Terrain }
        val keepAnchor = hasChildren || remaining.any { it.type !in setOf(SceneComponentTypes.Name, SceneComponentTypes.Transform) }
        val migratedEntities = entities.mapNotNull { entity ->
            when {
                entity.id != terrainEntity.id -> entity
                keepAnchor -> entity.copy(components = remaining)
                else -> null
            }
        }
        return migratedEntities to settings.copy(terrain = terrain)
    }

    fun applyToWorld(
        descriptor: SceneDescriptor,
        world: SceneWorld,
        logger: Logger? = null,
    ) {
        SceneDeserializer.applyToWorld(descriptor, world, logger)
    }

    private fun toEntityDescriptor(entity: Entity, previous: EntityDescriptor?): EntityDescriptor =
        EntityDescriptor(
            id = entity.id,
            name = entity.name,
            active = entity.active,
            parentId = entity.get<ParentComponent>()?.parentId,
            components = entity.components.all().mapNotNull(SceneComponentRegistry::encode).map { component ->
                val original = previous?.components?.firstOrNull { it.type == component.type }
                component.copy(properties = original?.properties.orEmpty() + component.properties)
            } +
                previous?.components.orEmpty().filter { SceneComponentRegistry.find(it.type) == null && it.type != SceneComponentTypes.Terrain },
        )

    private fun readEntities(entitiesNode: JsonElement?): List<EntityDescriptor> {
        val entities = entitiesNode as? JsonArray ?: return emptyList()
        return entities.mapIndexed { index, entityNode ->
            val entityObject =
                entityNode as? JsonObject
                    ?: throw IllegalArgumentException("Scene entity at index $index must be a JSON object")
            val entityId = entityObject.requiredLong("id", DocumentName)
            EntityDescriptor(
                id = entityId,
                name = entityObject.stringOrDefault("name", "Entity $entityId"),
                active = entityObject.booleanOrDefault("active", true),
                parentId = entityObject.longOrNull("parentId"),
                components = readComponents(entityObject["components"]),
            )
        }
    }

    private fun readComponents(componentsNode: JsonElement?): List<ComponentDescriptor> {
        val components = componentsNode as? JsonArray ?: return emptyList()
        return components.mapIndexed { index, componentNode ->
            val componentObject =
                componentNode as? JsonObject
                    ?: throw IllegalArgumentException("Scene component at index $index must be a JSON object")
            ComponentDescriptor(
                type = componentObject.stringOrDefault("type", ""),
                properties = readProperties(componentObject["properties"]),
            )
        }
    }

    private fun readProperties(propertiesNode: JsonElement?): Map<String, String> {
        val properties = propertiesNode as? JsonObject ?: return emptyMap()
        return properties.entries.associate { (name, value) ->
            name to (value as? JsonPrimitive)?.content.orEmpty()
        }
    }

    private fun readSettings(settingsNode: JsonElement?): SceneSettingsDescriptor {
        val settings = settingsNode as? JsonObject ?: return SceneSettingsDescriptor()
        val lightingNode = settings["lighting"] as? JsonObject
        val lighting =
            if (lightingNode != null) {
                SceneLightingDescriptor(
                    ambientColor = parseColor(lightingNode.stringOrNull("ambientColor"), defaultAmbientLightColor()),
                    ambientIntensity = lightingNode.floatOrDefault("ambientIntensity", DefaultAmbientLightIntensity),
                )
            } else {
                SceneLightingDescriptor(
                    ambientColor = parseColor(settings.stringOrNull("ambientLightColor"), defaultAmbientLightColor()),
                    ambientIntensity = settings.floatOrDefault("ambientLightIntensity", DefaultAmbientLightIntensity),
                )
            }

        val environmentNode = settings["environment"] as? JsonObject
        val environment =
            if (environmentNode != null) {
                val environmentAssetPath =
                    if ("environmentAssetPath" in environmentNode) {
                        normalizedOptionalProjectPath(environmentNode.stringOrNull("environmentAssetPath"))
                    } else {
                        legacyEnvironmentAssetPath(environmentNode)
                    }
                SceneEnvironmentDescriptor(
                    environmentAssetPath = environmentAssetPath,
                )
            } else {
                SceneEnvironmentDescriptor()
            }

        val terrainNode = settings["terrain"] as? JsonObject
        val terrain =
            SceneTerrainSettingsDescriptor(
                terrainAssetPath = terrainNode?.stringOrNull("terrainAssetPath"),
                visible = terrainNode?.booleanOrDefault("visible", true) ?: true,
                previewMode = terrainNode?.stringOrDefault("previewMode", "LayerColor") ?: "LayerColor",
                bakedTextureResolution = terrainNode?.intOrDefault("bakedTextureResolution", 8192) ?: 8192,
                materialLibraryPath =
                    terrainNode
                        ?.stringOrNull("materialLibraryPath")
                        ?.trim()
                        ?.replace('\\', '/')
                        ?.takeIf(String::isNotBlank)
                        ?: DefaultTerrainMaterialLibraryPath,
                position = terrainNode?.stringOrDefault("position", "0.0,0.0,0.0") ?: "0.0,0.0,0.0",
                rotation = terrainNode?.stringOrDefault("rotation", "0.0,0.0,0.0") ?: "0.0,0.0,0.0",
                scale = terrainNode?.stringOrDefault("scale", "1.0,1.0,1.0") ?: "1.0,1.0,1.0",
            )

        return SceneSettingsDescriptor(
            activeCameraEntityId = settings.longOrNull("activeCameraEntityId"),
            lighting = lighting,
            environment = environment,
            terrain = terrain,
        )
    }

    private fun SceneDescriptor.toJsonObject(): JsonObject =
        buildJsonObject {
            put("schemaVersion", JsonPrimitive(schemaVersion))
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(name))
            put(
                "entities",
                buildJsonArray {
                    entities.forEach { entity -> add(entity.toJsonObject()) }
                },
            )
            put("settings", settings.toJsonObject())
        }

    private fun EntityDescriptor.toJsonObject(): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(name))
            put("active", JsonPrimitive(active))
            put("parentId", parentId?.let(::JsonPrimitive) ?: JsonNull)
            put(
                "components",
                buildJsonArray {
                    components.forEach { component -> add(component.toJsonObject()) }
                },
            )
        }

    private fun ComponentDescriptor.toJsonObject(): JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive(type))
            put(
                "properties",
                buildJsonObject {
                    properties.forEach { (name, value) ->
                        put(name, JsonPrimitive(value))
                    }
                },
            )
        }

    private fun SceneSettingsDescriptor.toJsonObject(): JsonObject =
        buildJsonObject {
            put("activeCameraEntityId", activeCameraEntityId?.let(::JsonPrimitive) ?: JsonNull)
            put(
                "lighting",
                buildJsonObject {
                    put("ambientColor", JsonPrimitive(lighting.ambientColor.csv()))
                    put("ambientIntensity", JsonPrimitive(lighting.ambientIntensity))
                },
            )
            put(
                "environment",
                buildJsonObject {
                    put("environmentAssetPath", environment.environmentAssetPath?.let(::JsonPrimitive) ?: JsonNull)
                },
            )
            put(
                "terrain",
                buildJsonObject {
                    put("terrainAssetPath", terrain.terrainAssetPath?.let(::JsonPrimitive) ?: JsonNull)
                    put("visible", JsonPrimitive(terrain.visible))
                    put("previewMode", JsonPrimitive(terrain.previewMode))
                    put("bakedTextureResolution", JsonPrimitive(terrain.bakedTextureResolution))
                    put("materialLibraryPath", JsonPrimitive(terrain.materialLibraryPath))
                    put("position", JsonPrimitive(terrain.position))
                    put("rotation", JsonPrimitive(terrain.rotation))
                    put("scale", JsonPrimitive(terrain.scale))
                },
            )
        }

    private fun generateSceneId(): String = "scene:${UUID.randomUUID()}"

    private fun Vec3.csv(): String = "$x,$y,$z"

    private fun Color.csv(): String = "$r,$g,$b,$a"

    private fun EntityDescriptor.hasComponent(type: String): Boolean = components.any { component -> component.type == type }

    private fun parseColor(
        raw: String?,
        defaultValue: Color,
    ): Color {
        val values = raw?.split(',')?.map { it.trim().toFloatOrNull() }
        if (values != null && values.size >= 3 && values.take(3).all { it != null }) {
            return Color(
                r = values[0] ?: defaultValue.r,
                g = values[1] ?: defaultValue.g,
                b = values[2] ?: defaultValue.b,
                a = values.getOrNull(3) ?: defaultValue.a,
            )
        }
        return defaultValue
    }

    private fun legacyEnvironmentAssetPath(environmentNode: JsonObject): String? {
        val legacySkyboxPath = normalizedOptionalProjectPath(environmentNode.stringOrNull("skyboxAssetPath"))
        val showSkybox = environmentNode.booleanOrDefault("showSkybox", true)
        val environmentIntensity = environmentNode.floatOrDefault("environmentIntensity", 1f).coerceAtLeast(0f)
        return if (legacySkyboxPath != null || showSkybox || environmentIntensity > 0f) {
            HdrEnvironmentAssets.DEFAULT_MANIFEST
        } else {
            null
        }
    }
}

/**
 * Rebuilds a SceneWorld from a decoded descriptor while preserving serialized entity ids.
 */
object SceneDeserializer {
    fun applyToWorld(
        descriptor: SceneDescriptor,
        world: SceneWorld,
        logger: Logger? = null,
    ) {
        world.clear()
        descriptor.entities.forEach { entityDescriptor ->
            val entity = world.createEntityWithId(entityDescriptor.id, entityDescriptor.name)
            entity.active = entityDescriptor.active
            if (entityDescriptor.components.none { it.type == SceneComponentTypes.Transform }) {
                entity.remove(TransformComponent::class)
            }
            applyComponents(entityDescriptor, entity, logger)
            if (entity.get<ParentComponent>() == null) {
                entityDescriptor.parentId?.let { parentId -> entity.add(ParentComponent(parentId)) }
            }
        }
    }

    private fun applyComponents(
        descriptor: EntityDescriptor,
        entity: Entity,
        logger: Logger?,
    ) {
        descriptor.components.forEach { component ->
            SceneComponentRegistry.decode(component)?.let(entity::add)
        }
    }

}
