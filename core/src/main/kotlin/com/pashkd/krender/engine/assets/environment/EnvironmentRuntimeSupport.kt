package com.pashkd.krender.engine.assets.environment

/**
 * Resolves Environment manifest-relative resource paths without depending on a backend.
 */
object EnvironmentPathResolver {
    fun manifestDirectory(manifestPath: String): String {
        val normalized = manifestPath.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
    }

    fun resolvePath(
        manifestPath: String,
        relativePath: String,
    ): String {
        val manifestDir = manifestDirectory(manifestPath)
        if (manifestDir.isEmpty()) return relativePath.replace('\\', '/')
        return "$manifestDir/$relativePath".replace('\\', '/')
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
        asset: EnvironmentAsset,
        revision: Long = 0L,
    ): String =
        buildList {
            add("manifest=${asset.manifestPath}")
            add("revision=$revision")
            add("type=${asset.type}")
            add("defaultSource=${asset.sources.firstOrNull { source -> source.isDefault }?.id.orEmpty()}")
            asset.sources
                .sortedWith(compareBy<EnvironmentSourceVariant> { it.id }.thenBy { it.path })
                .forEach { source ->
                    add("source:${source.id}:${source.path}:${source.isDefault}")
                }
            asset.skybox?.faces
                ?.toSortedMap()
                ?.forEach { (face, path) ->
                    add("skybox:$face:$path")
                }
            asset.irradiance?.let { irradiance ->
                add("irradiance:${irradiance.path}:${irradiance.resolution}:${irradiance.format}")
            }
            asset.radiance?.mips
                ?.sortedBy(RadianceMip::level)
                ?.forEach { mip ->
                    add("radiance:${mip.level}:${mip.roughness}:${mip.path}")
                }
            asset.brdfLut?.let { lut ->
                add("brdf:${lut.path}")
            }
        }.joinToString("|")
}
