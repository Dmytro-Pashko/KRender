package com.pashkd.krender.engine.ui

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.SceneFileService
import java.util.Locale

/**
 * Encodes and decodes the shared ImGui layout JSON format used by assets/ui layout files.
 */
object ImGuiLayoutConfigCodec {
    /**
     * Decodes [text] into a validated layout config, falling back to [fallback] when the root schema is invalid.
     */
    fun decode(
        text: String,
        fallback: ImGuiLayoutConfig,
        logger: Logger,
        source: String = "<layout>",
    ): ImGuiLayoutConfig =
        decodeResult(text, fallback, logger, source).config

    /**
     * Decodes [text] and reports whether the whole fallback config had to be used.
     */
    fun decodeResult(
        text: String,
        fallback: ImGuiLayoutConfig,
        logger: Logger,
        source: String = "<layout>",
    ): ImGuiLayoutConfigDecodeResult =
        try {
            val root = JsonReader().parse(text)
            val panelsNode = root.get("panels")
            if (panelsNode == null || !panelsNode.isObject) {
                logger.warn(TAG) { "Layout asset '$source' is missing a valid 'panels' object. Using fallback layout." }
                ImGuiLayoutConfigDecodeResult(config = fallback, usedFallback = true)
            } else {
                ImGuiLayoutConfigDecodeResult(
                    config = ImGuiLayoutConfig(panels = parsePanels(panelsNode, fallback, logger, source)),
                    usedFallback = false,
                )
            }
        } catch (error: Exception) {
            logger.warn(TAG, error) { "Failed to parse layout asset '$source'. Using fallback ImGui layout." }
            ImGuiLayoutConfigDecodeResult(config = fallback, usedFallback = true)
        }

    /**
     * Serializes [config] using the existing layout schema.
     */
    fun encode(config: ImGuiLayoutConfig): String =
        buildString {
            appendLine("{")
            appendLine("  \"panels\": {")
            config.panels.entries.forEachIndexed { index, (panelId, layout) ->
                append("    \"")
                append(escapeJson(panelId))
                appendLine("\": {")
                append("      \"title\": \"")
                append(escapeJson(layout.title))
                appendLine("\",")
                append("      \"x\": ")
                appendLine(formatNumber(layout.x) + ",")
                append("      \"y\": ")
                appendLine(formatNumber(layout.y) + ",")
                append("      \"width\": ")
                appendLine(formatNumber(layout.width) + ",")
                append("      \"height\": ")
                appendLine(formatNumber(layout.height))
                append("    }")
                if (index < config.panels.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine("  }")
            appendLine("}")
        }

    /**
     * Writes [config] to [path], creating parent directories through the project file service.
     */
    fun save(path: String, config: ImGuiLayoutConfig, files: SceneFileService) {
        files.ensureDirectories(path)
        files.writeText(path, encode(config))
    }

    private fun parsePanels(
        panelsNode: JsonValue,
        fallback: ImGuiLayoutConfig,
        logger: Logger,
        source: String,
    ): Map<String, ImGuiPanelLayout> {
        val parsedPanels = fallback.panels.mapValuesTo(linkedMapOf()) { (panelId, fallbackPanel) ->
            val panelNode = panelsNode.get(panelId)
            if (panelNode == null || !panelNode.isObject) {
                logger.warn(TAG) {
                    "Layout asset '$source' is missing panel '$panelId'. Using fallback layout for that panel."
                }
                fallbackPanel
            } else {
                parsePanel(panelId, panelNode, fallbackPanel, logger, source)
            }
        }

        var panelNode = panelsNode.child
        while (panelNode != null) {
            val panelId = panelNode.name ?: ""
            if (panelId.isNotBlank() && panelId !in parsedPanels && panelNode.isObject) {
                parseJsonOnlyPanel(panelId, panelNode, logger, source)?.let { parsedPanels[panelId] = it }
            }
            panelNode = panelNode.next
        }

        return parsedPanels
    }

    private fun parsePanel(
        panelId: String,
        panelNode: JsonValue,
        fallbackPanel: ImGuiPanelLayout,
        logger: Logger,
        source: String,
    ): ImGuiPanelLayout {
        val title = panelNode.getString("title", fallbackPanel.title).takeUnless(String::isBlank) ?: fallbackPanel.title
        val x = readFiniteFloat(panelNode, "x", fallbackPanel.x)
        val y = readFiniteFloat(panelNode, "y", fallbackPanel.y)
        val width = readPositiveFloat(panelNode, "width", fallbackPanel.width, panelId, logger, source)
        val height = readPositiveFloat(panelNode, "height", fallbackPanel.height, panelId, logger, source)
        return ImGuiPanelLayout(
            title = title,
            x = x,
            y = y,
            width = width,
            height = height,
        )
    }

    private fun parseJsonOnlyPanel(
        panelId: String,
        panelNode: JsonValue,
        logger: Logger,
        source: String,
    ): ImGuiPanelLayout? {
        val title = panelNode.getString("title", panelId).takeUnless(String::isBlank) ?: panelId
        val x = readFiniteFloat(panelNode, "x", 0f)
        val y = readFiniteFloat(panelNode, "y", 0f)
        val width = readPositiveFloat(panelNode, "width", DEFAULT_WIDTH, panelId, logger, source)
        val height = readPositiveFloat(panelNode, "height", DEFAULT_HEIGHT, panelId, logger, source)
        return ImGuiPanelLayout(
            title = title,
            x = x,
            y = y,
            width = width,
            height = height,
        )
    }

    private fun readFiniteFloat(panelNode: JsonValue, name: String, fallbackValue: Float): Float {
        val value = panelNode.getFloat(name, fallbackValue)
        return if (value.isFinite()) value else fallbackValue
    }

    private fun readPositiveFloat(
        panelNode: JsonValue,
        name: String,
        fallbackValue: Float,
        panelId: String,
        logger: Logger,
        source: String,
    ): Float {
        val value = readFiniteFloat(panelNode, name, fallbackValue)
        if (value > 0f) {
            return value
        }

        logger.warn(TAG) {
            "Layout asset '$source' has invalid $name for panel '$panelId'. Using fallback value $fallbackValue."
        }
        return fallbackValue
    }

    private fun formatNumber(value: Float): String {
        val rounded = if (value.isFinite()) value else 0f
        if (rounded % 1f == 0f) {
            return rounded.toInt().toString()
        }
        return String.format(Locale.US, "%.3f", rounded).trimEnd('0').trimEnd('.')
    }

    private fun escapeJson(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }

    private const val TAG = "ImGuiLayoutConfigCodec"
    private const val DEFAULT_WIDTH = 320f
    private const val DEFAULT_HEIGHT = 240f
}

/**
 * Decoded layout plus root-level fallback status.
 */
data class ImGuiLayoutConfigDecodeResult(
    val config: ImGuiLayoutConfig,
    val usedFallback: Boolean,
)
