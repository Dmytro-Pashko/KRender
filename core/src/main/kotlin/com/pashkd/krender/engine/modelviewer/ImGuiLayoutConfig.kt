package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

/**
 * Defines the stable JSON ids used by the ModelViewer ImGui panels.
 */
object ModelViewerPanelIds {
    const val Toolbar = "toolbar"
    const val Viewport = "viewport"
    const val ModelInfo = "modelInfo"
    const val MeshParts = "meshParts"
    const val Materials = "materials"
    const val TextureChannels = "textureChannels"
    const val Loading = "loading"
    const val Logs = "runtimeLogs"
}

/**
 * Holds the asset path and fallback layouts for ModelViewer panels.
 */
object ModelViewerUiLayoutDefaults {
    const val assetPath = "ui/model_viewer_layout.json"

    val config =
        ImGuiLayoutConfig(
            panels =
                mapOf(
                    ModelViewerPanelIds.Toolbar to
                        ImGuiPanelLayout(
                            title = "Model Viewer",
                            x = 16f,
                            y = 16f,
                            width = 960f,
                            height = 120f,
                        ),
                    ModelViewerPanelIds.Viewport to
                        ImGuiPanelLayout(
                            title = "Viewport",
                            x = 16f,
                            y = 152f,
                            width = 360f,
                            height = 260f,
                        ),
                    ModelViewerPanelIds.ModelInfo to
                        ImGuiPanelLayout(
                            title = "Model Info",
                            x = 392f,
                            y = 152f,
                            width = 360f,
                            height = 360f,
                        ),
                    ModelViewerPanelIds.MeshParts to
                        ImGuiPanelLayout(
                            title = "Mesh Parts",
                            x = 768f,
                            y = 152f,
                            width = 360f,
                            height = 360f,
                        ),
                    ModelViewerPanelIds.Materials to
                        ImGuiPanelLayout(
                            title = "Materials",
                            x = 1144f,
                            y = 152f,
                            width = 360f,
                            height = 360f,
                        ),
                    ModelViewerPanelIds.TextureChannels to
                        ImGuiPanelLayout(
                            title = "Texture Channels",
                            x = 1144f,
                            y = 528f,
                            width = 360f,
                            height = 240f,
                        ),
                    ModelViewerPanelIds.Loading to
                        ImGuiPanelLayout(
                            title = "Loading",
                            x = 720f,
                            y = 320f,
                            width = 320f,
                            height = 120f,
                        ),
                    ModelViewerPanelIds.Logs to
                        ImGuiPanelLayout(
                            title = "Runtime Logs",
                            x = 392f,
                            y = 528f,
                            width = 736f,
                            height = 240f,
                        ),
                ),
        )
}
