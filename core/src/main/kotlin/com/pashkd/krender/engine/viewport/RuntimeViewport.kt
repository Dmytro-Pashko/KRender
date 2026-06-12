package com.pashkd.krender.engine.viewport

import com.pashkd.krender.engine.api.Vec2

/**
 * Defines how runtime UI logical coordinates are scaled into the physical render target.
 *
 * Policies are selected per scene through `Scene.config.viewport` and are intended for
 * runtime UI layout math, hit testing, and screen-space render preparation. They do
 * not request or force a physical window size.
 */
enum class UiScalePolicy {
    /**
     * Scales UI by physical height while keeping [RuntimeViewport.designHeight] as
     * the logical height.
     *
     * Use this for gameplay HUDs and runtime overlays that should preserve vertical
     * proportions while allowing wider or narrower screens to expose more or less
     * horizontal logical space.
     */
    ScaleByHeight,

    /**
     * Scales the full design canvas so it is entirely visible inside the render target.
     *
     * Use this for full-screen UI that must not crop authored edges, such as menus,
     * settings screens, dialogs, and fixed-canvas layouts. It may create positive
     * [RuntimeViewport.offsetX] or [RuntimeViewport.offsetY] values for letterboxing.
     */
    Fit,

    /**
     * Scales the design canvas until it covers the whole render target.
     *
     * Use this for image-backed or composition-heavy screens where filling the window
     * is more important than preserving every authored edge. It may create negative
     * [RuntimeViewport.offsetX] or [RuntimeViewport.offsetY] values when content is cropped.
     */
    Fill,

    /**
     * Uses one logical unit per physical pixel.
     *
     * Use this for pixel-accurate debug overlays, editor-like runtime diagnostics, or
     * bitmap UI that should not scale with the design resolution.
     */
    PixelPerfect,
}

/**
 * Scene-provided runtime viewport preferences.
 *
 * @property designWidth authored logical width of the UI canvas. Values below 1 are
 * coerced to 1 during calculation.
 * @property designHeight authored logical height of the UI canvas. Values below 1 are
 * coerced to 1 during calculation.
 * @property scalePolicy strategy used to convert logical UI units into screen pixels.
 */
data class RuntimeViewportConfig(
    val designWidth: Float = 1920f,
    val designHeight: Float = 1080f,
    val scalePolicy: UiScalePolicy = UiScalePolicy.ScaleByHeight,
)

/**
 * Calculated runtime viewport for UI and screen-space systems.
 *
 * [pixelWidth] and [pixelHeight] describe the actual render target supplied by the
 * backend. [logicalWidth] and [logicalHeight] describe the coordinate space used by
 * runtime UI. [scale] converts logical units to physical pixels, while [offsetX] and
 * [offsetY] describe the physical-pixel origin shift used by policies such as
 * [UiScalePolicy.Fit] and [UiScalePolicy.Fill].
 *
 * @property pixelWidth physical render target width in pixels, coerced to at least 1.
 * @property pixelHeight physical render target height in pixels, coerced to at least 1.
 * @property designWidth configured authored design width, coerced to at least 1.
 * @property designHeight configured authored design height, coerced to at least 1.
 * @property logicalWidth calculated UI canvas width in logical units.
 * @property logicalHeight calculated UI canvas height in logical units.
 * @property scale multiplier from logical units to physical pixels.
 * @property offsetX horizontal physical-pixel offset applied before rendering or hit testing.
 * @property offsetY vertical physical-pixel offset applied before rendering or hit testing.
 */
data class RuntimeViewport(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val designWidth: Float,
    val designHeight: Float,
    val logicalWidth: Float,
    val logicalHeight: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    /** Physical render target aspect ratio. */
    val aspectRatio: Float
        get() = pixelWidth.toFloat() / pixelHeight.toFloat()

    /** Logical UI canvas aspect ratio after the selected scale policy is applied. */
    val logicalAspectRatio: Float
        get() = logicalWidth / logicalHeight
}

/**
 * Calculates a [RuntimeViewport] from a physical render target size and scene policy.
 *
 * This function is pure and does not touch backend window APIs. Invalid physical sizes
 * and invalid design sizes are coerced to positive values, and the resulting [scale]
 * is always greater than zero.
 */
fun calculateRuntimeViewport(
    pixelWidth: Int,
    pixelHeight: Int,
    config: RuntimeViewportConfig = RuntimeViewportConfig(),
): RuntimeViewport {
    val width = pixelWidth.coerceAtLeast(1)
    val height = pixelHeight.coerceAtLeast(1)
    val designWidth = config.designWidth.coerceAtLeast(1f)
    val designHeight = config.designHeight.coerceAtLeast(1f)

    val scaleX = width.toFloat() / designWidth
    val scaleY = height.toFloat() / designHeight
    val scale =
        when (config.scalePolicy) {
            UiScalePolicy.ScaleByHeight -> scaleY
            UiScalePolicy.Fit -> minOf(scaleX, scaleY)
            UiScalePolicy.Fill -> maxOf(scaleX, scaleY)
            UiScalePolicy.PixelPerfect -> 1f
        }.coerceAtLeast(MinScale)

    val logicalWidth: Float
    val logicalHeight: Float
    val offsetX: Float
    val offsetY: Float

    when (config.scalePolicy) {
        UiScalePolicy.ScaleByHeight -> {
            logicalWidth = width.toFloat() / scale
            logicalHeight = designHeight
            offsetX = 0f
            offsetY = 0f
        }

        UiScalePolicy.Fit,
        UiScalePolicy.Fill,
            -> {
            logicalWidth = designWidth
            logicalHeight = designHeight
            val scaledWidth = designWidth * scale
            val scaledHeight = designHeight * scale
            offsetX = (width.toFloat() - scaledWidth) * 0.5f
            offsetY = (height.toFloat() - scaledHeight) * 0.5f
        }

        UiScalePolicy.PixelPerfect -> {
            logicalWidth = width.toFloat()
            logicalHeight = height.toFloat()
            offsetX = 0f
            offsetY = 0f
        }
    }

    return RuntimeViewport(
        pixelWidth = width,
        pixelHeight = height,
        designWidth = designWidth,
        designHeight = designHeight,
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
    )
}

/** Converts a physical screen-space point into this viewport's logical UI space. */
fun RuntimeViewport.screenToLogical(screen: Vec2): Vec2 =
    Vec2(
        x = (screen.x - offsetX) / scale,
        y = (screen.y - offsetY) / scale,
    )

/** Converts a logical UI-space point into physical screen-space pixels. */
fun RuntimeViewport.logicalToScreen(logical: Vec2): Vec2 =
    Vec2(
        x = logical.x * scale + offsetX,
        y = logical.y * scale + offsetY,
    )

private const val MinScale = 0.0001f
