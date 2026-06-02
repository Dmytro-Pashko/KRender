package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker

/**
 * UI-level actions for the Asset Browser scene.
 */
class AssetBrowserUiOperations(
    private val state: AssetBrowserState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun playWoolboyScene() {
        try {
            context.runtimeLauncher.launchScene(WoolboySandboxSceneId)
            state.statusMessage = "Playing Woolboy scene."
            context.logger.info(TAG) { "Launching playable Woolboy sandbox scene id='$WoolboySandboxSceneId'" }
        } catch (error: Exception) {
            state.statusMessage = "Play Woolboy scene failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to launch playable Woolboy sandbox scene id='$WoolboySandboxSceneId': ${error.message}"
            }
        }
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
        private const val WoolboySandboxSceneId = "woolboy_sandbox_scene"
    }
}
