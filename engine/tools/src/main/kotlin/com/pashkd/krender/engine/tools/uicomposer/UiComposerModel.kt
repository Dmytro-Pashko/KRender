package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.ui.scene.*

/**
 * Editor-only state for the UiComposer preview and selected-node property editor.
 *
 * This state belongs to editor preview UX and basic editor editing, not the
 * runtime UI service, shared `.krui` model definition, or a completed canvas
 * editing pipeline. It keeps document loading, validation diagnostics,
 * hierarchy selection, inspector edit state, bounds toggling, reload/save
 * requests, selection-only canvas interaction state, and transient editor
 * preview payload data in one simple place while intentionally omitting canvas drag/drop,
 * resizing actors on canvas, multi-select, canvas editing, Skin editing, Asset
 * Browser drag/drop, texture import/copy, texture thumbnails, atlas region
 * picking, snapping, transform gizmos, asset-id references, full actor
 * serialization, and any generic visual layout solver.
 */
data class UiComposerState(
    /** Project-relative `.krui` path opened by this editor preview tool. */
    val uiScenePath: String,
    /** Last decoded shared `.krui` document; null when parsing or preview build failed. */
    var document: UiSceneDocument? = null,
    /**
     * Document snapshot captured after the last successful load/save.
     *
     * Dirty state is derived by comparing [document] with this snapshot after
     * document-changing operations, undo, redo, load, and save.
     */
    var savedDocumentSnapshot: UiSceneDocument? = null,
    /**
     * Snapshot history for document-changing UiComposer operations.
     *
     * This is editor-only and is never saved to `.krui`.
     */
    val history: UiComposerHistory = UiComposerHistory(),
    /** Shared validator output shown by editor diagnostics without blocking the preview scene. */
    var validationIssues: List<UiSceneValidationIssue> = emptyList(),
    /** Editor-only Skin-backed warnings for missing style/background names in the current document. */
    var styleValidationIssues: List<UiSceneValidationIssue> = emptyList(),
    /** Editor-only Asset Registry-backed warnings for Image texture paths. */
    var textureValidationIssues: List<UiSceneValidationIssue> = emptyList(),
    /**
     * Editor-only binding diagnostics for unknown `.krui` placeholders/valueBinding keys.
     *
     * These warnings compare document references against document binding keys. They
     * do not block save, do not mutate runtime UI, and are not saved to `.krui`.
     */
    var bindingValidationIssues: List<UiSceneValidationIssue> = emptyList(),
    /**
     * Missing binding keys grouped for Scene Bindings quick-add actions.
     *
     * This is editor helper state used to add binding definitions to `.krui`. It
     * does not change runtime behavior.
     */
    var missingBindingKeys: List<UiComposerMissingBindingKey> = emptyList(),
    /** Parse or backend preview build error shown in diagnostics; this does not crash the scene. */
    var parseError: String? = null,
    /** Last inspected path-based Skin snapshot used by Inspector dropdowns and diagnostics. */
    var skinMetadata: UiComposerSkinMetadata? = null,
    /**
     * Last Asset Registry texture picker rows shown for Image.texture.
     *
     * This belongs to editor asset picking and Asset Registry integration. The
     * selected `.krui` node still stores only a path string, and these rows are
     * not saved as asset-id references. The picker intentionally does not
     * import/copy textures, pick atlas regions, show thumbnails, support
     * Asset Browser drag/drop, or change runtime behavior.
     */
    var textureOptions: List<UiComposerTextureOption> = emptyList(),
    /**
     * Requests a refresh of Asset Registry-backed Image.texture picker options.
     *
     * This is editor-only refresh state. It triggers registry rescanning for
     * picker choices and diagnostics, but it does not mutate `.krui`, import
     * textures, copy files, create asset ids in documents, support drag/drop, or
     * change runtime UI loading.
     */
    var textureOptionsReloadRequested: Boolean = false,
    /**
     * Current Inspector filter text for the Image texture picker.
     *
     * This belongs only to editor asset picking UI. It filters displayed rows
     * by display name/path and is never saved to `.krui`, never used as a
     * runtime value, and does not replace manual path editing.
     */
    var textureSearchQuery: String = "",
    /**
     * Current binding keys selected by Inspector binding helper controls.
     *
     * Keys are scoped by selected node id and field name so Text, Action,
     * Texture, and valueBinding controls do not mirror each other's selection.
     * This is editor-only UI state and is never saved.
     */
    val selectedBindingKeysByField: MutableMap<String, String> = mutableMapOf(),
    /**
     * Known registry asset types keyed by project-relative path for diagnostics.
     *
     * This supports editor warnings when an Image texture path resolves to a
     * non-texture registry asset. It is diagnostic context only and does not
     * create asset-id references, block save, import/copy textures, pick atlas
     * regions, or change runtime UI behavior.
     */
    var textureAssetTypesByPath: Map<String, AssetType> = emptyMap(),
    /** Selected `.krui` node id from the hierarchy, kept independent from Scene2D actor instances. */
    var selectedNodeId: String? = null,
    /** Shows Scene2D debug bounds for every built actor in the backend preview only. */
    var showBounds: Boolean = true,
    /** Shows a best-effort Scene2D debug highlight for only the selected node's actor. */
    var highlightSelected: Boolean = true,
    /**
     * Shows custom selected actor bounds in the Preview Canvas overlay.
     *
     * This is editor-only visual debugging state. It is not saved to `.krui`, does
     * not mutate the document, and does not implement drag/drop, resize handles,
     * snapping, transform gizmos, or canvas editing.
     */
    var showSelectedGuide: Boolean = true,
    /**
     * Shows custom hovered actor bounds in the Preview Canvas overlay.
     *
     * This is editor-only visual debugging state. It does not mutate `.krui`, does
     * not set dirty state, and does not dispatch runtime UI actions.
     */
    var showHoveredGuide: Boolean = true,
    /**
     * Shows parent-chain bounds for the selected actor.
     *
     * This helps debug Scene2D layout nesting. It is overlay-only and does not
     * change `.krui`, layout, or runtime UI behavior.
     */
    var showParentChainGuides: Boolean = true,
    /**
     * Shows padding guides for the selected node when its `.krui` padding is relevant.
     *
     * This is best-effort visual debugging for Container/Table padding. It does not
     * edit padding or enable canvas drag editing.
     */
    var showPaddingGuides: Boolean = true,
    /**
     * Shows alignment anchor marker for the selected node when it has `.krui` align.
     *
     * This is best-effort visual debugging only. It does not modify align values.
     */
    var showAlignmentGuide: Boolean = true,
    /**
     * Shows Table orientation marker for selected Table nodes.
     *
     * This is visual-only and does not modify tableOrientation.
     */
    var showTableOrientationGuide: Boolean = true,
    /**
     * Shows selected node id label near the selected actor.
     *
     * This is visual-only and does not modify node ids.
     */
    var showNodeIdLabel: Boolean = true,
    /**
     * Shows local Preview Canvas mouse coordinates.
     *
     * This is diagnostic-only and does not affect input, hit-testing, or `.krui`.
     */
    var showLocalMouseCoordinates: Boolean = true,
    /**
     * Node id currently hovered by the preview canvas hit-test.
     *
     * This is editor-only canvas interaction state. It is derived from the
     * backend preview actor map and never modifies the shared `.krui` document,
     * saves files, dispatches runtime UI actions, edits Skin data, picks assets,
     * starts drag/drop, shows resize handles, supports multi-select, snaps,
     * creates transform gizmos, or performs canvas structure editing.
     */
    var hoveredNodeId: String? = null,
    /**
     * Enables mouse selection directly in the Scene2D preview canvas.
     *
     * This belongs to editor canvas interaction and gates the selection-only
     * MVP. It does not modify `.krui`, drag/drop nodes, resize actors, edit
     * properties by dragging, add/delete/reorder nodes from the canvas, edit
     * Skins, support Asset Browser drag/drop, support snapping, create transform
     * gizmos, or enable multi-select.
     */
    var canvasSelectionEnabled: Boolean = true,
    /**
     * Shows hover bounds independently from selected-node highlighting.
     *
     * This belongs to editor canvas interaction feedback. It only controls
     * best-effort Scene2D debug drawing and does not change `.krui`, solve
     * layout, add resize handles, start drag/drop, edit Skin data, pick assets,
     * snap actors, display transform gizmos, or serialize Scene2D actors.
     */
    var highlightHovered: Boolean = true,
    /**
     * Last canvas hit-test status for editor diagnostics.
     *
     * This belongs to editor canvas interaction diagnostics only. It is not
     * saved, does not modify `.krui`, does not dispatch runtime UI actions, and
     * does not represent drag/drop, resizing, multi-select, snapping, Skin
     * editing, Asset Browser drag/drop, transform gizmos, or canvas editing.
     */
    var canvasStatusMessage: String? = null,
    /**
     * Full Preview Canvas panel content rectangle in screen coordinates.
     *
     * This is editor-only preview placement state. It is not saved to `.krui`,
     * is not runtime viewport state, and does not add drag/drop editing, resize
     * handles, snapping, transform gizmos, safe-area simulation, DPI simulation,
     * or canvas structure editing.
     */
    var canvasPanelRect: UiComposerCanvasRect = UiComposerCanvasRect(),
    /**
     * Effective preview rectangle inside [canvasPanelRect].
     *
     * This editor-only rectangle may be smaller than the panel because fixed
     * resolution presets are centered and letterboxed to preserve aspect ratio.
     * It is not saved to `.krui`, is not runtime viewport state, and does not
     * add drag/drop editing, resize handles, snapping, transform gizmos,
     * safe-area simulation, DPI simulation, or canvas structure editing.
     */
    var canvasPreviewRect: UiComposerCanvasRect = UiComposerCanvasRect(),
    /**
     * Selected editor-only preview resolution preset.
     *
     * This is not saved to `.krui`, is not runtime viewport state, and does not
     * simulate drag/drop editing, resize handles, safe areas, DPI, snapping,
     * transform gizmos, or canvas structure editing.
     */
    var previewResolutionPreset: UiComposerPreviewResolutionPreset =
        UiComposerPreviewResolutionPreset.FitPanel,
    /**
     * Custom preview width used only when [previewResolutionPreset] is Custom.
     *
     * This editor-only value is not saved to `.krui`, is not runtime viewport
     * state, and does not add drag/drop editing, resize handles, safe-area
     * simulation, DPI simulation, snapping, transform gizmos, or canvas structure editing.
     */
    var customPreviewWidth: Int = 1920,
    /**
     * Custom preview height used only when [previewResolutionPreset] is Custom.
     *
     * This editor-only value is not saved to `.krui`, is not runtime viewport
     * state, and does not add drag/drop editing, resize handles, safe-area
     * simulation, DPI simulation, snapping, transform gizmos, or canvas structure editing.
     */
    var customPreviewHeight: Int = 1080,
    /**
     * Current logical width used by the Scene2D preview Stage.
     *
     * This editor-only value is not saved to `.krui`, is not runtime viewport
     * state, and does not add drag/drop editing, resize handles, safe-area
     * simulation, DPI simulation, snapping, transform gizmos, or canvas structure editing.
     */
    var previewLogicalWidth: Int = 1,
    /**
     * Current logical height used by the Scene2D preview Stage.
     *
     * This editor-only value is not saved to `.krui`, is not runtime viewport
     * state, and does not add drag/drop editing, resize handles, safe-area
     * simulation, DPI simulation, snapping, transform gizmos, or canvas structure editing.
     */
    var previewLogicalHeight: Int = 1,
    /**
     * Last preview-local mouse x inside [canvasPreviewRect], for diagnostics only.
     *
     * This editor-only diagnostic value is not saved to `.krui`, is not runtime
     * viewport state, and does not add drag/drop editing, resize handles,
     * safe-area simulation, DPI simulation, snapping, transform gizmos, or
     * canvas structure editing.
     */
    var canvasLocalMouseX: Float? = null,
    /**
     * Last preview-local mouse y inside [canvasPreviewRect], for diagnostics only.
     *
     * This editor-only diagnostic value is not saved to `.krui`, is not runtime
     * viewport state, and does not add drag/drop editing, resize handles,
     * safe-area simulation, DPI simulation, snapping, transform gizmos, or
     * canvas structure editing.
     */
    var canvasLocalMouseY: Float? = null,
    /**
     * Last Scene2D world x under the Preview Canvas mouse, for diagnostics only.
     *
     * This editor-only diagnostic value is shown by ImGui so it stays independent
     * from Preview Canvas camera zoom/pan rendering. It is not saved to `.krui`,
     * is not runtime viewport state, and does not add canvas editing behavior.
     */
    var canvasWorldMouseX: Float? = null,
    /**
     * Last Scene2D world y under the Preview Canvas mouse, for diagnostics only.
     *
     * This editor-only diagnostic value is shown by ImGui so it stays independent
     * from Preview Canvas camera zoom/pan rendering. It is not saved to `.krui`,
     * is not runtime viewport state, and does not add canvas editing behavior.
     */
    var canvasWorldMouseY: Float? = null,
    /**
     * Scene2D logical-space camera x offset from the center of the preview.
     *
     * This editor-only value supports Preview Canvas pan/zoom inspection. It is
     * not saved to `.krui`, is not runtime viewport state, and does not add
     * drag/drop editing, resize handles, snapping, transform gizmos, safe-area
     * simulation, DPI simulation, or canvas structure editing.
     */
    var previewCameraOffsetX: Float = 0f,
    /**
     * Scene2D logical-space camera y offset from the center of the preview.
     *
     * This editor-only value supports Preview Canvas pan/zoom inspection. It is
     * not saved to `.krui`, is not runtime viewport state, and does not add
     * drag/drop editing, resize handles, snapping, transform gizmos, safe-area
     * simulation, DPI simulation, or canvas structure editing.
     */
    var previewCameraOffsetY: Float = 0f,
    /**
     * Preview Canvas camera zoom scale.
     *
     * A value of `1` fits the full logical preview into the effective preview
     * rectangle. Larger values zoom in, smaller values zoom out. This
     * editor-only value is not saved to `.krui`, is not runtime viewport state,
     * and does not add drag/drop editing, resize handles, snapping, transform
     * gizmos, safe-area simulation, DPI simulation, or canvas structure editing.
     */
    var previewZoom: Float = 1f,
    /**
     * True while Ctrl + left mouse button is panning the Preview Canvas camera.
     *
     * This is editor-only interaction state. It is not saved to `.krui`, is not
     * runtime viewport state, and does not add drag/drop editing, resize handles,
     * snapping, transform gizmos, safe-area simulation, DPI simulation, or
     * canvas structure editing.
     */
    var canvasPanning: Boolean = false,
    /** Requests the document file to be reloaded and the backend preview rebuilt. */
    var reloadRequested: Boolean = false,
    /** Requests the current in-memory `.krui` document to be saved to [uiScenePath]. */
    var saveRequested: Boolean = false,
    /** Requests the backend preview to rebuild from the current document and transient editor payload. */
    var previewRebuildRequested: Boolean = false,
    /** True when selected-node property edits changed the in-memory `.krui` document. */
    var dirty: Boolean = false,
    /**
     * True when Reload was requested while the document had unsaved `.krui` edits.
     *
     * This belongs to editor preview UX and provides the MVP toolbar-level reload
     * confirmation. It intentionally does not introduce a modal framework,
     * autosave, affect preview payload edits, affect panel layout saving, change
     * runtime behavior, or serialize Scene2D actors.
     */
    var pendingReloadConfirmation: Boolean = false,
    /** Toolbar status for editor preview operations such as reload, rebuild, and panel layout persistence. */
    var statusMessage: String = "Editor ready.",
    /** Last `.krui` document save result shown in toolbar and diagnostics. */
    var saveStatusMessage: String? = null,
    /** Last Scene2D actor metadata for the selected node, reported by the backend preview. */
    var selectedActorInfo: UiComposerActorPreviewInfo? = null,
    /** Last backend guide snapshot used by diagnostics panels. */
    var guideSnapshot: UiComposerGuideSnapshot = UiComposerGuideSnapshot(),
    /**
     * Mutable editor preview payload derived from document binding definitions.
     *
     * This map is rebuilt from `document.bindings.defaultValue` on reload and is
     * passed to the Scene2D preview builder. It is not a separate persisted model:
     * editing binding defaults updates `UiSceneDocument.bindings` and then syncs this
     * map. Runtime payloads are still provided explicitly by runtime systems and do
     * not automatically fall back to these defaults.
     */
    val previewPayload: MutableMap<String, String> = mutableMapOf(),
)

