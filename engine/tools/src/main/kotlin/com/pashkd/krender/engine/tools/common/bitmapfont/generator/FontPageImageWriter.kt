package com.pashkd.krender.engine.tools.common.bitmapfont.generator

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Writes a packed glyph page RGBA byte array to a PNG file.
 */
object FontPageImageWriter {
    fun writePng(
        rgba: ByteArray,
        width: Int,
        height: Int,
        outputFile: File,
    ): Boolean {
        if (width <= 0 || height <= 0 || rgba.size < width * height * 4) return false
        outputFile.parentFile?.mkdirs()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var srcIdx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = rgba[srcIdx].toInt() and 0xFF
                val g = rgba[srcIdx + 1].toInt() and 0xFF
                val b = rgba[srcIdx + 2].toInt() and 0xFF
                val a = rgba[srcIdx + 3].toInt() and 0xFF
                image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                srcIdx += 4
            }
        }
        return ImageIO.write(image, "png", outputFile)
    }
}
