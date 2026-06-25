package com.pashkd.krender.engine.tools.common

import com.pashkd.krender.engine.api.AssetRef
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
 * Shared editor-facing lookup wrapper for backend texture preview handles.
 *
 * This centralizes status reporting around [AssetService.texturePreviewHandle]
 * without changing how tools actually render previews.
 */
class EditorTexturePreviewService(
    private val assets: AssetService,
) {
    @Suppress("ReturnCount")
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
        if (handle != null) {
            return TexturePreviewResult.Available(path = path, handle = handle)
        }

        val loadState = runCatching { assets.isLoaded(AssetRef.texture(path)) }
        val reason =
            when {
                loadState.isFailure ->
                    "AssetService load state check failed: ${loadState.exceptionOrNull()?.message ?: "unknown error"}."

                loadState.getOrDefault(false) ->
                    "AssetService reports the texture is loaded, but the backend returned no preview handle."

                else ->
                    "Texture is not loaded by AssetService yet."
            }
        return TexturePreviewResult.Unavailable(
            path = path,
            reason = reason,
        )
    }
}
