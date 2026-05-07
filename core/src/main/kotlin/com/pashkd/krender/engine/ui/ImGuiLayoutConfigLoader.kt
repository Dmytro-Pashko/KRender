package com.pashkd.krender.engine.ui

import com.badlogic.gdx.Gdx
import com.pashkd.krender.engine.api.Logger

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
    fun load(logger: Logger): ImGuiLayoutConfig {
        val file = Gdx.files.internal(assetPath)
        if (!file.exists()) {
            logger.warn(TAG) { "Layout asset '$assetPath' is missing. Using fallback ImGui layout." }
            return fallback
        }

        return ImGuiLayoutConfigCodec.decode(
            text = file.readString(),
            fallback = fallback,
            logger = logger,
            source = assetPath,
        )
    }

    companion object {
        private const val TAG = "ImGuiLayoutConfigLoader"
    }
}
