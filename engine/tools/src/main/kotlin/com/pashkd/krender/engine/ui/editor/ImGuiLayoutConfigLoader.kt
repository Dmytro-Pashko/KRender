package com.pashkd.krender.engine.ui.editor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.SceneFileService

/**
 * Loads an ImGui layout asset and falls back to safe defaults when needed.
 */
class ImGuiLayoutConfigLoader(
    private val assetPath: String,
    private val fallback: ImGuiLayoutConfig,
) {
    /**
     * Reads the configured asset and returns a validated layout map.
     */
    fun load(
        logger: Logger,
        files: SceneFileService,
    ): ImGuiLayoutConfig {
        if (!files.exists(assetPath)) {
            logger.warn(TAG) { "Layout asset '$assetPath' is missing. Using fallback ImGui layout." }
            return fallback
        }

        return ImGuiLayoutConfigCodec.decode(
            text = files.readText(assetPath),
            fallback = fallback,
            logger = logger,
            source = assetPath,
        )
    }

    companion object {
        private const val TAG = "ImGuiLayoutConfigLoader"
    }
}
