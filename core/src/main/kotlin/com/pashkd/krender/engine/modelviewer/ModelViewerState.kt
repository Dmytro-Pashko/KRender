package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.editor.viewport.EditorViewportCameraState
import com.pashkd.krender.engine.editor.viewport.EditorViewportState

/**
 * Basic render display modes supported by the ModelViewer renderer path.
 */
enum class ModelViewerDisplayMode {
    Shaded,
    ShadedWireframe,
    Wireframe,
}

/**
 * Texture/material debug modes. Kept separate from shaded/wireframe display mode.
 */
enum class ModelViewerDebugMode {
    None,
    BaseColor,
    Normal,
    Emission,
    MetallicRoughness,
    Occlusion,
    Alpha,
    UvChecker,
}

data class ModelViewerUvCheckerTextureOption(
    val resolution: Int,
    val texturePath: String,
)

const val UV_CHECKER_TEXTURE_1K = "textures/uv_checker_1k.png"
const val UV_CHECKER_TEXTURE_2K = "textures/uv_checker_2k.png"
const val UV_CHECKER_TEXTURE_4K = "textures/uv_checker_4k.png"
const val DEFAULT_UV_CHECKER_TEXTURE = "textures/uv_checker_2k.png"

val UV_CHECKER_TEXTURE_OPTIONS = listOf(
    ModelViewerUvCheckerTextureOption(1024, UV_CHECKER_TEXTURE_1K),
    ModelViewerUvCheckerTextureOption(2048, UV_CHECKER_TEXTURE_2K),
    ModelViewerUvCheckerTextureOption(4096, UV_CHECKER_TEXTURE_4K),
)

/**
 * Holds mutable scene state shared between ModelViewer panels, systems, and operations.
 */
data class ModelViewerState(
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
    var statusMessage: String = "Model Viewer ready.",

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

    /** Grid extent in cells from the world origin. */
    var gridHalfExtentCells: Int = 24,

    /** World-space size of one grid cell. */
    var gridCellSize: Float = 1f,

    /** Current display mode for the model material. */
    var displayMode: ModelViewerDisplayMode = ModelViewerDisplayMode.Shaded,

    /** Current material/texture debug mode. */
    var debugMode: ModelViewerDebugMode = ModelViewerDebugMode.None,

    /** Limits debug rendering to the selected material when enabled. */
    var debugSelectedMaterialOnly: Boolean = false,

    /** Enables UV checker override. Mirrored with [debugMode] when selected in the UI. */
    var uvCheckerEnabled: Boolean = false,

    /** Backend-neutral texture asset path used by the UV checker override. */
    var uvCheckerTexturePath: String = DEFAULT_UV_CHECKER_TEXTURE,

    /** UV channel index used by the UV checker override. */
    var uvCheckerUvChannel: Int = 0,

    /** UV scale applied by the UV checker override. */
    var uvCheckerScale: Float = 1f,

    /** Current debug-rendering warning surfaced in the UI. */
    var debugWarning: String? = null,

    /** Currently selected mesh part in the Mesh Parts panel. */
    var selectedMeshPartIndex: Int? = null,

    /** Currently selected material in the Materials panel. */
    var selectedMaterialIndex: Int? = null,

    /** Currently selected texture channel in the Texture Channels panel. */
    var selectedTextureChannel: String? = null,

    /** Limits Mesh Parts panel rows to the selected material when enabled. */
    var filterMeshPartsBySelectedMaterial: Boolean = false,

    /** Limits viewport rendering to the selected mesh part when enabled. */
    var isolateSelectedMeshPart: Boolean = false,

    /** Cached loaded model metadata snapshot. */
    var modelInfo: ModelAssetInfo? = null,

    /** Set by the UI to request application exit. */
    var exitRequested: Boolean = false,
) {
    /** Exposes the active model path in a UI-friendly format. */
    val modelPath: String
        get() = model.path

    /** Returns whether the active model asset is still loading. */
    val isLoadingModel: Boolean
        get() = !assetLoaded

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
