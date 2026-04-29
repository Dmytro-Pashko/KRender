package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.EntityId

/**
 * Mutable UI state owned by one Scene Editor scene instance.
 */
data class SceneEditorState(
    /** File path of the scene currently open in the editor; null when no file has been saved or loaded yet. */
    var currentScenePath: String? = null,

    /** Human-readable scene name shown in the title bar and save dialogs. */
    var sceneName: String = "Untitled Scene",

    /** Entity ID of the currently selected object; null when nothing is selected. */
    var selectedEntityId: EntityId? = null,

    /** True when in-memory scene data differs from the last saved file, used to prompt unsaved-changes warnings. */
    var hasUnsavedChanges: Boolean = false,

    /** One-line message displayed in the editor status bar to surface results of the last action. */
    var statusMessage: String = "Scene Editor ready.",

    /** Set to true by the UI to trigger a Save As dialog on the next update tick. */
    var saveAsRequested: Boolean = false,

    /** Destination file path collected from the Save As dialog before the save is executed. */
    var saveAsPath: String = "",

    /** Non-null when the last save attempt failed; displayed in the UI as an error notice. */
    var saveErrorMessage: String? = null,

    /** Set to true by the UI to trigger an Open dialog on the next update tick. */
    var openRequested: Boolean = false,

    /** Source file path collected from the Open dialog before the load is executed. */
    var openPath: String = "",

    /** Non-null when the last open attempt failed; displayed in the UI as an error notice. */
    var openErrorMessage: String? = null,
)
