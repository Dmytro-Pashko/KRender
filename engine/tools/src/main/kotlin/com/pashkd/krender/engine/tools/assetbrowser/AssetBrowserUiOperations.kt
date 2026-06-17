package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

/**
 * UI-level actions for the Asset Browser scene.
 */
class AssetBrowserUiOperations(
    private val state: AssetBrowserState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun exit() {
        state.statusMessage = "Exit requested."
        context.logger.info(TAG) { "Asset Browser exit requested from controls panel" }
        context.requestExit()
    }

    fun saveUiLayout() {
        try {
            val config = layoutTracker.currentConfig()
            ImGuiLayoutConfigCodec.save(AssetBrowserUiLayoutDefaults.assetPath, config, context.sceneFiles)
            state.statusMessage = "UI layout saved."
            context.logger.info(TAG) {
                "Asset Browser UI layout saved path='${AssetBrowserUiLayoutDefaults.assetPath}' panels=${config.panels.size}"
            }
        } catch (error: Exception) {
            state.statusMessage = "UI layout save failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to save Asset Browser UI layout path='${AssetBrowserUiLayoutDefaults.assetPath}': ${error.message}"
            }
        }
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(AssetBrowserUiLayoutDefaults.config)
        state.statusMessage = "UI layout reset to default."
        context.logger.info(TAG) {
            "Asset Browser UI layout reset to default panels=${AssetBrowserUiLayoutDefaults.config.panels.size}"
        }
    }

    companion object {
        private const val TAG = "AssetBrowserUiOperations"
    }
}
