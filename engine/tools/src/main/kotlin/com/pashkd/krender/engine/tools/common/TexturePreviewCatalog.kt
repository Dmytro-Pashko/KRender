package com.pashkd.krender.engine.tools.common

import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.TexturePreviewHandle

sealed interface TexturePreviewResult {
    data class Available(
        val path: String,
        val handle: TexturePreviewHandle,
    ) : TexturePreviewResult

    data class Unavailable(
        val path: String,
        val reason: String,
    ) : TexturePreviewResult
}

/**
 * Shared editor lookup wrapper for backend texture preview handles.
 *
 * This centralizes status reporting around [AssetService.texturePreviewHandle]
 * without changing how tools actually render previews.
 */
class TexturePreviewCatalog(
    private val assets: AssetService,
) {
    fun preview(path: String?): TexturePreviewResult {
        if (path == null) {
            return TexturePreviewResult.Unavailable(
                path = "",
                reason = "No texture path provided.",
            )
        }
        if (path.isBlank()) {
            return TexturePreviewResult.Unavailable(
                path = path,
                reason = "Texture path is blank.",
            )
        }

        val handle = assets.texturePreviewHandle(path)
        return if (handle != null) {
            TexturePreviewResult.Available(path = path, handle = handle)
        } else {
            TexturePreviewResult.Unavailable(
                path = path,
                reason = "Texture preview handle is unavailable.",
            )
        }
    }
}
