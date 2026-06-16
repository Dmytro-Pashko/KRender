package com.pashkd.krender.engine.assets

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetCapabilitiesTest {
    @Test
    fun `Other Unknown assets are visible-only and not openable`() {
        val asset = descriptor(category = AssetCategory.Other, type = AssetType.Unknown)
        val capabilities = asset.assetCapabilities()

        assertTrue(asset.isVisibleOnlyAsset())
        assertFalse(asset.isManagedAsset())
        assertFalse(asset.canHaveMetadataSidecar())
        assertFalse(asset.canOpenWithTools())
        assertFalse(capabilities.canOpenWith)
        assertTrue(capabilities.canReveal)
        assertFalse(capabilities.canRename)
        assertFalse(capabilities.canDuplicate)
        assertFalse(capabilities.canDelete)
        assertFalse(capabilities.canPreview)
    }

    @Test
    fun `supported model assets are managed and openable`() {
        val asset = descriptor(category = AssetCategory.Model, type = AssetType.GltfModel)
        val capabilities = asset.assetCapabilities()

        assertTrue(asset.isManagedAsset())
        assertTrue(asset.canHaveMetadataSidecar())
        assertTrue(asset.canOpenWithTools())
        assertTrue(capabilities.canOpenWith)
        assertTrue(capabilities.canReveal)
        assertTrue(capabilities.canRename)
        assertTrue(capabilities.canDuplicate)
        assertTrue(capabilities.canDelete)
        assertFalse(capabilities.canPreview)
    }

    @Test
    fun `supported terrain scene and ui assets are openable`() {
        listOf(
            descriptor(category = AssetCategory.Terrain, type = AssetType.Terrain),
            descriptor(category = AssetCategory.Scene, type = AssetType.Scene),
            descriptor(category = AssetCategory.UI, type = AssetType.UiScene),
        ).forEach { asset ->
            val capabilities = asset.assetCapabilities()

            assertTrue(asset.isManagedAsset())
            assertTrue(capabilities.canOpenWith)
            assertTrue(capabilities.canReveal)
            assertTrue(capabilities.canRename)
            assertTrue(capabilities.canDuplicate)
            assertTrue(capabilities.canDelete)
        }
    }

    @Test
    fun `scene2d skin delete stays enabled while rename and duplicate remain disabled`() {
        val asset = descriptor(category = AssetCategory.UI, type = AssetType.Scene2DSkin)
        val capabilities = asset.assetCapabilities()

        assertTrue(asset.isManagedAsset())
        assertTrue(capabilities.canOpenWith)
        assertTrue(capabilities.canReveal)
        assertFalse(capabilities.canRename)
        assertFalse(capabilities.canDuplicate)
        assertTrue(capabilities.canDelete)
        assertFalse(capabilities.canPreview)
    }

    @Test
    fun `texture assets are previewable managed assets`() {
        val asset = descriptor(category = AssetCategory.Texture, type = AssetType.Texture)
        val capabilities = asset.assetCapabilities()

        assertTrue(asset.isManagedAsset())
        assertTrue(capabilities.canOpenWith)
        assertTrue(capabilities.canPreview)
    }

    private fun descriptor(
        category: AssetCategory,
        type: AssetType,
    ): AssetDescriptor =
        AssetDescriptor(
            id = AssetId("asset:${category.name}:${type.name}"),
            name = "asset",
            path = "assets/asset.dat",
            category = category,
            type = type,
            extension = "dat",
            sizeBytes = 1L,
            modifiedAtMillis = 1L,
        )
}
