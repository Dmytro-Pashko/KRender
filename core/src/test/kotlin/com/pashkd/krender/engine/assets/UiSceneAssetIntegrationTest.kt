package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.EngineLogService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiSceneAssetIntegrationTest {
    @Test
    fun `krui extension is detected as UiScene asset`() {
        val detection = AssetTypeDetector.detect("ui/scenes/woolboy_hud.krui")

        assertEquals(AssetType.UiScene, detection.type)
        assertEquals(AssetCategory.UI, detection.category)
    }

    @Test
    fun `ui scene importer extracts document id skin and schema metadata`() {
        val file = Files.createTempFile("test-ui-scene", ".krui")
        file.writeText(validUiSceneJson("woolboy_hud"), StandardCharsets.UTF_8)

        val metadata = UiSceneImporter().readMetadata(file.toFile())

        assertEquals("valid", metadata["uiSceneStatus"])
        assertEquals("woolboy_hud", metadata["uiSceneDocumentId"])
        assertEquals("ui/skins/craftacular-ui.json", metadata["uiSceneSkinPath"])
        assertEquals("1", metadata["uiSceneSchemaVersion"])
        assertEquals("0", metadata["uiSceneValidationWarningCount"])
    }

    @Test
    fun `invalid krui remains indexable with warning metadata`() {
        val logger = EngineLogService()
        val baseDir = Files.createTempDirectory("krender-ui-asset-test")
        val sceneDir = baseDir.resolve("ui/scenes").createDirectories()
        sceneDir.resolve("broken.krui").writeText("{", StandardCharsets.UTF_8)

        val registry =
            LocalAssetRegistryService(
                logger = logger,
                importers = AssetImporterRegistry.withDefaults(logger),
                baseDirectory = baseDir.toFile(),
                rootPaths = listOf("ui"),
            )

        val snapshot = registry.scanSnapshot()
        val asset = snapshot.assets.singleOrNull { descriptor -> descriptor.path == "ui/scenes/broken.krui" }

        assertNotNull(asset)
        assertEquals(AssetType.UiScene, asset.type)
        assertEquals(AssetCategory.UI, asset.category)
        assertEquals("invalid", asset.metadata["uiSceneStatus"])
        assertTrue(asset.metadata["uiSceneParseError"].orEmpty().isNotBlank())
        assertTrue(sceneDir.resolve("broken.krui.krmeta").toFile().exists())
    }

    private fun validUiSceneJson(id: String): String =
        """
        {
          "schemaVersion": 1,
          "id": "$id",
          "skin": "ui/skins/craftacular-ui.json",
          "root": {
            "id": "root",
            "type": "Stack",
            "children": []
          }
        }
        """.trimIndent()
}
