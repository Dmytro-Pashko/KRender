package com.pashkd.krender.engine.ui.editor

import com.pashkd.krender.engine.api.LogEntry
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.LogService
import glm_.vec2.Vec2
import imgui.ImGui
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stable ImGui panel id used by scene-local runtime log windows.
 */
object LogsPanelIds {
    const val RuntimeLogs = "runtimeLogs"
}

/**
 * Presents retained runtime logs in a scene-local ImGui window.
 */
class LogsPanel(
    private val logs: LogService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
    private val panelId: String = LogsPanelIds.RuntimeLogs,
    private val layoutTracker: ImGuiLayoutRuntimeTracker? = null,
    initialAutoScrollToLatest: Boolean = false,
) : UiPanel {
    private var scrollToEndEnabled = initialAutoScrollToLatest

    /**
     * Draws the logs window using the configured default layout.
     */
    override fun draw() {
        val layout = layoutConfig.panels[panelId] ?: return
        val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
        eventLogger.observe(panelId, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawToolbar()
        ImGui.separator()

        ImGui.beginChild("${panelId}_entries", Vec2(0f, 0f), true)
        val entries = logs.recentEntries.filter { entry ->
            entry.level.ordinal >= logs.minLevel.ordinal
        }
        if (entries.isEmpty()) {
            ImGui.text("No logs recorded.")
        } else {
            entries.forEach { entry ->
                ImGui.textUnformatted(formatLogEntryForPanel(entry))
            }
            if (scrollToEndEnabled) {
                ImGui.setScrollHereY(1f)
            }
        }
        ImGui.endChild()
        ImGui.end()
    }

    /**
     * Draws memory-only actions and the shared minimum level filter.
     */
    private fun drawToolbar() {
        if (ImGui.smallButton("Clear##$panelId")) {
            logs.clear()
        }
        ImGui.sameLine()
        ImGui.text("Min level")
        LogLevel.entries.forEachIndexed { index, level ->
            ImGui.sameLine()
            val label = if (logs.minLevel == level) "[${level.name}]" else level.name
            if (ImGui.smallButton("$label##${panelId}_${level.name}")) {
                logs.minLevel = level
            }
        }
        ImGui.sameLine()
        ImGui.checkbox("Scroll to End##$panelId", ::scrollToEndEnabled)
    }

    /**
     * Formats one structured log entry for the scene logs panel.
     */
    companion object {
        internal const val MaxDisplayedEntryLength = 2_048
    }
}

internal fun formatLogEntryForPanel(entry: LogEntry): String {
    val line =
        "${TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()))} " +
            "[${entry.level}] [${entry.tag}] ${entry.message}"
    return if (line.length <= LogsPanel.MaxDisplayedEntryLength) {
        line
    } else {
        line.take(LogsPanel.MaxDisplayedEntryLength - TruncationSuffix.length) + TruncationSuffix
    }
}

private const val TruncationSuffix = "..."

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
