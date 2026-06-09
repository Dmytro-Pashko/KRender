package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.UiSceneValidator

/**
 * Editor-only state for the UiComposer preview and selected-node property editor.
 *
 * This state belongs to editor preview UX and basic editor editing, not the
 * runtime UI service, shared `.krui` model definition, or a completed canvas
 * editing pipeline. It keeps document loading, validation diagnostics,
 * hierarchy selection, inspector edit state, bounds toggling, reload/save
 * requests, selection-only canvas interaction state, and preview-only payload
 * data in one simple place while intentionally omitting canvas drag/drop,
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
    /** Shared validator output shown by editor diagnostics without blocking the preview scene. */
    var validationIssues: List<UiSceneValidationIssue> = emptyList(),
    /** Editor-only Skin-backed warnings for missing style/background names in the current document. */
    var styleValidationIssues: List<UiSceneValidationIssue> = emptyList(),
    /** Editor-only Asset Registry-backed warnings for Image texture paths. */
    var textureValidationIssues: List<UiSceneValidationIssue> = emptyList(),
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
    /** Requests the document file to be reloaded and the backend preview rebuilt. */
    var reloadRequested: Boolean = false,
    /** Requests the current in-memory `.krui` document to be saved to [uiScenePath]. */
    var saveRequested: Boolean = false,
    /** Requests the backend preview to rebuild from the current document and preview-only payload. */
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
    /** Mutable editor-only binding payload used to test `.krui` placeholders; it is never saved or runtime state. */
    val previewPayload: MutableMap<String, String> = DefaultPreviewPayload.toMutableMap(),
)

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
 * Sample payload values used by the UiComposer preview.
 *
 * This belongs to editor preview data only: it lets bound Woolboy `.krui` labels,
 * progress bars, actions, and images render with useful placeholder values. It
 * is editable only inside the Phase 4.5 preview panel, is not saved to `.krui`,
 * does not introduce asset-id references, and is not a runtime Woolboy UI
 * behavior change.
 */
val DefaultPreviewPayload: Map<String, String> = mapOf(
    "title" to "Loading...",
    "progress" to "0.65",
    "primaryButtonText" to "Start Game",
    "primaryButtonAction" to "woolboy.start",
    "healthLabel" to "100/100",
    "scores" to "1230",
    "lives" to "3",
    "life1Texture" to "textures/woolboy/hud_heart_full.png",
    "life2Texture" to "textures/woolboy/hud_heart_full.png",
    "life3Texture" to "textures/woolboy/hud_heart_empty.png",
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
            state.validationIssues = validator.validate(document)
            state.parseError = null
            if (state.selectedNodeId != null && findUiSceneNodeById(document.root, state.selectedNodeId) == null) {
                state.selectedNodeId = null
            }
        } catch (error: Exception) {
            state.document = null
            state.validationIssues = emptyList()
            state.styleValidationIssues = emptyList()
            state.textureValidationIssues = emptyList()
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
