package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetId
import com.pashkd.krender.engine.assets.AssetType
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AssetHierarchyBuilderTest {
    @Test
    fun `nested asset paths form directory and asset nodes`() {
        val baseDir = Files.createTempDirectory("krender-hierarchy")
        baseDir.resolve("model/props").createDirectories()
        baseDir.resolve("model/props/crate.glb").writeBytes(byteArrayOf(1, 2, 3))
        val asset =
            descriptor(
                name = "crate",
                path = "model/props/crate.glb",
                category = AssetCategory.Model,
                type = AssetType.GltfModel,
                sizeBytes = 3L,
            )

        val root = AssetHierarchyBuilder.build(baseDir.toFile(), listOf(asset))

        val model = root.child("model")
        val props = model?.child("props")
        val crate = props?.child("crate")
        assertNotNull(model)
        assertNotNull(props)
        assertNotNull(crate)
        assertEquals(AssetHierarchyNodeKind.Asset, crate.kind)
        assertEquals(asset, crate.asset)
        assertEquals(1, root.assetCount)
        assertEquals(3L, root.sizeBytes)
    }

    @Test
    fun `empty folders from filesystem are included`() {
        val baseDir = Files.createTempDirectory("krender-hierarchy-empty")
        baseDir.resolve("textures/empty").createDirectories()

        val root = AssetHierarchyBuilder.build(baseDir.toFile(), emptyList())

        val textures = root.child("textures")
        val empty = textures?.child("empty")
        assertNotNull(textures)
        assertNotNull(empty)
        assertEquals(AssetHierarchyNodeKind.Directory, empty.kind)
        assertEquals(0, empty.assetCount)
    }

    @Test
    fun `hidden trash folders are skipped`() {
        val baseDir = Files.createTempDirectory("krender-hierarchy-trash")
        baseDir.resolve(".trash/model").createDirectories()

        val root = AssetHierarchyBuilder.build(baseDir.toFile(), emptyList())

        assertNull(root.child(".trash"))
    }

    private fun AssetHierarchyNode.child(name: String): AssetHierarchyNode? = children.firstOrNull { it.name == name }

    private fun descriptor(
        name: String,
        path: String,
        category: AssetCategory,
        type: AssetType,
        sizeBytes: Long = 1L,
    ): AssetDescriptor =
        AssetDescriptor(
            id = AssetId("asset:$path"),
            name = name,
            path = path,
            category = category,
            type = type,
            extension = path.substringAfterLast('.', ""),
            sizeBytes = sizeBytes,
            modifiedAtMillis = 1L,
        )
}