/**
 * Editor-only rectangle in screen coordinates.
 *
 * This belongs to UiComposer preview placement and canvas interaction. It is not
 * saved to `.krui`, does not affect runtime UI, and does not implement
 * drag/drop editing, resize handles, snapping, transform gizmos, safe-area
 * simulation, DPI simulation, or canvas structure editing.
 */
data class UiComposerCanvasRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isValid: Boolean get() = width > 1f && height > 1f

    fun contains(
        screenX: Float,
        screenY: Float,
    ): Boolean =
        screenX >= x &&
            screenY >= y &&
            screenX <= x + width &&
            screenY <= y + height
}

/**
 * Preview resolution preset used by UiComposer's Scene2D canvas.
 *
 * This belongs to editor preview UX only. It controls the logical size used to
 * build and inspect `.krui` inside the editor preview. It does not change
 * runtime viewport configuration, does not write to `.krui`, and does not
 * simulate DPI, safe areas, drag/drop editing, resize handles, snapping,
 * transform gizmos, canvas structure editing, or device-specific input.
 */
enum class UiComposerPreviewResolutionPreset {
    FitPanel,
    HD_1280x720,
    FullHD_1920x1080,
    QHD_2560x1440,
    Ultrawide_3440x1440,
    XGA_1024x768,
    Custom,
}

