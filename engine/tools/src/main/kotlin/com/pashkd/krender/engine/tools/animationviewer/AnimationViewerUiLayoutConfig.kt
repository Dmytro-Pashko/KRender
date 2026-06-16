package com.pashkd.krender.engine.tools.animationviewer

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

/**
 * Defines the stable JSON ids used by the Animation Viewer ImGui panels.
 */
object AnimationViewerPanelIds {
    const val Toolbar = "toolbar"
    const val Viewport = "viewport"
    const val Animations = "animations"
    const val Skeleton = "skeleton"
    const val Playback = "playback"
    const val Loading = "loading"
    const val Logs = "runtimeLogs"
}

/**
 * Holds the asset path and fallback layouts for Animation Viewer panels.
 */
object AnimationViewerUiLayoutDefaults {
    const val assetPath = "ui/animation_viewer_layout.json"

    val config =
        ImGuiLayoutConfig(
            panels =
                mapOf(
                    AnimationViewerPanelIds.Toolbar to
                        ImGuiPanelLayout(
                            title = "Animation Viewer",
                            x = 16f,
                            y = 16f,
                            width = 980f,
                            height = 120f,
                        ),
                    AnimationViewerPanelIds.Viewport to
                        ImGuiPanelLayout(
                            title = "Viewport",
                            x = 16f,
                            y = 152f,
                            width = 420f,
                            height = 300f,
                        ),
                    AnimationViewerPanelIds.Animations to
                        ImGuiPanelLayout(
                            title = "Animations",
                            x = 452f,
                            y = 152f,
                            width = 360f,
                            height = 320f,
                        ),
                    AnimationViewerPanelIds.Playback to
                        ImGuiPanelLayout(
                            title = "Playback",
                            x = 828f,
                            y = 152f,
                            width = 360f,
                            height = 320f,
                        ),
                    AnimationViewerPanelIds.Skeleton to
                        ImGuiPanelLayout(
                            title = "Skeleton",
                            x = 452f,
                            y = 488f,
                            width = 360f,
                            height = 360f,
                        ),
                    AnimationViewerPanelIds.Loading to
                        ImGuiPanelLayout(
                            title = "Loading",
                            x = 720f,
                            y = 320f,
                            width = 320f,
                            height = 120f,
                        ),
                    AnimationViewerPanelIds.Logs to
                        ImGuiPanelLayout(
                            title = "Runtime Logs",
                            x = 828f,
                            y = 488f,
                            width = 360f,
                            height = 360f,
                        ),
                ),
        )
}
