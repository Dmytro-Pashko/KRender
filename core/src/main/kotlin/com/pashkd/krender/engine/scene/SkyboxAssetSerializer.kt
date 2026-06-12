package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.serialization.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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

object SkyboxAssetSerializer : KRenderSerializer<SkyboxAssetDescriptor> {
    private const val DocumentName = "Skybox asset descriptor"
    private val json = KRenderJson.Pretty

    override fun encode(value: SkyboxAssetDescriptor): String {
        val descriptor = value
        validate(descriptor)
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("schemaVersion", JsonPrimitive(descriptor.schemaVersion))
                put("id", JsonPrimitive(descriptor.id))
                put("name", JsonPrimitive(descriptor.name))
                put("texturePath", JsonPrimitive(normalizedProjectPath(descriptor.texturePath)))
                put("intensity", JsonPrimitive(descriptor.intensity))
            },
        )
    }

    override fun decode(jsonText: String): SkyboxAssetDescriptor {
        val root = json.parseToJsonElement(jsonText) as? JsonObject
            ?: throw IllegalArgumentException("Skybox asset descriptor root must be a JSON object")
        val descriptor = SkyboxAssetDescriptor(
            schemaVersion = root.intOrDefault("schemaVersion", SkyboxAssetDescriptor.CurrentSchemaVersion),
            id = root.requiredString("id", DocumentName),
            name = root.stringOrDefault("name", "Skybox"),
            texturePath = normalizedProjectPath(root.requiredString("texturePath", DocumentName)),
            intensity = root.floatOrDefault("intensity", 1f),
        )
        validate(descriptor)
        return descriptor
    }

    private fun validate(descriptor: SkyboxAssetDescriptor) {
        require(descriptor.texturePath.trim().isNotBlank()) { "Skybox asset texturePath must not be blank" }
        require(descriptor.intensity >= 0f) { "Skybox asset intensity must be greater than or equal to 0" }
    }
}
