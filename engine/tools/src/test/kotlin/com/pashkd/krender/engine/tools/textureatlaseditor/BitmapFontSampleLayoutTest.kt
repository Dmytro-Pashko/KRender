package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class BitmapFontSampleLayoutTest {
    @Test
    fun `layout sample text reports tight bounds`() {
        val document =
            BitmapFontDocument(
                file = File("font.fnt"),
                common = BitmapFontCommon(lineHeight = 20, base = 16, scaleW = 128, scaleH = 64, pages = 1),
                glyphs =
                    listOf(
                        BitmapFontGlyph(id = 'A'.code, x = 0, y = 0, width = 8, height = 10, xOffset = -2, yOffset = 3, xAdvance = 9, page = 0, channel = 15),
                        BitmapFontGlyph(id = 'B'.code, x = 8, y = 0, width = 6, height = 12, xOffset = 1, yOffset = -1, xAdvance = 7, page = 0, channel = 15),
                    ),
            )

        val layout = layoutSampleText("AB", document)

        assertEquals(-2, layout.boundsMinX)
        assertEquals(-1, layout.boundsMinY)
        assertEquals(18, layout.boundsWidth)
        assertEquals(14, layout.boundsHeight)
        assertEquals(16, layout.totalWidth)
    }
}