/**
 * Logical preview size used by the Scene2D Stage.
 *
 * This is editor-only preview state. It is not saved into `.krui`, does not
 * change RuntimeViewportService or gameplay UI configuration, and does not add
 * drag/drop editing, resize handles, snapping, transform gizmos, safe-area
 * simulation, DPI simulation, or canvas structure editing.
 */
data class UiComposerPreviewResolution(
    val width: Int,
    val height: Int,
)

fun UiComposerPreviewResolutionPreset.defaultResolution(
    customWidth: Int,
    customHeight: Int,
    panelWidth: Int,
    panelHeight: Int,
): UiComposerPreviewResolution =
    when (this) {
        UiComposerPreviewResolutionPreset.FitPanel ->
            UiComposerPreviewResolution(panelWidth.coerceAtLeast(1), panelHeight.coerceAtLeast(1))

        UiComposerPreviewResolutionPreset.HD_1280x720 ->
            UiComposerPreviewResolution(1280, 720)

        UiComposerPreviewResolutionPreset.FullHD_1920x1080 ->
            UiComposerPreviewResolution(1920, 1080)

        UiComposerPreviewResolutionPreset.QHD_2560x1440 ->
            UiComposerPreviewResolution(2560, 1440)

        UiComposerPreviewResolutionPreset.Ultrawide_3440x1440 ->
            UiComposerPreviewResolution(3440, 1440)

        UiComposerPreviewResolutionPreset.XGA_1024x768 ->
            UiComposerPreviewResolution(1024, 768)

        UiComposerPreviewResolutionPreset.Custom ->
            UiComposerPreviewResolution(
                customWidth.coerceAtLeast(1),
                customHeight.coerceAtLeast(1),
            )
    }

