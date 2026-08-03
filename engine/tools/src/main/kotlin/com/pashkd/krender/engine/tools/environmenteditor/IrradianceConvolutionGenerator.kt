package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.IrradianceGenerationConfig
import java.io.File
import kotlin.math.PI
import kotlin.math.abs

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

        val irradiancePixels =
            EnvironmentCubemapFace.ordered
                .associateWith { face ->
                    Array(config.resolution * config.resolution) { index ->
                        val x = index % config.resolution
                        val y = index / config.resolution
                        val normal = CubemapDirectionMath.cubemapFacePixelToDirection(face, x, y, config.resolution)
                        convolveDiffuseIrradiance(
                            source = source,
                            normal = normal,
                            sampleCount = config.sampleCount,
                            sequenceOffset = EnvironmentSamplingMath.sampleSequenceOffset(face.ordinal, x, y),
                        )
                    }
                }.mapValues { (_, pixels) -> smoothDiffuseIrradiance(pixels, config.resolution) }

        return exportSupport.exportFaces(
            outputDirectory = outputDirectory,
            resolution = config.resolution,
            outputFormat = outputFormat,
            overwritePolicy = overwritePolicy,
        ) { face, x, y ->
            EnvironmentLdrToneMapper.prepare(
                color = irradiancePixels.getValue(face)[y * config.resolution + x],
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
        sequenceOffset: Vec2,
    ): Vec3 {
        var accumulated = Vec3.zero()
        for (sampleIndex in 0 until sampleCount) {
            val direction =
                EnvironmentSamplingMath.cosineWeightedHemisphereDirection(
                    normal = normal,
                    sampleIndex = sampleIndex,
                    sampleCount = sampleCount,
                    sequenceOffset = sequenceOffset,
                )
            val uv = EquirectangularProjection.directionToEquirectangularUv(direction)
            accumulated += source.sampleEquirectangular(uv.x, uv.y)
        }
        return accumulated * (PI.toFloat() / sampleCount.toFloat())
    }

    private fun smoothDiffuseIrradiance(
        pixels: Array<Vec3>,
        resolution: Int,
    ): Array<Vec3> =
        Array(pixels.size) { index ->
            val x = index % resolution
            val y = index / resolution
            var accumulated = Vec3.zero()
            var totalWeight = 0f
            for (offsetY in -DiffuseFilterRadius..DiffuseFilterRadius) {
                for (offsetX in -DiffuseFilterRadius..DiffuseFilterRadius) {
                    val weight =
                        (
                            (DiffuseFilterRadius + 1 - abs(offsetX)) *
                                (DiffuseFilterRadius + 1 - abs(offsetY))
                        ).toFloat()
                    val sampleX = (x + offsetX).coerceIn(0, resolution - 1)
                    val sampleY = (y + offsetY).coerceIn(0, resolution - 1)
                    accumulated += pixels[sampleY * resolution + sampleX] * weight
                    totalWeight += weight
                }
            }
            accumulated * (1f / totalWeight)
        }

    private companion object {
        private const val DiffuseFilterRadius = 2
    }
}
