package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.io.File

/**
 * Owns Environment Editor commands that cross the in-memory/disk boundary.
 *
 * Panels call this controller instead of accessing file services directly, which
 * keeps state transitions, user messages, and structured logs consistent.
 */
class EnvironmentEditorController(
    private val state: EnvironmentEditorState,
    private val engine: EngineContext,
    private val environmentService: com.pashkd.krender.engine.assets.environment.EnvironmentService,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun revert() {
        loadFromDisk(action = "reverted", clearStateOnFailure = false)
    }

    fun save() {
        val env = state.environment ?: return
        try {
            environmentService.save(env)
            state.validation = environmentService.validate(env)
            state.dirty = false
            state.invalidateEnvironmentCache()
            state.statusMessage = "Environment saved."
            engine.logger.info(TAG) {
                "Environment saved id='${env.id}' path='${state.manifestPath}' cacheRevision=${state.environmentCacheRevision}"
            }
        } catch (error: Exception) {
            state.statusMessage = "Save failed: ${error.message}"
            engine.logger.error(TAG, error) { "Environment save failed path='${state.manifestPath}': ${error.message}" }
        }
    }

    fun reload() {
        loadFromDisk(action = "reloaded", clearStateOnFailure = true)
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
        return normalized.takeIf(String::isNotBlank)?.let { path ->
            val direct = File(path)
            if (direct.isAbsolute) direct else File(engine.assetRegistry.baseDir(), path).canonicalFile
        }
    }

    private fun loadFromDisk(
        action: String,
        clearStateOnFailure: Boolean,
    ) {
        try {
            val asset = environmentService.load(state.manifestPath)
            state.applyLoadedEnvironment(asset)
            state.validation = environmentService.validate(asset)
            state.loadError = null
            state.statusMessage = "Environment $action."
            engine.logger.info(TAG) {
                "Environment $action id='${asset.id}' path='${state.manifestPath}'"
            }
        } catch (error: Exception) {
            if (clearStateOnFailure) {
                state.environment = null
                state.validation = null
            }
            state.loadError = error.message ?: "Unknown error"
            state.statusMessage = "Environment ${action.removeSuffix("ed")} failed: ${error.message}"
            engine.logger.error(TAG, error) {
                "Environment ${action.removeSuffix("ed")} failed path='${state.manifestPath}': ${error.message}"
            }
        }
    }

    companion object {
        private const val TAG = "EnvironmentEditorCtrl"
    }
}

/** Resolved file information displayed without exposing file IO to the panel. */
data class EnvironmentEditorFileInfo(
    val displayPath: String,
    val resolvedPath: String?,
    val sizeBytes: Long?,
)
