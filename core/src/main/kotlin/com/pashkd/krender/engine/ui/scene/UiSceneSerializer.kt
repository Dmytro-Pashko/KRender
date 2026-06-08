package com.pashkd.krender.engine.ui.scene

import com.pashkd.krender.engine.serialization.KRenderJson
import com.pashkd.krender.engine.serialization.KRenderSerializer
import com.pashkd.krender.engine.serialization.booleanOrDefault
import com.pashkd.krender.engine.serialization.enumOrDefault
import com.pashkd.krender.engine.serialization.enumOrNull
import com.pashkd.krender.engine.serialization.floatOrDefault
import com.pashkd.krender.engine.serialization.floatOrNull
import com.pashkd.krender.engine.serialization.intOrDefault
import com.pashkd.krender.engine.serialization.normalizedProjectPath
import com.pashkd.krender.engine.serialization.putIfNonDefault
import com.pashkd.krender.engine.serialization.putIfNotNull
import com.pashkd.krender.engine.serialization.requiredEnum
import com.pashkd.krender.engine.serialization.requiredString
import com.pashkd.krender.engine.serialization.stringOrDefault
import com.pashkd.krender.engine.serialization.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * JSON codec for the shared `.krui` document format.
 *
 * This serializer belongs to the shared UI pipeline: runtime loaders use it to
 * build Scene2D actors, and the future UI Composer will use the same stable JSON
 * shape when editing documents. The format remains an explicit MVP subset of
 * Scene2D widgets and does not serialize arbitrary Actor state or Skin styles.
 */
class UiSceneSerializer : KRenderSerializer<UiSceneDocument> {
    private val json = KRenderJson.Pretty

    private companion object {
        const val DocumentName = "UI scene document"
    }

    /**
     * Decodes a human-authored `.krui` JSON document into the shared model.
     *
     * Missing optional fields use [UiSceneNode] defaults so early editor/runtime
     * documents can stay compact.
     */
    override fun decode(json: String): UiSceneDocument {
        val root = this.json.parseToJsonElement(json) as? JsonObject
            ?: throw IllegalArgumentException("UI scene document root must be a JSON object")
        return UiSceneDocument(
            schemaVersion = root.intOrDefault("schemaVersion", UiSceneDocument.CurrentSchemaVersion),
            id = root.requiredString("id", DocumentName),
            skin = normalizedProjectPath(root.requiredString("skin", DocumentName)),
            root = readNode(root["root"], "root"),
        )
    }

    /**
     * Encodes a shared `.krui` document as stable, pretty-printed JSON.
     *
     * The output uses enum names as strings and keeps path values normalized to
     * project-relative forward-slash form.
     */
    override fun encode(document: UiSceneDocument): String =
        json.encodeToString(JsonObject.serializer(), document.toJsonObject())

    private fun readNode(
        element: JsonElement?,
        context: String,
    ): UiSceneNode {
        val node = element as? JsonObject
            ?: throw IllegalArgumentException("UI scene node '$context' must be a JSON object")
        return UiSceneNode(
            id = node.requiredString("id", DocumentName),
            type = node.requiredEnum("type", DocumentName, UiSceneNodeType::valueOf),
            visible = node.booleanOrDefault("visible", true),
            style = node.stringOrNull("style"),
            text = node.stringOrNull("text"),
            action = node.stringOrNull("action"),
            texture = node.stringOrNull("texture")?.let(::normalizedProjectPath),
            scaling = node.enumOrDefault("scaling", DocumentName, UiSceneScaling.Fit, UiSceneScaling::valueOf),
            value = node.floatOrNull("value"),
            valueBinding = node.stringOrNull("valueBinding"),
            min = node.floatOrDefault("min", 0f),
            max = node.floatOrDefault("max", 1f),
            step = node.floatOrDefault("step", 0.01f),
            width = node.floatOrNull("width"),
            height = node.floatOrNull("height"),
            align = node.enumOrNull("align", DocumentName, UiSceneAlign::valueOf),
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
            put("skin", JsonPrimitive(normalizedProjectPath(skin)))
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
            putIfNotNull("texture", texture?.let(::normalizedProjectPath))
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
