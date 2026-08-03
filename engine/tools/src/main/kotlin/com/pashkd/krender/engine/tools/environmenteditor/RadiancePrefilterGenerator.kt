package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentRoughnessDistribution
import com.pashkd.krender.engine.assets.environment.RadianceGenerationConfig
import com.pashkd.krender.engine.assets.environment.radianceMipResolution
import com.pashkd.krender.engine.assets.environment.requiredRadianceMipCount
import java.io.File

/**
 * Generates a roughness-indexed radiance mip chain from an equirectangular HDR source.
 */
class RadiancePrefilterGenerator(
    private val exportSupport: CubemapImageExportSupport = CubemapImageExportSupport(),
) {
    fun generate(
        source: HdrImage,
        outputDirectory: File,
        outputFormat: String,
        overwritePolicy: EnvironmentIblOverwritePolicy,
        config: RadianceGenerationConfig,
    ): Map<Int, Map<String, File>> {
        require(config.baseResolution > 0) { "Radiance base resolution must be positive." }
        require(config.mipCount == requiredRadianceMipCount(config.baseResolution)) {
            "Radiance mip count must be ${requiredRadianceMipCount(config.baseResolution)} for " +
                "a ${config.baseResolution}px base resolution."
        }
        require(config.sampleCount > 0) { "Radiance sample count must be positive." }
        require(config.roughnessDistribution == EnvironmentRoughnessDistribution.Linear) {
            "Only linear roughness distribution is supported."
        }

        val mipFiles = linkedMapOf<Int, Map<String, File>>()
        for (level in 0 until config.mipCount) {
            val roughness = roughnessFor(level, config.mipCount)
            val resolution = radianceMipResolution(config.baseResolution, level)
            val mipDirectory = File(outputDirectory, "mip_$level")
            val faces =
                exportSupport.exportFaces(
                    outputDirectory = mipDirectory,
                    resolution = resolution,
                    outputFormat = outputFormat,
                    overwritePolicy = overwritePolicy,
                ) { face, x, y ->
                    val reflectionDirection = CubemapDirectionMath.cubemapFacePixelToDirection(face, x, y, resolution)
                    samplePrefilteredRadiance(source, reflectionDirection, roughness, config.sampleCount)
                }
            mipFiles[level] = faces
        }
        return mipFiles
    }

    private fun samplePrefilteredRadiance(
        source: HdrImage,
        reflectionDirection: Vec3,
        roughness: Float,
        sampleCount: Int,
    ): Vec3 {
        if (roughness <= 1e-4f) {
            val uv = EquirectangularProjection.directionToEquirectangularUv(reflectionDirection)
            return source.sampleEquirectangular(uv.x, uv.y)
        }

        var accumulated = Vec3.zero()
        var totalWeight = 0f
        for (sampleIndex in 0 until sampleCount) {
            val sampleDirection =
                EnvironmentSamplingMath.ggxImportanceSampleDirection(
                    reflectionDirection = reflectionDirection,
                    roughness = roughness,
                    sampleIndex = sampleIndex,
                    sampleCount = sampleCount,
                )
            val weight = EnvironmentSamplingMath.dot(reflectionDirection, sampleDirection).coerceAtLeast(0f)
            if (weight <= 0f) continue

            val uv = EquirectangularProjection.directionToEquirectangularUv(sampleDirection)
            accumulated += source.sampleEquirectangular(uv.x, uv.y) * weight
            totalWeight += weight
        }
        return if (totalWeight > 0f) accumulated * (1f / totalWeight) else Vec3.zero()
    }

    private fun roughnessFor(
        level: Int,
        mipCount: Int,
    ): Float = if (mipCount <= 1) 0f else level.toFloat() / (mipCount - 1).toFloat()
}
