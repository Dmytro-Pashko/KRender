package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.api.EngineLogService
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetId
import com.pashkd.krender.engine.assets.AssetImporterRegistry
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.assets.AssetTypeDetector
import com.pashkd.krender.engine.assets.LocalAssetRegistryService
import com.pashkd.krender.engine.assets.Scene2DSkinAssetMetadataReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Scene2DSkinAssetIntegrationTest {
    @Test
    fun `asset type detector keeps json routes path specific`() {
        assertDetection("ui/skins/craftacular-ui.json", AssetType.Scene2DSkin, AssetCategory.UI)
        assertDetection("ui/scenes/main_menu.krui", AssetType.UiScene, AssetCategory.UI)
        assertDetection("materials/foo.json", AssetType.Material, AssetCategory.Material)
        assertDetection("terrains/foo.json", AssetType.Terrain, AssetCategory.Terrain)
        assertDetection("scenes/foo.json", AssetType.Scene, AssetCategory.Scene)
    }

    @Test
    fun `default importer registry contains scene2d skin importer`() {
        val registry = AssetImporterRegistry.withDefaults(EngineLogService())
        val importer = registry.all().firstOrNull { it.id == "scene2d-skin" }

        assertNotNull(importer)
        assertTrue(importer.canImport("ui/skins/craftacular-ui.json"))
        assertFalse(importer.canImport("materials/foo.json"))
        assertEquals(AssetType.Scene2DSkin, importer.outputType)
        assertEquals(AssetCategory.UI, importer.outputCategory)
    }

    @Test
    fun `registry indexes scene2d skin assets with metadata`() {
        val logger = EngineLogService()
        val baseDir = Files.createTempDirectory("krender-skin-asset-test")
        val skinDir = baseDir.resolve("ui/skins").createDirectories()
        skinDir.resolve("minimal.json").writeText(
            """
            {
              "LabelStyle": {
                "default": { "font": "font" }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val registry =
            LocalAssetRegistryService(
                logger = logger,
                importers = AssetImporterRegistry.withDefaults(logger),
                baseDirectory = baseDir.toFile(),
                rootPaths = listOf("ui/skins"),
            )

        val snapshot = registry.scanSnapshot()
        val asset = snapshot.assets.singleOrNull { descriptor -> descriptor.path == "ui/skins/minimal.json" }

        assertNotNull(asset)
        assertEquals(AssetType.Scene2DSkin, asset.type)
        assertEquals(AssetCategory.UI, asset.category)
        assertEquals("ok", asset.metadata["skinStatus"])
        assertEquals("1", asset.metadata["skinLabelStyleCount"])
        assertTrue(skinDir.resolve("minimal.json.krmeta").toFile().exists())
    }

    @Test
    fun `valid minimal skin json returns ok metadata and counts styles`() {
        val file = Files.createTempFile("scene2d-skin", ".json")
        file.writeText(
            """
            {
              "Color": {
                "white": { "r": 1, "g": 1, "b": 1, "a": 1 }
              },
              "LabelStyle": {
                "default": { "font": "font" },
                "title": { "font": "title" }
              },
              "TextButtonStyle": {
                "default": { "font": "font" }
              },
              "ProgressBarStyle": {
                "health": { "background": "bar" }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val metadata = Scene2DSkinAssetMetadataReader.read(file.toFile()).toMetadataMap()

        assertEquals("ok", metadata["skinStatus"])
        assertEquals("1", metadata["skinColorCount"])
        assertEquals("2", metadata["skinLabelStyleCount"])
        assertEquals("1", metadata["skinTextButtonStyleCount"])
        assertEquals("1", metadata["skinProgressBarStyleCount"])
        assertEquals("colors=1, drawables=0, labelStyles=2, textButtonStyles=1", metadata["skinPreview"])
    }

    @Test
    fun `fully qualified skin class names are counted`() {
        val file = Files.createTempFile("scene2d-skin-qualified", ".json")
        file.writeText(
            """
            {
              "com.badlogic.gdx.graphics.Color": {
                "white": { "r": 1, "g": 1, "b": 1, "a": 1 }
              },
              "com.badlogic.gdx.scenes.scene2d.ui.TextButton${'$'}TextButtonStyle": {
                "default": { "font": "default-font" },
                "toggle": { "font": "default-font" }
              },
              "com.badlogic.gdx.scenes.scene2d.ui.CheckBox${'$'}CheckBoxStyle": {
                "default": { "font": "default-font" }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val metadata = Scene2DSkinAssetMetadataReader.read(file.toFile()).toMetadataMap()

        assertEquals("ok", metadata["skinStatus"])
        assertEquals("1", metadata["skinColorCount"])
        assertEquals("2", metadata["skinTextButtonStyleCount"])
        assertEquals("1", metadata["skinCheckBoxStyleCount"])
    }

    @Test
    fun `invalid skin json returns parse error metadata`() {
        val file = Files.createTempFile("scene2d-skin-invalid", ".json")
        file.writeText("{", StandardCharsets.UTF_8)

        val metadata = Scene2DSkinAssetMetadataReader.read(file.toFile()).toMetadataMap()

        assertEquals("parse_error", metadata["skinStatus"])
        assertTrue(metadata["skinParseError"].orEmpty().isNotBlank())
    }

    @Test
    fun `skin metadata reader never throws`() {
        val file = Files.createTempFile("scene2d-skin-array", ".json")
        file.writeText("[]", StandardCharsets.UTF_8)

        val metadata = runCatching { Scene2DSkinAssetMetadataReader.read(file.toFile()).toMetadataMap() }

        assertTrue(metadata.isSuccess)
        assertEquals("parse_error", metadata.getOrThrow()["skinStatus"])
    }

    @Test
    fun `default ui scene skin selection prefers craftacular skin`() {
        val selected =
            defaultUiSceneSkinPath(
                listOf(
                    skinAsset("ui/skins/default_ui.json"),
                    skinAsset(DefaultUiSceneSkinPath),
                ),
            )

        assertEquals(DefaultUiSceneSkinPath, selected)
    }

    @Test
    fun `default ui scene skin selection falls back to first discovered skin`() {
        val selected =
            defaultUiSceneSkinPath(
                listOf(
                    skinAsset("ui/skins/arcade.json"),
                    skinAsset("ui/skins/default_ui.json"),
                ),
            )

        assertEquals("ui/skins/arcade.json", selected)
    }

    @Test
    fun `default ui scene skin selection falls back when no skins exist`() {
        assertEquals(DefaultUiSceneSkinPath, defaultUiSceneSkinPath(emptyList()))
    }

    @Test
    fun `create asset draft builds ui scene path and default params`() {
        val draft =
            CreateAssetDraft(
                kind = CreatableAssetKind.UiScene,
                name = "main menu",
                uiSceneSkinPath = "ui\\skins\\default_ui.json",
            )

        assertEquals("ui/scenes/main menu.krui", createAssetRelativePath(draft))
        assertEquals(
            listOf("Skin: ui/skins/default_ui.json", "Root: Stack", "Schema: 1"),
            createAssetDefaultParams(draft),
        )
    }

    @Test
    fun `create asset draft builds terrain path and default params`() {
        val draft = CreateAssetDraft(kind = CreatableAssetKind.Terrain, name = "sandbox")

        assertEquals("terrains/sandbox.json", createAssetRelativePath(draft))
        assertEquals(
            listOf("Size: 64 x 64", "Vertex spacing: 1.0", "Layers: 0"),
            createAssetDefaultParams(draft),
        )
    }

    @Test
    fun `create asset draft builds scene path and default params`() {
        val draft = CreateAssetDraft(kind = CreatableAssetKind.Scene, name = "level_01")

        assertEquals("scenes/level_01.krscene", createAssetRelativePath(draft))
        assertEquals(
            listOf("Schema: 1", "Entities: 0", "Settings: default"),
            createAssetDefaultParams(draft),
        )
    }

    private fun assertDetection(
        path: String,
        type: AssetType,
        category: AssetCategory,
    ) {
        val detection = AssetTypeDetector.detect(path)

        assertEquals(type, detection.type)
        assertEquals(category, detection.category)
    }

    private fun skinAsset(path: String): AssetDescriptor =
        AssetDescriptor(
            id = AssetId("asset:$path"),
            name = path.substringAfterLast('/').substringBeforeLast('.'),
            path = path,
            category = AssetCategory.UI,
            type = AssetType.Scene2DSkin,
            extension = "json",
            sizeBytes = 1L,
            modifiedAtMillis = 1L,
        )
}
