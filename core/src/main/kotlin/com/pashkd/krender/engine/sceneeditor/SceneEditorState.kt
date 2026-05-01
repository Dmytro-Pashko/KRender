package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.Vec3

/**
 * Runtime-only camera settings mirrored into the Scene Editor UI.
 */
data class SceneEditorCameraState(
    /** Current editor camera world-space position. */
    var position: Vec3 = Vec3(0f, 2f, 6f),

    /** Current editor camera euler rotation in degrees. */
    var eulerDegrees: Vec3 = Vec3(-10f, 180f, 0f),

    /** Base free-camera movement speed in world units per second. */
    var speed: Float = 6f,

    /** Mouse-look sensitivity in degrees per pixel. */
    var sensitivity: Float = 0.18f,

    /** True while right mouse is actively driving viewport camera navigation. */
    var navigating: Boolean = false,
)

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

    /** Runtime-only editor viewport camera state; never serialized into `.krscene` files. */
    var camera: SceneEditorCameraState = SceneEditorCameraState(),

    /** Runtime-only request to move the editor camera entity on the next camera-system update. */
    var pendingCameraPosition: Vec3? = null,

    /** Runtime-only request to rotate the editor camera entity on the next camera-system update. */
    var pendingCameraEulerDegrees: Vec3? = null,

    /** True when the viewport panel is hovered or focused and can claim camera input. */
    var viewportFocused: Boolean = false,

    /** Runtime-only editor preference for drawing the viewport grid; never serialized into `.krscene` files. */
    var showGrid: Boolean = true,

    /** Runtime-only editor preference for drawing world axes; never serialized into `.krscene` files. */
    var showAxes: Boolean = true,

    /** Grid extent in cells from the world origin for editor viewport guides. */
    var gridHalfExtentCells: Int = 24,

    /** World-space size of one editor viewport grid cell. */
    var gridCellSize: Float = 1f,

    /** Runtime-only model path typed into the Scene Editor placement UI. */
    var modelPlacementPath: String = "",

    /** Non-null when the last model placement request failed validation. */
    var modelPlacementError: String? = null,

    /** Distance in front of the editor camera used for newly placed model entities. */
    var placeModelDistance: Float = 5f,

    /** Runtime-only terrain path typed into the Scene Editor placement UI. */
    var terrainPlacementPath: String = "",

    /** Non-null when the last terrain placement request failed validation. */
    var terrainPlacementError: String? = null,
)
