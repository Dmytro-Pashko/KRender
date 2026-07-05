package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentToneMapping
import com.pashkd.krender.engine.assets.environment.SkyboxGenerationConfig
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow

class EquirectangularToCubemapGenerator {
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
                    val direction =
                        CubemapDirectionMath.cubemapFacePixelToDirection(
                            face = face,
                            x = x,
                            y = y,
                            size = config.resolution,
                        )
                    val uv = EquirectangularProjection.directionToEquirectangularUv(direction)
                    val hdr = source.sampleEquirectangular(uv.x, uv.y) * config.exposure
                    val mapped = toneMap(hdr, config.toneMapping)
                    image.setRGB(x, y, linearToSrgbArgb(mapped))
                }
            }
            ImageIO.write(image, "png", file)
            faceFiles[face.id] = file
        }
        return faceFiles
    }

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

    private fun toneMap(
        color: Vec3,
        mode: EnvironmentToneMapping,
    ): Vec3 =
        when (mode) {
            EnvironmentToneMapping.None -> color
            EnvironmentToneMapping.Reinhard ->
                Vec3(
                    color.x / (1f + color.x),
                    color.y / (1f + color.y),
                    color.z / (1f + color.z),
                )
            EnvironmentToneMapping.ACES -> Vec3(aces(color.x), aces(color.y), aces(color.z))
        }

    private fun aces(value: Float): Float {
        val a = 2.51f
        val b = 0.03f
        val c = 2.43f
        val d = 0.59f
        val e = 0.14f
        return ((value * (a * value + b)) / (value * (c * value + d) + e)).coerceIn(0f, 1f)
    }

    private fun linearToSrgbArgb(color: Vec3): Int {
        val red = linearChannelToSrgb(color.x)
        val green = linearChannelToSrgb(color.y)
        val blue = linearChannelToSrgb(color.z)
        return (255 shl 24) or (red shl 16) or (green shl 8) or blue
    }

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
