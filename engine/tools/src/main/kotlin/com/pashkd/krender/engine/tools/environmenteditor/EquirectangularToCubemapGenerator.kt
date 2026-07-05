package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentToneMapping
import com.pashkd.krender.engine.assets.environment.SkyboxGenerationConfig
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
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
        require(outputFormat.equals("png", ignoreCase = true)) {
            "Only PNG cubemap export is currently supported."
        }
        val faceFiles = linkedMapOf<String, File>()
        EnvironmentCubemapFace.ordered.forEach { face ->
            val file = File(outputDirectory, "${face.id}.png")
            prepareOutputFile(file, overwritePolicy)
            val image = BufferedImage(config.resolution, config.resolution, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until config.resolution) {
                for (x in 0 until config.resolution) {
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
                    val mapped = toneMap(hdr, config.toneMapping)
                    // PNG export is LDR, so the tone-mapped linear color must be encoded to sRGB.
                    image.setRGB(x, y, linearToSrgbArgb(mapped))
                }
            }
            ImageIO.write(image, "png", file)
            faceFiles[face.id] = file
        }
        return faceFiles
    }

    /**
     * Applies overwrite policy checks and ensures the output directory exists.
     *
     * `SkipExisting` currently exits early from this preparation step only; the caller still
     * proceeds with generation. This method therefore documents the policy intent, while higher
     * level workflow code should decide whether a full-file skip is desired.
     */
    private fun prepareOutputFile(
        file: File,
        overwritePolicy: EnvironmentIblOverwritePolicy,
    ) {
        if (file.exists()) {
            when (overwritePolicy) {
                EnvironmentIblOverwritePolicy.Fail -> error("Output file already exists: ${file.path}")
                EnvironmentIblOverwritePolicy.Replace -> Unit
                EnvironmentIblOverwritePolicy.SkipExisting -> return
            }
        }
        file.parentFile?.mkdirs()
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

    /**
     * Converts a linear RGB color into an opaque ARGB pixel for Java2D image output.
     */
    private fun linearToSrgbArgb(color: Vec3): Int {
        val red = linearChannelToSrgb(color.x)
        val green = linearChannelToSrgb(color.y)
        val blue = linearChannelToSrgb(color.z)
        return (255 shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /**
     * Converts one linear channel into 8-bit sRGB.
     *
     * The transfer function uses the standard piecewise sRGB curve: linear near black to avoid
     * banding in dark values, gamma-like in the rest of the range.
     */
    private fun linearChannelToSrgb(value: Float): Int {
        val clamped = value.coerceIn(0f, 1f)
        val srgb =
            if (clamped <= 0.0031308f) {
                clamped * 12.92f
            } else {
                1.055f * clamped.pow(1f / 2.4f) - 0.055f
            }
        return (srgb * 255f).toInt().coerceIn(0, 255)
    }
}
