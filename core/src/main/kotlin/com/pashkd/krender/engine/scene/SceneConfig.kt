package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.viewport.RuntimeViewportConfig
import com.pashkd.krender.engine.viewport.UiScalePolicy
import com.pashkd.krender.engine.window.RuntimeWindowConfig
import com.pashkd.krender.engine.window.WindowMode
import com.pashkd.krender.engine.window.WindowResolution

/**
 * Scene-level runtime configuration applied when a scene becomes active.
 *
 * Default [SceneConfig] is intended for runtime and gameplay scenes. It uses a
 * 16:9 `1920x1080` physical window preference together with a `1920x1080`
 * logical runtime UI design resolution.
 *
 * Editor and tool scenes should explicitly use [SceneConfigPresets.EditorTool],
 * which keeps the same logical UI design resolution but requests a taller
 * `1920x1280` physical window for panel-heavy layouts.
 *
 * KRender intentionally allows scenes and tools to request different physical
 * window sizes. These values are preferences: the active backend may clamp,
 * ignore, or adapt them depending on platform capabilities.
 */
data class SceneConfig(
    val viewport: RuntimeViewportConfig = RuntimeViewportConfig(
        designWidth = 1920f,
        designHeight = 1080f,
        scalePolicy = UiScalePolicy.ScaleByHeight,
    ),
    val window: RuntimeWindowConfig = RuntimeWindowConfig(
        resolution = WindowResolution(width = 1920, height = 1080),
        mode = WindowMode.Windowed,
    ),
)
