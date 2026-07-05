package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import java.io.File
import kotlin.math.floor

/**
 * Reads an HDR-capable image file and exposes it as linear floating-point RGB data.
 *
 * The abstraction exists so Environment-generation workflows do not depend on a конкретний EXR/HDR
 * decoder implementation. A future implementation may read OpenEXR, Radiance HDR, or delegate to an
 * external conversion tool, while the rest of the editor code keeps working against this interface.
 */
interface HdrImageReader {
    /**
     * Decodes [path] into a linear HDR image.
     *
     * Implementations are expected to return RGB data in linear color space with three float
     * channels per pixel.
     */
    fun read(path: File): HdrImage
}

/**
 * In-memory HDR image stored as linear floating-point RGB pixels.
 *
 * @property width image width in pixels
 * @property height image height in pixels
 * @property pixels packed linear RGB pixel data in row-major order:
 * `r0, g0, b0, r1, g1, b1, ...`
 */
data class HdrImage(
    val width: Int,
    val height: Int,
    val pixels: FloatArray,
) {
    init {
        require(width > 0) { "HDR image width must be positive." }
        require(height > 0) { "HDR image height must be positive." }
        require(pixels.size == width * height * 3) {
            "HDR image pixel buffer must contain width * height * 3 float values."
        }
    }

    /**
     * Samples the HDR image as an equirectangular environment map.
     *
     * Horizontal coordinates wrap around so sampling can cross the left/right seam, while vertical
     * coordinates clamp to the top/bottom poles. The returned color is bilinearly filtered in
     * linear space.
     */
    fun sampleEquirectangular(
        u: Float,
        v: Float,
    ): Vec3 {
        // Equirectangular maps wrap horizontally, so negative and >1 values are folded back into
        // the canonical [0, 1) range instead of being clamped.
        val wrappedU = ((u % 1f) + 1f) % 1f
        // Vertical coordinates represent the poles, so they clamp instead of wrapping.
        val clampedV = v.coerceIn(0f, 1f)

        // Floating-point source pixel coordinate inside the HDR raster.
        val x = wrappedU * (width - 1)
        val y = clampedV * (height - 1)

        // Integer texel corners used for bilinear interpolation.
        val x0 = floor(x).toInt().coerceIn(0, width - 1)
        val y0 = floor(y).toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)

        // Fractional position between the integer texel corners.
        val tx = x - x0
        val ty = y - y0

        // Sample the four neighboring texels that bound the lookup point.
        val c00 = colorAt(x0, y0)
        val c10 = colorAt(x1, y0)
        val c01 = colorAt(x0, y1)
        val c11 = colorAt(x1, y1)

        // Interpolate first horizontally, then vertically.
        val top = lerp(c00, c10, tx)
        val bottom = lerp(c01, c11, tx)
        return lerp(top, bottom, ty)
    }

    /**
     * Reads one pixel from the packed RGB buffer.
     */
    private fun colorAt(
        x: Int,
        y: Int,
    ): Vec3 {
        // Packed RGB index for the requested pixel in row-major order.
        val index = (y * width + x) * 3
        return Vec3(
            pixels[index],
            pixels[index + 1],
            pixels[index + 2],
        )
    }

    /**
     * Linearly interpolates between two RGB colors.
     */
    private fun lerp(
        a: Vec3,
        b: Vec3,
        t: Float,
    ): Vec3 =
        Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t,
        )
}
