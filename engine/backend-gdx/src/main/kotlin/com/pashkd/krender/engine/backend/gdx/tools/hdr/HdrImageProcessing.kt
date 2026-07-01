package com.pashkd.krender.engine.backend.gdx.tools.hdr

import java.awt.RenderingHints
import java.awt.image.BufferedImage

internal object HdrImageProcessing {
    fun resize(
        source: BufferedImage,
        width: Int,
        height: Int,
    ): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { target ->
            val graphics = target.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                graphics.drawImage(source, 0, 0, width, height, null)
            } finally {
                graphics.dispose()
            }
        }

    fun repeatedBoxBlur(
        source: BufferedImage,
        radius: Int,
        passes: Int,
    ): BufferedImage {
        var current = source
        repeat(passes) {
            val next = boxBlur(current, radius)
            if (current !== source) current.flush()
            current = next
        }
        return current
    }

    private fun boxBlur(
        source: BufferedImage,
        radius: Int,
    ): BufferedImage {
        if (radius <= 0) return resize(source, source.width, source.height)
        val width = source.width
        val height = source.height
        val input = source.getRGB(0, 0, width, height, null, 0, width)
        val horizontal = IntArray(input.size)
        val output = IntArray(input.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                horizontal[y * width + x] = averagePixels(input, width, height, x, y, radius, horizontalPass = true)
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                output[y * width + x] = averagePixels(horizontal, width, height, x, y, radius, horizontalPass = false)
            }
        }
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
            it.setRGB(0, 0, width, height, output, 0, width)
        }
    }

    private fun averagePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Int,
        horizontalPass: Boolean,
    ): Int {
        var alpha = 0L
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        for (offset in -radius..radius) {
            val sampleX = if (horizontalPass) (x + offset).coerceIn(0, width - 1) else x
            val sampleY = if (horizontalPass) y else (y + offset).coerceIn(0, height - 1)
            val color = pixels[sampleY * width + sampleX]
            alpha += (color ushr 24).toLong() and CHANNEL_MASK
            red += (color ushr 16).toLong() and CHANNEL_MASK
            green += (color ushr 8).toLong() and CHANNEL_MASK
            blue += color.toLong() and CHANNEL_MASK
            count++
        }
        return ((alpha / count).toInt() shl 24) or
            ((red / count).toInt() shl 16) or
            ((green / count).toInt() shl 8) or
            (blue / count).toInt()
    }

    private const val CHANNEL_MASK = 0xffL
}
