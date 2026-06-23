package com.pashkd.krender.engine.tools.texturemanager.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.pashkd.krender.engine.tools.texturemanager.NinePatchPixelData
import com.pashkd.krender.engine.tools.texturemanager.NinePatchPixelReader

class GdxNinePatchPixelReader : NinePatchPixelReader {
    override fun read(path: String): NinePatchPixelData {
        val pixmap = Pixmap(Gdx.files.absolute(path))
        try {
            val pixels = IntArray(pixmap.width * pixmap.height)
            var index = 0
            for (y in 0 until pixmap.height) {
                for (x in 0 until pixmap.width) {
                    pixels[index++] = rgba8888ToArgb(pixmap.getPixel(x, y))
                }
            }
            return NinePatchPixelData(
                width = pixmap.width,
                height = pixmap.height,
                pixels = pixels,
            )
        } finally {
            pixmap.dispose()
        }
    }

    private fun rgba8888ToArgb(pixel: Int): Int {
        val red = (pixel ushr 24) and 0xFF
        val green = (pixel ushr 16) and 0xFF
        val blue = (pixel ushr 8) and 0xFF
        val alpha = pixel and 0xFF
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
