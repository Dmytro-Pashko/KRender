package com.pashkd.krender.engine.scene

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.NameComponent
import com.pashkd.krender.engine.api.ParentComponent
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.TerrainComponent
import java.util.UUID

/**
 * Converts runtime scene worlds to and from the `.krscene` descriptor format.
 */
object SceneSerializer {
    fun toDescriptor(
        world: SceneWorld,
        sceneName: String,
        existingDescriptor: SceneDescriptor? = null,
        includeEntity: (Entity) -> Boolean = { true },
    ): SceneDescriptor {
        val entities = world.all()
            .filter(includeEntity)
            .map(::toEntityDescriptor)
        val existingSettings = existingDescriptor?.settings
        val activeCameraEntityId = existingSettings
            ?.activeCameraEntityId
            ?.takeIf { id -> entities.any { it.id == id } }
            ?: world.all()
                .firstOrNull { entity -> includeEntity(entity) && entity.get<PerspectiveCameraComponent>() != null }
                ?.id
        val ambientLightEntityId = existingSettings
            ?.ambientLightEntityId
            ?.takeIf { id -> entities.any { it.id == id } }

        return SceneDescriptor(
            schemaVersion = SceneDescriptor.CurrentSchemaVersion,
            id = existingDescriptor?.id ?: generateSceneId(),
            name = sceneName,
            entities = entities,
            settings = SceneSettingsDescriptor(
                activeCameraEntityId = activeCameraEntityId,
                ambientLightEntityId = ambientLightEntityId,
            ),
        )
    }

