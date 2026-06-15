package com.pashkd.krender.engine.ui.editor

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.ui.UiService as EngineUiService

typealias UiService = EngineUiService

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
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        panels.forEach(UiPanel::draw)
    }
}
