package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.*
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
                .map(::toEntityDescriptor)
        val existingSettings = existingDescriptor?.settings
        val activeCameraEntityId =
            existingSettings
                ?.activeCameraEntityId
                ?.takeIf { id -> entities.any { it.id == id } }
                ?: world
                    .all()
                    .firstOrNull { entity -> includeEntity(entity) && entity.get<PerspectiveCameraComponent>() != null }
                    ?.id
        val activeTerrainEntityId =
            existingSettings
                ?.activeTerrainEntityId
                ?.takeIf { id -> entities.any { it.id == id && it.hasComponent(SceneComponentTypes.Terrain) } }
                ?: if (existingSettings == null) {
                    world
                        .all()
                        .firstOrNull { entity -> includeEntity(entity) && entity.get<TerrainComponent>() != null }
                        ?.id
                } else {
                    null
                }

        return SceneDescriptor(
            schemaVersion = SceneDescriptor.CurrentSchemaVersion,
            id = existingDescriptor?.id ?: generateSceneId(),
            name = sceneName,
            entities = entities,
            settings =
                SceneSettingsDescriptor(
                    activeCameraEntityId = activeCameraEntityId,
                    activeTerrainEntityId = activeTerrainEntityId,
                    lighting =
                        existingSettings?.lighting?.copy(
                            ambientColor = existingSettings.lighting.ambientColor.copy(),
                        ) ?: SceneLightingDescriptor(),
                    environment = existingSettings?.environment ?: SceneEnvironmentDescriptor(),
                    terrain = existingSettings?.terrain ?: SceneTerrainSettingsDescriptor(),
                ),
        )
    }

    override fun encode(value: SceneDescriptor): String =
        json.encodeToString(JsonObject.serializer(), value.toJsonObject())

    override fun decode(json: String): SceneDescriptor {
        val root =
            this.json.parseToJsonElement(json) as? JsonObject
                ?: throw IllegalArgumentException("Scene descriptor root must be a JSON object")
        return SceneDescriptor(
            schemaVersion = root.intOrDefault("schemaVersion", SceneDescriptor.CurrentSchemaVersion),
            id = root.requiredString("id", DocumentName),
            name = root.stringOrDefault("name", "Untitled Scene"),
            entities = readEntities(root["entities"]),
            settings = readSettings(root["settings"]),
        )
    }

    fun applyToWorld(
        descriptor: SceneDescriptor,
        world: SceneWorld,
        logger: Logger? = null,
    ) {
        SceneDeserializer.applyToWorld(descriptor, world, logger)
    }

    private fun toEntityDescriptor(entity: Entity): EntityDescriptor =
        EntityDescriptor(
            id = entity.id,
            name = entity.name,
            active = entity.active,
            parentId = entity.get<ParentComponent>()?.parentId,
            components = entity.components.all().mapNotNull(::toComponentDescriptor),
        )

    private fun toComponentDescriptor(component: Component): ComponentDescriptor? =
        when (component) {
            is NameComponent ->
                ComponentDescriptor(
                    type = SceneComponentTypes.Name,
                    properties = mapOf("name" to component.name),
                )

            is TransformComponent ->
                ComponentDescriptor(
                    type = SceneComponentTypes.Transform,
                    properties =
                        mapOf(
                            "position" to component.position.csv(),
                            "rotation" to component.eulerDegrees.csv(),
                            "scale" to component.scale.csv(),
                        ),
                )

            is ParentComponent ->
                ComponentDescriptor(
                    type = SceneComponentTypes.Parent,
                    properties = mapOf("parentId" to component.parentId.toString()),
                )

            is PerspectiveCameraComponent ->
                ComponentDescriptor(
                    type = SceneComponentTypes.Camera,
                    properties =
                        mapOf(
                            "fieldOfViewDegrees" to component.fieldOfViewDegrees.toString(),
                            "near" to component.near.toString(),
                            "far" to component.far.toString(),
                        ),
                )

            is LightComponent ->
                ComponentDescriptor(
                    type = SceneComponentTypes.Light,
                    properties =
                        mapOf(
                            "type" to component.type.name,
                            "intensity" to component.intensity.toString(),
                            "color" to component.color.csv(),
                            "direction" to component.direction.csv(),
                        ),
                )

            is ModelComponent ->
                ComponentDescriptor(
                    type = SceneComponentTypes.Model,
                    properties = mapOf("model" to component.model.path),
                )

            is TerrainComponent ->
                ComponentDescriptor(
                    type = SceneComponentTypes.Terrain,
                    properties =
                        mapOf(
                            "terrain" to
                                component.terrain.path
                                    .trim()
                                    .replace('\\', '/'),
                            "visible" to component.visible.toString(),
                            "previewMode" to component.previewMode.name,
                            "bakedTextureResolution" to component.bakedTextureResolution.toString(),
                        ),
                )

            else -> null
        }

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
                SceneEnvironmentDescriptor(
                    skyboxAssetPath = normalizedOptionalProjectPath(environmentNode.stringOrNull("skyboxAssetPath")),
                    showSkybox = environmentNode.booleanOrDefault("showSkybox", true),
                    environmentIntensity = environmentNode.floatOrDefault("environmentIntensity", 1f).coerceAtLeast(0f),
                )
            } else {
                SceneEnvironmentDescriptor()
            }

        val terrainNode = settings["terrain"] as? JsonObject
        val terrain =
            SceneTerrainSettingsDescriptor(
                materialLibraryPath =
                    terrainNode
                        ?.stringOrNull("materialLibraryPath")
                        ?.trim()
                        ?.replace('\\', '/')
                        ?.takeIf(String::isNotBlank)
                        ?: DefaultTerrainMaterialLibraryPath,
            )

        return SceneSettingsDescriptor(
            activeCameraEntityId = settings.longOrNull("activeCameraEntityId"),
            activeTerrainEntityId = settings.longOrNull("activeTerrainEntityId"),
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
            put("activeTerrainEntityId", activeTerrainEntityId?.let(::JsonPrimitive) ?: JsonNull)
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
                    put("skyboxAssetPath", environment.skyboxAssetPath?.let(::JsonPrimitive) ?: JsonNull)
                    put("showSkybox", JsonPrimitive(environment.showSkybox))
                    put("environmentIntensity", JsonPrimitive(environment.environmentIntensity))
                },
            )
            put(
                "terrain",
                buildJsonObject {
                    put("materialLibraryPath", JsonPrimitive(terrain.materialLibraryPath))
                },
            )
        }

    private fun generateSceneId(): String = "scene:${UUID.randomUUID()}"

    private fun Vec3.csv(): String = "$x,$y,$z"

    private fun Color.csv(): String = "$r,$g,$b,$a"

    private fun EntityDescriptor.hasComponent(type: String): Boolean =
        components.any { component -> component.type == type }

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
            when (component.type) {
                SceneComponentTypes.Name ->
                    entity.add(
                        NameComponent(component.properties["name"] ?: descriptor.name),
                    )

                SceneComponentTypes.Transform -> entity.add(readTransform(component, entity.id, logger))

                SceneComponentTypes.Parent ->
                    readLong(
                        raw = component.properties["parentId"],
                        defaultValue = descriptor.parentId,
                        context = "${SceneComponentTypes.Parent}.parentId",
                        entityId = entity.id,
                        logger = logger,
                    )?.let { parentId -> entity.add(ParentComponent(parentId)) }

                SceneComponentTypes.Camera -> entity.add(readCamera(component, entity.id, logger))

                SceneComponentTypes.Light -> entity.add(readLight(component, entity.id, logger))

                SceneComponentTypes.Model ->
                    readModel(component, entity.id, logger)
                        ?.let(entity::add)

                SceneComponentTypes.Terrain ->
                    readTerrain(component, entity.id, logger)
                        ?.let(entity::add)
            }
        }
    }

    private fun readTransform(
        component: ComponentDescriptor,
        entityId: Long,
        logger: Logger?,
    ): TransformComponent =
        TransformComponent(
            position =
                readVec3(
                    component.properties["position"],
                    Vec3.zero(),
                    "${SceneComponentTypes.Transform}.position",
                    entityId,
                    logger,
                ),
            eulerDegrees =
                readVec3(
                    component.properties["rotation"],
                    Vec3.zero(),
                    "${SceneComponentTypes.Transform}.rotation",
                    entityId,
                    logger,
                ),
            scale =
                readVec3(
                    component.properties["scale"],
                    Vec3.one(),
                    "${SceneComponentTypes.Transform}.scale",
                    entityId,
                    logger,
                ),
        )

    private fun readCamera(
        component: ComponentDescriptor,
        entityId: Long,
        logger: Logger?,
    ): PerspectiveCameraComponent =
        PerspectiveCameraComponent(
            fieldOfViewDegrees =
                readFloat(
                    component.properties["fieldOfViewDegrees"],
                    PerspectiveCameraComponent().fieldOfViewDegrees,
                    "${SceneComponentTypes.Camera}.fieldOfViewDegrees",
                    entityId,
                    logger,
                ),
            near =
                readFloat(
                    component.properties["near"],
                    PerspectiveCameraComponent().near,
                    "${SceneComponentTypes.Camera}.near",
                    entityId,
                    logger,
                ),
            far =
                readFloat(
                    component.properties["far"],
                    PerspectiveCameraComponent().far,
                    "${SceneComponentTypes.Camera}.far",
                    entityId,
                    logger,
                ),
        )

    private fun readLight(
        component: ComponentDescriptor,
        entityId: Long,
        logger: Logger?,
    ): LightComponent =
        LightComponent(
            type = readLightType(component.properties["type"], LightType.Directional, entityId, logger),
            intensity =
                readFloat(
                    component.properties["intensity"],
                    1f,
                    "${SceneComponentTypes.Light}.intensity",
                    entityId,
                    logger,
                ),
            color =
                readColor(
                    component.properties["color"],
                    Color.white(),
                    "${SceneComponentTypes.Light}.color",
                    entityId,
                    logger,
                ),
            direction =
                readVec3(
                    component.properties["direction"],
                    Vec3(-1f, -0.8f, -0.2f),
                    "${SceneComponentTypes.Light}.direction",
                    entityId,
                    logger,
                ),
        )

    private fun readModel(
        component: ComponentDescriptor,
        entityId: Long,
        logger: Logger?,
    ): ModelComponent? {
        val path = component.properties["model"]?.trim()?.replace('\\', '/') ?: ""
        if (path.isBlank()) {
            logger?.warn(TAG) { "Invalid ${SceneComponentTypes.Model}.model for entityId=$entityId value='<missing>'; skipping component" }
            return null
        }
        return ModelComponent(model = AssetRef.model(path))
    }

    private fun readTerrain(
        component: ComponentDescriptor,
        entityId: Long,
        logger: Logger?,
    ): TerrainComponent? {
        val path = component.properties["terrain"]?.trim()?.replace('\\', '/') ?: ""
        if (path.isBlank()) {
            logger?.warn(
                TAG,
            ) { "Invalid ${SceneComponentTypes.Terrain}.terrain for entityId=$entityId value='<missing>'; skipping component" }
            return null
        }
        val visible = component.properties["visible"]?.trim()?.toBooleanStrictOrNull() ?: true
        val previewMode = readTerrainPreviewMode(component.properties["previewMode"], entityId, logger)
        val bakedTextureResolution =
            readInt(
                component.properties["bakedTextureResolution"],
                defaultValue = 8192,
                context = "${SceneComponentTypes.Terrain}.bakedTextureResolution",
                entityId = entityId,
                logger = logger,
            ).coerceIn(2, 8192)
        return TerrainComponent(
            terrain = AssetRef.terrain(path),
            visible = visible,
            previewMode = previewMode,
            bakedTextureResolution = bakedTextureResolution,
        )
    }

    private fun readVec3(
        raw: String?,
        defaultValue: Vec3,
        context: String,
        entityId: Long,
        logger: Logger?,
    ): Vec3 {
        val values = raw?.split(',')?.map { it.trim().toFloatOrNull() }
        if (values != null && values.size >= 3 && values.take(3).all { it != null }) {
            return Vec3(values[0] ?: defaultValue.x, values[1] ?: defaultValue.y, values[2] ?: defaultValue.z)
        }
        warnParse(raw, context, entityId, logger)
        return defaultValue
    }

    private fun readColor(
        raw: String?,
        defaultValue: Color,
        context: String,
        entityId: Long,
        logger: Logger?,
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
        warnParse(raw, context, entityId, logger)
        return defaultValue
    }

    private fun readFloat(
        raw: String?,
        defaultValue: Float,
        context: String,
        entityId: Long,
        logger: Logger?,
    ): Float {
        val value = raw?.trim()?.toFloatOrNull()
        if (value != null) return value
        warnParse(raw, context, entityId, logger)
        return defaultValue
    }

    private fun readLong(
        raw: String?,
        defaultValue: Long?,
        context: String,
        entityId: Long,
        logger: Logger?,
    ): Long? {
        val value = raw?.trim()?.toLongOrNull()
        if (value != null) return value
        if (raw != null) warnParse(raw, context, entityId, logger)
        return defaultValue
    }

    private fun readInt(
        raw: String?,
        defaultValue: Int,
        context: String,
        entityId: Long,
        logger: Logger?,
    ): Int {
        val value = raw?.trim()?.toIntOrNull()
        if (value != null) return value
        warnParse(raw, context, entityId, logger)
        return defaultValue
    }

    private fun readLightType(
        raw: String?,
        defaultValue: LightType,
        entityId: Long,
        logger: Logger?,
    ): LightType {
        val value = LightType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (value != null) return value
        warnParse(raw, "${SceneComponentTypes.Light}.type", entityId, logger)
        return defaultValue
    }

    private fun readTerrainPreviewMode(
        raw: String?,
        entityId: Long,
        logger: Logger?,
    ): TerrainPreviewMode {
        val value =
            TerrainPreviewMode.entries.firstOrNull { mode ->
                mode.name.equals(raw, ignoreCase = true) ||
                    (mode == TerrainPreviewMode.MaterialTexture && raw.equals("TexturePreview", ignoreCase = true))
            }
        return when (value) {
            TerrainPreviewMode.MaterialTexture -> TerrainPreviewMode.MaterialTexture
            TerrainPreviewMode.LayerColor -> TerrainPreviewMode.LayerColor
            TerrainPreviewMode.MaterialColor,
            TerrainPreviewMode.SelectedLayerMask,
            null,
                -> {
                if (raw != null) {
                    warnParse(raw, "${SceneComponentTypes.Terrain}.previewMode", entityId, logger)
                }
                TerrainPreviewMode.LayerColor
            }
        }
    }

    private fun warnParse(
        raw: String?,
        context: String,
        entityId: Long,
        logger: Logger?,
    ) {
        logger?.warn(TAG) { "Invalid $context for entityId=$entityId value='${raw ?: "<missing>"}'; using default" }
    }

    private const val TAG = "SceneDeserializer"
}
