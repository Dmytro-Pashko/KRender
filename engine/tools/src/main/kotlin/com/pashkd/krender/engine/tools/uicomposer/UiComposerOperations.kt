package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.tools.uicomposer.*
import com.pashkd.krender.engine.ui.scene.*

/**
 * UI Composer editor operations for panel layout persistence and basic `.krui` editing.
 *
 * This class belongs to editor editing and editor preview UX. It can persist
 * ImGui panel placement, update scalar properties on the selected `.krui` node,
 * perform hierarchy/inspector-driven structure edits, validate the in-memory
 * document, request preview rebuilds, and save the `.krui` document. It
 * intentionally does not implement drag/drop or canvas selection, resize actors
 * on canvas, edit Skins, import/copy textures, add Asset Browser drag/drop,
 * introduce asset-id references, alter
 * runtime Woolboy UI behavior, solve generic visual layout, or serialize full
 * Scene2D actors.
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
     * This serializes the shared `.krui` document model, including scene binding
     * definitions and their editor default preview values. It does not persist
     * transient canvas state, panel layout, runtime UI state, Skin data, Asset
     * Browser choices, runtime payload values, or Scene2D actor instances.
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
            refreshDiagnostics(document)
            context.sceneFiles.writeText(state.uiScenePath, serializer.encode(document))
            state.savedDocumentSnapshot = document
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
     * nodes, reorder nodes, save automatically, edit Skins,
     * create asset-id references, or serialize Scene2D actors.
     */
    fun updateSelectedNode(transform: (UiSceneNode) -> UiSceneNode) {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        val oldNode = findUiSceneNodeById(document.root, selectedId) ?: return
        val newNode = transform(oldNode)
        if (newNode == oldNode) return

        applyDocumentChange(
            description = "Document edited.",
            transform = { current ->
                current.updateNode(selectedId) { newNode }
            },
            selectNodeId = { newDocument ->
                newNode.id.takeIf { id -> findUiSceneNodeById(newDocument.root, id) != null }
            },
        )
    }

    /**
     * Updates a saved binding default preview value and the live editor preview payload.
     *
     * Binding definitions are stored in `.krui`, so editing default preview
     * values marks the document dirty and will be persisted by Save. Runtime
     * payload values are still supplied explicitly by runtime systems.
     */
    fun updateBindingDefaultValue(
        key: String,
        defaultValue: String,
    ) {
        val document = state.document ?: return
        if (key !in document.bindings.map { binding -> binding.key }) return
        applyDocumentChange(
            description = "Binding default preview value changed.",
            transform = { current ->
                current.copy(
                    bindings = updateBindingDefaultValue(current.bindings, key, defaultValue),
                )
            },
        )
    }

    /**
     * Adds or replaces a saved scene binding definition and syncs the preview payload.
     */
    fun upsertBindingDefinition(binding: UiSceneBindingDefinition) {
        val document = state.document ?: return
        if (binding.key.isBlank()) {
            state.statusMessage = "Binding key cannot be blank."
            return
        }
        val replacing = binding.key in bindingKeys(document)
        val description =
            if (replacing) {
                "Binding '${binding.key}' updated in scene bindings."
            } else {
                "Binding '${binding.key}' added to scene bindings."
            }
        applyDocumentChange(
            description = description,
            transform = { current ->
                current.copy(bindings = upsertBindingDefinition(current.bindings, binding))
            },
        )
    }

    /**
     * Adds a new saved scene binding definition without rewriting existing node usages.
     */
    fun addBindingDefinition(binding: UiSceneBindingDefinition) {
        val document = state.document ?: return
        val key = binding.key.trim()
        if (key.isBlank()) {
            state.statusMessage = "Binding key cannot be blank."
            return
        }
        if (key in bindingKeys(document)) {
            state.statusMessage = "Binding '$key' already exists."
            return
        }

        applyDocumentChange(
            description = "Binding '$key' added.",
            transform = { current ->
                current.copy(
                    bindings =
                        (current.bindings + binding.copy(key = key))
                            .sortedBy { existing -> existing.key.lowercase() },
                )
            },
        )
    }

    /**
     * Renames a scene binding definition key without rewriting node usages.
     */
    fun renameBindingKey(
        oldKey: String,
        newKey: String,
    ) {
        val document = state.document ?: return
        val trimmed = newKey.trim()
        if (trimmed.isBlank()) {
            state.statusMessage = "Binding key cannot be blank."
            return
        }
        if (trimmed == oldKey) return
        if (document.bindings.any { binding -> binding.key == trimmed }) {
            state.statusMessage = "Binding '$trimmed' already exists."
            return
        }

        applyDocumentChange(
            description = "Binding '$oldKey' renamed to '$trimmed'.",
            transform = { current ->
                current.copy(
                    bindings =
                        current.bindings
                            .map { binding -> if (binding.key == oldKey) binding.copy(key = trimmed) else binding }
                            .sortedBy { binding -> binding.key.lowercase() },
                )
            },
        )
    }

    /**
     * Updates a binding definition type and leaves its default preview value untouched.
     */
    fun updateBindingType(
        key: String,
        type: UiSceneBindingType,
    ) {
        val document = state.document ?: return
        val binding = document.bindings.firstOrNull { existing -> existing.key == key } ?: return
        if (binding.type == type) return

        applyDocumentChange(
            description = "Binding '$key' type changed to ${type.name}.",
            transform = { current ->
                current.copy(
                    bindings =
                        current.bindings.map { existing ->
                            if (existing.key == key) existing.copy(type = type) else existing
                        },
                )
            },
        )
    }

    /**
     * Deletes a binding definition without removing references from nodes.
     */
    fun deleteBindingDefinition(key: String) {
        val document = state.document ?: return
        if (document.bindings.none { binding -> binding.key == key }) return

        applyDocumentChange(
            description = "Binding '$key' deleted.",
            transform = { current ->
                current.copy(
                    bindings = current.bindings.filterNot { binding -> binding.key == key },
                )
            },
        )
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
        applyDocumentChange(
            description = "Added ${type.name} child.",
            selectNodeId = { updatedDocument ->
                childId.takeIf { updatedDocument.containsNodeId(it) }
            },
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
        applyDocumentChange(
            description = "Deleted selected node.",
            selectNodeId = { updatedDocument ->
                parentId.takeIf { updatedDocument.containsNodeId(it) } ?: updatedDocument.root.id
            },
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
        applyDocumentChange(
            description = "Duplicated selected node.",
            selectNodeId = { updatedDocument ->
                duplicateId.takeIf { updatedDocument.containsNodeId(it) }
            },
        ) { current ->
            current.duplicateNode(selectedId, duplicateId)
        }
    }

    /**
     * Moves the selected non-root node one position earlier among siblings.
     *
     * This operation belongs to editor structure editing and keeps selection on
     * the moved node. It does not implement canvas drag/drop, autosave, Skin
     * editing, Asset Browser drag/drop, asset-id references, layout solving, or
     * full actor serialization.
     */
    fun moveSelectedNodeUp() {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        if (selectedId == document.root.id) {
            state.statusMessage = "Root node cannot be moved."
            return
        }
        applyDocumentChange(
            description = "Moved selected node up.",
            selectNodeId = { updatedDocument ->
                selectedId.takeIf { updatedDocument.containsNodeId(it) }
            },
        ) { current ->
            current.moveNodeUp(selectedId)
        }
    }

    /**
     * Moves the selected non-root node one position later among siblings.
     *
     * This operation belongs to editor structure editing and keeps selection on
     * the moved node. It does not implement canvas drag/drop, autosave, Skin
     * editing, Asset Browser drag/drop, asset-id references, layout solving, or
     * full actor serialization.
     */
    fun moveSelectedNodeDown() {
        val document = state.document ?: return
        val selectedId = state.selectedNodeId ?: return
        if (selectedId == document.root.id) {
            state.statusMessage = "Root node cannot be moved."
            return
        }
        applyDocumentChange(
            description = "Moved selected node down.",
            selectNodeId = { updatedDocument ->
                selectedId.takeIf { updatedDocument.containsNodeId(it) }
            },
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
        applyDocumentChange(
            description = "Wrapped selected node in ${wrapperType.name}.",
            selectNodeId = { updatedDocument ->
                wrapperId.takeIf { updatedDocument.containsNodeId(it) }
            },
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

    fun undo() {
        val currentDocument = state.document ?: return
        val entry =
            state.history.undo(
                UiComposerHistoryEntry(
                    document = currentDocument,
                    selectedNodeId = state.selectedNodeId,
                    description = "Current",
                ),
            ) ?: return

        restoreHistoryEntry(entry, statusPrefix = "Undo")
    }

    fun redo() {
        val currentDocument = state.document ?: return
        val entry =
            state.history.redo(
                UiComposerHistoryEntry(
                    document = currentDocument,
                    selectedNodeId = state.selectedNodeId,
                    description = "Current",
                ),
            ) ?: return

        restoreHistoryEntry(entry, statusPrefix = "Redo")
    }

    private fun applyDocumentChange(
        description: String,
        selectNodeId: ((UiSceneDocument) -> String?)? = null,
        transform: (UiSceneDocument) -> UiSceneDocument,
    ) {
        val oldDocument = state.document ?: return
        val oldSelectedNodeId = state.selectedNodeId
        val newDocument = transform(oldDocument)
        if (newDocument == oldDocument) return

        state.history.recordBeforeChange(
            UiComposerHistoryEntry(
                document = oldDocument,
                selectedNodeId = oldSelectedNodeId,
                description = description,
            ),
        )

        state.document = newDocument
        state.selectedNodeId = selectNodeId?.invoke(newDocument)
            ?: oldSelectedNodeId?.takeIf { id -> findUiSceneNodeById(newDocument.root, id) != null }
                ?: newDocument.root.id

        afterDocumentChanged(newDocument)
        state.statusMessage = description
    }

    private fun afterDocumentChanged(document: UiSceneDocument) {
        state.previewPayload.clear()
        state.previewPayload.putAll(previewPayloadFromBindings(document.bindings))
        refreshDiagnostics(document)
        state.dirty = document != state.savedDocumentSnapshot
        state.pendingReloadConfirmation = false
        state.previewRebuildRequested = true
        state.saveStatusMessage = null
    }

    private fun restoreHistoryEntry(
        entry: UiComposerHistoryEntry,
        statusPrefix: String,
    ) {
        state.document = entry.document
        state.selectedNodeId = entry.selectedNodeId
            ?.takeIf { id -> findUiSceneNodeById(entry.document.root, id) != null }
            ?: entry.document.root.id

        state.previewPayload.clear()
        state.previewPayload.putAll(previewPayloadFromBindings(entry.document.bindings))
        refreshDiagnostics(entry.document)

        state.dirty = entry.document != state.savedDocumentSnapshot
        state.pendingReloadConfirmation = false
        state.previewRebuildRequested = true
        state.saveStatusMessage = null
        state.statusMessage = "$statusPrefix: ${entry.description}"
    }

    private fun refreshDiagnostics(document: UiSceneDocument) {
        refreshUiComposerValidationBuckets(
            state = state,
            document = document,
            validator = validator,
        )
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
