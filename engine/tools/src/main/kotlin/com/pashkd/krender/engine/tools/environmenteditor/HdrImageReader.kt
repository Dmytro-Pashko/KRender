package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import java.io.File
import kotlin.math.floor

interface HdrImageReader {
    fun read(path: File): HdrImage
}

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

    fun sampleEquirectangular(
        u: Float,
        v: Float,
    ): Vec3 {
        val wrappedU = ((u % 1f) + 1f) % 1f
        val clampedV = v.coerceIn(0f, 1f)
        val x = wrappedU * (width - 1)
        val y = clampedV * (height - 1)
        val x0 = floor(x).toInt().coerceIn(0, width - 1)
        val y0 = floor(y).toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = x - x0
        val ty = y - y0

        val c00 = colorAt(x0, y0)
        val c10 = colorAt(x1, y0)
        val c01 = colorAt(x0, y1)
        val c11 = colorAt(x1, y1)
        val top = lerp(c00, c10, tx)
        val bottom = lerp(c01, c11, tx)
        return lerp(top, bottom, ty)
    }

    private fun colorAt(
        x: Int,
        y: Int,
    ): Vec3 {
        val index = (y * width + x) * 3
        return Vec3(
            pixels[index],
            pixels[index + 1],
            pixels[index + 2],
        )
    }

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
