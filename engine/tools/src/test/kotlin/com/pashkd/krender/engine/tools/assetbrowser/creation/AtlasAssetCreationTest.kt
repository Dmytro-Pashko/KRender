package com.pashkd.krender.engine.tools.assetbrowser.creation

import com.pashkd.krender.engine.api.EngineLogService
import com.pashkd.krender.engine.assets.AssetOperationResult
import com.pashkd.krender.engine.tools.assetbrowser.CreatableAssetKind
import com.pashkd.krender.engine.tools.assetbrowser.CreateAssetDraft
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AtlasAssetCreationTest {
    @Test
    fun `create atlas asset writes descriptor and default page texture`() {
        val root = Files.createTempDirectory("krender-atlas-create-test").toFile()

        val result =
            createAtlasAsset(
                draft =
                    CreateAssetDraft(
                        kind = CreatableAssetKind.Atlas,
                        name = "skin-composer-ui",
                        atlasWidth = 512,
                        atlasHeight = 512,
                    ),
                assetRoot = root,
                logger = EngineLogService(),
            )

        val success = assertIs<AssetOperationResult.Success>(result)
        assertEquals("atlases/skin-composer-ui.atlas", success.path)

        val atlasFile = root.resolve("atlases/skin-composer-ui.atlas")
        val pageFile = root.resolve("atlases/skin-composer-ui.png")
        assertTrue(atlasFile.isFile)
        assertTrue(pageFile.isFile)

        val atlasText = atlasFile.readText()
        assertTrue(atlasText.contains("skin-composer-ui.png"))
        assertTrue(atlasText.contains("size: 512, 512"))
        assertTrue(atlasText.contains("format: RGBA8888"))
        assertTrue(atlasText.contains("filter: Linear, Linear"))
        assertTrue(atlasText.contains("repeat: none"))

        val image = ImageIO.read(pageFile)
        assertNotNull(image)
        assertEquals(512, image.width)
        assertEquals(512, image.height)
    }
}
