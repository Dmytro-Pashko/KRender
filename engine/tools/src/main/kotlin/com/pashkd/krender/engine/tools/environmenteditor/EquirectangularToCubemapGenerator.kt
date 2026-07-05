package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentToneMapping
import com.pashkd.krender.engine.assets.environment.SkyboxGenerationConfig
import java.io.File
import kotlin.math.pow

/**
 * Converts an equirectangular HDR environment into six cubemap face PNG files.
 *
 * The generator is intentionally manifest-agnostic: it only knows how to project sampled HDR data
 * into face images and write them to disk. Manifest updates happen separately in
 * [EnvironmentGenerationManifestUpdater].
 *
 * The output path layout is determined by the caller, while the image content is controlled by
 * [SkyboxGenerationConfig]:
 *
 * - face resolution decides the output image size;
 * - exposure scales sampled HDR radiance before tone mapping;
 * - tone mapping compresses HDR values into an LDR range suitable for PNG;
 * - final pixels are converted from linear space to sRGB 8-bit output.
 */
class EquirectangularToCubemapGenerator {
    private val exportSupport = CubemapImageExportSupport()

    /**
     * Generates one PNG file per cubemap face and returns the written files keyed by face id.
     *
     * For each output pixel the generator:
     *
     * 1. converts the face pixel into a normalized cubemap direction;
     * 2. converts that direction into equirectangular UV coordinates;
     * 3. samples the HDR source in linear space;
     * 4. applies exposure and tone mapping;
     * 5. converts the result to sRGB and writes the pixel to the destination image.
     */
    fun generate(
        source: HdrImage,
        outputDirectory: File,
        outputFormat: String,
        overwritePolicy: EnvironmentIblOverwritePolicy,
        config: SkyboxGenerationConfig,
    ): Map<String, File> {
        return exportSupport.exportFaces(
            outputDirectory = outputDirectory,
            resolution = config.resolution,
            outputFormat = outputFormat,
            overwritePolicy = overwritePolicy,
        ) { face, x, y ->
            // Each cubemap face pixel becomes one world-space lookup direction.
            val direction =
                CubemapDirectionMath.cubemapFacePixelToDirection(
                    face = face,
                    x = x,
                    y = y,
                    size = config.resolution,
                )
            // Equirectangular HDR textures are sampled in latitude-longitude UV space.
            val uv = EquirectangularProjection.directionToEquirectangularUv(direction)
            val hdr = source.sampleEquirectangular(uv.x, uv.y) * config.exposure
            // PNG export is LDR, so the tone-mapped linear color must be encoded to sRGB.
            toneMap(hdr, config.toneMapping)
        }
    }

    /**
     * Compresses linear HDR values into an LDR-friendly range.
     *
     * Tone mapping is only relevant because PNG cannot preserve the original HDR radiance range.
     */
    private fun toneMap(
        color: Vec3,
        mode: EnvironmentToneMapping,
    ): Vec3 =
        when (mode) {
            EnvironmentToneMapping.None -> color
            EnvironmentToneMapping.Reinhard ->
                Vec3(
                    // Classic Reinhard operator: simple, stable, and monotonic.
                    color.x / (1f + color.x),
                    color.y / (1f + color.y),
                    color.z / (1f + color.z),
                )
            EnvironmentToneMapping.ACES -> Vec3(aces(color.x), aces(color.y), aces(color.z))
        }

    /**
     * Applies an ACES-inspired filmic curve to one channel.
     */
    private fun aces(value: Float): Float {
        val a = 2.51f
        val b = 0.03f
        val c = 2.43f
        val d = 0.59f
        val e = 0.14f
        return ((value * (a * value + b)) / (value * (c * value + d) + e)).coerceIn(0f, 1f)
    }

}
