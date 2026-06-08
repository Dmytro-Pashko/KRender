package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.ui.scene.UiSceneValidator

/**
 * UI Composer editor operations for panel layout persistence and basic `.krui` editing.
 *
 * This class belongs to editor editing and editor preview UX. It can persist
 * ImGui panel placement, update scalar properties on the selected `.krui` node,
 * perform hierarchy/inspector-driven structure edits, validate the in-memory
 * document, request preview rebuilds, and save the `.krui` document. It
 * intentionally does not implement drag/drop or canvas selection, resize actors
 * on canvas, edit Skins, add Asset Browser pickers, introduce asset-id
 * references, save preview payload into `.krui`, alter runtime Woolboy UI
 * behavior, solve generic visual layout, or serialize full Scene2D actors.
 */
class UiComposerOperations(
    private val state: UiComposerState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val serializer: UiSceneSerializer = UiSceneSerializer(),
    private val validator: UiSceneValidator = UiSceneValidator(),
) {
    /**
     * Persists the current ImGui panel layout to the UiComposer layout asset path.
     *
     * This operation saves only editor panel rectangles. It does not modify the
     * loaded `.krui` file or any Scene2D preview data.
     */
    fun saveUiLayout() {
        try {
            val config = layoutTracker.currentConfig()
            ImGuiLayoutConfigCodec.save(UiComposerUiLayoutDefaults.assetPath, config, context.sceneFiles)
            state.statusMessage = "Panel layout saved."
            context.logger.info(TAG) {
                "UiComposer UI layout saved path='${UiComposerUiLayoutDefaults.assetPath}' panels=${config.panels.size}"
            }
        } catch (error: Exception) {
            state.statusMessage = "Panel layout save failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to save UiComposer UI layout path='${UiComposerUiLayoutDefaults.assetPath}': ${error.message}"
            }
        }
    }

    /**
     * Saves the current in-memory `.krui` document to [UiComposerState.uiScenePath].
     *
     * This operation belongs to editor document saving. It serializes only the
     * shared `.krui` document model and deliberately does not persist preview
     * payload values, ImGui panel layout, runtime UI state, Skin data, Asset
     * Browser choices, or Scene2D actor instances.
     */
    fun saveDocument() {
        val document = state.document
        if (document == null) {
            state.saveStatusMessage = "Save failed: no document loaded."
            state.statusMessage = "Save failed."
            state.saveRequested = false
            return
        }

        try {
            state.validationIssues = validator.validate(document)
            context.sceneFiles.writeText(state.uiScenePath, serializer.encode(document))
            state.dirty = false
            state.pendingReloadConfirmation = false
            state.saveStatusMessage = "Document saved."
            state.statusMessage = "Document saved."
            context.logger.info(TAG) {
                "UiComposer document saved path='${state.uiScenePath}' validationIssues=${state.validationIssues.size}"
            }
        } catch (error: Exception) {
            state.saveStatusMessage = "Save failed: ${error.message}"
            state.statusMessage = "Save failed."
            context.logger.error(TAG, error) {
                "Failed to save UiComposer document path='${state.uiScenePath}': ${error.message}"
            }
        } finally {
            state.saveRequested = false
        }
    }

    /**
     * Applies a scalar property edit to the currently selected `.krui` node.
     *
     * This operation belongs to editor selected-node property editing. It updates
     * the in-memory document, revalidates it, marks `.krui` dirty, and requests a
     * preview rebuild. It intentionally does not edit child structure, add/delete
     * nodes, reorder nodes, save automatically, edit preview payload, edit Skins,
     * create asset-id references, or serialize Scene2D actors.
     */
    fun updateSelectedNode(transform: (UiSceneNode) -> UiSceneNode) {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        val oldNode = findUiSceneNodeById(document.root, selectedId) ?: return
        val newNode = transform(oldNode)
        if (newNode == oldNode) return

        val updatedDocument = document.updateNode(selectedId) { newNode }
        state.document = updatedDocument
        state.selectedNodeId = newNode.id
        state.validationIssues = validator.validate(updatedDocument)
        state.dirty = true
        state.pendingReloadConfirmation = false
        state.previewRebuildRequested = true
        state.saveStatusMessage = null
        state.statusMessage = "Document edited."
    }

    /**
     * Adds a default child node of [type] to the selected container-like node.
     *
     * This operation belongs to editor structure editing and creation UX. It
     * creates a unique id, updates the in-memory document, selects the new child,
     * validates, marks dirty, and asks the preview to rebuild. It does not
     * autosave, add children through canvas drag/drop, select by preview click,
     * resize actors, edit Skins, pick assets, create asset ids, solve layout, or
     * serialize full Scene2D actors.
     */
    fun addChildToSelected(type: UiSceneNodeType) {
        val document = state.document ?: return
        val parentId = state.selectedNodeId ?: document.root.id
        val parent = findUiSceneNodeById(document.root, parentId) ?: return
        if (!parent.type.isContainerLike()) {
            state.statusMessage = "Selected node type is leaf-like; add child is disabled."
            return
        }

        val childId = document.uniqueNodeId(type.defaultIdBase())
        val child = createDefaultUiSceneNode(type, childId)
        applyDocumentEdit(
            status = "Added ${type.name} child.",
            selectNodeId = childId,
        ) { current ->
            current.addChildNode(parentId, child)
        }
    }

    /**
     * Deletes the selected non-root node and selects its parent.
     *
     * This operation belongs to hierarchy-driven editor structure editing. It
     * keeps root deletion disabled and updates only the in-memory `.krui` tree
     * until the user explicitly saves. It does not delete through canvas input,
     * autosave, edit Skins, pick assets, create asset ids, solve layout, or
     * serialize Scene2D actors.
     */
    fun deleteSelectedNode() {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        if (selectedId == document.root.id) {
            state.statusMessage = "Root node cannot be deleted."
            return
        }
        val parentId = document.parentOf(selectedId)?.id ?: document.root.id
        applyDocumentEdit(
            status = "Deleted selected node.",
            selectNodeId = parentId,
        ) { current ->
            current.deleteNode(selectedId)
        }
    }

    /**
     * Duplicates the selected non-root node next to the original.
     *
     * This operation belongs to editor structure editing. It creates unique ids
     * for the duplicate subtree, selects the duplicate root, validates, marks
     * dirty, and rebuilds preview. It does not duplicate the document root,
     * autosave, use canvas drag/drop, edit Skins, pick assets, create asset-id
     * references, solve layout, or serialize actors.
     */
    fun duplicateSelectedNode() {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        if (selectedId == document.root.id) {
            state.statusMessage = "Root node cannot be duplicated."
            return
        }
        val duplicateId = document.uniqueNodeId("${selectedId}_copy")
        applyDocumentEdit(
            status = "Duplicated selected node.",
            selectNodeId = duplicateId,
        ) { current ->
            current.duplicateNode(selectedId, duplicateId)
        }
    }

    /**
     * Moves the selected non-root node one position earlier among siblings.
     *
     * This operation belongs to editor structure editing and keeps selection on
     * the moved node. It does not implement canvas drag/drop, autosave, Skin
     * editing, Asset Browser picking, asset-id references, layout solving, or
     * full actor serialization.
     */
    fun moveSelectedNodeUp() {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        if (selectedId == document.root.id) {
            state.statusMessage = "Root node cannot be moved."
            return
        }
        applyDocumentEdit(
            status = "Moved selected node up.",
            selectNodeId = selectedId,
        ) { current ->
            current.moveNodeUp(selectedId)
        }
    }

    /**
     * Moves the selected non-root node one position later among siblings.
     *
     * This operation belongs to editor structure editing and keeps selection on
     * the moved node. It does not implement canvas drag/drop, autosave, Skin
     * editing, Asset Browser picking, asset-id references, layout solving, or
     * full actor serialization.
     */
    fun moveSelectedNodeDown() {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        if (selectedId == document.root.id) {
            state.statusMessage = "Root node cannot be moved."
            return
        }
        applyDocumentEdit(
            status = "Moved selected node down.",
            selectNodeId = selectedId,
        ) { current ->
            current.moveNodeDown(selectedId)
        }
    }

    /**
     * Wraps the selected non-root node in a Container, Stack, or Table wrapper.
     *
     * This operation belongs to editor structure editing. It creates a unique
     * wrapper node, places the selected node inside it, selects the wrapper,
     * validates, marks dirty, and rebuilds preview. It does not wrap through
     * canvas gestures, autosave, edit Skins, pick assets, create asset ids, solve
     * layout, or serialize Scene2D actors.
     */
    fun wrapSelectedNode(wrapperType: UiSceneNodeType) {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        if (selectedId == document.root.id) {
            state.statusMessage = "Root node cannot be wrapped."
            return
        }
        if (!wrapperType.isContainerLike()) {
            state.statusMessage = "Only Container, Stack, and Table wrappers are supported."
            return
        }

        val wrapperId = document.uniqueNodeId(wrapperType.defaultIdBase())
        val wrapper = createDefaultUiSceneNode(wrapperType, wrapperId)
        applyDocumentEdit(
            status = "Wrapped selected node in ${wrapperType.name}.",
            selectNodeId = wrapperId,
        ) { current ->
            current.wrapNode(selectedId, wrapper)
        }
    }

    /**
     * Restores UiComposer ImGui panels to the built-in fallback layout.
     *
     * This operation affects only editor panel placement. It does not delete the
     * saved layout file, mutate `.krui`, edit Skins, or change preview content.
     */
    fun restoreUiLayout() {
        layoutTracker.requestRestore(UiComposerUiLayoutDefaults.config)
        state.statusMessage = "Panel layout reset to default."
        context.logger.info(TAG) {
            "UiComposer UI layout reset to default panels=${UiComposerUiLayoutDefaults.config.panels.size}"
        }
    }

    private fun applyDocumentEdit(
        status: String,
        selectNodeId: String? = state.selectedNodeId,
        transform: (UiSceneDocument) -> UiSceneDocument,
    ) {
        val document = state.document ?: return
        val updatedDocument = transform(document)
        if (updatedDocument == document) {
            state.statusMessage = "Structure edit had no effect."
            return
        }

        // Every structure edit follows the same validate/dirty/rebuild path.
        state.document = updatedDocument
        state.selectedNodeId = selectNodeId?.takeIf { updatedDocument.containsNodeId(it) } ?: updatedDocument.root.id
        state.validationIssues = validator.validate(updatedDocument)
        state.dirty = true
        state.pendingReloadConfirmation = false
        state.previewRebuildRequested = true
        state.saveStatusMessage = null
        state.statusMessage = status
    }

    companion object {
        private const val TAG = "UiComposerOperations"
    }
}

private fun UiSceneNodeType.defaultIdBase(): String =
    when (this) {
        UiSceneNodeType.Stack -> "stack"
        UiSceneNodeType.Table -> "table"
        UiSceneNodeType.Container -> "container"
        UiSceneNodeType.Label -> "label"
        UiSceneNodeType.TextButton -> "button"
        UiSceneNodeType.ProgressBar -> "progress"
        UiSceneNodeType.Image -> "image"
        UiSceneNodeType.Space -> "space"
    }

private fun UiSceneNodeType.isContainerLike(): Boolean =
    this == UiSceneNodeType.Stack || this == UiSceneNodeType.Table || this == UiSceneNodeType.Container