/**
 * Computes the centered preview rectangle inside a panel while preserving the
 * selected logical preview aspect ratio.
 *
 * `FitPanel` usually uses the panel size as logical size, so this rect equals
 * the panel rect. Fixed presets preserve their aspect ratio and may letterbox
 * inside the panel. This is editor-only preview placement and is not saved to
 * `.krui` or used as runtime viewport state.
 */
fun computePreviewRect(
    panel: UiComposerCanvasRect,
    logicalWidth: Int,
    logicalHeight: Int,
): UiComposerCanvasRect {
    if (!panel.isValid || logicalWidth <= 0 || logicalHeight <= 0) {
        return UiComposerCanvasRect()
    }

    val targetAspect = logicalWidth.toFloat() / logicalHeight.toFloat()
    val panelAspect = panel.width / panel.height

    val previewWidth: Float
    val previewHeight: Float
    if (panelAspect > targetAspect) {
        previewHeight = panel.height
        previewWidth = previewHeight * targetAspect
    } else {
        previewWidth = panel.width
        previewHeight = previewWidth / targetAspect
    }

    return UiComposerCanvasRect(
        x = panel.x + (panel.width - previewWidth) * 0.5f,
        y = panel.y + (panel.height - previewHeight) * 0.5f,
        width = previewWidth,
        height = previewHeight,
    )
}

