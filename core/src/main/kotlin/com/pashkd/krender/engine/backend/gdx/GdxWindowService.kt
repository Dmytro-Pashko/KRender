package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Application.ApplicationType
import com.badlogic.gdx.Gdx
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.window.RuntimeWindowConfig
import com.pashkd.krender.engine.window.WindowMode
import com.pashkd.krender.engine.window.WindowService
import com.pashkd.krender.engine.window.WindowState

/**
 * LibGDX-backed [WindowService] for desktop window management.
 *
 * On non-desktop platforms this service reports the current surface size but does
 * not attempt to change the application presentation mode.
 */
class GdxWindowService(
    private val logger: Logger,
) : WindowService {
    override val current: WindowState
        get() = readCurrentState()

    override fun apply(config: RuntimeWindowConfig): WindowState {
        val before = readCurrentState()
        val resolution = config.resolution.coerceAtLeast()
        if (Gdx.app.type == ApplicationType.Desktop) {
            when (config.mode) {
                WindowMode.Windowed -> Gdx.graphics.setWindowedMode(resolution.width, resolution.height)
                WindowMode.Fullscreen -> Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
            }
        }
        val after = readCurrentState(fallbackMode = config.mode)
        logger.info(TAG) {
            "Window apply requested=${config.mode} ${resolution.width}x${resolution.height} " +
                "before=${before.mode} ${before.pixelWidth}x${before.pixelHeight} " +
                "after=${after.mode} ${after.pixelWidth}x${after.pixelHeight}"
        }
        return after
    }

    private fun readCurrentState(fallbackMode: WindowMode? = null): WindowState =
        WindowState(
            pixelWidth = Gdx.graphics.width.coerceAtLeast(1),
            pixelHeight = Gdx.graphics.height.coerceAtLeast(1),
            mode = fallbackMode ?: inferMode(),
        )

    private fun inferMode(): WindowMode = if (Gdx.graphics.isFullscreen) WindowMode.Fullscreen else WindowMode.Windowed

    companion object {
        private const val TAG = "GdxWindowService"
    }
}
