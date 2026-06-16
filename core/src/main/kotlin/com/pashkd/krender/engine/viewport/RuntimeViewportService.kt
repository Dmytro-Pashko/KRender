package com.pashkd.krender.engine.viewport

/**
 * Stores and recalculates the active runtime UI viewport.
 *
 * The service is intentionally small: it owns the latest [RuntimeViewportConfig] and
 * the latest calculated [RuntimeViewport], and recalculates them when the backend
 * reports a resize. It does not emit events or mutate the physical window; callers are
 * responsible for invoking [resize] from the engine resize flow.
 */
class RuntimeViewportService(
    initialPixelWidth: Int = 1280,
    initialPixelHeight: Int = 720,
    initialConfig: RuntimeViewportConfig = RuntimeViewportConfig(),
) {
    /** Last config used to calculate [current]. */
    var config: RuntimeViewportConfig = initialConfig
        private set

    /** Latest calculated runtime viewport. */
    var current: RuntimeViewport =
        calculateRuntimeViewport(
            initialPixelWidth,
            initialPixelHeight,
            initialConfig,
        )
        private set

    /**
     * Recalculates [current] for a new physical render target size.
     *
     * [config] defaults to the previously stored config so callers may propagate
     * pure size changes without repeating scene policy.
     */
    fun resize(
        pixelWidth: Int,
        pixelHeight: Int,
        config: RuntimeViewportConfig = this.config,
    ): RuntimeViewport {
        this.config = config
        current = calculateRuntimeViewport(pixelWidth, pixelHeight, config)
        return current
    }
}
