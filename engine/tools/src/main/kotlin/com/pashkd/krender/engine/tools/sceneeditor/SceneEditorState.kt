package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.scene.SceneValidationReport
import com.pashkd.krender.engine.tools.viewport.EditorViewportCameraState
import com.pashkd.krender.engine.tools.viewport.EditorViewportState

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
    /** Non-null when the last save attempt failed; displayed in the UI as an error notice. */
    var saveErrorMessage: String? = null,
    /** Non-null when the last open attempt failed; displayed in the UI as an error notice. */
    var openErrorMessage: String? = null,
    /** Runtime-only editor viewport camera state; never serialized into `.krscene` files. */
    var camera: EditorViewportCameraState = EditorViewportCameraState(),
    /** True when the viewport panel is hovered or focused and can claim camera input. */
    var viewportFocused: Boolean = false,
    /** Top-left viewport panel position in screen pixels, used to convert mouse clicks into viewport-local rays. */
    var viewportOrigin: Vec2 = Vec2.zero(),
    /** Viewport panel size in screen pixels, used by viewport-local picking. */
    var viewportSize: Vec2 = Vec2.zero(),
    /** Runtime-only editor preference for drawing the viewport grid; never serialized into `.krscene` files. */
    var showGrid: Boolean = true,
    /** Runtime-only editor preference for drawing world axes; never serialized into `.krscene` files. */
    var showAxes: Boolean = true,
    /** Runtime-only editor preference for drawing the selected entity bounds; never serialized into `.krscene` files. */
    var showSelectedBoundingBox: Boolean = true,
    /** Grid extent in cells from the world origin for editor viewport guides. */
    var gridHalfExtentCells: Int = 24,
    /** World-space size of one editor viewport grid cell. */
    var gridCellSize: Float = 1f,
    /** Cached validation result for the current scene snapshot. */
    var validationReport: SceneValidationReport = SceneValidationReport(emptyList()),
    /** True when the cached validation report no longer matches the current scene state. */
    var validationDirty: Boolean = true,
) {
    var viewport: EditorViewportState = EditorViewportState(viewportFocused, viewportOrigin, viewportSize)
}
