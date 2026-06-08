package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.UiSceneValidator

/**
 * Editor-only state for the Phase 4 read-only UiComposer preview.
 *
 * This state belongs to editor UI, not the runtime UI service, shared `.krui`
 * model, or a completed editing pipeline. It keeps document loading,
 * validation diagnostics, hierarchy selection, bounds toggling, and reload
 * requests in one simple place while intentionally omitting save/editing,
 * property mutation, Skin editing, drag/drop canvas editing, Asset Browser
 * picking, asset-id references, and full Scene2D actor serialization.
 */
data class UiComposerState(
    /** Project-relative `.krui` path opened by this editor preview tool. */
    val uiScenePath: String,
    /** Last decoded shared `.krui` document; null when parsing or preview build failed. */
    var document: UiSceneDocument? = null,
    /** Shared validator output shown by editor diagnostics without blocking the preview scene. */
    var validationIssues: List<UiSceneValidationIssue> = emptyList(),
    /** Parse or backend preview build error shown in diagnostics; this does not crash the scene. */
    var parseError: String? = null,
    /** Selected `.krui` node id from the hierarchy, kept independent from Scene2D actor instances. */
    var selectedNodeId: String? = null,
    /** Shows Scene2D debug bounds for every built actor in the backend preview only. */
    var showBounds: Boolean = true,
    /** Shows a best-effort Scene2D debug highlight for only the selected node's actor. */
    var highlightSelected: Boolean = true,
    /** Requests the document file to be reloaded and the backend preview rebuilt. */
    var reloadRequested: Boolean = false,
    /** Requests the backend preview to rebuild from the current document and preview-only payload. */
    var previewRebuildRequested: Boolean = false,
    /** Toolbar status for editor preview operations such as reload, rebuild, and panel layout persistence. */
    var statusMessage: String = "Read-only preview.",
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
 * Sample payload values used by the read-only UiComposer preview.
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
 * Backend-neutral loader for read-only UiComposer `.krui` documents.
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
            state.parseError = error.message ?: error::class.simpleName ?: "Unknown UI scene parse error."
        } finally {
            state.reloadRequested = false
        }
    }
}

/**
 * Finds a node by id in a `.krui` tree for read-only hierarchy and inspector UI.
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
