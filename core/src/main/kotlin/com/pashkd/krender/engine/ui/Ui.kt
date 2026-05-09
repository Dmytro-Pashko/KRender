package com.pashkd.krender.engine.ui

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TexturePreviewHandle

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

    /** Informs the UI backend about viewport size changes. */
    fun resize(width: Int, height: Int)

    /** Draws an opaque backend texture preview at the requested UI size. */
    fun drawTexturePreview(handle: TexturePreviewHandle, width: Float, height: Float): Boolean = false

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
 * System that drives panel drawing inside an externally-managed UI frame.
 *
 * The owning [com.pashkd.krender.engine.api.GameLoop] is responsible for
 * calling [UiService.beginFrame] before scene systems run and
 * [UiService.endFrame] afterwards. Centralizing the frame boundary keeps the
 * current-frame UI capture flags (e.g. `wantCaptureMouse`) available to the
 * input snapshot used by every other system, instead of being one frame stale.
 */
class UiSystem(
    @Suppress("unused") private val ui: UiService,
    private val panels: MutableList<UiPanel> = mutableListOf(),
) : System() {
    /** Registers a panel to be drawn every UI frame. */
    fun addPanel(panel: UiPanel): UiPanel {
        panels += panel
        return panel
    }

    /** Draws all registered panels inside the active UI frame. */
    override fun update(world: SceneWorld, dt: Float) {
        panels.forEach(UiPanel::draw)
    }
}
