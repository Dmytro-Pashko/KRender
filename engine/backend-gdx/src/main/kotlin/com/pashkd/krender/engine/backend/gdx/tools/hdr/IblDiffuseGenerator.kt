package com.pashkd.krender.engine.backend.gdx.tools.hdr

import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifest
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal class IblDiffuseGenerator(
    private val splitter: CubemapCrossSplitter = CubemapCrossSplitter(),
) {
    fun generate(
        manifestPath: Path,
        manifest: HdrEnvironmentManifest,
    ): List<Path> {
        val skybox = manifest.skybox ?: error("Manifest does not define a skybox.")
        val inputPaths =
            skybox.faces.associateWith { face ->
                HdrEnvironmentManifestLoader.resolve(
                    manifestPath,
                    skybox.generatedFacesPath.replace(FACE_TOKEN, face),
                )
            }
        if (inputPaths.values.any { !Files.isRegularFile(it) }) {
            splitter.split(manifestPath, skybox)
        }
        return manifest.irradiance.faces.map { face ->
            val inputPath = inputPaths[face] ?: error("No generated skybox face '$face'.")
            val source = ImageIO.read(inputPath.toFile()) ?: error("Unsupported skybox face: $inputPath")
            val resized = HdrImageProcessing.resize(source, manifest.irradiance.size, manifest.irradiance.size)
            val blurred =
                HdrImageProcessing.repeatedBoxBlur(
                    resized,
                    radius = (manifest.irradiance.size / BLUR_RADIUS_DIVISOR).coerceAtLeast(1),
                    passes = BLUR_PASSES,
                )
            val outputPath =
                HdrEnvironmentManifestLoader.resolve(
                    manifestPath,
                    manifest.irradiance.path.replace(FACE_TOKEN, face),
                )
            Files.createDirectories(outputPath.parent)
            ImageIO.write(blurred, "png", outputPath.toFile())
            resized.flush()
            blurred.flush()
            source.flush()
            outputPath
        }
    }

    companion object {
        private const val FACE_TOKEN = "{face}"
        private const val BLUR_RADIUS_DIVISOR = 12
        private const val BLUR_PASSES = 4
    }
}
