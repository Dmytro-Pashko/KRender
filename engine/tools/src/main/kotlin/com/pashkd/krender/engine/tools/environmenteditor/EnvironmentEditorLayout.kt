package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

/** Stable panel identifiers shared by fallback and persisted layouts. */
object EnvironmentEditorPanelIds {
    const val Control = "environment_editor_control"
    const val Inspector = "environment_editor_inspector"
    const val Settings = "environment_editor_settings"
    const val Sources = "environment_editor_sources"
    const val Tools = "environment_editor_generated_maps"
    const val Diagnostics = "environment_editor_diagnostics"
    const val Preview = "environment_editor_preview"
    const val SelectedResourcePreview = "environment_editor_selected_resource_preview"
    const val Logs = "environment_editor_logs"
}

/** Built-in layout used when no valid persisted Environment Editor layout exists. */
object EnvironmentEditorUiLayoutDefaults {
    const val assetPath = "ui/layouts/environment-editor.layout.json"

    val config =
        ImGuiLayoutConfig(
            panels =
                mapOf(
                    EnvironmentEditorPanelIds.Control to
                        ImGuiPanelLayout("Environment Editor Control Panel", 16f, 16f, 1580f, 128f),
                    EnvironmentEditorPanelIds.Preview to
                        ImGuiPanelLayout("Preview", 16f, 160f, 980f, 340f),
                    EnvironmentEditorPanelIds.SelectedResourcePreview to
                        ImGuiPanelLayout("Selected Resource Preview", 1012f, 160f, 776f, 340f),
                    EnvironmentEditorPanelIds.Settings to
                        ImGuiPanelLayout("Settings", 16f, 516f, 420f, 360f),
                    EnvironmentEditorPanelIds.Inspector to
                        ImGuiPanelLayout("Inspector", 452f, 516f, 420f, 360f),
                    EnvironmentEditorPanelIds.Sources to
                        ImGuiPanelLayout("Source Variants", 888f, 516f, 420f, 360f),
                    EnvironmentEditorPanelIds.Tools to
                        ImGuiPanelLayout("Resource Inspector", 1324f, 516f, 464f, 360f),
                    EnvironmentEditorPanelIds.Diagnostics to
                        ImGuiPanelLayout("Diagnostics", 16f, 892f, 900f, 160f),
                    EnvironmentEditorPanelIds.Logs to
                        ImGuiPanelLayout("Logs", 932f, 892f, 856f, 160f),
                ),
        )
}
