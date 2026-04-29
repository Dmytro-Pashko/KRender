package com.pashkd.krender.engine.sceneeditor

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.NameComponent
import com.pashkd.krender.engine.api.ParentComponent
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import java.util.UUID

/**
 * Converts Scene Editor documents to and from the `.krscene` descriptor format.
 */
object SceneSerializer {
    fun toDescriptor(
        document: SceneEditorDocument,
        state: SceneEditorState,
    ): SceneDescriptor {
        val entities = document.world.all()
            .filterNot(::isEditorOnly)
            .map(::toEntityDescriptor)
        val existingSettings = document.descriptor?.settings
        val activeCameraEntityId = existingSettings
            ?.activeCameraEntityId
            ?.takeIf { id -> entities.any { it.id == id } }
            ?: document.world.all()
                .firstOrNull { entity -> !isEditorOnly(entity) && entity.get<PerspectiveCameraComponent>() != null }
                ?.id
        val ambientLightEntityId = existingSettings
            ?.ambientLightEntityId
            ?.takeIf { id -> entities.any { it.id == id } }

        return SceneDescriptor(
            schemaVersion = SceneDescriptor.CurrentSchemaVersion,
            id = document.descriptor?.id ?: generateSceneId(),
            name = state.sceneName,
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

    private fun isEditorOnly(entity: Entity): Boolean = entity.get<EditorOnlyComponent>() != null

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
