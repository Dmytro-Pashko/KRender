package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.EntityId

/**
 * Mutable UI state owned by one Scene Editor scene instance.
 */
data class SceneEditorState(
    var currentScenePath: String? = null,
    var sceneName: String = "Untitled Scene",
    var selectedEntityId: EntityId? = null,
    var hasUnsavedChanges: Boolean = false,
    var statusMessage: String = "Scene Editor ready.",
    var saveAsRequested: Boolean = false,
    var saveAsPath: String = "",
    var saveErrorMessage: String? = null,
    var openRequested: Boolean = false,
    var openPath: String = "",
    var openErrorMessage: String? = null,
)
