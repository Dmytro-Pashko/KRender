package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.json.Json

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
            json.decodeFromString<EnvironmentManifestDto>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode environment manifest: ${e.message}", e)
        }

    fun encode(manifest: EnvironmentManifestDto): String = json.encodeToString(manifest)
}
