package com.pashkd.krender.engine.tools.textureatlaseditor

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextureAtlasRegionExportServiceTest {
    @Test
    fun `export image resource writes cropped png into atlas export directory`() {
        val root = createTempDir(prefix = "krender-atlas-export-test-")
        try {
            val atlasFile =
                File(root, "atlases/ui.atlas").apply {
                    parentFile.mkdirs()
                    writeText("ui.png\n")
                }
            val textureFile = File(root, "atlases/ui.png")
            val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(2, 3, 0xFF12AB34.toInt())
            ImageIO.write(image, "png", textureFile)

            val result =
                TextureAtlasRegionExportService().exportResource(
                    assetRoot = root,
                    atlasFile = atlasFile,
                    resource =
                        ImageAtlasResource(
                            id = "button",
                            name = "button",
                            sourcePath = normalizePath(textureFile.path),
                            sourceX = 2,
                            sourceY = 3,
                            sourceWidth = 1,
                            sourceHeight = 1,
                        ),
                )

            assertTrue(result.success, result.message)
            val exported = File(root, "atlases/export/button.png")
            assertTrue(exported.isFile)
            val exportedImage = ImageIO.read(exported)
            assertEquals(1, exportedImage.width)
            assertEquals(1, exportedImage.height)
            assertEquals(0xFF12AB34.toInt(), exportedImage.getRGB(0, 0))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `export nine patch resource reconstructs 9png guides`() {
        val root = createTempDir(prefix = "krender-atlas-ninepatch-export-test-")
        try {
            val atlasFile =
                File(root, "atlases/ui.atlas").apply {
                    parentFile.mkdirs()
                    writeText("ui.png\n")
                }
            val textureFile = File(root, "atlases/ui.png")
            val image = BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(1, 1, 0xFFFF0000.toInt())
            ImageIO.write(image, "png", textureFile)

            val result =
                TextureAtlasRegionExportService().exportResource(
                    assetRoot = root,
                    atlasFile = atlasFile,
                    resource =
                        NinePatchAtlasResource(
                            id = "panel",
                            name = "panel",
                            sourcePath = normalizePath(textureFile.path),
                            sourceX = 1,
                            sourceY = 1,
                            sourceWidth = 4,
                            sourceHeight = 4,
                            split = listOf(1, 1, 1, 1),
                            pad = listOf(0, 0, 0, 0),
                        ),
                )

            assertTrue(result.success, result.message)
            val exported = File(root, "atlases/export/panel.9.png")
            assertTrue(exported.isFile)
            val exportedImage = ImageIO.read(exported)
            assertEquals(6, exportedImage.width)
            assertEquals(6, exportedImage.height)
            assertEquals(0xFF000000.toInt(), exportedImage.getRGB(2, 0))
            assertEquals(0xFF000000.toInt(), exportedImage.getRGB(0, 2))
            assertEquals(0xFF000000.toInt(), exportedImage.getRGB(1, 5))
            assertEquals(0xFFFF0000.toInt(), exportedImage.getRGB(1, 1))
        } finally {
            root.deleteRecursively()
        }
    }
}
