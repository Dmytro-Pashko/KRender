package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.SkyboxGenerationConfig
import java.io.File

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
    ): Map<String, File> =
        exportSupport.exportFaces(
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
            // PNG export is LDR, so the tone-mapped linear color must be encoded to sRGB.
            EnvironmentLdrToneMapper.prepare(
                color = source.sampleEquirectangular(uv.x, uv.y),
                exposure = config.exposure,
                toneMapping = config.toneMapping,
            )
        }
}
