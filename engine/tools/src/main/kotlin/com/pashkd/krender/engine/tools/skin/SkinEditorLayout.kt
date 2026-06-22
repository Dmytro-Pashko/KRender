package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

object SkinEditorPanelIds {
    const val Toolbar = "skinEditorToolbar"
    const val PreviewCanvas = "skinEditorPreviewCanvas"
    const val StyleTree = "skinEditorStyleTree"
    const val ResourceBrowser = "skinEditorResourceBrowser"
    const val Problems = "skinEditorProblems"
    const val Inspector = "skinEditorInspector"
    const val PreviewControls = "skinEditorPreviewControls"
    const val ResourcePreview = "skinEditorResourcePreview"
    const val Logs = "runtimeLogs"
}

object SkinEditorUiLayoutDefaults {
    const val assetPath = "ui/skin_editor_layout.json"

    val config =
        ImGuiLayoutConfig(
            panels =
                mapOf(
                    SkinEditorPanelIds.Toolbar to
                        ImGuiPanelLayout(
                            title = "Skin Editor Toolbar",
                            x = 16f,
                            y = 16f,
                            width = 1180f,
                            height = 104f,
                        ),
                    SkinEditorPanelIds.StyleTree to
                        ImGuiPanelLayout(
                            title = "Styles",
                            x = 16f,
                            y = 136f,
                            width = 320f,
                            height = 300f,
                        ),
                    SkinEditorPanelIds.ResourceBrowser to
                        ImGuiPanelLayout(
                            title = "Resources",
                            x = 16f,
                            y = 452f,
                            width = 320f,
                            height = 220f,
                        ),
                    SkinEditorPanelIds.Problems to
                        ImGuiPanelLayout(
                            title = "Problems",
                            x = 16f,
                            y = 688f,
                            width = 320f,
                            height = 176f,
                        ),
                    SkinEditorPanelIds.PreviewCanvas to
                        ImGuiPanelLayout(
                            title = "Preview Canvas",
                            x = 352f,
                            y = 136f,
                            width = 640f,
                            height = 728f,
                        ),
                    SkinEditorPanelIds.Inspector to
                        ImGuiPanelLayout(
                            title = "Inspector",
                            x = 1008f,
                            y = 136f,
                            width = 320f,
                            height = 456f,
                        ),
                    SkinEditorPanelIds.PreviewControls to
                        ImGuiPanelLayout(
                            title = "Preview Controls",
                            x = 1008f,
                            y = 608f,
                            width = 320f,
                            height = 256f,
                        ),
                    SkinEditorPanelIds.ResourcePreview to
                        ImGuiPanelLayout(
                            title = "Resource Preview",
                            x = 1344f,
                            y = 472f,
                            width = 320f,
                            height = 392f,
                        ),
                    SkinEditorPanelIds.Logs to
                        ImGuiPanelLayout(
                            title = "Runtime Logs",
                            x = 1344f,
                            y = 136f,
                            width = 320f,
                            height = 320f,
                        ),
                ),
        )
}
