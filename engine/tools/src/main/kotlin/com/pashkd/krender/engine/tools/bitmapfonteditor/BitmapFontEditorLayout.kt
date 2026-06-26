package com.pashkd.krender.engine.tools.bitmapfonteditor

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

object BitmapFontEditorPanelIds {
    const val Toolbar = "bfe_toolbar"
    const val Preview = "bfe_preview"
    const val GlyphList = "bfe_glyph_list"
    const val Inspector = "bfe_inspector"
    const val Generation = "bfe_generation"
    const val Diagnostics = "bfe_diagnostics"
    const val Logs = "bfe_logs"
}

object BitmapFontEditorUiLayoutDefaults {
    const val assetPath = "ui/layouts/bitmap-font-editor.layout.json"

    val config =
        ImGuiLayoutConfig(
            panels =
                mapOf(
                    BitmapFontEditorPanelIds.Toolbar to ImGuiPanelLayout("Toolbar", 0f, 0f, 1755f, 28f),
                    BitmapFontEditorPanelIds.Preview to ImGuiPanelLayout("Font Page Preview", 0f, 28f, 1100f, 800f),
                    BitmapFontEditorPanelIds.GlyphList to ImGuiPanelLayout("Glyph List", 1100f, 28f, 330f, 500f),
                    BitmapFontEditorPanelIds.Inspector to ImGuiPanelLayout("Inspector", 1430f, 28f, 325f, 500f),
                    BitmapFontEditorPanelIds.Generation to ImGuiPanelLayout("Generation", 1100f, 528f, 655f, 300f),
                    BitmapFontEditorPanelIds.Diagnostics to ImGuiPanelLayout("Diagnostics", 0f, 828f, 1100f, 200f),
                    BitmapFontEditorPanelIds.Logs to ImGuiPanelLayout("Logs", 0f, 1028f, 1755f, 250f),
                ),
        )
}
