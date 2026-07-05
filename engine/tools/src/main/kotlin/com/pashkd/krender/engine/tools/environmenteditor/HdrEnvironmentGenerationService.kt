package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentIblGenerationConfig
import com.pashkd.krender.engine.assets.environment.EnvironmentIblGenerationResult
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import java.io.File

class HdrEnvironmentGenerationService(
    private val assetRoot: File,
    private val hdrImageReader: HdrImageReader? = DefaultHdrImageReader(),
    private val cubemapGenerator: EquirectangularToCubemapGenerator = EquirectangularToCubemapGenerator(),
    private val irradianceGenerator: IrradianceConvolutionGenerator = IrradianceConvolutionGenerator(),
    private val radianceGenerator: RadiancePrefilterGenerator = RadiancePrefilterGenerator(),
) {
    fun availability(dialog: HdrEnvironmentGenerationDialog): HdrEnvironmentGenerationAvailability =
        when (dialog) {
            HdrEnvironmentGenerationDialog.Skybox ->
                if (hdrImageReader != null) {
                    HdrEnvironmentGenerationAvailability(true, null)
                } else {
                    HdrEnvironmentGenerationAvailability(false, "HDR/EXR reader is not available yet.")
                }
            HdrEnvironmentGenerationDialog.Irradiance,
            HdrEnvironmentGenerationDialog.Radiance,
            HdrEnvironmentGenerationDialog.AllIbl,
            ->
                if (hdrImageReader != null) {
                    HdrEnvironmentGenerationAvailability(true, null)
                } else {
                    HdrEnvironmentGenerationAvailability(false, "HDR/EXR reader is not available yet.")
                }
            HdrEnvironmentGenerationDialog.BrdfLut ->
                HdrEnvironmentGenerationAvailability(false, "BRDF LUT generator is not available yet.")
        }

    fun generateSkybox(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult {
        validateConfig(config)
        val image = readSourceImage(config)
        val manifestDirectory = manifestDirectoryFile(config.environmentManifestPath)
        val outputDirectory =
            resolveOutputDirectory(
                manifestDirectory = manifestDirectory,
                manifestPath = config.environmentManifestPath,
                outputDirectory = config.outputDirectory,
            )
        val writtenFiles =
            cubemapGenerator.generate(
                source = image,
                outputDirectory = outputDirectory,
                outputFormat = config.format.name,
                overwritePolicy = config.overwritePolicy,
                config = config.skybox,
            )
        return EnvironmentIblGenerationResult(
            skyboxFaces =
                writtenFiles.mapValues { (_, file) ->
                    relativeToManifestDirectory(manifestDirectory, file)
                },
        )
    }

    fun generateIrradiance(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult {
        validateConfig(config)
        val image = readSourceImage(config)
        val manifestDirectory = manifestDirectoryFile(config.environmentManifestPath)
        val outputDirectory =
            resolveOutputDirectory(
                manifestDirectory = manifestDirectory,
                manifestPath = config.environmentManifestPath,
                outputDirectory = config.outputDirectory,
            )
        val writtenFiles =
            irradianceGenerator.generate(
                source = image,
                outputDirectory = outputDirectory,
                outputFormat = config.format.name,
                overwritePolicy = config.overwritePolicy,
                config = config.irradiance,
            )
        return EnvironmentIblGenerationResult(
            irradianceFaces =
                writtenFiles.mapValues { (_, file) ->
                    relativeToManifestDirectory(manifestDirectory, file)
                },
        )
    }

    fun generateRadiance(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult {
        validateConfig(config)
        val image = readSourceImage(config)
        val manifestDirectory = manifestDirectoryFile(config.environmentManifestPath)
        val outputDirectory =
            resolveOutputDirectory(
                manifestDirectory = manifestDirectory,
                manifestPath = config.environmentManifestPath,
                outputDirectory = config.outputDirectory,
            )
        val writtenFiles =
            radianceGenerator.generate(
                source = image,
                outputDirectory = outputDirectory,
                outputFormat = config.format.name,
                overwritePolicy = config.overwritePolicy,
                config = config.radiance,
            )
        return EnvironmentIblGenerationResult(
            radianceMipFaces =
                writtenFiles.mapValues { (_, faces) ->
                    faces.mapValues { (_, file) -> relativeToManifestDirectory(manifestDirectory, file) }
                },
        )
    }

    fun generateBrdfLut(config: HdrBrdfLutGenerationRequest): EnvironmentIblGenerationResult =
        unsupported("BRDF LUT generator is not available yet.")

    fun generateAll(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult {
        validateConfig(config)
        if (config.brdfLut.enabled) {
            unsupported("BRDF LUT generation is not implemented yet. Disable BRDF LUT to continue.")
        }

        val image = readSourceImage(config)
        val manifestDirectory = manifestDirectoryFile(config.environmentManifestPath)
        val outputRoot =
            resolveOutputDirectory(
                manifestDirectory = manifestDirectory,
                manifestPath = config.environmentManifestPath,
                outputDirectory = config.outputDirectory,
            )

        val skyboxFaces =
            if (config.skybox.enabled) {
                cubemapGenerator.generate(
                    source = image,
                    outputDirectory = File(outputRoot, "skybox"),
                    outputFormat = config.format.name,
                    overwritePolicy = config.overwritePolicy,
                    config = config.skybox,
                ).mapValues { (_, file) -> relativeToManifestDirectory(manifestDirectory, file) }
            } else {
                emptyMap()
            }

        val irradianceFaces =
            if (config.irradiance.enabled) {
                irradianceGenerator.generate(
                    source = image,
                    outputDirectory = File(outputRoot, "irradiance"),
                    outputFormat = config.format.name,
                    overwritePolicy = config.overwritePolicy,
                    config = config.irradiance,
                ).mapValues { (_, file) -> relativeToManifestDirectory(manifestDirectory, file) }
            } else {
                emptyMap()
            }

        val radianceMipFaces =
            if (config.radiance.enabled) {
                radianceGenerator.generate(
                    source = image,
                    outputDirectory = File(outputRoot, "radiance"),
                    outputFormat = config.format.name,
                    overwritePolicy = config.overwritePolicy,
                    config = config.radiance,
                ).mapValues { (_, faces) ->
                    faces.mapValues { (_, file) -> relativeToManifestDirectory(manifestDirectory, file) }
                }
            } else {
                emptyMap()
            }

        return EnvironmentIblGenerationResult(
            skyboxFaces = skyboxFaces,
            irradianceFaces = irradianceFaces,
            radianceMipFaces = radianceMipFaces,
        )
    }

    private fun readSourceImage(config: EnvironmentIblGenerationConfig): HdrImage {
        val reader = hdrImageReader ?: error("HDR/EXR reader is not available yet.")
        val sourceFile = resolveInputFile(config.environmentManifestPath, config.sourceHdrPath)
        require(sourceFile.isFile) { "HDR/EXR source file not found: ${config.sourceHdrPath}" }
        return reader.read(sourceFile)
    }

    private fun validateConfig(config: EnvironmentIblGenerationConfig) {
        require(config.sourceHdrPath.isNotBlank()) { "Select a source HDR/EXR file." }
        require(config.format.name.equals("png", ignoreCase = true)) {
            "Only PNG output is currently supported."
        }
        require(config.skybox.resolution > 0) { "Skybox resolution must be positive." }
        require(config.irradiance.resolution > 0) { "Irradiance resolution must be positive." }
        require(config.irradiance.sampleCount > 0) { "Irradiance sample count must be positive." }
        require(config.radiance.baseResolution > 0) { "Radiance base resolution must be positive." }
        require(config.radiance.mipCount >= 1) { "Radiance mip count must be at least 1." }
        require(config.radiance.sampleCount > 0) { "Radiance sample count must be positive." }
    }

    private fun resolveInputFile(
        manifestPath: String,
        inputPath: String,
    ): File {
        val direct = File(inputPath)
        if (direct.isAbsolute) return direct
        val manifestDirectory = manifestDirectoryFile(manifestPath)
        return File(manifestDirectory, inputPath).takeIf(File::exists) ?: File(assetRoot, inputPath)
    }

    private fun manifestDirectoryFile(manifestPath: String): File =
        File(assetRoot, EnvironmentPathResolver.manifestDirectory(manifestPath))

    private fun resolveOutputDirectory(
        manifestDirectory: File,
        manifestPath: String,
        outputDirectory: String,
    ): File {
        val direct = File(outputDirectory)
        if (direct.isAbsolute) return direct

        val normalized = outputDirectory.replace('\\', '/').trim()
        val manifestDirectoryPath = EnvironmentPathResolver.manifestDirectory(manifestPath).trim('/').trim()
        if (
            manifestDirectoryPath.isNotBlank() &&
            (normalized == manifestDirectoryPath || normalized.startsWith("$manifestDirectoryPath/"))
        ) {
            return File(assetRoot, normalized)
        }
        return File(manifestDirectory, normalized)
    }

    private fun relativeToManifestDirectory(
        manifestDirectory: File,
        file: File,
    ): String =
        manifestDirectory
            .toPath()
            .relativize(file.toPath())
            .toString()
            .replace('\\', '/')
    private fun unsupported(message: String): Nothing = throw UnsupportedOperationException(message)
}

data class HdrEnvironmentGenerationAvailability(
    val available: Boolean,
    val reason: String?,
)
