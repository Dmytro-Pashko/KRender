package com.pashkd.krender.engine.ui.editor

import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.WindowFlag

/**
 * Tracks live ImGui window positions and applies one-shot layout restores.
 */
class ImGuiLayoutRuntimeTracker(
    private val baseConfig: ImGuiLayoutConfig,
) {
    var panelsLocked: Boolean = true

    private val currentPanels =
        linkedMapOf<String, ImGuiPanelLayout>().apply {
            putAll(baseConfig.panels)
        }
    private var pendingRestorePanels: LinkedHashMap<String, ImGuiPanelLayout>? = null

    /**
     * Captures the current ImGui window rect for [panelId].
     */
    fun capture(panelId: String) {
        val position = ImGui.windowPos
        val size = ImGui.windowSize
        if (!position.x.isFinite() || !position.y.isFinite() || !size.x.isFinite() || !size.y.isFinite()) {
            return
        }
        if (size.x <= 0f || size.y <= 0f) {
            return
        }

        val existing = currentPanels[panelId] ?: baseConfig.panels[panelId]
        currentPanels[panelId] =
            ImGuiPanelLayout(
                title = existing?.title ?: panelId,
                x = position.x,
                y = position.y,
                width = size.x,
                height = size.y,
            )
    }

    /**
     * Returns the latest known layout for all tracked panels.
     */
    fun currentConfig(): ImGuiLayoutConfig = ImGuiLayoutConfig(panels = LinkedHashMap(currentPanels))

    /**
     * Queues [config] to be force-applied once as panels are next drawn.
     */
    fun requestRestore(config: ImGuiLayoutConfig) {
        currentPanels.clear()
        currentPanels.putAll(config.panels)
        pendingRestorePanels = LinkedHashMap(config.panels)
    }

    /**
     * Returns and consumes a pending forced layout for [panelId], if one exists.
     */
    fun consumeRestoreLayout(panelId: String): ImGuiPanelLayout? {
        val pendingPanels = pendingRestorePanels ?: return null
        val layout = pendingPanels.remove(panelId)
        if (pendingPanels.isEmpty()) {
            pendingRestorePanels = null
        }
        return layout
    }
}

/**
 * Applies an ImGui panel layout with the requested condition.
 */
fun applyImGuiPanelLayout(
    layout: ImGuiPanelLayout,
    condition: Cond = Cond.FirstUseEver,
) {
    ImGui.setNextWindowPos(Vec2(layout.x, layout.y), condition, Vec2())
    ImGui.setNextWindowSize(Vec2(layout.width, layout.height), condition)
}

/**
 * Begins a panel window and captures its runtime layout when [tracker] is present.
 */
fun beginImGuiPanel(
    panelId: String,
    layout: ImGuiPanelLayout,
    tracker: ImGuiLayoutRuntimeTracker? = null,
): Boolean {
    val restoredLayout = tracker?.consumeRestoreLayout(panelId)
    val activeLayout = restoredLayout ?: layout
    applyImGuiPanelLayout(activeLayout, if (restoredLayout == null) Cond.FirstUseEver else Cond.Always)
    val windowName = imGuiPanelWindowName(activeLayout.title, panelId)
    val expanded =
        if (tracker?.panelsLocked == true) {
            ImGui.begin(windowName, flags_ = WindowFlag.NoMove)
        } else {
            ImGui.begin(windowName)
        }
    tracker?.capture(panelId)
    return expanded
}

/**
 * Draws a shared layout lock toggle button for tool control panels.
 */
fun drawImGuiLayoutLockButton(
    tracker: ImGuiLayoutRuntimeTracker,
    id: String,
) {
    val label = if (tracker.panelsLocked) "Unlock Layout" else "Lock Layout"
    if (ImGui.button("$label##${id}_layout_lock")) {
        tracker.panelsLocked = !tracker.panelsLocked
    }
}

/**
 * Builds a stable ImGui window name from visible title plus hidden panel id.
 */
fun imGuiPanelWindowName(
    title: String,
    id: String,
): String = "$title###$id"
