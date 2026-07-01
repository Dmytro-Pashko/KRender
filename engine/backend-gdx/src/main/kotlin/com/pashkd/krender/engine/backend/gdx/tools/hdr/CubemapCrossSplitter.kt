package com.pashkd.krender.engine.backend.gdx.tools.hdr

import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestLoader
import com.pashkd.krender.engine.assets.hdr.HdrSkyboxConfig
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal class CubemapCrossSplitter {
    fun split(
        manifestPath: Path,
        skybox: HdrSkyboxConfig,
    ): List<Path> {
        val inputPath = HdrEnvironmentManifestLoader.resolve(manifestPath, skybox.path)
        val source = ImageIO.read(inputPath.toFile()) ?: error("Unsupported skybox image: $inputPath")
        validate(source)
        require(skybox.faces.toSet() == FACE_REGIONS.keys) {
            "Cubemap faces must contain exactly ${FACE_REGIONS.keys.joinToString()}."
        }
        val faceSize = source.width / CROSS_COLUMNS
        return skybox.faces.map { face ->
            val region = FACE_REGIONS.getValue(face)
            val outputPath =
                HdrEnvironmentManifestLoader.resolve(
                    manifestPath,
                    skybox.generatedFacesPath.replace(FACE_TOKEN, face),
                )
            Files.createDirectories(outputPath.parent)
            ImageIO.write(copyRegion(source, region.first * faceSize, region.second * faceSize, faceSize), "png", outputPath.toFile())
            outputPath
        }
    }

    private fun validate(source: BufferedImage) {
        require(source.width % CROSS_COLUMNS == 0) {
            "Cubemap-cross width must be divisible by $CROSS_COLUMNS, got ${source.width}."
        }
        require(source.height % CROSS_ROWS == 0) {
            "Cubemap-cross height must be divisible by $CROSS_ROWS, got ${source.height}."
        }
        require(source.width / CROSS_COLUMNS == source.height / CROSS_ROWS) {
            "Cubemap-cross faces must be square, got ${source.width / CROSS_COLUMNS}x${source.height / CROSS_ROWS}."
        }
    }

    private fun copyRegion(
        source: BufferedImage,
        x: Int,
        y: Int,
        size: Int,
    ): BufferedImage =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { target ->
            val graphics = target.createGraphics()
            try {
                graphics.drawImage(source, 0, 0, size, size, x, y, x + size, y + size, null)
            } finally {
                graphics.dispose()
            }
        }

    companion object {
        private const val CROSS_COLUMNS = 4
        private const val CROSS_ROWS = 3
        private const val FACE_TOKEN = "{face}"
        private val FACE_REGIONS =
            linkedMapOf(
                "negx" to (0 to 1),
                "posx" to (2 to 1),
                "negy" to (1 to 2),
                "posy" to (1 to 0),
                "negz" to (3 to 1),
                "posz" to (1 to 1),
            )
    }
}
