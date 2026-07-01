package com.pashkd.krender.engine.backend.gdx.tools.hdr

import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifest
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal class IblSpecularGenerator(
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
        val outputs = mutableListOf<Path>()
        for (mip in 0 until manifest.radiance.mipLevels) {
            val roughness =
                if (manifest.radiance.mipLevels == 1) {
                    0.0
                } else {
                    mip.toDouble() / (manifest.radiance.mipLevels - 1)
                }
            val size = (manifest.radiance.baseSize shr mip).coerceAtLeast(1)
            manifest.radiance.faces.forEach { face ->
                val inputPath = inputPaths[face] ?: error("No generated skybox face '$face'.")
                val source = ImageIO.read(inputPath.toFile()) ?: error("Unsupported skybox face: $inputPath")
                val resized = HdrImageProcessing.resize(source, size, size)
                val filtered =
                    if (mip == 0 || size == 1) {
                        resized
                    } else {
                        HdrImageProcessing.repeatedBoxBlur(
                            resized,
                            radius = (roughness * size / BLUR_SCALE).roundToInt().coerceAtLeast(1),
                            passes = (MIN_BLUR_PASSES + roughness * EXTRA_BLUR_PASSES).roundToInt(),
                        )
                    }
                val outputPath =
                    HdrEnvironmentManifestLoader.resolve(
                        manifestPath,
                        manifest.radiance.path
                            .replace(MIP_TOKEN, mip.toString())
                            .replace(FACE_TOKEN, face),
                    )
                Files.createDirectories(outputPath.parent)
                ImageIO.write(filtered, "png", outputPath.toFile())
                if (filtered !== resized) filtered.flush()
                resized.flush()
                source.flush()
                outputs.add(outputPath)
            }
        }
        return outputs
    }

    companion object {
        private const val FACE_TOKEN = "{face}"
        private const val MIP_TOKEN = "{mip}"
        private const val BLUR_SCALE = 12.0
        private const val MIN_BLUR_PASSES = 1
        private const val EXTRA_BLUR_PASSES = 3
    }
}
