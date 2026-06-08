package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.ui.scene.UiSceneValidator

/**
 * UI Composer editor operations for panel layout persistence and basic `.krui` editing.
 *
 * This class belongs to editor editing and editor preview UX. It can persist
 * ImGui panel placement, update scalar properties on the selected `.krui` node,
 * validate the in-memory document, request preview rebuilds, and save the
 * `.krui` document. It intentionally does not add/delete/duplicate/reorder
 * nodes, edit child structure, implement drag/drop or canvas selection, edit
 * Skins, add Asset Browser pickers, introduce asset-id references, save preview
 * payload into `.krui`, alter runtime Woolboy UI behavior, or serialize full
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
        state.previewRebuildRequested = true
        state.saveStatusMessage = null
        state.statusMessage = "Document edited."
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

    companion object {
        private const val TAG = "UiComposerOperations"
    }
}
