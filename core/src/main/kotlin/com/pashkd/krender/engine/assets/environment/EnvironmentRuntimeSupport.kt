package com.pashkd.krender.engine.assets.environment

/**
 * Resolves Environment manifest-relative resource paths without depending on a backend.
 */
object EnvironmentPathResolver {
    val orderedFaceNames: List<String> = listOf("posx", "negx", "posy", "negy", "posz", "negz")

    fun manifestDirectory(manifestPath: String): String {
        val normalized = manifestPath.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
    }

    fun resolvePath(
        manifestPath: String,
        relativePath: String,
    ): String {
        val normalizedRelative = relativePath.replace('\\', '/')
        val manifestDir = manifestDirectory(manifestPath)
        if (manifestDir.isEmpty()) return normalizedRelative
        if (normalizedRelative == manifestDir || normalizedRelative.startsWith("$manifestDir/")) {
            return normalizedRelative
        }
        return "$manifestDir/$normalizedRelative".replace('\\', '/')
    }

    fun resolveCubemapFacePaths(
        manifestPath: String,
        resourcePath: String?,
    ): List<String> {
        val normalized = resourcePath?.replace('\\', '/')?.trim().orEmpty()
        if (normalized.isBlank()) return emptyList()
        return if (normalized.contains(EnvironmentCubemapFaceToken)) {
            orderedFaceNames.map { face -> resolvePath(manifestPath, normalized.replace(EnvironmentCubemapFaceToken, face)) }
        } else {
            listOf(resolvePath(manifestPath, normalized))
        }
    }
}

/**
 * Builds a deterministic cache key for runtime environment assets loaded from manifest files.
 *
 * The key tracks manifest fields that affect cubemap/LUT resolution. Runtime-only values such
 * as exposure or background tint are applied separately via [com.pashkd.krender.engine.api.GltfRendererSettings].
 */
object EnvironmentRuntimeCacheKeyFactory {
    fun create(
        environment: Environment,
        revision: Long = 0L,
    ): String =
        buildList {
            add("manifest=${environment.manifestPath}")
            add("revision=$revision")
            environment.skybox
                ?.faces
                ?.toSortedMap()
                ?.forEach { (face, path) ->
                    add("skybox:$face:$path")
                }
            environment.irradiance?.let { irradiance ->
                add("irradiance:${irradiance.path}:${irradiance.resolution}:${irradiance.format}")
            }
            environment.radiance
                ?.mips
                ?.sortedBy(RadianceMip::level)
                ?.forEach { mip ->
                    add("radiance:${mip.level}:${mip.roughness}:${mip.path}")
                }
            environment.brdfLut?.let { lut ->
                add("brdf:${lut.path}")
            }
        }.joinToString("|")
}

private const val EnvironmentCubemapFaceToken = "{face}"
