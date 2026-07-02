package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.io.File

class EnvironmentEditorController(
    private val state: EnvironmentEditorState,
    private val engine: EngineContext,
    private val environmentService: com.pashkd.krender.engine.assets.environment.EnvironmentService,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun revert() {
        try {
            val asset = environmentService.load(state.manifestPath)
            state.applyLoadedEnvironment(asset)
            state.validation = environmentService.validate(asset)
            state.loadError = null
            state.dirty = false
            state.statusMessage = "Environment reverted to saved state."
            engine.logger.info(TAG) { "Environment reverted id='${asset.id.path}' path='${state.manifestPath}'" }
        } catch (error: Exception) {
            state.statusMessage = "Revert failed: ${error.message}"
            engine.logger.error(TAG, error) { "Environment revert failed path='${state.manifestPath}': ${error.message}" }
        }
    }

    fun save() {
        val env = state.environment ?: return
        try {
            environmentService.save(env)
            state.validation = environmentService.validate(env)
            state.dirty = false
            state.statusMessage = "Environment saved."
            engine.logger.info(TAG) { "Environment saved id='${env.id.path}' path='${state.manifestPath}'" }
        } catch (error: Exception) {
            state.statusMessage = "Save failed: ${error.message}"
            engine.logger.error(TAG, error) { "Environment save failed path='${state.manifestPath}': ${error.message}" }
        }
    }

    fun reload() {
        try {
            val asset = environmentService.load(state.manifestPath)
            state.applyLoadedEnvironment(asset)
            state.validation = environmentService.validate(asset)
            state.loadError = null
            state.dirty = false
            state.statusMessage = "Environment reloaded."
            engine.logger.info(TAG) { "Environment reloaded id='${asset.id.path}' path='${state.manifestPath}'" }
        } catch (error: Exception) {
            state.environment = null
            state.validation = null
            state.loadError = error.message ?: "Unknown error"
            state.statusMessage = "Reload failed: ${error.message}"
            engine.logger.error(TAG, error) { "Environment reload failed path='${state.manifestPath}': ${error.message}" }
        }
    }

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(EnvironmentEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(EnvironmentEditorUiLayoutDefaults.config)
        state.statusMessage = "Panel layout reset to default."
    }

    fun requestExit() {
        state.statusMessage = "Closing Environment Editor."
        engine.requestExit()
    }

    fun fileInfo(): EnvironmentEditorFileInfo {
        val resolved = resolveManifestFile()
        return EnvironmentEditorFileInfo(
            displayPath = state.manifestPath,
            resolvedPath = resolved?.path?.replace('\\', '/'),
            sizeBytes = resolved?.takeIf(File::isFile)?.length(),
        )
    }

    private fun resolveManifestFile(): File? {
        val normalized = state.manifestPath.trim()
        if (normalized.isBlank()) return null
        val direct = File(normalized)
        if (direct.isAbsolute) {
            return direct.takeIf(File::exists) ?: direct
        }
        return File(engine.assetRegistry.baseDir(), normalized).canonicalFile
    }

    companion object {
        private const val TAG = "EnvironmentEditorCtrl"
    }
}

data class EnvironmentEditorFileInfo(
    val displayPath: String,
    val resolvedPath: String?,
    val sizeBytes: Long?,
)
