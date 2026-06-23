package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

object TextureManagerPanelIds {
    const val Toolbar = "textureManagerToolbar"
    const val Assets = "textureManagerAssets"
    const val Preview = "textureManagerPreview"
    const val Inspector = "textureManagerInspector"
    const val Regions = "textureManagerRegions"
    const val Tools = "textureManagerTools"
    const val Diagnostics = "textureManagerDiagnostics"
    const val Logs = "runtimeLogs"
}

object TextureManagerUiLayoutDefaults {
    const val assetPath = "ui/texture_manager_layout.json"

    val config =
        ImGuiLayoutConfig(
            panels =
                mapOf(
                    TextureManagerPanelIds.Toolbar to ImGuiPanelLayout("Texture Manager Toolbar", 16f, 16f, 1500f, 120f),
                    TextureManagerPanelIds.Assets to ImGuiPanelLayout("Assets", 16f, 152f, 380f, 640f),
                    TextureManagerPanelIds.Preview to ImGuiPanelLayout("Preview Canvas", 412f, 152f, 940f, 820f),
                    TextureManagerPanelIds.Inspector to ImGuiPanelLayout("Inspector", 1368f, 152f, 360f, 340f),
                    TextureManagerPanelIds.Regions to ImGuiPanelLayout("Atlas Regions", 1368f, 508f, 360f, 464f),
                    TextureManagerPanelIds.Tools to ImGuiPanelLayout("Tools", 1744f, 152f, 300f, 340f),
                    TextureManagerPanelIds.Diagnostics to ImGuiPanelLayout("Diagnostics", 1744f, 508f, 300f, 464f),
                    TextureManagerPanelIds.Logs to ImGuiPanelLayout("Runtime Logs", 16f, 808f, 380f, 164f),
                ),
        )
}

