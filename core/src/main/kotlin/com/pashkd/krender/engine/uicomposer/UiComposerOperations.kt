package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

/**
 * UI Composer editor operations that are not document editing operations.
 *
 * This class belongs to editor UI layout persistence for the Phase 4 read-only
 * preview. It saves and restores ImGui panel placement only; it intentionally
 * does not save `.krui` documents, edit node properties, add/delete/reorder
 * nodes, edit Skins, implement drag/drop canvas editing, add Asset Browser
 * pickers, introduce asset-id references, or serialize Scene2D actors.
 */
class UiComposerOperations(
    private val state: UiComposerState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
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
            state.statusMessage = "UI layout saved."
            context.logger.info(TAG) {
                "UiComposer UI layout saved path='${UiComposerUiLayoutDefaults.assetPath}' panels=${config.panels.size}"
            }
        } catch (error: Exception) {
            state.statusMessage = "UI layout save failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to save UiComposer UI layout path='${UiComposerUiLayoutDefaults.assetPath}': ${error.message}"
            }
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
        state.statusMessage = "UI layout reset to default."
        context.logger.info(TAG) {
            "UiComposer UI layout reset to default panels=${UiComposerUiLayoutDefaults.config.panels.size}"
        }
    }

    companion object {
        private const val TAG = "UiComposerOperations"
    }
}
