package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

/**
 * Stable panel ids used by the UiComposer MVP.
 *
 * These ids belong to editor UI layout and structure editing panel placement
 * only. They do not affect the shared `.krui` document, runtime UI layers,
 * saving, Skin editing, drag/drop canvas editing, Asset Browser drag/drop,
 * asset-id references, generic layout solving, or Scene2D actor serialization.
 */
object UiComposerPanelIds {
    const val Toolbar = "uiComposerToolbar"
    const val PreviewCanvas = "uiComposerPreviewCanvas"
    const val Hierarchy = "uiComposerHierarchy"
    /**
     * Panel id for hierarchy-driven structure editing controls.
     *
     * This belongs to editor structure editing layout. It does not add canvas
     * drag/drop, canvas selection, Skin editing, Asset Browser drag/drop,
     * asset-id references, layout solving, saving behavior, or actor
     * serialization.
     */
    const val Structure = "uiComposerStructure"
    const val Inspector = "uiComposerInspector"
    const val SceneBindings = "uiComposerSceneBindings"
    const val Diagnostics = "uiComposerDiagnostics"
    const val Logs = "runtimeLogs"
}

/**
 * Fallback ImGui layout for the UiComposer preview and structure editing tool.
 *
 * This belongs to editor window layout. It intentionally keeps the Scene2D
 * preview in a dedicated Preview Canvas panel and does not add Skin editing,
 * drag/drop, texture import/copy, Asset Browser drag/drop, asset-id references,
 * generic layout solving, or full Scene2D actor serialization.
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
            UiComposerPanelIds.PreviewCanvas to ImGuiPanelLayout(
                title = "Preview Canvas",
                x = 360f,
                y = 72f,
                width = 960f,
                height = 720f,
            ),
            UiComposerPanelIds.Hierarchy to ImGuiPanelLayout(
                title = "UI Scene Hierarchy",
                x = 16f,
                y = 136f,
                width = 320f,
                height = 352f,
            ),
            UiComposerPanelIds.Structure to ImGuiPanelLayout(
                title = "UI Structure",
                x = 16f,
                y = 504f,
                width = 320f,
                height = 240f,
            ),
            UiComposerPanelIds.Inspector to ImGuiPanelLayout(
                title = "UI Scene Inspector",
                x = 928f,
                y = 136f,
                width = 320f,
                height = 472f,
            ),
            UiComposerPanelIds.SceneBindings to ImGuiPanelLayout(
                title = "UI Scene Bindings",
                x = 928f,
                y = 624f,
                width = 320f,
                height = 240f,
            ),
            UiComposerPanelIds.Diagnostics to ImGuiPanelLayout(
                title = "UI Scene info",
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
