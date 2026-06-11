package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.EngineLogService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssetRegistryVisibleOnlyTest {
    @Test
    fun `unsupported files are indexed as visible-only Other assets without krmeta`() {
        val baseDir = Files.createTempDirectory("krender-visible-only-test")
        val assetsDir = baseDir.resolve("assets").createDirectories()
        assetsDir.resolve("random.txt").writeText("notes", StandardCharsets.UTF_8)

        val snapshot = registry(baseDir.toFile()).scanSnapshot()
        val asset = snapshot.assets.singleOrNull { descriptor -> descriptor.path == "assets/random.txt" }

        assertNotNull(asset)
        assertEquals(AssetCategory.Other, asset.category)
        assertEquals(AssetType.Unknown, asset.type)
        assertEquals("visibleOnly", asset.metadata["indexPolicy"])
        assertFalse(assetsDir.resolve("random.txt.krmeta").toFile().exists())
    }

    @Test
    fun `supported ui scene assets remain managed and create krmeta`() {
        val baseDir = Files.createTempDirectory("krender-managed-ui-test")
        val sceneDir = baseDir.resolve("ui/scenes").createDirectories()
        sceneDir.resolve("main.krui").writeText(validUiSceneJson(), StandardCharsets.UTF_8)

        val snapshot = registry(baseDir.toFile()).scanSnapshot()
        val asset = snapshot.assets.singleOrNull { descriptor -> descriptor.path == "ui/scenes/main.krui" }

        assertNotNull(asset)
        assertEquals(AssetCategory.UI, asset.category)
        assertEquals(AssetType.UiScene, asset.type)
        assertTrue(asset.isManagedAsset())
        assertTrue(sceneDir.resolve("main.krui.krmeta").toFile().exists())
    }

    @Test
    fun `scene2d skin assets remain managed and create krmeta`() {
        val baseDir = Files.createTempDirectory("krender-managed-skin-test")
        val skinDir = baseDir.resolve("ui/skins").createDirectories()
        skinDir.resolve("craftacular-ui.json").writeText("""{"LabelStyle":{"default":{"font":"font"}}}""", StandardCharsets.UTF_8)

        val snapshot = registry(baseDir.toFile()).scanSnapshot()
        val asset = snapshot.assets.singleOrNull { descriptor -> descriptor.path == "ui/skins/craftacular-ui.json" }

        assertNotNull(asset)
        assertEquals(AssetCategory.UI, asset.category)
        assertEquals(AssetType.Scene2DSkin, asset.type)
        assertTrue(asset.isManagedAsset())
        assertEquals("ok", asset.metadata["skinStatus"])
        assertTrue(skinDir.resolve("craftacular-ui.json.krmeta").toFile().exists())
    }

    @Test
    fun `krmeta files are ignored as source assets`() {
        val baseDir = Files.createTempDirectory("krender-krmeta-ignore-test")
        val assetsDir = baseDir.resolve("assets").createDirectories()
        assetsDir.resolve("orphan.krmeta").writeText("{}", StandardCharsets.UTF_8)

        val snapshot = registry(baseDir.toFile()).scanSnapshot()

        assertTrue(snapshot.assets.none { descriptor -> descriptor.path.endsWith(".krmeta") })
    }

    @Test
    fun `temporary hidden and backup files are ignored`() {
        val baseDir = Files.createTempDirectory("krender-ignored-files-test")
        val assetsDir = baseDir.resolve("assets").createDirectories()
        assetsDir.resolve("draft.tmp").writeText("tmp", StandardCharsets.UTF_8)
        assetsDir.resolve("draft.temp").writeText("tmp", StandardCharsets.UTF_8)
        assetsDir.resolve("draft.bak").writeText("bak", StandardCharsets.UTF_8)
        assetsDir.resolve("draft.swp").writeText("swap", StandardCharsets.UTF_8)
        assetsDir.resolve("draft.txt~").writeText("backup", StandardCharsets.UTF_8)
        assetsDir.resolve("~\$lock.txt").writeText("lock", StandardCharsets.UTF_8)
        assetsDir.resolve(".hidden").writeText("hidden", StandardCharsets.UTF_8)

        val snapshot = registry(baseDir.toFile()).scanSnapshot()

        assertTrue(snapshot.assets.isEmpty())
    }

    @Test
    fun `json detection remains path specific`() {
        assertDetection("ui/skins/craftacular-ui.json", AssetType.Scene2DSkin, AssetCategory.UI)
        assertDetection("ui/scenes/main.krui", AssetType.UiScene, AssetCategory.UI)
        assertDetection("materials/foo.json", AssetType.Material, AssetCategory.Material)
        assertDetection("terrains/foo.json", AssetType.Terrain, AssetCategory.Terrain)
        assertDetection("scenes/foo.json", AssetType.Scene, AssetCategory.Scene)
        assertDetection("assets/foo.json", AssetType.Unknown, AssetCategory.Other)
    }

    private fun registry(baseDir: java.io.File): LocalAssetRegistryService {
        val logger = EngineLogService()
        return LocalAssetRegistryService(
            logger = logger,
            importers = AssetImporterRegistry.withDefaults(logger),
            baseDirectory = baseDir,
            rootPaths = listOf("assets", "ui/scenes", "ui/skins", "materials", "terrains", "scenes"),
        )
    }

    private fun assertDetection(path: String, type: AssetType, category: AssetCategory) {
        val detection = AssetTypeDetector.detect(path)

        assertEquals(type, detection.type)
        assertEquals(category, detection.category)
    }

    private fun validUiSceneJson(): String =
        """
        {
          "schemaVersion": 1,
          "id": "main",
          "skin": "ui/skins/craftacular-ui.json",
          "root": {
            "id": "root",
            "type": "Stack",
            "children": []
          }
        }
        """.trimIndent()
}
