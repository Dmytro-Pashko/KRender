package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentIblGenerationConfig
import com.pashkd.krender.engine.assets.environment.EnvironmentIblGenerationResult
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import java.io.File

class HdrEnvironmentGenerationService(
    private val assetRoot: File,
    private val hdrImageReader: HdrImageReader? = null,
    private val cubemapGenerator: EquirectangularToCubemapGenerator = EquirectangularToCubemapGenerator(),
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
                HdrEnvironmentGenerationAvailability(false, "HDR/EXR reader is not available yet.")
            HdrEnvironmentGenerationDialog.BrdfLut ->
                HdrEnvironmentGenerationAvailability(false, "BRDF LUT generator is not available yet.")
        }

    fun generateSkybox(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult {
        val reader = hdrImageReader ?: error("HDR/EXR reader is not available yet.")
        val sourceFile = resolveInputFile(config.environmentManifestPath, config.sourceHdrPath)
        require(sourceFile.isFile) { "HDR/EXR source file not found: ${config.sourceHdrPath}" }
        val image = reader.read(sourceFile)
        val manifestDirectory = manifestDirectoryFile(config.environmentManifestPath)
        val outputDirectory = resolveOutputDirectory(manifestDirectory, config.outputDirectory)
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

    fun generateIrradiance(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult =
        unsupported("Irradiance generation is not available yet.")

    fun generateRadiance(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult =
        unsupported("Radiance generation is not available yet.")

    fun generateBrdfLut(config: HdrBrdfLutGenerationRequest): EnvironmentIblGenerationResult =
        unsupported("BRDF LUT generator is not available yet.")

    fun generateAll(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult =
        unsupported("Generate All IBL is not available until HDR/EXR generation is implemented.")

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
        outputDirectory: String,
    ): File {
        val direct = File(outputDirectory)
        return if (direct.isAbsolute) direct else File(manifestDirectory, outputDirectory)
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
