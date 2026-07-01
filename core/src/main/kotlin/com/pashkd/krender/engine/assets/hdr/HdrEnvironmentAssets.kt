package com.pashkd.krender.engine.assets.hdr

import java.nio.file.Path

/**
 * Shared HDR environment asset conventions used by tools and backends.
 */
object HdrEnvironmentAssets {
    const val DEFAULT_PRESET = "default"
    const val DEFAULT_MANIFEST = "hdr/default/environment.json"
    const val SHARED_BRDF_LUT = "hdr/_common/brdf/brdfLUT.png"

    fun manifestPathForPreset(presetNameOrPath: String): String =
        when {
            presetNameOrPath.isBlank() || presetNameOrPath == DEFAULT_PRESET ->
                DEFAULT_MANIFEST
            presetNameOrPath.endsWith(".json", ignoreCase = true) ->
                normalizeAssetPath(presetNameOrPath)
            else ->
                "hdr/${presetNameOrPath.trim('/')}/environment.json"
        }

    fun resolveRelativeToManifest(
        manifestPath: String,
        relativePath: String,
    ): String {
        val parent = Path.of(normalizeAssetPath(manifestPath)).parent ?: Path.of("")
        return normalizeAssetPath(parent.resolve(relativePath).normalize().toString())
    }

    fun normalizeAssetPath(path: String): String = path.replace('\\', '/').removePrefix("./")
}
