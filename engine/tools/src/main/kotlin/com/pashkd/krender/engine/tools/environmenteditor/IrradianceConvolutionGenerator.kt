package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.IrradianceGenerationConfig
import java.io.File
import kotlin.math.PI

/**
 * Generates diffuse irradiance cubemap faces from an equirectangular HDR source.
 */
class IrradianceConvolutionGenerator(
    private val exportSupport: CubemapImageExportSupport = CubemapImageExportSupport(),
) {
    fun generate(
        source: HdrImage,
        outputDirectory: File,
        outputFormat: String,
        overwritePolicy: EnvironmentIblOverwritePolicy,
        config: IrradianceGenerationConfig,
    ): Map<String, File> {
        require(config.resolution > 0) { "Irradiance resolution must be positive." }
        require(config.sampleCount > 0) { "Irradiance sample count must be positive." }

        return exportSupport.exportFaces(
            outputDirectory = outputDirectory,
            resolution = config.resolution,
            outputFormat = outputFormat,
            overwritePolicy = overwritePolicy,
        ) { face, x, y ->
            val normal = CubemapDirectionMath.cubemapFacePixelToDirection(face, x, y, config.resolution)
            EnvironmentLdrToneMapper.prepare(
                color = convolveDiffuseIrradiance(source, normal, config.sampleCount),
                exposure = config.exposure,
                toneMapping = config.toneMapping,
            )
        }
    }

    /**
     * Monte Carlo Lambert irradiance estimate using cosine-weighted hemisphere sampling.
     *
     * Because the PDF is `cos(theta) / PI`, the irradiance integral reduces to `PI * average(Li)`.
     */
    private fun convolveDiffuseIrradiance(
        source: HdrImage,
        normal: Vec3,
        sampleCount: Int,
    ): Vec3 {
        var accumulated = Vec3.zero()
        for (sampleIndex in 0 until sampleCount) {
            val direction =
                EnvironmentSamplingMath.cosineWeightedHemisphereDirection(
                    normal = normal,
                    sampleIndex = sampleIndex,
                    sampleCount = sampleCount,
                )
            val uv = EquirectangularProjection.directionToEquirectangularUv(direction)
            accumulated += source.sampleEquirectangular(uv.x, uv.y)
        }
        return accumulated * (PI.toFloat() / sampleCount.toFloat())
    }
}
