package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.assets.environment.EnvironmentValidationReport
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewState

/**
 * Mutable editor state for the Environment Editor tool.
 */
class EnvironmentEditorState(
    val manifestPath: String,
) {
    val previewState = EnvironmentPreviewState()
    var previewModelEntityId: Long? = null
    var environment: EnvironmentAsset? = null
    var validation: EnvironmentValidationReport? = null
    var dirty: Boolean = false
    var loadError: String? = null
    var statusMessage: String? = null
    var environmentCacheRevision: Long = 0L

    /** Replaces editor data with a clean snapshot loaded from disk. */
    fun applyLoadedEnvironment(asset: EnvironmentAsset) {
        environment = asset
        dirty = false
        environmentCacheRevision += 1L
    }

    /** Applies an in-memory edit and marks the manifest as modified. */
    fun updateEnvironment(transform: (EnvironmentAsset) -> EnvironmentAsset) {
        val current = environment ?: return
        val updated = transform(current)
        if (updated != current) {
            environment = updated
            dirty = true
        }
    }

    /** Forces the preview/runtime environment preset cache key to advance after disk writes. */
    fun invalidateEnvironmentCache() {
        environmentCacheRevision += 1L
    }
}
