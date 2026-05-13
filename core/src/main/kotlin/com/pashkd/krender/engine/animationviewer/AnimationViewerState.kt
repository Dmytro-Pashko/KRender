package com.pashkd.krender.engine.animationviewer

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelSkeletonInfo
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.editor.viewport.EditorViewportCameraState
import com.pashkd.krender.engine.editor.viewport.EditorViewportState

/**
 * Viewport modes supported by the Animation Viewer.
 */
enum class AnimationViewerViewMode {
    Model,
    Skeleton,
    ModelAndSkeleton,
}

/**
 * Shared mutable state for the Animation Viewer scene, UI panels, and runtime systems.
 */
data class AnimationViewerState(
    /** Single model inspected by this viewer instance. */
    val model: AssetRef<ModelAsset>,

    /** Runtime entity id of the visible model instance. */
    var modelEntityId: EntityId? = null,

    /** Uniform scale applied to the inspected model entity. */
    val modelScale: Float = 1f,

    /** True when the inspected model asset is loaded. */
    var assetLoaded: Boolean = false,

    /** Normalized asset loading progress. */
    var assetProgress: Float = 1f,

    /** Human-readable loading status. */
    var loadingStatus: String = "Loading",

    /** Last viewer error, if any. */
    var errorMessage: String? = null,

    /** Last operation/status message shown in the toolbar. */
    var statusMessage: String = "Animation Viewer ready.",

    /** Runtime-only editor-style camera state. */
    var camera: EditorViewportCameraState = EditorViewportCameraState(
        position = Vec3(0f, 2f, 6f),
        eulerDegrees = Vec3(-10f, 180f, 0f),
    ),

    /** Runtime-only viewport focus and size state. */
    var viewport: EditorViewportState = EditorViewportState(),

    /** Runtime-only preference for drawing the viewport grid. */
    var showGrid: Boolean = true,

    /** Runtime-only preference for drawing world axes. */
    var showAxes: Boolean = true,

    /** Runtime-only preference for drawing the model bounds. */
    var showBoundingBox: Boolean = true,

    /** Runtime-only preference for wireframe-only rendering. */
    var wireframe: Boolean = false,

    /** Grid extent in cells from the world origin. */
    var gridHalfExtentCells: Int = 24,

    /** World-space size of one grid cell. */
    var gridCellSize: Float = 1f,

    /** Cached loaded model metadata snapshot. */
    var modelInfo: ModelAssetInfo? = null,

    /** Cached backend-neutral skeleton metadata snapshot. */
    var skeletonInfo: ModelSkeletonInfo? = null,

    /** Animation clip names exposed in the current metadata snapshot. */
    var animationNames: List<String> = emptyList(),

    /** Currently selected animation row index. */
    var selectedAnimationIndex: Int? = null,

    /** Currently selected animation name. */
    var selectedAnimationName: String? = null,

    /** Whether playback is currently advancing in update. */
    var isPlaying: Boolean = false,

    /** Whether playback wraps when the selected clip reaches the end. */
    var loop: Boolean = true,

    /** Current preview time in seconds. */
    var currentTimeSeconds: Float = 0f,

    /** Duration of the selected clip, when known. */
    var durationSeconds: Float? = null,

    /** Playback speed multiplier. */
    var playbackSpeed: Float = 1f,

    /** Current viewport visualization mode. */
    var viewMode: AnimationViewerViewMode = AnimationViewerViewMode.Model,

    /** Warning surfaced when animation preview cannot be applied cleanly. */
    var animationWarning: String? = null,

    /** Warning surfaced when skeleton preview cannot be generated. */
    var skeletonWarning: String? = null,

    /** Set by the UI to request application exit. */
    var exitRequested: Boolean = false,
) {
    /** Exposes the active model path in a UI-friendly format. */
    val modelPath: String
        get() = model.path

    /** Returns whether the active model asset is still loading. */
    val isLoadingModel: Boolean
        get() = !assetLoaded

    /** Returns whether at least one animation clip is available. */
    val hasAnimations: Boolean
        get() = animationNames.isNotEmpty()

    /** Returns whether backend-neutral skeleton data is available. */
    val hasSkeletonData: Boolean
        get() = skeletonInfo?.bones?.isNotEmpty() == true

    /** Compatibility shortcut for panels that work directly with viewport focus. */
    var viewportFocused: Boolean
        get() = viewport.focused
        set(value) {
            viewport.focused = value
        }

    /** Compatibility shortcut for panels that work directly with viewport origin. */
    var viewportOrigin: Vec2
        get() = viewport.origin
        set(value) {
            viewport.origin = value
        }

    /** Compatibility shortcut for panels that work directly with viewport size. */
    var viewportSize: Vec2
        get() = viewport.size
        set(value) {
            viewport.size = value
        }
}

