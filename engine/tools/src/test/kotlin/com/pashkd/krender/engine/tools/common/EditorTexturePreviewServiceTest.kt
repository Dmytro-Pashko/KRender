package com.pashkd.krender.engine.tools.common

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.TexturePreviewHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EditorTexturePreviewServiceTest {
    @Test
    fun `null path returns unavailable`() {
        val assets = FakeAssetService()
        val service = EditorTexturePreviewService(assets)

        val result = service.preview(null)

        val unavailable = assertIs<TexturePreviewResult.Unavailable>(result)
        assertEquals("", unavailable.path)
        assertEquals("No texture path provided.", unavailable.reason)
        assertEquals(emptyList(), assets.requestedPreviewPaths)
    }

    @Test
    fun `blank path returns unavailable`() {
        val assets = FakeAssetService()
        val service = EditorTexturePreviewService(assets)

        val result = service.preview(" ")

        val unavailable = assertIs<TexturePreviewResult.Unavailable>(result)
        assertEquals(" ", unavailable.path)
        assertEquals("Texture path is blank.", unavailable.reason)
        assertEquals(emptyList(), assets.requestedPreviewPaths)
    }

    @Test
    fun `missing handle returns unavailable`() {
        val assets = FakeAssetService()
        val service = EditorTexturePreviewService(assets)

        val result = service.preview("textures/missing.png")

        val unavailable = assertIs<TexturePreviewResult.Unavailable>(result)
        assertEquals("textures/missing.png", unavailable.path)
        assertEquals("Texture is not loaded by AssetService yet.", unavailable.reason)
        assertEquals(listOf("textures/missing.png"), assets.requestedPreviewPaths)
    }

    @Test
    fun `available handle is returned unchanged`() {
        val handle = TexturePreviewHandle(id = 7, width = 128, height = 64)
        val assets =
            FakeAssetService(
                previewHandles =
                    mapOf(
                        "textures/known.png" to handle,
                    ),
            )
        val service = EditorTexturePreviewService(assets)

        val result = service.preview("textures/known.png")

        val available = assertIs<TexturePreviewResult.Available>(result)
        assertEquals("textures/known.png", available.path)
        assertEquals(handle, available.handle)
        assertEquals(listOf("textures/known.png"), assets.requestedPreviewPaths)
        assertEquals(emptyList(), assets.queuedAssets)
        assertEquals(emptyList(), assets.loadedChecks)
    }
}

private class FakeAssetService(
    private val previewHandles: Map<String, TexturePreviewHandle> = emptyMap(),
) : AssetService {
    val requestedPreviewPaths = mutableListOf<String>()
    val queuedAssets = mutableListOf<AssetRef<*>>()
    val loadedChecks = mutableListOf<AssetRef<*>>()

    override fun queue(asset: AssetRef<*>) {
        queuedAssets += asset
    }

    override fun update(budgetMs: Int): Float = 1f

    override fun isLoaded(asset: AssetRef<*>): Boolean {
        loadedChecks += asset
        return false
    }

    override fun <T : Any> get(asset: AssetRef<T>): T {
        error("Not used in test")
    }

    override fun triangleCount(asset: AssetRef<ModelAsset>): Int? = null

    override fun texturePreviewHandle(texturePathOrId: String): TexturePreviewHandle? {
        requestedPreviewPaths += texturePathOrId
        return previewHandles[texturePathOrId]
    }

    override fun unload(asset: AssetRef<*>) = Unit
}
