package com.pashkd.krender.engine.uiscene

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull

/**
 * JSON codec for the shared `.krui` document format.
 *
 * This serializer belongs to the shared UI pipeline: runtime loaders use it to
 * build Scene2D actors, and the future UI Composer will use the same stable JSON
 * shape when editing documents. The format remains an explicit MVP subset of
 * Scene2D widgets and does not serialize arbitrary Actor state or Skin styles.
 */
class UiSceneSerializer {


    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Decodes a human-authored `.krui` JSON document into the shared model.
     *
     * Missing optional fields use [UiSceneNode] defaults so early editor/runtime
     * documents can stay compact.
     */
    fun decode(json: String): UiSceneDocument {
        val root = this.json.parseToJsonElement(json) as? JsonObject
            ?: throw IllegalArgumentException("UI scene document root must be a JSON object")
        return UiSceneDocument(
            schemaVersion = root.intOrDefault("schemaVersion", UiSceneDocument.CurrentSchemaVersion),
            id = root.requiredString("id"),
            skin = root.requiredString("skin").trim().replace('\\', '/'),
            root = readNode(root["root"], "root"),
        )
    }

    /**
     * Encodes a shared `.krui` document as stable, pretty-printed JSON.
     *
     * The output uses enum names as strings and keeps path values normalized to
     * project-relative forward-slash form.
     */
    fun encode(document: UiSceneDocument): String =
        json.encodeToString(JsonObject.serializer(), document.toJsonObject())

    private fun readNode(
        element: JsonElement?,
        context: String,
    ): UiSceneNode {
        val node = element as? JsonObject
            ?: throw IllegalArgumentException("UI scene node '$context' must be a JSON object")
        return UiSceneNode(
            id = node.requiredString("id"),
            type = node.requiredEnum("type", UiSceneNodeType::valueOf),
            visible = node.booleanOrDefault("visible", true),
            style = node.stringOrNull("style"),
            text = node.stringOrNull("text"),
            action = node.stringOrNull("action"),
            texture = node.stringOrNull("texture")?.trim()?.replace('\\', '/'),
            scaling = node.enumOrDefault("scaling", UiSceneScaling.Fit, UiSceneScaling::valueOf),
            value = node.floatOrNull("value"),
            valueBinding = node.stringOrNull("valueBinding"),
            min = node.floatOrDefault("min", 0f),
            max = node.floatOrDefault("max", 1f),
            step = node.floatOrDefault("step", 0.01f),
            width = node.floatOrNull("width"),
            height = node.floatOrNull("height"),
            align = node.enumOrNull("align", UiSceneAlign::valueOf),
            padding = readSpacing(node["padding"]),
            spacing = node.floatOrDefault("spacing", 0f),
            children = readChildren(node["children"], node.stringOrDefault("id", context)),
        )
    }

    private fun readChildren(
        element: JsonElement?,
        parentId: String,
    ): List<UiSceneNode> {
        val children = element as? JsonArray ?: return emptyList()
        return children.mapIndexed { index, child -> readNode(child, "$parentId.children[$index]") }
    }

    private fun readSpacing(element: JsonElement?): UiSceneSpacing {
        val spacing = element as? JsonObject ?: return UiSceneSpacing.zero()
        return UiSceneSpacing(
            left = spacing.floatOrDefault("left", 0f),
            top = spacing.floatOrDefault("top", 0f),
            right = spacing.floatOrDefault("right", 0f),
            bottom = spacing.floatOrDefault("bottom", 0f),
        )
    }

    private fun UiSceneDocument.toJsonObject(): JsonObject =
        buildJsonObject {
            put("schemaVersion", JsonPrimitive(schemaVersion))
            put("id", JsonPrimitive(id))
            put("skin", JsonPrimitive(skin.trim().replace('\\', '/')))
            put("root", root.toJsonObject())
        }

    private fun UiSceneNode.toJsonObject(): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(id))
            put("type", JsonPrimitive(type.name))
            putIfNonDefault("visible", visible, true)
            putIfNotNull("style", style)
            putIfNotNull("text", text)
            putIfNotNull("action", action)
            putIfNotNull("texture", texture?.trim()?.replace('\\', '/'))
            putIfNonDefault("scaling", scaling.name, UiSceneScaling.Fit.name)
            putIfNotNull("value", value)
            putIfNotNull("valueBinding", valueBinding)
            putIfNonDefault("min", min, 0f)
            putIfNonDefault("max", max, 1f)
            putIfNonDefault("step", step, 0.01f)
            putIfNotNull("width", width)
            putIfNotNull("height", height)
            putIfNotNull("align", align?.name)
            putIfNonZero("padding", padding)
            putIfNonDefault("spacing", spacing, 0f)
            if (children.isNotEmpty()) {
                put(
                    "children",
                    buildJsonArray {
                        children.forEach { child -> add(child.toJsonObject()) }
                    },
                )
            }
        }

    private fun JsonObject.requiredString(name: String): String =
        stringOrNull(name) ?: throw IllegalArgumentException("UI scene document is missing required field '$name'")

    private fun <T : Enum<T>> JsonObject.requiredEnum(
        name: String,
        parser: (String) -> T,
    ): T {
        val raw = requiredString(name)
        return runCatching { parser(raw) }
            .getOrElse { throw IllegalArgumentException("UI scene field '$name' has unsupported enum value '$raw'") }
    }

    private fun <T : Enum<T>> JsonObject.enumOrDefault(
        name: String,
        defaultValue: T,
        parser: (String) -> T,
    ): T =
        enumOrNull(name, parser) ?: defaultValue

    private fun <T : Enum<T>> JsonObject.enumOrNull(
        name: String,
        parser: (String) -> T,
    ): T? {
        val raw = stringOrNull(name) ?: return null
        return runCatching { parser(raw) }
            .getOrElse { throw IllegalArgumentException("UI scene field '$name' has unsupported enum value '$raw'") }
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return primitive.content.takeUnless { it.equals("null", ignoreCase = true) }
    }

    private fun JsonObject.stringOrDefault(name: String, defaultValue: String): String =
        stringOrNull(name) ?: defaultValue

    private fun JsonObject.floatOrNull(name: String): Float? =
        (this[name] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.floatOrDefault(name: String, defaultValue: Float): Float =
        floatOrNull(name) ?: defaultValue

    private fun JsonObject.intOrDefault(name: String, defaultValue: Int): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: defaultValue

    private fun JsonObject.booleanOrDefault(name: String, defaultValue: Boolean): Boolean =
        (this[name] as? JsonPrimitive)?.booleanOrNull ?: defaultValue

    private fun JsonObjectBuilder.putIfNotNull(name: String, value: String?) {
        if (value != null) put(name, JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.putIfNotNull(name: String, value: Float?) {
        if (value != null) put(name, JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.putIfNonDefault(name: String, value: Boolean, defaultValue: Boolean) {
        if (value != defaultValue) put(name, JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.putIfNonDefault(name: String, value: String, defaultValue: String) {
        if (value != defaultValue) put(name, JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.putIfNonDefault(name: String, value: Float, defaultValue: Float) {
        if (value != defaultValue) put(name, JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.putIfNonZero(name: String, spacing: UiSceneSpacing) {
        if (spacing != UiSceneSpacing.zero()) put(name, spacing.toJsonObject())
    }

    private fun UiSceneSpacing.toJsonObject(): JsonObject =
        buildJsonObject {
            put("left", JsonPrimitive(left))
            put("top", JsonPrimitive(top))
            put("right", JsonPrimitive(right))
            put("bottom", JsonPrimitive(bottom))
        }
}
