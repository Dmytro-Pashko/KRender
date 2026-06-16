package com.pashkd.krender.engine.window

/**
 * Physical window presentation mode requested by a scene.
 *
 * [Windowed] uses the configured [RuntimeWindowConfig.resolution] as the window
 * client size. [Fullscreen] asks the backend to switch to a monitor-sized
 * fullscreen mode.
 */
enum class WindowMode {
    /** Creates or resizes a normal desktop window. */
    Windowed,

    /** Uses the active monitor's fullscreen display mode. */
    Fullscreen,
}

/**
 * Physical window size preference in pixels.
 *
 * Values are coerced to at least one pixel when applied.
 *
 * Runtime and gameplay scenes should normally inherit their resolution from
 * [com.pashkd.krender.engine.scene.SceneConfig] or
 * [com.pashkd.krender.engine.scene.SceneConfigPresets.RuntimeGame16By9].
 *
 * Editor and tool scenes should explicitly use
 * [com.pashkd.krender.engine.scene.SceneConfigPresets.EditorTool] when they
 * need the taller `1920x1280` presentation.
 */
data class WindowResolution(
    val width: Int = 1920,
    val height: Int = 1080,
) {
    /** Returns the same resolution with both dimensions clamped to positive values. */
    fun coerceAtLeast(minSize: Int = 1): WindowResolution =
        WindowResolution(
            width = width.coerceAtLeast(minSize),
            height = height.coerceAtLeast(minSize),
        )
}

/**
 * Scene-declared physical window preference.
 *
 * The engine owns application and backend lifecycles. Scenes only declare the
 * desired presentation mode here; they do not call window APIs directly.
 * Backends may clamp, ignore, or adapt these preferences on platforms where
 * exact desktop window management is unavailable.
 */
data class RuntimeWindowConfig(
    val resolution: WindowResolution = WindowResolution(),
    val mode: WindowMode = WindowMode.Windowed,
)

/**
 * Current physical window state reported by the backend.
 */
data class WindowState(
    val pixelWidth: Int = 1920,
    val pixelHeight: Int = 1080,
    val mode: WindowMode = WindowMode.Windowed,
) {
    /** Returns the state with a positive pixel size. */
    fun coerceAtLeast(minSize: Int = 1): WindowState =
        WindowState(
            pixelWidth = pixelWidth.coerceAtLeast(minSize),
            pixelHeight = pixelHeight.coerceAtLeast(minSize),
            mode = mode,
        )
}

/**
 * Backend-neutral facade for physical window management.
 *
 * Core runtime code uses this service to apply scene window preferences and to
 * read back the actual surface size after platform adjustments.
 */
interface WindowService {
    /** Most recently observed physical window state. */
    val current: WindowState

    /** Applies a scene window preference and returns the resulting window state. */
    fun apply(config: RuntimeWindowConfig): WindowState
}

/**
 * In-memory [WindowService] used by tests and backends without mutable desktop windows.
 */
open class InMemoryWindowService(
    initialState: WindowState = WindowState(),
) : WindowService {
    private var state: WindowState = initialState.coerceAtLeast()

    override val current: WindowState
        get() = state

    override fun apply(config: RuntimeWindowConfig): WindowState {
        val resolution = config.resolution.coerceAtLeast()
        state =
            WindowState(
                pixelWidth = resolution.width,
                pixelHeight = resolution.height,
                mode = config.mode,
            )
        return state
    }
}
