package com.pashkd.krender.engine.tools.textureatlaseditor.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPreviewInfo
import java.io.File

class GdxTextureAtlasEditorPreview(
    private val logger: Logger,
) : Disposable {
    private var loadedPath: String? = null
    private var loadedTexture: Texture? = null
    private var failedPath: String? = null

    fun update(
        texturePath: String?,
        atlasPageName: String?,
        selectedAssetPath: String?,
    ): TextureAtlasEditorPreviewInfo {
        if (texturePath.isNullOrBlank()) {
            unloadTexture()
            return TextureAtlasEditorPreviewInfo(statusMessage = "Select a texture or atlas page to preview.")
        }
        if (texturePath.endsWith(".atlas", ignoreCase = true)) {
            logger.warn(TAG) { "Rejected atlas descriptor as preview texture path='$texturePath'" }
            return TextureAtlasEditorPreviewInfo(
                resolvedTexturePath = texturePath,
                atlasPageName = atlasPageName,
                statusMessage = "Atlas preview could not resolve a page texture.",
            )
        }
        if (!ensureTextureLoaded(texturePath)) {
            return TextureAtlasEditorPreviewInfo(
                resolvedTexturePath = texturePath,
                atlasPageName = atlasPageName,
                statusMessage = "Failed to load preview for '${File(texturePath).name}'.",
            )
        }
        val texture = loadedTexture
        return TextureAtlasEditorPreviewInfo(
            resolvedTexturePath = texturePath,
            atlasPageName = atlasPageName,
            texturePreviewHandle =
                texture?.let {
                    TexturePreviewHandle(
                        id = it.textureObjectHandle,
                        width = it.width,
                        height = it.height,
                    )
                },
            textureWidth = texture?.width ?: 0,
            textureHeight = texture?.height ?: 0,
            statusMessage =
                if (atlasPageName != null) {
                    "Showing atlas page '$atlasPageName'."
                } else {
                    "Showing '${File(selectedAssetPath ?: texturePath).name}'."
                },
        )
    }

    override fun dispose() {
        unloadTexture()
        logger.info(TAG) { "Disposed Texture Atlas Editor preview adapter." }
    }

    private fun ensureTextureLoaded(path: String): Boolean {
        if (loadedPath == path && loadedTexture != null) return true
        if (failedPath == path) return false
        unloadTexture()
        return runCatching {
            Texture(Gdx.files.absolute(path))
        }.fold(
            onSuccess = { texture ->
                loadedPath = path
                loadedTexture = texture
                failedPath = null
                logger.info(TAG) { "Loaded Texture Atlas Editor preview path='$path' size=${texture.width}x${texture.height}" }
                true
            },
            onFailure = { error ->
                loadedPath = null
                loadedTexture = null
                failedPath = path
                logger.warn(TAG, error) { "Failed to load Texture Atlas Editor preview path='$path': ${error.message}" }
                false
            },
        )
    }

    private fun unloadTexture() {
        val path = loadedPath
        loadedTexture?.dispose()
        loadedTexture = null
        loadedPath = null
        if (path != null) {
            logger.info(TAG) { "Disposed Texture Atlas Editor preview texture path='$path'" }
        }
    }

    companion object {
        private const val TAG = "TextureAtlasEditorPreview"
    }
}
