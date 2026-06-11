package com.pashkd.krender.engine.assets.importing

import com.pashkd.krender.engine.api.EngineLogService
import com.pashkd.krender.engine.assets.AssetBrowserState
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetImporterRegistry
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.assets.LocalAssetRegistryService
import com.pashkd.krender.engine.assets.Scene2DSkinAssetMetadataReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetImportServiceTest {
    @Test
    fun `selecting source path updates plan`() {
        val fixture = fixture()
        val source = fixture.sourceDir.resolve("albedo.png")
        source.writeBytes(pngBytes())
        val state = AssetBrowserState()

        ImportAssetDialogState.selectSourcePath(state, fixture.service, source.toString())

        assertEquals(source.toString(), state.importSourcePath)
        assertEquals("textures/albedo.png", state.importPlan?.entries?.single()?.targetPath)
        assertTrue(ImportAssetDialogState.canImport(state))
    }

    @Test
    fun `selecting uiskin source path derives import name from package directory`() {
        val fixture = fixture()
        val skinDir = fixture.sourceDir.resolve("commodore64").resolve("skin").createDirectories()
        val source = skinDir.resolve("uiskin.json")
        source.writeText(libgdxSkinJson(), StandardCharsets.UTF_8)
        val state = AssetBrowserState()

        ImportAssetDialogState.selectSourcePath(state, fixture.service, source.toString())

        assertEquals("commodore64", state.importName)
        assertEquals("ui/skins/commodore64/uiskin.json", state.importPlan?.entries?.single()?.targetPath)
    }

    @Test
    fun `null file picker result does not change import state`() {
        val fixture = fixture()
        val state = AssetBrowserState(importSourcePath = "previous")

        ImportAssetDialogState.selectSourcePath(state, fixture.service, null)

        assertEquals("previous", state.importSourcePath)
        assertNull(state.importPlan)
    }

    @Test
    fun `unsupported extension disables import`() {
        val fixture = fixture()
        val source = fixture.sourceDir.resolve("notes.txt")
        source.writeText("not an asset", StandardCharsets.UTF_8)
        val state = AssetBrowserState()

        ImportAssetDialogState.selectSourcePath(state, fixture.service, source.toString())

        assertFalse(state.importPlan?.entries?.single()?.supported ?: true)
        assertFalse(ImportAssetDialogState.canImport(state))
    }

    @Test
    fun `texture import creates file metadata registry descriptor and no dependencies`() {
        val fixture = fixture()
        val source = fixture.sourceDir.resolve("albedo.png")
        source.writeBytes(pngBytes())

        val plan = fixture.service.planImport(source.toString())
        val result = fixture.service.importAssets(plan)

        assertTrue(result.errors.isEmpty())
        assertTrue(plan.entries.single().dependencies.isEmpty())
        assertEquals(listOf("textures/albedo.png"), result.imported.map { it.targetPath })
        assertTrue(fixture.baseDir.resolve("textures/albedo.png").toFile().exists())
        assertTrue(fixture.baseDir.resolve("textures/albedo.png.krmeta").toFile().exists())

        val descriptor = fixture.registry.assets.singleOrNull { asset -> asset.path == "textures/albedo.png" }
        assertNotNull(descriptor)
        assertEquals(AssetType.Texture, descriptor.type)
        assertEquals(AssetCategory.Texture, descriptor.category)
    }

    @Test
    fun `glb import creates file metadata registry descriptor and no dependencies`() {
        val fixture = fixture()
        val source = fixture.sourceDir.resolve("prop.glb")
        source.writeBytes(byteArrayOf(0x67, 0x6c, 0x54, 0x46))

        val plan = fixture.service.planImport(source.toString())
        val result = fixture.service.importAssets(plan)

        assertTrue(result.errors.isEmpty())
        assertTrue(plan.entries.single().dependencies.isEmpty())
        assertEquals(listOf("model/prop.glb"), result.imported.map { it.targetPath })
        assertTrue(fixture.baseDir.resolve("model/prop.glb").toFile().exists())
        assertTrue(fixture.baseDir.resolve("model/prop.glb.krmeta").toFile().exists())

        val descriptor = fixture.registry.assets.singleOrNull { asset -> asset.path == "model/prop.glb" }
        assertNotNull(descriptor)
        assertEquals(AssetType.GltfModel, descriptor.type)
        assertEquals(AssetCategory.Model, descriptor.category)
    }

    @Test
    fun `gltf and obj imports are explicitly unsupported`() {
        val fixture = fixture()
        val gltf = fixture.sourceDir.resolve("prop.gltf")
        val obj = fixture.sourceDir.resolve("prop.obj")
        gltf.writeText("{}", StandardCharsets.UTF_8)
        obj.writeText("o prop", StandardCharsets.UTF_8)

        val gltfPlan = fixture.service.planImport(gltf.toString())
        val objPlan = fixture.service.planImport(obj.toString())
        val gltfResult = fixture.service.importAssets(gltfPlan)
        val objResult = fixture.service.importAssets(objPlan)

        assertFalse(gltfPlan.entries.single().supported)
        assertEquals("GLTF text format is not supported by import yet. Use binary .glb.", gltfPlan.entries.single().status)
        assertFalse(objPlan.entries.single().supported)
        assertEquals("OBJ import is not supported. Use .glb.", objPlan.entries.single().status)
        assertTrue(gltfResult.imported.isEmpty())
        assertTrue(objResult.imported.isEmpty())
        assertFalse(fixture.baseDir.resolve("model/prop.gltf").toFile().exists())
        assertFalse(fixture.baseDir.resolve("model/prop.obj").toFile().exists())
        assertFalse(fixture.baseDir.resolve("model/prop.gltf.krmeta").toFile().exists())
        assertFalse(fixture.baseDir.resolve("model/prop.obj.krmeta").toFile().exists())
    }

    @Test
    fun `unsupported import does not create metadata`() {
        val fixture = fixture()
        val source = fixture.sourceDir.resolve("notes.txt")
        source.writeText("not an asset", StandardCharsets.UTF_8)

        val plan = fixture.service.planImport(source.toString())
        val result = fixture.service.importAssets(plan)

        assertFalse(plan.entries.single().supported)
        assertTrue(result.imported.isEmpty())
        assertTrue(result.skipped.isNotEmpty())
        assertFalse(fixture.baseDir.resolve("assets/notes.txt.krmeta").toFile().exists())
        assertTrue(fixture.registry.assets.isEmpty())
    }

    @Test
    fun `collision rename produces unique target path`() {
        val fixture = fixture()
        val textureDir = fixture.baseDir.resolve("textures").createDirectories()
        textureDir.resolve("albedo.png").writeBytes(pngBytes())
        val source = fixture.sourceDir.resolve("albedo.png")
        source.writeBytes(pngBytes())

        val plan = fixture.service.planImport(source.toString(), AssetImportCollisionPolicy.Rename)
        val result = fixture.service.importAssets(plan)

        assertEquals("textures/albedo_2.png", plan.entries.single().targetPath)
        assertTrue(result.errors.isEmpty())
        assertTrue(fixture.baseDir.resolve("textures/albedo_2.png").toFile().exists())
        assertTrue(fixture.baseDir.resolve("textures/albedo_2.png.krmeta").toFile().exists())
    }

    @Test
    fun `collision skip skips existing target`() {
        val fixture = fixture()
        val textureDir = fixture.baseDir.resolve("textures").createDirectories()
        textureDir.resolve("albedo.png").writeBytes(pngBytes())
        val source = fixture.sourceDir.resolve("albedo.png")
        source.writeBytes(pngBytes())

        val plan = fixture.service.planImport(source.toString(), AssetImportCollisionPolicy.Skip)
        val result = fixture.service.importAssets(plan)

        assertFalse(plan.entries.single().supported)
        assertEquals("Skipped because target exists.", plan.entries.single().status)
        assertTrue(result.imported.isEmpty())
        assertTrue(result.skipped.isNotEmpty())
        assertFalse(fixture.baseDir.resolve("textures/albedo.png.krmeta").toFile().exists())
    }

    @Test
    fun `overwrite requires confirmation before import and replaces existing main file after confirmation`() {
        val fixture = fixture()
        val textureDir = fixture.baseDir.resolve("textures").createDirectories()
        val target = textureDir.resolve("albedo.png")
        target.writeText("old", StandardCharsets.UTF_8)
        val source = fixture.sourceDir.resolve("albedo.png")
        source.writeBytes(pngBytes())
        val state = AssetBrowserState(importCollisionPolicy = AssetImportCollisionPolicy.Overwrite)

        ImportAssetDialogState.selectSourcePath(state, fixture.service, source.toString())
        val requestedPlan = ImportAssetDialogState.requestImport(state)

        assertNull(requestedPlan)
        assertTrue(state.showImportOverwriteConfirmDialog)
        assertTrue(state.pendingImportPlan?.requiresOverwriteConfirmation == true)

        val confirmed = ImportAssetDialogState.acceptOverwrite(state)
        assertNotNull(confirmed)
        val result = fixture.service.importAssets(confirmed)

        assertTrue(result.errors.isEmpty())
        assertTrue(target.readBytes().contentEquals(pngBytes()))
        assertTrue(fixture.baseDir.resolve("textures/albedo.png.krmeta").toFile().exists())
    }

    @Test
    fun `scene2d skin import copies main json and same basename dependencies`() {
        val fixture = fixture()
        val source = fixture.sourceDir.resolve("skin.json")
        source.writeText(validSkinJson(), StandardCharsets.UTF_8)
        fixture.sourceDir.resolve("skin.atlas").writeText("atlas", StandardCharsets.UTF_8)
        fixture.sourceDir.resolve("skin.png").writeBytes(pngBytes())
        fixture.sourceDir.resolve("skin.fnt").writeText("font", StandardCharsets.UTF_8)

        val plan = fixture.service.planImport(source.toString())
        val result = fixture.service.importAssets(plan)

        assertTrue(result.errors.isEmpty())
        assertEquals(listOf("ui/skins/skin/skin.json"), result.imported.map { it.targetPath })
        assertEquals(
            setOf("ui/skins/skin/skin.atlas", "ui/skins/skin/skin.png", "ui/skins/skin/skin.fnt"),
            plan.entries.single().dependencies.map { dependency -> dependency.targetPath }.toSet(),
        )
        val imported = fixture.baseDir.resolve("ui/skins/skin/skin.json")
        assertTrue(imported.toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/skin/skin.json.krmeta").toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/skin/skin.atlas").toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/skin/skin.png").toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/skin/skin.fnt").toFile().exists())

        val metadata = Scene2DSkinAssetMetadataReader.read(imported.toFile()).toMetadataMap()
        assertEquals("ok", metadata["skinStatus"])
        assertEquals("1", metadata["skinLabelStyleCount"])
    }

    @Test
    fun `libgdx skin json notation is supported and referenced font is copied`() {
        val fixture = fixture()
        val source = fixture.sourceDir.resolve("uiskin.json")
        source.writeText(libgdxSkinJson(), StandardCharsets.UTF_8)
        fixture.sourceDir.resolve("uiskin.atlas").writeText("atlas", StandardCharsets.UTF_8)
        fixture.sourceDir.resolve("uiskin.png").writeBytes(pngBytes())
        fixture.sourceDir.resolve("commodore-64.fnt").writeText("font", StandardCharsets.UTF_8)

        val plan = fixture.service.planImport(source.toString(), importName = "Commodore64")
        val result = fixture.service.importAssets(plan)

        assertTrue(plan.entries.single().supported)
        assertTrue(result.errors.isEmpty())
        assertEquals("ui/skins/Commodore64/uiskin.json", plan.entries.single().targetPath)
        assertEquals(
            setOf(
                "ui/skins/Commodore64/uiskin.atlas",
                "ui/skins/Commodore64/uiskin.png",
                "ui/skins/Commodore64/commodore-64.fnt",
            ),
            plan.entries.single().dependencies.map { dependency -> dependency.targetPath }.toSet(),
        )
        assertTrue(fixture.baseDir.resolve("ui/skins/Commodore64/uiskin.json").toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/Commodore64/uiskin.json.krmeta").toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/Commodore64/uiskin.atlas").toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/Commodore64/uiskin.png").toFile().exists())
        assertTrue(fixture.baseDir.resolve("ui/skins/Commodore64/commodore-64.fnt").toFile().exists())
    }

    @Test
    fun `scene2d skin dependencies overwrite without extra confirmation while main overwrite requires confirmation`() {
        val fixture = fixture()
        val skinDir = fixture.baseDir.resolve("ui/skins/Cloud").createDirectories()
        skinDir.resolve("skin.json").writeText("old-json", StandardCharsets.UTF_8)
        skinDir.resolve("skin.png").writeText("old-png", StandardCharsets.UTF_8)
        val source = fixture.sourceDir.resolve("skin.json")
        source.writeText(validSkinJson(), StandardCharsets.UTF_8)
        fixture.sourceDir.resolve("skin.png").writeBytes(pngBytes())
        val state = AssetBrowserState(importName = "Cloud", importCollisionPolicy = AssetImportCollisionPolicy.Overwrite)

        state.importSourcePath = source.toString()
        ImportAssetDialogState.replan(state, fixture.service)

        val entry = state.importPlan?.entries?.single()
        assertNotNull(entry)
        assertTrue(state.importPlan?.requiresOverwriteConfirmation == true)
        assertEquals(listOf("ui/skins/Cloud/skin.png"), entry.dependencies.map { dependency -> dependency.targetPath })
        assertTrue(entry.dependencies.single().targetExists)

        val requestedPlan = ImportAssetDialogState.requestImport(state)
        assertNull(requestedPlan)
        val confirmed = ImportAssetDialogState.acceptOverwrite(state)
        assertNotNull(confirmed)
        val result = fixture.service.importAssets(confirmed)

        assertTrue(result.errors.isEmpty())
        assertEquals(validSkinJson(), skinDir.resolve("skin.json").toFile().readText(StandardCharsets.UTF_8))
        assertTrue(skinDir.resolve("skin.png").readBytes().contentEquals(pngBytes()))
        assertTrue(skinDir.resolve("skin.json.krmeta").toFile().exists())
    }

    private fun fixture(): ImportFixture {
        val logger = EngineLogService()
        val baseDir = Files.createTempDirectory("krender-import-assets")
        val sourceDir = Files.createTempDirectory("krender import source")
        val importers = AssetImporterRegistry.withDefaults(logger)
        val registry = LocalAssetRegistryService(
            logger = logger,
            importers = importers,
            baseDirectory = baseDir.toFile(),
            rootPaths = listOf("model", "textures", "ui/skins", "assets"),
        )
        return ImportFixture(
            baseDir = baseDir,
            sourceDir = sourceDir,
            registry = registry,
            service = LocalAssetImportService(registry, importers, logger),
        )
    }

    private fun validSkinJson(): String =
        """
        {
          "LabelStyle": {
            "default": { "font": "font" }
          }
        }
        """.trimIndent()

    private fun libgdxSkinJson(): String =
        """
        {
        com.badlogic.gdx.graphics.Color: {
          white: { r: 1, g: 1, b: 1, a: 1 }
        },
        com.badlogic.gdx.graphics.g2d.BitmapFont: {
          commodore: { file: commodore-64.fnt }
        },
        com.badlogic.gdx.scenes.scene2d.ui.TextButton${'$'}TextButtonStyle: {
          default: { font: commodore, up: button, down: button-down }
        }
        }
        """.trimIndent()

    private fun pngBytes(): ByteArray =
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lZ2eWQAAAABJRU5ErkJggg==",
        )

    private data class ImportFixture(
        val baseDir: java.nio.file.Path,
        val sourceDir: java.nio.file.Path,
        val registry: LocalAssetRegistryService,
        val service: LocalAssetImportService,
    )
}