    fun encode(descriptor: SceneDescriptor): String =
        buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": ${descriptor.schemaVersion},")
            appendLine("  \"id\": ${jsonString(descriptor.id)},")
            appendLine("  \"name\": ${jsonString(descriptor.name)},")
            appendLine("  \"entities\": [")
            descriptor.entities.forEachIndexed { index, entity ->
                append(encodeEntity(entity, "    "))
                appendLine(if (index == descriptor.entities.lastIndex) "" else ",")
            }
            appendLine("  ],")
            appendLine("  \"settings\": {")
            appendLine("    \"activeCameraEntityId\": ${descriptor.settings.activeCameraEntityId ?: "null"},")
            appendLine("    \"ambientLightEntityId\": ${descriptor.settings.ambientLightEntityId ?: "null"}")
            appendLine("  }")
            appendLine("}")
        }

    fun decode(jsonText: String): SceneDescriptor {
        val root = JsonReader().parse(jsonText)
        require(root.isObject) { "Scene descriptor root must be a JSON object" }
        return SceneDescriptor(
            schemaVersion = root.getInt("schemaVersion", SceneDescriptor.CurrentSchemaVersion),
            id = root.getString("id"),
            name = root.getString("name", "Untitled Scene"),
            entities = readEntities(root.get("entities")),
            settings = readSettings(root.get("settings")),
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
            is NameComponent -> ComponentDescriptor(
                type = "NameComponent",
                properties = mapOf("name" to component.name),
            )

            is TransformComponent -> ComponentDescriptor(
                type = "TransformComponent",
                properties = mapOf(
                    "position" to component.position.csv(),
                    "rotation" to component.eulerDegrees.csv(),
                    "scale" to component.scale.csv(),
                ),
            )

            is ParentComponent -> ComponentDescriptor(
                type = "ParentComponent",
                properties = mapOf("parentId" to component.parentId.toString()),
            )

            is PerspectiveCameraComponent -> ComponentDescriptor(
                type = "PerspectiveCameraComponent",
                properties = mapOf(
                    "fieldOfViewDegrees" to component.fieldOfViewDegrees.toString(),
                    "near" to component.near.toString(),
                    "far" to component.far.toString(),
                ),
            )

            is LightComponent -> ComponentDescriptor(
                type = "LightComponent",
                properties = mapOf(
                    "type" to component.type.name,
                    "intensity" to component.intensity.toString(),
                    "color" to component.color.csv(),
                    "direction" to component.direction.csv(),
                ),
            )

            is ModelComponent -> ComponentDescriptor(
                type = "ModelComponent",
                properties = mapOf("model" to component.model.path),
            )

            is TerrainComponent -> ComponentDescriptor(
                type = "TerrainComponent",
                properties = mapOf(
                    "terrain" to component.terrain.path.trim().replace('\\', '/'),
                    "visible" to component.visible.toString(),
                ),
            )

            else -> null
        }

    private fun encodeEntity(entity: EntityDescriptor, indent: String): String =
        buildString {
            appendLine("${indent}{")
            appendLine("$indent  \"id\": ${entity.id},")
            appendLine("$indent  \"name\": ${jsonString(entity.name)},")
            appendLine("$indent  \"active\": ${entity.active},")
            appendLine("$indent  \"parentId\": ${entity.parentId ?: "null"},")
            appendLine("$indent  \"components\": [")
            entity.components.forEachIndexed { index, component ->
                append(encodeComponent(component, "$indent    "))
                appendLine(if (index == entity.components.lastIndex) "" else ",")
            }
            appendLine("$indent  ]")
            append("$indent}")
        }

    private fun encodeComponent(component: ComponentDescriptor, indent: String): String =
        buildString {
            appendLine("${indent}{")
            appendLine("$indent  \"type\": ${jsonString(component.type)},")
            appendLine("$indent  \"properties\": {")
            component.properties.entries.forEachIndexed { index, entry ->
                val suffix = if (index == component.properties.size - 1) "" else ","
                appendLine("$indent    ${jsonString(entry.key)}: ${jsonString(entry.value)}$suffix")
            }
            appendLine("$indent  }")
            append("$indent}")
        }

    private fun readEntities(entitiesNode: JsonValue?): List<EntityDescriptor> {
        if (entitiesNode == null || !entitiesNode.isArray) return emptyList()
        return entitiesNode.map { entityNode ->
            EntityDescriptor(
                id = entityNode.getLong("id"),
                name = entityNode.getString("name", "Entity ${entityNode.getLong("id")}"),
                active = entityNode.getBoolean("active", true),
                parentId = entityNode.get("parentId")?.takeUnless { it.isNull }?.asLong(),
                components = readComponents(entityNode.get("components")),
            )
        }
    }

    private fun readComponents(componentsNode: JsonValue?): List<ComponentDescriptor> {
        if (componentsNode == null || !componentsNode.isArray) return emptyList()
        return componentsNode.map { componentNode ->
            ComponentDescriptor(
                type = componentNode.getString("type", ""),
                properties = readProperties(componentNode.get("properties")),
            )
        }
    }

    private fun readProperties(propertiesNode: JsonValue?): Map<String, String> {
        if (propertiesNode == null || !propertiesNode.isObject) return emptyMap()
        val properties = linkedMapOf<String, String>()
        var child = propertiesNode.child
        while (child != null) {
            child.name?.let { name -> properties[name] = child.asString() }
            child = child.next
        }
        return properties
    }

    private fun readSettings(settingsNode: JsonValue?): SceneSettingsDescriptor {
        if (settingsNode == null || !settingsNode.isObject) return SceneSettingsDescriptor()
        return SceneSettingsDescriptor(
            activeCameraEntityId = settingsNode.get("activeCameraEntityId")?.takeUnless { it.isNull }?.asLong(),
            ambientLightEntityId = settingsNode.get("ambientLightEntityId")?.takeUnless { it.isNull }?.asLong(),
        )
    }

    private fun generateSceneId(): String = "scene:${UUID.randomUUID()}"

    private fun Vec3.csv(): String = "$x,$y,$z"

    private fun Color.csv(): String = "$r,$g,$b,$a"

    private fun jsonString(value: String): String =
        buildString(value.length + 2) {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (ch.code < 0x20) {
                        append("\\u%04x".format(ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
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
                "NameComponent" -> entity.add(
                    NameComponent(component.properties["name"] ?: descriptor.name),
                )

                "TransformComponent" -> entity.add(readTransform(component, entity.id, logger))

                "ParentComponent" -> readLong(
                    raw = component.properties["parentId"],
                    defaultValue = descriptor.parentId,
                    context = "ParentComponent.parentId",
                    entityId = entity.id,
                    logger = logger,
                )?.let { parentId -> entity.add(ParentComponent(parentId)) }

                "PerspectiveCameraComponent" -> entity.add(readCamera(component, entity.id, logger))

                "LightComponent" -> entity.add(readLight(component, entity.id, logger))

                "ModelComponent" -> readModel(component, entity.id, logger)
                    ?.let(entity::add)

                "TerrainComponent" -> readTerrain(component, entity.id, logger)
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
            position = readVec3(component.properties["position"], Vec3.zero(), "TransformComponent.position", entityId, logger),
            eulerDegrees = readVec3(component.properties["rotation"], Vec3.zero(), "TransformComponent.rotation", entityId, logger),
            scale = readVec3(component.properties["scale"], Vec3.one(), "TransformComponent.scale", entityId, logger),
        )

    private fun readCamera(
        component: ComponentDescriptor,
        entityId: Long,
        logger: Logger?,
    ): PerspectiveCameraComponent =
        PerspectiveCameraComponent(
            fieldOfViewDegrees = readFloat(
                component.properties["fieldOfViewDegrees"],
                PerspectiveCameraComponent().fieldOfViewDegrees,
                "PerspectiveCameraComponent.fieldOfViewDegrees",
                entityId,
                logger,
            ),
            near = readFloat(
                component.properties["near"],
                PerspectiveCameraComponent().near,
                "PerspectiveCameraComponent.near",
                entityId,
                logger,
            ),
            far = readFloat(
                component.properties["far"],
                PerspectiveCameraComponent().far,
                "PerspectiveCameraComponent.far",
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
            intensity = readFloat(component.properties["intensity"], 1f, "LightComponent.intensity", entityId, logger),
            color = readColor(component.properties["color"], Color.white(), "LightComponent.color", entityId, logger),
            direction = readVec3(
                component.properties["direction"],
                Vec3(-1f, -0.8f, -0.2f),
                "LightComponent.direction",
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
            logger?.warn(TAG) { "Invalid ModelComponent.model for entityId=$entityId value='<missing>'; skipping component" }
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
            logger?.warn(TAG) { "Invalid TerrainComponent.terrain for entityId=$entityId value='<missing>'; skipping component" }
            return null
        }
        val visible = component.properties["visible"]?.trim()?.toBooleanStrictOrNull() ?: true
        return TerrainComponent(terrain = AssetRef.terrain(path), visible = visible)
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

    private fun readLightType(
        raw: String?,
        defaultValue: LightType,
        entityId: Long,
        logger: Logger?,
    ): LightType {
        val value = LightType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (value != null) return value
        warnParse(raw, "LightComponent.type", entityId, logger)
        return defaultValue
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
