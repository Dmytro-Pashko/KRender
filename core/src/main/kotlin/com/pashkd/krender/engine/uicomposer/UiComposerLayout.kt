package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

/**
 * Stable panel ids used by the read-only UiComposer MVP.
 *
 * These ids belong to editor UI layout only. They do not affect the shared
 * `.krui` document, runtime UI layers, future editing data, saving, Skin
 * editing, drag/drop canvas editing, Asset Browser picking, asset-id references,
 * or Scene2D actor serialization.
 */
object UiComposerPanelIds {
    const val Toolbar = "uiComposerToolbar"
    const val Hierarchy = "uiComposerHierarchy"
    const val Inspector = "uiComposerInspector"
    const val PreviewPayload = "uiComposerPreviewPayload"
    const val Diagnostics = "uiComposerDiagnostics"
    const val Logs = "runtimeLogs"
}

/**
 * Fallback ImGui layout for the read-only UiComposer preview tool.
 *
 * This belongs to editor window layout. It intentionally keeps the Scene2D
 * preview fullscreen behind ImGui rather than embedding it into an ImGui child
 * viewport, and it does not add save/editing, Skin editing, drag/drop, Asset
 * Browser pickers, asset-id references, or full Scene2D actor serialization.
 */
object UiComposerUiLayoutDefaults {
    /** Project-relative path where UiComposer ImGui panel layout is loaded and persisted. */
    const val assetPath = "ui/ui_composer_layout.json"

    val config = ImGuiLayoutConfig(
        panels = mapOf(
            UiComposerPanelIds.Toolbar to ImGuiPanelLayout(
                title = "UI Composer Toolbar",
                x = 16f,
                y = 16f,
                width = 720f,
                height = 104f,
            ),
            UiComposerPanelIds.Hierarchy to ImGuiPanelLayout(
                title = "UI Scene Hierarchy",
                x = 16f,
                y = 136f,
                width = 320f,
                height = 472f,
            ),
            UiComposerPanelIds.Inspector to ImGuiPanelLayout(
                title = "UI Scene Inspector",
                x = 928f,
                y = 136f,
                width = 320f,
                height = 472f,
            ),
            UiComposerPanelIds.PreviewPayload to ImGuiPanelLayout(
                title = "UI Preview Payload",
                x = 928f,
                y = 624f,
                width = 320f,
                height = 240f,
            ),
            UiComposerPanelIds.Diagnostics to ImGuiPanelLayout(
                title = "UI Scene Diagnostics",
                x = 352f,
                y = 640f,
                width = 896f,
                height = 224f,
            ),
            UiComposerPanelIds.Logs to ImGuiPanelLayout(
                title = "Runtime Logs",
                x = 16f,
                y = 624f,
                width = 320f,
                height = 240f,
            ),
        ),
    )
}
