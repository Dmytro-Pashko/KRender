package com.pashkd.krender.engine.tools.common

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetId
import com.pashkd.krender.engine.assets.AssetRegistryError
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetRegistrySnapshot
import com.pashkd.krender.engine.assets.AssetType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorAssetPickerCatalogTest {
    @Test
    fun `filters by category`() {
        val catalog =
            EditorAssetPickerCatalog(
                FakeAssetRegistry(
                    listOf(
                        descriptor("a", "textures/a.png", AssetCategory.Texture, AssetType.Texture),
                        descriptor("b", "model/b.glb", AssetCategory.Model, AssetType.GltfModel),
                    ),
                ),
            )

        val options = catalog.listAssets(AssetCategory.Texture)

        assertEquals(listOf("textures/a.png"), options.map { it.path })
    }

    @Test
    fun `optional type filter works`() {
        val catalog =
            EditorAssetPickerCatalog(
                FakeAssetRegistry(
                    listOf(
                        descriptor("texture", "textures/a.png", AssetCategory.Texture, AssetType.Texture),
                        descriptor("skin", "ui/skins/default.json", AssetCategory.UI, AssetType.Scene2DSkin),
                        descriptor("scene", "ui/scenes/menu.krui", AssetCategory.UI, AssetType.UiScene),
                    ),
                ),
            )

        val options = catalog.listAssets(AssetCategory.UI, AssetType.UiScene)

        assertEquals(listOf("ui/scenes/menu.krui"), options.map { it.path })
    }

    @Test
    fun `blank paths are ignored`() {
        val catalog =
            EditorAssetPickerCatalog(
                FakeAssetRegistry(
                    listOf(
                        descriptor("blank", "", AssetCategory.Texture, AssetType.Texture),
                        descriptor("valid", "textures/a.png", AssetCategory.Texture, AssetType.Texture),
                    ),
                ),
            )

        val options = catalog.listAssets(AssetCategory.Texture)

        assertEquals(listOf("textures/a.png"), options.map { it.path })
    }

    @Test
    fun `duplicate paths are deduplicated`() {
        val catalog =
            EditorAssetPickerCatalog(
                FakeAssetRegistry(
                    listOf(
                        descriptor("first", "textures/a.png", AssetCategory.Texture, AssetType.Texture),
                        descriptor("second", "textures/a.png", AssetCategory.Texture, AssetType.Texture),
                    ),
                ),
            )

        val options = catalog.listAssets(AssetCategory.Texture)

        assertEquals(1, options.size)
        assertEquals("textures/a.png", options.single().path)
    }

    @Test
    fun `display name uses metadata override`() {
        val catalog =
            EditorAssetPickerCatalog(
                FakeAssetRegistry(
                    listOf(
                        descriptor(
                            name = "fallback",
                            path = "textures/a.png",
                            category = AssetCategory.Texture,
                            type = AssetType.Texture,
                            metadata = mapOf("displayName" to "Overridden"),
                        ),
                    ),
                ),
            )

        val option = catalog.listAssets(AssetCategory.Texture).single()

        assertEquals("Overridden", option.displayName)
    }

    @Test
    fun `sorting is stable by display name then path`() {
        val catalog =
            EditorAssetPickerCatalog(
                FakeAssetRegistry(
                    listOf(
                        descriptor("Beta", "textures/z.png", AssetCategory.Texture, AssetType.Texture),
                        descriptor("alpha", "textures/b.png", AssetCategory.Texture, AssetType.Texture),
                        descriptor("Alpha", "textures/a.png", AssetCategory.Texture, AssetType.Texture),
                    ),
                ),
            )

        val options = catalog.listAssets(AssetCategory.Texture)

        assertEquals(
            listOf("textures/a.png", "textures/b.png", "textures/z.png"),
            options.map { it.path },
        )
    }

    @Test
    fun `asset type by path returns current descriptor map`() {
        val catalog =
            EditorAssetPickerCatalog(
                FakeAssetRegistry(
                    listOf(
                        descriptor("texture", "textures/a.png", AssetCategory.Texture, AssetType.Texture),
                        descriptor("ui", "ui/scenes/menu.krui", AssetCategory.UI, AssetType.UiScene),
                        descriptor("blank", "", AssetCategory.Texture, AssetType.Texture),
                    ),
                ),
            )

        val typesByPath = catalog.assetTypeByPath()

        assertEquals(
            mapOf(
                "textures/a.png" to AssetType.Texture,
                "ui/scenes/menu.krui" to AssetType.UiScene,
            ),
            typesByPath,
        )
    }
}

private class FakeAssetRegistry(
    override val assets: List<AssetDescriptor>,
) : AssetRegistryService {
    override fun scanSnapshot(): AssetRegistrySnapshot =
        AssetRegistrySnapshot(
            assets = assets,
            scannedAtMillis = 0L,
            durationMillis = 0L,
            errors = emptyList<AssetRegistryError>(),
        )

    override fun applySnapshot(snapshot: AssetRegistrySnapshot) = Unit

    override fun findById(id: AssetId): AssetDescriptor? = assets.firstOrNull { asset -> asset.id == id }

    override fun findByPath(path: String): AssetDescriptor? = assets.firstOrNull { asset -> asset.path == path }

    override fun byCategory(category: AssetCategory): List<AssetDescriptor> = assets.filter { asset -> asset.category == category }

    override fun baseDir(): File = File(".")
}

private fun descriptor(
    name: String,
    path: String,
    category: AssetCategory,
    type: AssetType,
    metadata: Map<String, String> = emptyMap(),
): AssetDescriptor =
    AssetDescriptor(
        id = AssetId("asset:$path"),
        name = name,
        path = path,
        category = category,
        type = type,
        extension = path.substringAfterLast('.', ""),
        sizeBytes = 1L,
        modifiedAtMillis = 0L,
        metadata = metadata,
    )
