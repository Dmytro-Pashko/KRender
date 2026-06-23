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
                    pixels[index++] = pixmap.getPixel(x, y)
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
}
