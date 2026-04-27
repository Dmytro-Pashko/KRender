package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout

/**
 * Defines the stable JSON ids used by the ModelViewer ImGui panels.
 */
object ModelViewerPanelIds {
    const val ModelList = "modelList"
    const val ModelInfo = "modelInfo"
    const val Controls = "controls"
    const val Loading = "loading"
    const val Logs = "runtimeLogs"
}

/**
 * Holds the asset path and fallback layouts for ModelViewer panels.
 */
object ModelViewerUiLayoutDefaults {
    const val assetPath = "ui/model_viewer_layout.json"

    val config = ImGuiLayoutConfig(
        panels = mapOf(
            ModelViewerPanelIds.ModelList to ImGuiPanelLayout(
                title = "Models",
                x = 16f,
                y = 16f,
                width = 320f,
                height = 480f,
            ),
            ModelViewerPanelIds.ModelInfo to ImGuiPanelLayout(
                title = "Model Info",
                x = 352f,
                y = 16f,
                width = 320f,
                height = 220f,
            ),
            ModelViewerPanelIds.Controls to ImGuiPanelLayout(
                title = "Controls",
                x = 16f,
                y = 512f,
                width = 320f,
                height = 220f,
            ),
            ModelViewerPanelIds.Loading to ImGuiPanelLayout(
                title = "Loading",
                x = 720f,
                y = 320f,
                width = 320f,
                height = 120f,
            ),
            ModelViewerPanelIds.Logs to ImGuiPanelLayout(
                title = "Runtime Logs",
                x = 352f,
                y = 252f,
                width = 640f,
                height = 480f,
            ),
        ),
    )
}
