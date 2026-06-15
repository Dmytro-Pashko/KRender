package com.pashkd.krender.engine.ui.editor

/**
 * Stores default ImGui window layouts keyed by stable panel id.
 */
data class ImGuiLayoutConfig(
    val panels: Map<String, ImGuiPanelLayout>,
)

/**
 * Describes the initial title, position, and size for one ImGui window.
 */
data class ImGuiPanelLayout(
    val title: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
