package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.viewport.RuntimeViewportConfig
import com.pashkd.krender.engine.viewport.UiScalePolicy
import com.pashkd.krender.engine.window.RuntimeWindowConfig
import com.pashkd.krender.engine.window.WindowMode
import com.pashkd.krender.engine.window.WindowResolution

/**
 * Shared scene configuration presets for common KRender scene categories.
 *
 * Runtime scenes default to a `1920x1080` 16:9 presentation. Editor and tool
 * scenes intentionally request a taller `1920x1280` physical window because
 * they host panel-heavy layouts.
 */
object SceneConfigPresets {
    /** Default runtime and gameplay presentation preset. */
    val RuntimeGame16By9 =
        SceneConfig(
            viewport =
                RuntimeViewportConfig(
                    designWidth = 1920f,
                    designHeight = 1080f,
                    scalePolicy = UiScalePolicy.ScaleByHeight,
                ),
            window =
                RuntimeWindowConfig(
                    resolution = WindowResolution(width = 1920, height = 1080),
                    mode = WindowMode.Windowed,
                ),
        )

    /** Shared editor and viewer preset with extra vertical space for panels. */
    val EditorTool =
        SceneConfig(
            viewport =
                RuntimeViewportConfig(
                    designWidth = 1920f,
                    designHeight = 1080f,
                    scalePolicy = UiScalePolicy.ScaleByHeight,
                ),
            window =
                RuntimeWindowConfig(
                    resolution = WindowResolution(width = 1920, height = 1280),
                    mode = WindowMode.Windowed,
                ),
        )

    /** Current Asset Browser preset. Kept separate for future divergence. */
    val AssetBrowser =
        SceneConfig(
            viewport =
                RuntimeViewportConfig(
                    designWidth = 1920f,
                    designHeight = 1080f,
                    scalePolicy = UiScalePolicy.ScaleByHeight,
                ),
            window =
                RuntimeWindowConfig(
                    resolution = WindowResolution(width = 1800, height = 1100),
                    mode = WindowMode.Windowed,
                ),
        )

    /** Current Asset Browser preset. Kept separate for future divergence. */
    val UiComposer =
        SceneConfig(
            viewport =
                RuntimeViewportConfig(
                    designWidth = 1920f,
                    designHeight = 1080f,
                    scalePolicy = UiScalePolicy.ScaleByHeight,
                ),
            window =
                RuntimeWindowConfig(
                    resolution = WindowResolution(width = 2138, height = 1335),
                    mode = WindowMode.Windowed,
                ),
        )
    /** Current Skin Editor preset. Kept separate for future divergence. */
    val SkinEditor =
        SceneConfig(
            viewport =
                RuntimeViewportConfig(
                    designWidth = 1920f,
                    designHeight = 1080f,
                    scalePolicy = UiScalePolicy.ScaleByHeight,
                ),
            window =
                RuntimeWindowConfig(
                    resolution = WindowResolution(width = 2260, height = 1350),
                    mode = WindowMode.Windowed,
                ),
        )
}
