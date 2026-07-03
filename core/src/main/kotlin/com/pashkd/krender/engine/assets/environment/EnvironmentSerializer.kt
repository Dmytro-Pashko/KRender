package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.json.Json

/**
 * JSON encode/decode for [Environment].
 */
object EnvironmentSerializer {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    fun decode(text: String): Environment =
        try {
            json.decodeFromString(Environment.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode environment manifest: ${e.message}", e)
        }

    fun encode(environment: Environment): String = json.encodeToString(Environment.serializer(), environment)
}
