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
    val uiScenePath: String,
    var document: UiSceneDocument? = null,
    var validationIssues: List<UiSceneValidationIssue> = emptyList(),
    var parseError: String? = null,
    var selectedNodeId: String? = null,
    var showBounds: Boolean = true,
    var reloadRequested: Boolean = false,
    var statusMessage: String = "Read-only preview.",
)

/**
 * Sample payload values used by the read-only UiComposer preview.
 *
 * This belongs to editor preview data only: it lets bound Woolboy `.krui` labels,
 * progress bars, actions, and images render with useful placeholder values. It
 * is not editable in Phase 4, is not saved to `.krui`, does not introduce
 * asset-id references, and is not a runtime Woolboy UI behavior change.
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
