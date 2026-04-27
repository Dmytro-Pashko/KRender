package com.pashkd.krender.engine.ui

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System

/**
 * Describes whether the UI wants to consume mouse or keyboard input this frame.
 */
data class UiCaptureState(
    /** True when the UI is actively consuming mouse input. */
    val mouse: Boolean = false,

    /** True when the UI is actively consuming keyboard input. */
    val keyboard: Boolean = false,
)

/**
 * Shared contract for frame-based UI backends.
 */
interface UiContext {
    /** Current input-capture state reported by the UI backend. */
    val captureState: UiCaptureState

    /** Starts a new UI frame using the elapsed frame time in seconds. */
    fun beginFrame(deltaSeconds: Float)

    /** Finalizes the current UI frame after all panels have been drawn. */
    fun endFrame()

    /** Submits the prepared UI draw data to the active renderer. */
    fun render()

    /** Applies the shared debug window layout used by the current scene. */
    fun setDebugWindowLayout(layoutConfig: ImGuiLayoutConfig)

    /** Informs the UI backend about viewport size changes. */
    fun resize(width: Int, height: Int)

    /** Releases all resources owned by the UI backend. */
    fun dispose()
}

/**
 * Concrete engine-facing UI service.
 */
interface UiService : UiContext

/**
 * One drawable UI panel callback.
 */
fun interface UiPanel {
    /** Draws the panel's UI for the current frame. */
    fun draw()
}

/**
 * System that drives UI frame begin/end and panel drawing.
 */
class UiSystem(
    private val ui: UiService,
    private val panels: MutableList<UiPanel> = mutableListOf(),
) : System() {
    /** Registers a panel to be drawn every UI frame. */
    fun addPanel(panel: UiPanel): UiPanel {
        panels += panel
        return panel
    }

    /** Opens a UI frame, draws all registered panels, and closes the frame safely. */
    override fun update(world: SceneWorld, dt: Float) {
        ui.beginFrame(dt)
        try {
            panels.forEach(UiPanel::draw)
        } finally {
            ui.endFrame()
        }
    }
}
