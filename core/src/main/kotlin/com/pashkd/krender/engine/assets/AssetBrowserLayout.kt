package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout

/**
 * Stable panel ids used by the Asset Browser scene.
 */
object AssetBrowserPanelIds {
    const val Controls = "assetBrowserControls"
    const val Browser = "assetBrowser"
    const val Details = "assetDetails"
    const val Logs = "runtimeLogs"
}

/**
 * Fallback layout for the Asset Browser scene.
 */
object AssetBrowserUiLayoutDefaults {
    const val assetPath = "ui/asset_browser_layout.json"

    val config = ImGuiLayoutConfig(
        panels = mapOf(
            AssetBrowserPanelIds.Controls to ImGuiPanelLayout(
                title = "Asset Browser Controls",
                x = 792f,
                y = 16f,
                width = 420f,
                height = 152f,
            ),
            AssetBrowserPanelIds.Browser to ImGuiPanelLayout(
                title = "Asset Browser",
                x = 16f,
                y = 16f,
                width = 760f,
                height = 560f,
            ),
            AssetBrowserPanelIds.Details to ImGuiPanelLayout(
                title = "Asset Details",
                x = 792f,
                y = 184f,
                width = 420f,
                height = 392f,
            ),
            AssetBrowserPanelIds.Logs to ImGuiPanelLayout(
                title = "Runtime Logs",
                x = 16f,
                y = 592f,
                width = 1196f,
                height = 260f,
            ),
        ),
    )
}
