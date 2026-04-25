package com.pashkd.krender.engine.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
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

        return try {
            val root = JsonReader().parse(file.readString())
            val panelsNode = root.get("panels")
            if (panelsNode == null || !panelsNode.isObject) {
                logger.warn(TAG) { "Layout asset '$assetPath' is missing a valid 'panels' object. Using fallback layout." }
                return fallback
            }

            ImGuiLayoutConfig(
                panels = fallback.panels.mapValues { (panelId, fallbackPanel) ->
                    val panelNode = panelsNode.get(panelId)
                    if (panelNode == null || !panelNode.isObject) {
                        logger.warn(TAG) {
                            "Layout asset '$assetPath' is missing panel '$panelId'. Using fallback layout for that panel."
                        }
                        fallbackPanel
                    } else {
                        parsePanel(panelId, panelNode, fallbackPanel, logger)
                    }
                },
            )
        } catch (error: Exception) {
            logger.warn(TAG, error) { "Failed to parse layout asset '$assetPath'. Using fallback ImGui layout." }
            fallback
        }
    }

    /**
     * Builds one panel layout, mixing JSON values with fallback defaults.
     */
    private fun parsePanel(
        panelId: String,
        panelNode: JsonValue,
        fallbackPanel: ImGuiPanelLayout,
        logger: Logger,
    ): ImGuiPanelLayout {
        val title = panelNode.getString("title", fallbackPanel.title).takeUnless(String::isBlank) ?: fallbackPanel.title
        val x = readFiniteFloat(panelNode, "x", fallbackPanel.x)
        val y = readFiniteFloat(panelNode, "y", fallbackPanel.y)
        val width = readPositiveFloat(panelNode, "width", fallbackPanel.width, panelId, logger)
        val height = readPositiveFloat(panelNode, "height", fallbackPanel.height, panelId, logger)
        return ImGuiPanelLayout(
            title = title,
            x = x,
            y = y,
            width = width,
            height = height,
        )
    }

    /**
     * Reads a float field while rejecting non-finite values.
     */
    private fun readFiniteFloat(panelNode: JsonValue, name: String, fallbackValue: Float): Float {
        val value = panelNode.getFloat(name, fallbackValue)
        return if (value.isFinite()) value else fallbackValue
    }

    /**
     * Reads a strictly positive size field and logs when fallback is used.
     */
    private fun readPositiveFloat(
        panelNode: JsonValue,
        name: String,
        fallbackValue: Float,
        panelId: String,
        logger: Logger,
    ): Float {
        val value = readFiniteFloat(panelNode, name, fallbackValue)
        if (value > 0f) {
            return value
        }

        logger.warn(TAG) {
            "Layout asset '$assetPath' has invalid $name for panel '$panelId'. Using fallback value $fallbackValue."
        }
        return fallbackValue
    }

    companion object {
        private const val TAG = "ImGuiLayoutConfigLoader"
    }
}
