package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import com.pashkd.krender.engine.assets.environment.EnvironmentService
import java.io.File

class HdrEnvironmentGenerationController(
    private val state: EnvironmentEditorState,
    assetRoot: File,
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

    fun setSkyboxSourcePath(path: String) {
        generatorState.skyboxRequest.sourceHdrPath = path
    }

    fun setIrradianceSourcePath(path: String) {
        generatorState.irradianceRequest.sourceHdrPath = path
    }

    fun setRadianceSourcePath(path: String) {
        generatorState.radianceRequest.sourceHdrPath = path
    }

    fun setAllSourcePath(path: String) {
        generatorState.allIblRequest.sourceHdrPath = path
    }

    fun generateSkybox() {
        val environment = state.environment ?: return
        val config = generatorState.skyboxRequest.toConfig(environment.manifestPath)
        runGeneration(HdrEnvironmentGenerationDialog.Skybox, environment, rememberSource = generatorState.skyboxRequest.rememberSource) {
            val result = generationService.generateSkybox(config)
            manifestUpdater.applySkybox(environment, config, result, generatorState.skyboxRequest.rememberSource)
        }
    }

    fun generateIrradiance() {
        val environment = state.environment ?: return
        val config = generatorState.irradianceRequest.toConfig(environment.manifestPath)
        runGeneration(HdrEnvironmentGenerationDialog.Irradiance, environment, rememberSource = generatorState.irradianceRequest.rememberSource) {
            generationService.generateIrradiance(config)
            manifestUpdater.applyIrradiance(environment, config, generatorState.irradianceRequest.rememberSource)
        }
    }

    fun generateRadiance() {
        val environment = state.environment ?: return
        val config = generatorState.radianceRequest.toConfig(environment.manifestPath)
        runGeneration(HdrEnvironmentGenerationDialog.Radiance, environment, rememberSource = generatorState.radianceRequest.rememberSource) {
            generationService.generateRadiance(config)
            manifestUpdater.applyRadiance(environment, config, generatorState.radianceRequest.rememberSource)
        }
    }

    fun generateBrdfLut() {
        val environment = state.environment ?: return
        runGeneration(HdrEnvironmentGenerationDialog.BrdfLut, environment, rememberSource = false) {
            generationService.generateBrdfLut(generatorState.brdfLutRequest)
            manifestUpdater.applyBrdfLut(environment)
        }
    }

    fun generateAll() {
        val environment = state.environment ?: return
        val config = generatorState.allIblRequest.toConfig(environment.manifestPath)
        runGeneration(HdrEnvironmentGenerationDialog.AllIbl, environment, rememberSource = generatorState.allIblRequest.rememberSource) {
            val result = generationService.generateAll(config)
            manifestUpdater.applyAll(environment, config, result, generatorState.allIblRequest.rememberSource)
        }
    }

    private fun prepareDefaultPaths(dialog: HdrEnvironmentGenerationDialog) {
        val environment = state.environment ?: return
        when (dialog) {
            HdrEnvironmentGenerationDialog.AllIbl -> {
                generatorState.allIblRequest.outputRoot = EnvironmentPathResolver.manifestDirectory(environment.manifestPath)
            }
            else -> Unit
        }
    }

    private fun runGeneration(
        dialog: HdrEnvironmentGenerationDialog,
        environment: Environment,
        rememberSource: Boolean,
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
                "Environment generation started dialog=${dialog.name} manifest='${environment.manifestPath}' rememberSource=$rememberSource"
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
