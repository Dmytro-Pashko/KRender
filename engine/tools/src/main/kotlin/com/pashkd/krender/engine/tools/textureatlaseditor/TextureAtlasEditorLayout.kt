package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

object TextureAtlasEditorPanelIds {
    const val Toolbar = "textureManagerToolbar"
    const val Preview = "textureManagerPreview"
    const val Inspector = "textureManagerInspector"
    const val Regions = "textureManagerRegions"
    const val Tools = "textureManagerTools"
    const val Diagnostics = "textureManagerDiagnostics"
    const val Logs = "runtimeLogs"
}

object TextureAtlasEditorUiLayoutDefaults {
    const val assetPath = "ui/texture_atlas_editor_layout.json"

    val config =
        ImGuiLayoutConfig(
            panels =
                mapOf(
                    TextureAtlasEditorPanelIds.Toolbar to ImGuiPanelLayout("Texture Atlas Editor Control", 16f, 16f, 1500f, 120f),
                    TextureAtlasEditorPanelIds.Preview to ImGuiPanelLayout("Preview Canvas", 412f, 152f, 940f, 620f),
                    TextureAtlasEditorPanelIds.Inspector to ImGuiPanelLayout("Inspector", 1368f, 152f, 360f, 340f),
                    TextureAtlasEditorPanelIds.Regions to ImGuiPanelLayout("Atlas Resources", 1368f, 508f, 360f, 464f),
                    TextureAtlasEditorPanelIds.Tools to ImGuiPanelLayout("Tools", 1744f, 152f, 300f, 340f),
                    TextureAtlasEditorPanelIds.Diagnostics to ImGuiPanelLayout("Diagnostics", 1744f, 508f, 300f, 464f),
                    TextureAtlasEditorPanelIds.Logs to ImGuiPanelLayout("Runtime Logs", 16f, 808f, 380f, 164f),
                ),
        )
}
