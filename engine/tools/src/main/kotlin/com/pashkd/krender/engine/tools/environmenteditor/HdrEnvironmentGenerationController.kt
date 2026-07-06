package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.assets.environment.TextureResourceRef
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File

class HdrEnvironmentGenerationController(
    private val state: EnvironmentEditorState,
    private val assetRoot: File,
    private val environmentService: EnvironmentService,
    private val logger: Logger? = null,
) {
    private val generatorState = state.hdrGenerationState
    private val manifestUpdater = EnvironmentGenerationManifestUpdater()
    private val generationService = HdrEnvironmentGenerationService(assetRoot)

    fun open(dialog: HdrEnvironmentGenerationDialog) {
        prepareDefaultPaths(dialog)
        generatorState.openDialog = dialog
        generatorState.statusMessage = generationService.availability(dialog).reason
        state.statusMessage = dialog.title
    }

    fun close() {
        generatorState.openDialog = null
    }

    fun availability(dialog: HdrEnvironmentGenerationDialog): HdrEnvironmentGenerationAvailability =
        generationService.availability(dialog)

    fun setAllSourcePath(path: String) {
        generatorState.allIblRequest.sourceHdrPath = path
    }

    fun generateAll() {
        val environment = state.environment ?: return
        val config = generatorState.allIblRequest.toConfig(environment.manifestPath)
        runGeneration(HdrEnvironmentGenerationDialog.AllIbl, environment) {
            val result = generationService.generateAll(config)
            manifestUpdater.applyAll(environment, config, result)
        }
    }

    fun importBrdfLut(sourcePath: String) {
        val environment = state.environment ?: return
        require(sourcePath.isNotBlank()) { "Select a BRDF LUT image to import." }

        val sourceFile = File(sourcePath).toPath().toAbsolutePath().normalize()
        require(Files.isRegularFile(sourceFile)) { "BRDF LUT file not found: $sourcePath" }

        val manifestDirectory =
            File(assetRoot, EnvironmentPathResolver.manifestDirectory(environment.manifestPath))
                .toPath()
                .toAbsolutePath()
                .normalize()
        val targetFile = manifestDirectory.resolve("brdf").resolve(sourceFile.fileName.toString())
        Files.createDirectories(targetFile.parent)
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)

        val relativePath = manifestDirectory.relativize(targetFile).toString().replace('\\', '/')
        val updated = environment.copy(brdfLut = TextureResourceRef(relativePath))
        state.updateEnvironment { updated }
        state.validation = environmentService.validate(updated)
        state.invalidateEnvironmentCache()
        generatorState.statusMessage = "BRDF LUT imported."
        state.statusMessage = "BRDF LUT imported."
    }

    private fun prepareDefaultPaths(dialog: HdrEnvironmentGenerationDialog) {
        val environment = state.environment ?: return
        when (dialog) {
            HdrEnvironmentGenerationDialog.AllIbl -> {
                generatorState.allIblRequest.outputRoot = EnvironmentPathResolver.manifestDirectory(environment.manifestPath)
            }
        }
    }

    private fun runGeneration(
        dialog: HdrEnvironmentGenerationDialog,
        environment: Environment,
        block: () -> Environment,
    ) {
        val availability = generationService.availability(dialog)
        if (!availability.available) {
            val message = availability.reason ?: "${dialog.title} is not available."
            generatorState.statusMessage = message
            state.statusMessage = message
            logger?.warn(TAG) { "Environment generation unavailable dialog=${dialog.name} reason='$message'" }
            return
        }
        try {
            logger?.info(TAG) {
                "Environment generation started dialog=${dialog.name} manifest='${environment.manifestPath}'"
            }
            val updated = block()
            state.updateEnvironment { updated }
            state.validation = environmentService.validate(updated)
            state.invalidateEnvironmentCache()
            generatorState.statusMessage = "${dialog.title} completed."
            state.statusMessage = "${dialog.title} completed."
            close()
            logger?.info(TAG) {
                "Environment generation completed dialog=${dialog.name} manifest='${updated.manifestPath}' cacheRevision=${state.environmentCacheRevision}"
            }
        } catch (error: Exception) {
            generatorState.statusMessage = error.message ?: "${dialog.title} failed."
            state.statusMessage = generatorState.statusMessage
            logger?.warn(TAG, error) {
                "Environment generation failed dialog=${dialog.name} manifest='${environment.manifestPath}': ${error.message}"
            }
        }
    }

    companion object {
        private const val TAG = "HdrEnvironmentGenCtrl"
    }
}
