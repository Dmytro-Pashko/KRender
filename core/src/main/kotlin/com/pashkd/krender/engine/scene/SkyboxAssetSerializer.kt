package com.pashkd.krender.engine.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull

data class SkyboxAssetDescriptor(
    val schemaVersion: Int = CurrentSchemaVersion,
    val id: String,
    val name: String,
    val texturePath: String,
    val intensity: Float = 1f,
) {
    companion object {
        const val CurrentSchemaVersion = 1
    }
}

object SkyboxAssetSerializer {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun encode(descriptor: SkyboxAssetDescriptor): String {
        validate(descriptor)
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("schemaVersion", JsonPrimitive(descriptor.schemaVersion))
                put("id", JsonPrimitive(descriptor.id))
                put("name", JsonPrimitive(descriptor.name))
                put("texturePath", JsonPrimitive(descriptor.texturePath.trim().replace('\\', '/')))
                put("intensity", JsonPrimitive(descriptor.intensity))
            },
        )
    }

    fun decode(jsonText: String): SkyboxAssetDescriptor {
        val root = json.parseToJsonElement(jsonText) as? JsonObject
            ?: throw IllegalArgumentException("Skybox asset descriptor root must be a JSON object")
        val descriptor = SkyboxAssetDescriptor(
            schemaVersion = root.intOrDefault("schemaVersion", SkyboxAssetDescriptor.CurrentSchemaVersion),
            id = root.requiredString("id"),
            name = root.stringOrDefault("name", "Skybox"),
            texturePath = root.requiredString("texturePath").trim().replace('\\', '/'),
            intensity = root.floatOrDefault("intensity", 1f),
        )
        validate(descriptor)
        return descriptor
    }

    private fun validate(descriptor: SkyboxAssetDescriptor) {
        require(descriptor.texturePath.trim().isNotBlank()) { "Skybox asset texturePath must not be blank" }
        require(descriptor.intensity >= 0f) { "Skybox asset intensity must be greater than or equal to 0" }
    }

    private fun JsonObject.requiredString(name: String): String =
        stringOrNull(name) ?: throw IllegalArgumentException("Skybox asset descriptor is missing required field '$name'")

    private fun JsonObject.stringOrNull(name: String): String? =
        (this[name] as? JsonPrimitive)?.content

    private fun JsonObject.stringOrDefault(name: String, defaultValue: String): String =
        stringOrNull(name) ?: defaultValue

    private fun JsonObject.floatOrDefault(name: String, defaultValue: Float): Float =
        (this[name] as? JsonPrimitive)?.floatOrNull ?: defaultValue

    private fun JsonObject.intOrDefault(name: String, defaultValue: Int): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: defaultValue
}