const val UiComposerPreviewMinZoom = 0.1f
const val UiComposerPreviewMaxZoom = 8f

fun clampPreviewZoom(zoom: Float): Float = zoom.takeIf(Float::isFinite)?.coerceIn(UiComposerPreviewMinZoom, UiComposerPreviewMaxZoom) ?: 1f

fun previewWorldUnitsPerScreenPixel(
    logicalSize: Int,
    screenSize: Float,
    zoom: Float,
): Float =
    logicalSize.coerceAtLeast(1).toFloat() /
        screenSize.coerceAtLeast(1f) /
        clampPreviewZoom(zoom)

/**
 * Backend-neutral visual guide options passed to the preview renderer.
 *
 * These values are editor-only overlay state. They are not saved to `.krui`,
 * do not mutate documents, and do not enable canvas editing.
 */
data class UiComposerVisualGuideOptions(
    val showSelectedGuide: Boolean = true,
    val showHoveredGuide: Boolean = true,
    val showParentChainGuides: Boolean = true,
    val showPaddingGuides: Boolean = true,
    val showAlignmentGuide: Boolean = true,
    val showTableOrientationGuide: Boolean = true,
    val showNodeIdLabel: Boolean = true,
    val showLocalMouseCoordinates: Boolean = true,
    val canvasLocalMouseX: Float? = null,
    val canvasLocalMouseY: Float? = null,
)

