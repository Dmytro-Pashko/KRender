package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.EngineLogService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssetOperationsServiceTest {
    @Test
    fun `rename moves managed asset metadata and preserves asset id`() {
        val fixture = fixture()
        val textureDir = fixture.baseDir.resolve("textures").createDirectories()
        val source = textureDir.resolve("albedo.png")
        source.writeBytes(pngBytes())
        val original = fixture.scanAsset("textures/albedo.png")
        val originalMeta = fixture.metadataFor(textureDir.resolve("albedo.png.krmeta").readText(StandardCharsets.UTF_8))

        val result = fixture.service.rename(original, "albedo_renamed")

        val success = assertIs<AssetOperationResult.Success>(result)
        assertEquals("textures/albedo_renamed.png", success.path)
        assertTrue(fixture.changed)
        assertFalse(textureDir.resolve("albedo.png").toFile().exists())
        assertFalse(textureDir.resolve("albedo.png.krmeta").toFile().exists())
        assertTrue(textureDir.resolve("albedo_renamed.png").toFile().exists())
        assertTrue(textureDir.resolve("albedo_renamed.png.krmeta").toFile().exists())

        val renamedMeta = fixture.metadataFor(textureDir.resolve("albedo_renamed.png.krmeta").readText(StandardCharsets.UTF_8))
        assertEquals(originalMeta.id, renamedMeta.id)
        assertEquals("albedo_renamed", renamedMeta.displayName)

        fixture.rescan()
        assertNotNull(fixture.registry.findByPath("textures/albedo_renamed.png"))
    }

    @Test
    fun `duplicate creates a fresh metadata sidecar for the new managed asset`() {
        val fixture = fixture()
        val modelDir = fixture.baseDir.resolve("model").createDirectories()
        val source = modelDir.resolve("prop.glb")
        source.writeBytes(byteArrayOf(0x67, 0x6c, 0x54, 0x46))
        val original = fixture.scanAsset("model/prop.glb")
        val originalMeta = fixture.metadataFor(modelDir.resolve("prop.glb.krmeta").readText(StandardCharsets.UTF_8))

        val result = fixture.service.duplicate(original, "prop_copy")

        val success = assertIs<AssetOperationResult.Success>(result)
        assertEquals("model/prop_copy.glb", success.path)
        assertTrue(fixture.changed)
        assertTrue(modelDir.resolve("prop.glb").toFile().exists())
        assertTrue(modelDir.resolve("prop_copy.glb").toFile().exists())
        assertTrue(modelDir.resolve("prop_copy.glb.krmeta").toFile().exists())

        val duplicateMeta = fixture.metadataFor(modelDir.resolve("prop_copy.glb.krmeta").readText(StandardCharsets.UTF_8))
        assertNotEquals(originalMeta.id, duplicateMeta.id)
        assertEquals("prop_copy", duplicateMeta.displayName)
        assertEquals(originalMeta.type, duplicateMeta.type)
        assertEquals(originalMeta.category, duplicateMeta.category)
    }

    @Test
    fun `delete removes managed asset and sidecar`() {
        val fixture = fixture()
        val sceneDir = fixture.baseDir.resolve("ui/scenes").createDirectories()
        sceneDir.resolve("main.krui").writeText(validUiSceneJson(), StandardCharsets.UTF_8)
        val asset = fixture.scanAsset("ui/scenes/main.krui")

        val result = fixture.service.delete(asset)

        assertIs<AssetOperationResult.Success>(result)
        assertTrue(fixture.changed)
        assertFalse(sceneDir.resolve("main.krui").toFile().exists())
        assertFalse(sceneDir.resolve("main.krui.krmeta").toFile().exists())
    }

    @Test
    fun `visible only assets keep file operations disabled and do not create krmeta`() {
        val fixture = fixture()
        val assetsDir = fixture.baseDir.resolve("assets").createDirectories()
        assetsDir.resolve("notes.txt").writeText("notes", StandardCharsets.UTF_8)
        val asset = fixture.scanAsset("assets/notes.txt")

        val renameResult = fixture.service.rename(asset, "notes_renamed")
        val duplicateResult = fixture.service.duplicate(asset, "notes_copy")
        val deleteResult = fixture.service.delete(asset)

        assertIs<AssetOperationResult.Failure>(renameResult)
        assertIs<AssetOperationResult.Failure>(duplicateResult)
        assertIs<AssetOperationResult.Failure>(deleteResult)
        assertFalse(fixture.changed)
        assertTrue(assetsDir.resolve("notes.txt").toFile().exists())
        assertFalse(assetsDir.resolve("notes.txt.krmeta").toFile().exists())
    }

    @Test
    fun `scene2d skin delete removes only the direct skin folder and its dependencies`() {
        val fixture = fixture()
        val skinDir = fixture.baseDir.resolve("ui/skins/Cloud").createDirectories()
        skinDir.resolve("skin.json").writeText(validSkinJson(), StandardCharsets.UTF_8)
        skinDir.resolve("skin.png").writeBytes(pngBytes())
        skinDir.resolve("skin.atlas").writeText("atlas", StandardCharsets.UTF_8)
        skinDir.resolve("font.fnt").writeText("font", StandardCharsets.UTF_8)
        val asset = fixture.scanAsset("ui/skins/Cloud/skin.json")
        fixture.rescan()

        skinDir.resolve("skin.atlas.krmeta").writeText("{}", StandardCharsets.UTF_8)
        val siblingSkinDir = fixture.baseDir.resolve("ui/skins/Other").createDirectories()
        siblingSkinDir.resolve("other.json").writeText(validSkinJson(), StandardCharsets.UTF_8)

        val result = fixture.service.delete(asset)

        assertIs<AssetOperationResult.Success>(result)
        assertTrue(fixture.changed)
        assertFalse(skinDir.toFile().exists())
        assertTrue(siblingSkinDir.toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins").toFile().exists())
    }

    @Test
    fun `scene2d skin delete refuses nested folders outside direct ui skins child`() {
        val fixture = fixture()
        val skinDir = fixture.baseDir.resolve("ui/skins/packs/Cloud").createDirectories()
        skinDir.resolve("skin.json").writeText(validSkinJson(), StandardCharsets.UTF_8)
        val asset = AssetDescriptor(
            id = AssetId("asset:skin:nested"),
            name = "skin",
            path = "ui/skins/packs/Cloud/skin.json",
            category = AssetCategory.UI,
            type = AssetType.Scene2DSkin,
            extension = "json",
            sizeBytes = 1L,
            modifiedAtMillis = 1L,
        )

        val result = fixture.service.delete(asset)

        val failure = assertIs<AssetOperationResult.Failure>(result)
        assertTrue(failure.message.contains("direct folders under ui/skins"))
        assertFalse(fixture.changed)
        assertTrue(skinDir.toFile().exists())
    }

    @Test
    fun `delete refuses paths that escape the asset root`() {
        val fixture = fixture()
        val outside = fixture.baseDir.parent.resolve("outside.png")
        outside.writeBytes(pngBytes())
        val asset = AssetDescriptor(
            id = AssetId("asset:escape"),
            name = "outside",
            path = "../outside.png",
            category = AssetCategory.Texture,
            type = AssetType.Texture,
            extension = "png",
            sizeBytes = outside.toFile().length(),
            modifiedAtMillis = outside.toFile().lastModified(),
        )

        val result = fixture.service.delete(asset)

        val failure = assertIs<AssetOperationResult.Failure>(result)
        assertTrue(failure.message.contains("outside asset root"))
        assertFalse(fixture.changed)
        assertTrue(outside.toFile().exists())
    }

    private fun fixture(): OperationFixture {
        val logger = EngineLogService()
        val baseDir = Files.createTempDirectory("krender-asset-ops")
        val importers = AssetImporterRegistry.withDefaults(logger)
        val registry = LocalAssetRegistryService(
            logger = logger,
            importers = importers,
            baseDirectory = baseDir.toFile(),
            rootPaths = listOf("model", "textures", "ui/scenes", "ui/skins", "assets"),
        )
        val fixture = OperationFixture(
            baseDir = baseDir,
            registry = registry,
            changed = false,
        )
        fixture.serviceInstance = LocalAssetOperationsService(
            registry = registry,
            importers = importers,
            logger = logger,
            onChanged = { fixture.changed = true },
        )
        return fixture
    }

    private fun OperationFixture.scanAsset(path: String): AssetDescriptor {
        rescan()
        return registry.findByPath(path)
            ?: error("Expected asset '$path' to be indexed")
    }

    private fun OperationFixture.rescan() {
        registry.applySnapshot(registry.scanSnapshot())
    }

    private fun OperationFixture.metadataFor(text: String): AssetMetadataDocument =
        AssetMetadataCodec.decode(text)

    private fun validSkinJson(): String =
        """
        {
          "LabelStyle": {
            "default": { "font": "font" }
          }
        }
        """.trimIndent()

    private fun validUiSceneJson(): String =
        """
        {
          "schemaVersion": 1,
          "id": "main",
          "skin": "ui/skins/default_ui.json",
          "root": {
            "id": "root",
            "type": "Stack",
            "children": []
          }
        }
        """.trimIndent()

    private fun pngBytes(): ByteArray =
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lZ2eWQAAAABJRU5ErkJggg==",
        )

    private data class OperationFixture(
        val baseDir: java.nio.file.Path,
        val registry: LocalAssetRegistryService,
        var changed: Boolean,
        var serviceInstance: LocalAssetOperationsService? = null,
    ) {
        val service: LocalAssetOperationsService
            get() = serviceInstance ?: error("Service not initialized")
    }
}
