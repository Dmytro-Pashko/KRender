package com.pashkd.krender.engine.tools.common.bitmapfont

import com.pashkd.krender.engine.tools.common.bitmapfont.io.BitmapFontParser
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontGlyph
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.glyphCompactLabel
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.glyphDisplayCharacter
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitmapFontUnicodeSupportTest {
    @Test
    fun `glyph display helpers preserve ukrainian codepoints`() {
        val glyph =
            BitmapFontGlyph(
                id = 'Ї'.code,
                x = 0,
                y = 0,
                width = 12,
                height = 16,
                xOffset = 0,
                yOffset = 0,
                xAdvance = 12,
                page = 0,
                channel = 15,
            )

        assertEquals("Ї", glyphDisplayCharacter(glyph))
        assertTrue(glyphCompactLabel(glyph).contains("'Ї'"))
    }

    @Test
    fun `parser derives ukrainian glyph labels from glyph id`() {
        val tempDir = createTempDirectory("bitmap-font-unicode-test").toFile()
        val textureFile = tempDir.resolve("page0.png").apply { writeBytes(byteArrayOf()) }
        val fontFile =
            tempDir.resolve("sample.fnt").apply {
                writeText(
                    """
                    info face="Roboto" size=24
                    common lineHeight=24 base=18 scaleW=64 scaleH=64 pages=1 packed=0
                    page id=0 file="page0.png"
                    chars count=1
                    char id=${'Є'.code} x=0 y=0 width=12 height=14 xoffset=0 yoffset=0 xadvance=12 page=0 chnl=15
                    """.trimIndent(),
                )
            }

        val document = BitmapFontParser().parse(fontFile)

        assertTrue(textureFile.isFile)
        assertEquals("Є", document.glyphs.single().char)
    }
}