/**
 * Backend-neutral actor bounds snapshot in Scene2D logical coordinates.
 *
 * This belongs to Preview Canvas visual debugging. It deliberately avoids
 * exposing mutable LibGDX Actor instances to editor UI panels.
 */
data class UiComposerGuideBounds(
    val nodeId: String?,
    val label: String,
    val actorClass: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * Snapshot of all guide-relevant actor bounds for the current selection/hover.
 *
 * This is editor-only diagnostic data. It is not saved to `.krui` and does not
 * represent document editing state.
 */
data class UiComposerGuideSnapshot(
    val selected: UiComposerGuideBounds? = null,
    val hovered: UiComposerGuideBounds? = null,
    val parentChain: List<UiComposerGuideBounds> = emptyList(),
)

/**
 * Backend-neutral guide rectangle in Scene2D logical coordinates.
 *
 * Kept separate from LibGDX Rectangle so guide math can be tested without a
 * graphics backend.
 */
data class UiComposerGuideRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val left: Float get() = x
    val right: Float get() = x + width
    val bottom: Float get() = y
    val top: Float get() = y + height
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f
}

/** Backend-neutral guide point in Scene2D logical coordinates. */
data class UiComposerGuidePoint(
    val x: Float,
    val y: Float,
)

/**
 * Computes the best-effort content rectangle inside [bounds] after `.krui`
 * padding is applied.
 */
fun computePaddingInnerRect(
    bounds: UiComposerGuideRect,
    padding: UiSceneSpacing,
): UiComposerGuideRect =
    UiComposerGuideRect(
        x = bounds.x + padding.left,
        y = bounds.y + padding.bottom,
        width = (bounds.width - padding.left - padding.right).coerceAtLeast(0f),
        height = (bounds.height - padding.top - padding.bottom).coerceAtLeast(0f),
    )

/** Computes the marker point for a `.krui` alignment value inside [bounds]. */
fun computeAlignmentAnchor(
    bounds: UiComposerGuideRect,
    align: UiSceneAlign,
): UiComposerGuidePoint =
    when (align) {
        UiSceneAlign.TopLeft -> UiComposerGuidePoint(bounds.left, bounds.top)
        UiSceneAlign.Top -> UiComposerGuidePoint(bounds.centerX, bounds.top)
        UiSceneAlign.TopRight -> UiComposerGuidePoint(bounds.right, bounds.top)
        UiSceneAlign.Left -> UiComposerGuidePoint(bounds.left, bounds.centerY)
        UiSceneAlign.Center -> UiComposerGuidePoint(bounds.centerX, bounds.centerY)
        UiSceneAlign.Right -> UiComposerGuidePoint(bounds.right, bounds.centerY)
        UiSceneAlign.BottomLeft -> UiComposerGuidePoint(bounds.left, bounds.bottom)
        UiSceneAlign.Bottom -> UiComposerGuidePoint(bounds.centerX, bounds.bottom)
        UiSceneAlign.BottomRight -> UiComposerGuidePoint(bounds.right, bounds.bottom)
    }

/**
 * Backend-neutral snapshot of one built Scene2D actor for the selected `.krui` node.
 *
 * This belongs to editor preview UX diagnostics. It lets the read-only Inspector
 * report whether a selected node reached the backend preview and what bounds the
 * generated actor has, without exposing mutable LibGDX Actor instances to editor
 * UI panels. It intentionally does not serialize actors, edit properties, save
 * `.krui`, add drag/drop, edit Skins, introduce asset-id references, or represent
 * runtime UI state.
 */
