package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.SkyboxResourceSet
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SkyboxAtlasImportService(
    private val assetRoot: File,
) {
    fun importSkybox(
        environment: Environment,
        sourceFile: File,
        outputDirectory: String,
        regions: Collection<SkyboxImportRegion>,
    ): Environment {
        val sourceImage = ImageIO.read(sourceFile) ?: error("Failed to read source image '${sourceFile.path}'.")
        val normalizedOutputDirectory = outputDirectory.trim().replace('\\', '/').trim('/').ifBlank { "skybox" }
        val manifestDirectory = environment.manifestPath.replace('\\', '/').substringBeforeLast('/', "")
        val targetRoot = if (manifestDirectory.isBlank()) assetRoot else File(assetRoot, manifestDirectory)
        val facePaths = linkedMapOf<String, String>()
        regions.forEach { region ->
            validateRegion(region, sourceImage.width, sourceImage.height)
            val cropped = sourceImage.getSubimage(region.x, region.y, region.width, region.height)
            val transformed = applyTransform(copyArgb(cropped), region.transform)
            val outputRelativePath = "$normalizedOutputDirectory/${region.face.id}.png"
            val outputFile = File(targetRoot, outputRelativePath)
            outputFile.parentFile?.mkdirs()
            require(ImageIO.write(transformed, "png", outputFile)) { "PNG writer is unavailable for '${outputFile.path}'." }
            facePaths[region.face.id] = outputRelativePath
        }
        val faceResolution = regions.firstOrNull()?.width ?: 1024
        return environment.copy(
            skybox =
                SkyboxResourceSet(
                    layout = "SixFaces",
                    resolution = faceResolution,
                    format = "PNG",
                    faces = facePaths,
                ),
        )
    }

    private fun validateRegion(
        region: SkyboxImportRegion,
        imageWidth: Int,
        imageHeight: Int,
    ) {
        require(region.width > 0 && region.height > 0) { "Face '${region.face.id}' region must have positive size." }
        require(region.x >= 0 && region.y >= 0) { "Face '${region.face.id}' region must stay within source bounds." }
        require(region.x + region.width <= imageWidth && region.y + region.height <= imageHeight) {
            "Face '${region.face.id}' region exceeds source image bounds."
        }
    }

    private fun applyTransform(
        image: BufferedImage,
        transform: SkyboxFaceTransform,
    ): BufferedImage {
        var current = image
        if (transform.flipX) {
            current = transform(current, AffineTransform.getScaleInstance(-1.0, 1.0).apply { translate(-current.width.toDouble(), 0.0) })
        }
        if (transform.flipY) {
            current = transform(current, AffineTransform.getScaleInstance(1.0, -1.0).apply { translate(0.0, -current.height.toDouble()) })
        }
        return when ((transform.rotateDegrees % 360 + 360) % 360) {
            90 -> rotate(current, 90)
            180 -> rotate(current, 180)
            270 -> rotate(current, 270)
            else -> current
        }
    }

    private fun rotate(
        image: BufferedImage,
        degrees: Int,
    ): BufferedImage {
        val radians = Math.toRadians(degrees.toDouble())
        val targetWidth = if (degrees == 180) image.width else image.height
        val targetHeight = if (degrees == 180) image.height else image.width
        val transform = AffineTransform()
        transform.translate(targetWidth / 2.0, targetHeight / 2.0)
        transform.rotate(radians)
        transform.translate(-image.width / 2.0, -image.height / 2.0)
        return transform(image, transform, targetWidth, targetHeight)
    }

    private fun transform(
        image: BufferedImage,
        transform: AffineTransform,
        targetWidth: Int = image.width,
        targetHeight: Int = image.height,
    ): BufferedImage {
        val output = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val op = AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
        op.filter(image, output)
        return output
    }

    private fun copyArgb(image: BufferedImage): BufferedImage {
        val copy = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return copy
    }
}
