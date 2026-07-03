package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * JSON encode/decode for [EnvironmentManifestDto].
 */
object EnvironmentManifestCodec {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    fun decode(text: String): EnvironmentManifestDto =
        try {
            json.decodeFromJsonElement(EnvironmentManifestDto.serializer(), normalizeLegacyStructure(json.parseToJsonElement(text)))
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode environment manifest: ${e.message}", e)
        }

    fun encode(manifest: EnvironmentManifestDto): String = json.encodeToString(manifest)

    private fun normalizeLegacyStructure(element: JsonElement): JsonElement {
        val root = element as? JsonObject ?: return element
        val generated = root["generated"]?.jsonObject
        if (generated == null && "generation" !in root) return root
        return buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "generated" && key != "generation") {
                    put(key, value)
                }
            }
            generated?.forEach { (key, value) ->
                if (key !in root) {
                    put(key, value)
                }
            }
        }
    }
}
