package com.pashkd.krender.engine.ui

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System

data class UiCaptureState(
    val mouse: Boolean = false,
    val keyboard: Boolean = false,
)

interface UiContext {
    val captureState: UiCaptureState

    fun beginFrame(deltaSeconds: Float)
    fun endFrame()
    fun render()
    fun setDebugWindowLayout(layoutConfig: ImGuiLayoutConfig)
    fun resize(width: Int, height: Int)
    fun dispose()
}

interface UiService : UiContext

fun interface UiPanel {
    fun draw()
}

class UiSystem(
    private val ui: UiService,
    private val panels: MutableList<UiPanel> = mutableListOf(),
) : System() {
    fun addPanel(panel: UiPanel): UiPanel {
        panels += panel
        return panel
    }

    override fun update(world: SceneWorld, dt: Float) {
        ui.beginFrame(dt)
        try {
            panels.forEach(UiPanel::draw)
        } finally {
            ui.endFrame()
        }
    }
}
