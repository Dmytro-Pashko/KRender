package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitmapFontWriterTest {
    @Test
    fun `write preserves glyph page-local coordinates`() {
        val root = createTempDir(prefix = "krender-font-writer-test-")
        try {
            val document =
                BitmapFontDocument(
                    file = File(root, "source.fnt"),
                    common = BitmapFontCommon(lineHeight = 20, base = 16, scaleW = 128, scaleH = 64, pages = 1),
                    pages = listOf(BitmapFontPage(id = 0, file = "font.png")),
                    glyphs =
                        listOf(
                            BitmapFontGlyph(
                                id = 65,
                                char = "A",
                                x = 7,
                                y = 11,
                                width = 9,
                                height = 13,
                                xOffset = 1,
                                yOffset = 2,
                                xAdvance = 10,
                                page = 0,
                                channel = 15,
                            ),
                        ),
                )

            val result =
                BitmapFontWriter().write(
                    assetRoot = root,
                    targetPath = "exported.fnt",
                    document = document,
                    overwrite = true,
                )

            assertTrue(result.success, result.message)
            val text = File(root, "exported.fnt").readText()
            assertContains(text, "page id=0 file=\"font.png\"")
            assertContains(text, "char id=65 x=7 y=11 width=9 height=13 xoffset=1 yoffset=2 xadvance=10 page=0 chnl=15")
            assertFalse(text.contains("x=0 y=0 width=9 height=13"), "Glyph coordinates should not be remapped to atlas placement.")
        } finally {
            root.deleteRecursively()
        }
    }
}
