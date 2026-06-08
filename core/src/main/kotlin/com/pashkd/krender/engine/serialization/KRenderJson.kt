package com.pashkd.krender.engine.serialization

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for KRender document formats.
 *
 * KRender asset documents are intentionally human-readable and stable across
 * runtime/editor pipelines, so serializers should use this configuration unless
 * a format has a specific compatibility reason not to.
 */
object KRenderJson {
    /** Pretty JSON config used for `.krscene`, `.krskybox`, `.krui`, and future formats. */
    val Pretty: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
