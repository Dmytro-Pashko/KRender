package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow

/**
 * Shared cubemap face export helper for Environment-generation workflows.
 *
 * All generators in this package ultimately emit six PNG files, so file preparation, face
 * iteration, and linear-to-sRGB packing live here instead of being duplicated across skybox,
 * irradiance, and radiance generators.
 */
class CubemapImageExportSupport {
    /**
     * Renders and writes one image per cubemap face.
     *
     * [renderPixel] must return a linear RGB color already prepared for LDR export. This helper
     * only handles the common PNG writing path and final sRGB conversion.
     */
    fun exportFaces(
        outputDirectory: File,
        resolution: Int,
        outputFormat: String,
        overwritePolicy: EnvironmentIblOverwritePolicy,
        renderPixel: (face: EnvironmentCubemapFace, x: Int, y: Int) -> Vec3,
    ): Map<String, File> {
        require(resolution > 0) { "Cubemap resolution must be positive." }
        require(outputFormat.equals("png", ignoreCase = true)) {
            "Only PNG cubemap export is currently supported."
        }

        val writtenFiles = linkedMapOf<String, File>()
        EnvironmentCubemapFace.ordered.forEach { face ->
            val file = File(outputDirectory, "${face.id}.png")
            when (prepareOutputFile(file, overwritePolicy)) {
                OutputWriteMode.Skip -> {
                    writtenFiles[face.id] = file
                    return@forEach
                }
                OutputWriteMode.Write -> Unit
            }

            val image = BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until resolution) {
                for (x in 0 until resolution) {
                    image.setRGB(x, y, linearToSrgbArgb(renderPixel(face, x, y)))
                }
            }
            ImageIO.write(image, "png", file)
            writtenFiles[face.id] = file
        }
        return writtenFiles
    }

    /**
     * Applies overwrite policy checks and ensures the destination directory exists.
     *
     * `SkipExisting` is treated as a real skip: the caller receives the output path in the result,
     * but no file is regenerated.
     */
    fun prepareOutputFile(
        file: File,
        overwritePolicy: EnvironmentIblOverwritePolicy,
    ): OutputWriteMode {
        if (file.exists()) {
            return when (overwritePolicy) {
                EnvironmentIblOverwritePolicy.Fail -> error("Output file already exists: ${file.path}")
                EnvironmentIblOverwritePolicy.Replace -> OutputWriteMode.Write
                EnvironmentIblOverwritePolicy.SkipExisting -> OutputWriteMode.Skip
            }
        }
        file.parentFile?.mkdirs()
        return OutputWriteMode.Write
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
     * Values are clamped to `[0, 1]` because PNG cannot represent the original HDR range.
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

enum class OutputWriteMode {
    Write,
    Skip,
}
