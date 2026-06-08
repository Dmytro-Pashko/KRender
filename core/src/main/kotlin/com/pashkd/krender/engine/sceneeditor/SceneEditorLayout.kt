package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout

/**
 * Stable panel ids used by the Scene Editor scene.
 */
object SceneEditorPanelIds {
    const val Toolbar = "sceneToolbar"
    const val Hierarchy = "sceneHierarchy"
    const val Assets = "sceneAssets"
    const val Inspector = "sceneInspector"
    const val Viewport = "sceneViewport"
    const val Logs = "runtimeLogs"
}

/**
 * Fallback layout for Scene Editor MVP panels.
 */
object SceneEditorUiLayoutDefaults {
    const val assetPath = "ui/scene_editor_layout.json"

    val config = ImGuiLayoutConfig(
        panels = mapOf(
            SceneEditorPanelIds.Toolbar to ImGuiPanelLayout(
                title = "Scene Toolbar",
                x = 16f,
                y = 16f,
                width = 1232f,
                height = 96f,
            ),
            SceneEditorPanelIds.Hierarchy to ImGuiPanelLayout(
                title = "Scene Hierarchy",
                x = 16f,
                y = 128f,
                width = 320f,
                height = 224f,
            ),
            SceneEditorPanelIds.Assets to ImGuiPanelLayout(
                title = "Assets",
                x = 16f,
                y = 368f,
                width = 320f,
                height = 240f,
            ),
            SceneEditorPanelIds.Viewport to ImGuiPanelLayout(
                title = "Scene Viewport",
                x = 352f,
                y = 128f,
                width = 560f,
                height = 480f,
            ),
            SceneEditorPanelIds.Inspector to ImGuiPanelLayout(
                title = "Scene Inspector",
                x = 928f,
                y = 128f,
                width = 320f,
                height = 480f,
            ),
            SceneEditorPanelIds.Logs to ImGuiPanelLayout(
                title = "Runtime Logs",
                x = 16f,
                y = 624f,
                width = 1232f,
                height = 240f,
            ),
        ),
    )
}
