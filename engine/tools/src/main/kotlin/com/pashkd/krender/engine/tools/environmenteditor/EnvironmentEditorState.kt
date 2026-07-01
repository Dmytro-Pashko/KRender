package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.assets.environment.EnvironmentAssetId
import com.pashkd.krender.engine.assets.environment.EnvironmentValidationReport

/**
 * Mutable editor state for the Environment Editor tool.
 */
class EnvironmentEditorState(
    val manifestPath: String,
) {
    var selectedEnvironmentId: EnvironmentAssetId? = null
    var environment: EnvironmentAsset? = null
    var validation: EnvironmentValidationReport? = null
    var dirty: Boolean = false
    var loadError: String? = null
    var statusMessage: String? = null
}
