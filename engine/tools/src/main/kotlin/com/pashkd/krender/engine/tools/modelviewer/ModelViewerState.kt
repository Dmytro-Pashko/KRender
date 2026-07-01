package com.pashkd.krender.engine.tools.modelviewer

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.tools.viewport.EditorViewportCameraState
import com.pashkd.krender.engine.tools.viewport.EditorViewportState

/**
 * Basic render display modes supported by the ModelViewer renderer path.
 */
enum class ModelViewerDisplayMode {
    Shaded,
    ShadedWireframe,
    Wireframe,
}

enum class ModelViewerRendererMode {
    LibGdx,
    GltfPbr,
}

val DEFAULT_MODEL_VIEWER_RENDERER_MODE = ModelViewerRendererMode.GltfPbr

data class ModelViewerUvCheckerTextureOption(
    val resolution: Int,
    val texturePath: String,
)

const val UV_CHECKER_TEXTURE_1K = "textures/uv_checker_1k.png"
const val UV_CHECKER_TEXTURE_2K = "textures/uv_checker_2k.png"
const val UV_CHECKER_TEXTURE_4K = "textures/uv_checker_4k.png"
const val DEFAULT_UV_CHECKER_TEXTURE = "textures/uv_checker_2k.png"
const val DEFAULT_GLTF_ENVIRONMENT_PRESET = "default"

val UV_CHECKER_TEXTURE_OPTIONS =
    listOf(
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
    /** Runtime entity id of the ambient light that lights the preview. */
    var ambientLightEntityId: EntityId? = null,
    /** Runtime entity id of the Legacy directional light. */
    var legacyDirectionalLightEntityId: EntityId? = null,
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
    var camera: EditorViewportCameraState =
        EditorViewportCameraState(
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
    /** Ambient light intensity used by the viewer light entity. */
    var ambientLightIntensity: Float = 0.8f,
    var legacyAmbientLightColor: Color = Color(0.55f, 0.58f, 0.64f, 1f),
    var legacyDirectionalLightEnabled: Boolean = true,
    var legacyDirectionalLightIntensity: Float = 1f,
    var legacyDirectionalLightColor: Color = Color.white(),
    var legacyDirectionalLightYawDegrees: Float = 45f,
    var legacyDirectionalLightPitchDegrees: Float = -35f,
    /** Current display mode for the model material. */
    var displayMode: ModelViewerDisplayMode = ModelViewerDisplayMode.Shaded,
    /** Current material/texture debug mode. */
    var debugMode: MaterialDebugMode = MaterialDebugMode.Combined,
    /** Last non-UV-checker channel display mode so the UV checker toggle can restore it. */
    var lastNonUvCheckerDebugMode: MaterialDebugMode = MaterialDebugMode.Combined,
    /** Enables UV checker override. Mirrored with [debugMode] when selected in the UI. */
    var uvCheckerEnabled: Boolean = false,
    /** Culling behavior used by shader debug rendering. */
    var debugCullingMode: DebugCullingMode = DebugCullingMode.Backface,
    /** Backend-neutral texture asset path used by the UV checker override. */
    var uvCheckerTexturePath: String = DEFAULT_UV_CHECKER_TEXTURE,
    /** UV channel index used by the UV checker override. */
    var uvCheckerUvChannel: Int = 0,
    /** UV scale applied by the UV checker override. */
    var uvCheckerScale: Float = 1f,
    /** Current debug-rendering warning surfaced in the UI. */
    var debugWarning: String? = null,
    /** Current renderer warning surfaced in the UI. */
    var gltfRendererWarning: String? = null,
    /** Current renderer path for shaded model rendering. */
    var rendererMode: ModelViewerRendererMode = DEFAULT_MODEL_VIEWER_RENDERER_MODE,
    var gltfEnvironmentPreset: String = DEFAULT_GLTF_ENVIRONMENT_PRESET,
    var gltfExposure: Float = 1f,
    var gltfShowSkybox: Boolean = true,
    var gltfEnvironmentIntensity: Float = 1f,
    var gltfEnvironmentRotationDegrees: Float = 0f,
    var gltfToneMapping: PbrToneMapping = PbrToneMapping.Aces,
    var gltfGammaCorrection: Boolean = true,
    var gltfSrgbTextures: Boolean = true,
    var gltfDirectionalLightEnabled: Boolean = true,
    var gltfDirectionalLightIntensity: Float = 1f,
    var gltfDirectionalLightColor: Color = Color.white(),
    var gltfDirectionalLightYawDegrees: Float = 45f,
    var gltfDirectionalLightPitchDegrees: Float = -35f,
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
    /** Set by the UI to request a model reload from disk. */
    var reloadRequested: Boolean = false,
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
