package com.pashkd.krender.engine.tools.textureatlaseditor.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPreviewSlice
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPreviewInfo
import java.io.File

class GdxTextureAtlasEditorPreview(
    private val logger: Logger,
) : Disposable {
    private var loadedKey: String? = null
    private var loadedLabel: String? = null
    private var loadedTexture: Texture? = null
    private var failedKey: String? = null

    fun update(
        texturePath: String?,
        atlasPageName: String?,
        selectedAssetPath: String?,
        previewSlice: TextureAtlasEditorPreviewSlice? = null,
        packedPage: TextureAtlasPackingPage? = null,
    ): TextureAtlasEditorPreviewInfo {
        if (packedPage != null) {
            if (!ensurePackedPageLoaded(packedPage)) {
                return TextureAtlasEditorPreviewInfo(
                    resolvedTexturePath = "packed:${packedPage.name}",
                    atlasPageName = packedPage.name,
                    statusMessage = "Failed to compose packed atlas preview for '${packedPage.name}'.",
                )
            }
            val texture = loadedTexture
            return TextureAtlasEditorPreviewInfo(
                resolvedTexturePath = "packed:${packedPage.name}",
                atlasPageName = packedPage.name,
                texturePreviewHandle =
                    texture?.let { loaded ->
                        TexturePreviewHandle(
                            id = loaded.textureObjectHandle,
                            width = loaded.width,
                            height = loaded.height,
                        )
                    },
                textureWidth = texture?.width ?: 0,
                textureHeight = texture?.height ?: 0,
                statusMessage = "Showing packed atlas page '${packedPage.name}'.",
            )
        }
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
        val effectiveSlice = previewSlice?.let { slice -> clampSlice(texture, slice) }
        val previewHandle =
            texture?.let { loaded ->
                effectiveSlice?.let { slice -> slicedHandle(loaded, slice) }
                    ?: TexturePreviewHandle(
                        id = loaded.textureObjectHandle,
                        width = loaded.width,
                        height = loaded.height,
                    )
            }
        return TextureAtlasEditorPreviewInfo(
            resolvedTexturePath = texturePath,
            atlasPageName = atlasPageName,
            texturePreviewHandle = previewHandle,
            textureWidth = effectiveSlice?.width ?: texture?.width ?: 0,
            textureHeight = effectiveSlice?.height ?: texture?.height ?: 0,
            statusMessage =
                if (effectiveSlice != null) {
                    "Showing isolated preview for '${File(selectedAssetPath ?: texturePath).name}'."
                } else if (atlasPageName != null) {
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
        if (loadedKey == path && loadedTexture != null) return true
        if (failedKey == path) return false
        unloadTexture()
        return runCatching {
            Texture(Gdx.files.absolute(path))
        }.fold(
            onSuccess = { texture ->
                loadedKey = path
                loadedLabel = path
                loadedTexture = texture
                failedKey = null
                logger.info(TAG) { "Loaded Texture Atlas Editor preview path='$path' size=${texture.width}x${texture.height}" }
                true
            },
            onFailure = { error ->
                loadedKey = null
                loadedLabel = null
                loadedTexture = null
                failedKey = path
                logger.warn(TAG, error) { "Failed to load Texture Atlas Editor preview path='$path': ${error.message}" }
                false
            },
        )
    }

    private fun ensurePackedPageLoaded(page: TextureAtlasPackingPage): Boolean {
        val key = buildPackedPageKey(page)
        if (loadedKey == key && loadedTexture != null) return true
        if (failedKey == key) return false
        unloadTexture()
        return runCatching {
            composePackedPageTexture(page)
        }.fold(
            onSuccess = { texture ->
                loadedKey = key
                loadedLabel = "packed:${page.name}"
                loadedTexture = texture
                failedKey = null
                logger.info(TAG) {
                    "Composed Texture Atlas Editor packed preview page='${page.name}' size=${texture.width}x${texture.height} regions=${page.regions.size}"
                }
                true
            },
            onFailure = { error ->
                loadedKey = null
                loadedLabel = null
                loadedTexture = null
                failedKey = key
                logger.warn(TAG, error) {
                    "Failed to compose Texture Atlas Editor packed preview page='${page.name}': ${error.message}"
                }
                false
            },
        )
    }

    private fun unloadTexture() {
        val label = loadedLabel
        loadedTexture?.dispose()
        loadedTexture = null
        loadedKey = null
        loadedLabel = null
        if (label != null) {
            logger.info(TAG) { "Disposed Texture Atlas Editor preview texture path='$label'" }
        }
    }

    private fun clampSlice(
        texture: Texture?,
        slice: TextureAtlasEditorPreviewSlice,
    ): TextureAtlasEditorPreviewSlice? {
        if (texture == null) return null
        val textureWidth = texture.width.coerceAtLeast(1)
        val textureHeight = texture.height.coerceAtLeast(1)
        val clampedX = slice.sourceX.coerceIn(0, textureWidth - 1)
        val clampedY = slice.sourceY.coerceIn(0, textureHeight - 1)
        val clampedWidth = slice.width.coerceIn(1, textureWidth - clampedX)
        val clampedHeight = slice.height.coerceIn(1, textureHeight - clampedY)
        return TextureAtlasEditorPreviewSlice(
            sourceX = clampedX,
            sourceY = clampedY,
            width = clampedWidth,
            height = clampedHeight,
        )
    }

    private fun slicedHandle(
        texture: Texture,
        slice: TextureAtlasEditorPreviewSlice,
    ): TexturePreviewHandle {
        val textureWidth = texture.width.coerceAtLeast(1).toFloat()
        val textureHeight = texture.height.coerceAtLeast(1).toFloat()
        val u0 = slice.sourceX / textureWidth
        val v0 = slice.sourceY / textureHeight
        val u1 = (slice.sourceX + slice.width) / textureWidth
        val v1 = (slice.sourceY + slice.height) / textureHeight
        return TexturePreviewHandle(
            id = texture.textureObjectHandle,
            width = slice.width,
            height = slice.height,
            u0 = u0,
            v0 = v0,
            u1 = u1,
            v1 = v1,
        )
    }

    private fun composePackedPageTexture(page: TextureAtlasPackingPage): Texture {
        val output = Pixmap(page.width, page.height, Pixmap.Format.RGBA8888)
        try {
            output.setColor(0f, 0f, 0f, 0f)
            output.fill()
            page.regions.forEach { region ->
                val sourceFile = File(region.sourcePath)
                if (!sourceFile.isFile) {
                    logger.warn(TAG) {
                        "Skipping packed preview source because file is missing path='${region.sourcePath}' region='${region.displayName}'"
                    }
                    return@forEach
                }
                runCatching {
                    Pixmap(Gdx.files.absolute(region.sourcePath))
                }.onSuccess { source ->
                    try {
                        output.drawPixmap(
                            source,
                            region.sourceX,
                            region.sourceY,
                            region.sourceWidth,
                            region.sourceHeight,
                            region.x,
                            region.y,
                            region.width,
                            region.height,
                        )
                    } finally {
                        source.dispose()
                    }
                }.onFailure { error ->
                    logger.warn(TAG, error) {
                        "Skipping packed preview source path='${region.sourcePath}' region='${region.displayName}': ${error.message}"
                    }
                }
            }
            return Texture(output)
        } finally {
            output.dispose()
        }
    }

    private fun buildPackedPageKey(page: TextureAtlasPackingPage): String =
        buildString {
            append("packed:")
            append(page.index)
            append(':')
            append(page.width)
            append('x')
            append(page.height)
            page.regions.forEach { region ->
                append('|')
                append(region.id)
                append('@')
                append(region.x)
                append(',')
                append(region.y)
                append(':')
                append(region.width)
                append('x')
                append(region.height)
            }
        }

    companion object {
        private const val TAG = "TextureAtlasEditorPreview"
    }
}
