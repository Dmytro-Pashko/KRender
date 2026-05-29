package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.viewport.RuntimeViewportConfig
import com.pashkd.krender.engine.window.RuntimeWindowConfig

/**
 * Scene-level runtime configuration applied by the engine during activation.
 *
 * [viewport] controls logical UI coordinates and screen-space layout math.
 * [window] controls the physical desktop window mode and size. Scenes only
 * declare these preferences; the engine backend decides how to realize them on
 * the current platform.
 */
data class SceneConfig(
    val viewport: RuntimeViewportConfig = RuntimeViewportConfig(),
    val window: RuntimeWindowConfig = RuntimeWindowConfig(),
)