data class UiComposerActorPreviewInfo(
    val nodeId: String,
    val actorClass: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val visible: Boolean,
)

/**
 * Backend-neutral snapshot of a Scene2D actor hit by the UiComposer preview canvas.
 *
 * This exists so editor canvas selection can talk about a hit actor without
 * exposing mutable LibGDX Actor instances outside the backend preview. It
 * belongs to editor canvas interaction and backend preview diagnostics, not the
 * shared `.krui` model or runtime UI action system. It intentionally does not
 * serialize actors, modify documents, drag/drop nodes, resize actors, edit
 * Skin data, pick assets, support multi-select, snap to grids, create transform
 * gizmos, or perform canvas-based structure editing.
 */
data class UiComposerCanvasHit(
    /** `.krui` node id for the nearest mapped actor under the preview mouse position. */
    val nodeId: String,
    /** Backend actor class name reported for diagnostics without exposing the mutable actor. */
    val actorClass: String,
    /** Actor local x position in its Scene2D parent, reported for diagnostics only. */
    val x: Float,
    /** Actor local y position in its Scene2D parent, reported for diagnostics only. */
    val y: Float,
    /** Actor width in the Scene2D preview, reported for diagnostics only. */
    val width: Float,
    /** Actor height in the Scene2D preview, reported for diagnostics only. */
    val height: Float,
)

/**
 * Backend-neutral loader for UiComposer `.krui` documents.
 *
 * This belongs to editor preview backend-neutral plumbing: it decodes the file
 * and runs shared validation so the UI can show diagnostics without crashing.
 * It intentionally does not edit, save, repair, schema-migrate, open a JSON text
 * editor, edit Skins, add drag/drop canvas support, pick Asset Browser assets,
 * create asset-id references, or serialize full Scene2D actors.
 */
class UiComposerDocumentLoader(
    private val readText: (String) -> String,
    private val serializer: UiSceneSerializer = UiSceneSerializer(),
    private val validator: UiSceneValidator = UiSceneValidator(),
) {
    /**
     * Reloads [state.uiScenePath], decodes it, validates it, and stores the result.
     *
     * Failures are captured in [UiComposerState.parseError] so the editor scene
     * remains open and can report diagnostics. Selection is preserved only when
     * the selected node id still exists in the reloaded document.
     */
    fun reload(state: UiComposerState) {
        try {
            val document = serializer.decode(readText(state.uiScenePath))
            state.document = document
            state.savedDocumentSnapshot = document
            state.history.clear()
            state.dirty = false
            state.previewPayload.clear()
            state.previewPayload.putAll(previewPayloadFromBindings(document.bindings))
            refreshUiComposerValidationBuckets(
                state = state,
                document = document,
                validator = validator,
                includeSkinMetadata = false,
                includeTextureMetadata = false,
            )
            state.parseError = null
            if (state.selectedNodeId != null && findUiSceneNodeById(document.root, state.selectedNodeId) == null) {
                state.selectedNodeId = null
            }
        } catch (error: Exception) {
            state.document = null
            state.savedDocumentSnapshot = null
            state.history.clear()
            state.validationIssues = emptyList()
            state.styleValidationIssues = emptyList()
            state.textureValidationIssues = emptyList()
            state.bindingValidationIssues = emptyList()
            state.missingBindingKeys = emptyList()
            state.previewPayload.clear()
            state.skinMetadata = null
            state.parseError = error.message ?: error::class.simpleName ?: "Unknown UI scene parse error."
        } finally {
            state.reloadRequested = false
        }
    }
}

/**
 * Finds a node by id in a `.krui` tree for hierarchy and inspector UI.
 *
 * This helper belongs to backend-neutral editor UI state. It is deliberately a
 * simple traversal and does not support mutation, reordering, drag/drop, saving,
 * asset lookup, Skin editing, or full Scene2D actor serialization.
 */
fun findUiSceneNodeById(
    node: UiSceneNode,
    id: String?,
): UiSceneNode? {
    if (id == null) return null
    if (node.id == id) return node
    // Depth-first search is enough for the small MVP document trees.
    node.children.forEach { child ->
        val match = findUiSceneNodeById(child, id)
        if (match != null) return match
    }
    return null
}
