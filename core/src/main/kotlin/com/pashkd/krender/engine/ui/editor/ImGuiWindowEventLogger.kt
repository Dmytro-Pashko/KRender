package com.pashkd.krender.engine.ui.editor

import com.pashkd.krender.engine.api.Logger
import imgui.ImGui
import kotlin.math.abs

/**
 * Logs window position and size changes after an ImGui panel settles.
 */
class ImGuiWindowEventLogger(
    private val logger: Logger,
    private val tag: String = DEFAULT_TAG,
) {
    private val panels = mutableMapOf<String, TrackedPanel>()

    /**
     * Samples the current ImGui window rect and emits a log when it changes.
     */
    fun observe(panelId: String, title: String) {
        val position = ImGui.windowPos
        val size = ImGui.windowSize
        val snapshot = PanelRect(
            x = position.x,
            y = position.y,
            width = size.x,
            height = size.y,
        )

        val tracked = panels.getOrPut(panelId) { TrackedPanel(title = title) }
        tracked.title = title

        val committed = tracked.committedRect
        if (committed == null) {
            tracked.committedRect = snapshot
            logger.debug(tag) { "Panel '$title' ($panelId) initialized at ${snapshot.describe()}" }
            return
        }

        if (snapshot.isApproximatelyEqualTo(committed)) {
            tracked.pendingRect = null
            tracked.pendingStableFrames = 0
            return
        }

        val pending = tracked.pendingRect
        if (pending == null || !snapshot.isApproximatelyEqualTo(pending)) {
            tracked.pendingRect = snapshot
            tracked.pendingStableFrames = 0
            return
        }

        tracked.pendingStableFrames += 1
        if (tracked.pendingStableFrames < STABLE_FRAME_THRESHOLD) return

        tracked.committedRect = snapshot
        tracked.pendingRect = null
        tracked.pendingStableFrames = 0
        logger.debug(tag) {
            "Panel '$title' ($panelId) changed from ${committed.describe()} to ${snapshot.describe()}"
        }
    }

    /**
     * Keeps the last committed and pending rect for one tracked window.
     */
    private data class TrackedPanel(
        var title: String,
        var committedRect: PanelRect? = null,
        var pendingRect: PanelRect? = null,
        var pendingStableFrames: Int = 0,
    )

    /**
     * Stores one sampled ImGui window rectangle.
     */
    private data class PanelRect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) {
        /**
         * Treats tiny float jitter as unchanged.
         */
        fun isApproximatelyEqualTo(other: PanelRect): Boolean =
            approximatelyEqual(x, other.x) &&
                approximatelyEqual(y, other.y) &&
                approximatelyEqual(width, other.width) &&
                approximatelyEqual(height, other.height)

        /**
         * Formats the rect for debug logging.
         */
        fun describe(): String =
            "position=(${format(x)}, ${format(y)}), size=(${format(width)}, ${format(height)})"
    }

    companion object {
        private const val DEFAULT_TAG = "ImGuiUi"
        private const val STABLE_FRAME_THRESHOLD = 1
        private const val EPSILON = 0.5f

        private fun approximatelyEqual(left: Float, right: Float): Boolean =
            abs(left - right) <= EPSILON

        private fun format(value: Float): String = "%.1f".format(value)
    }
}
