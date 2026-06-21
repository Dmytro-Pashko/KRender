package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import com.pashkd.krender.engine.tools.common.TexturePreviewResult
import imgui.ImGui

class TextureAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.Texture

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Preview")
        drawTexturePreview(asset, context)
        ImGui.separator()
        ImGui.text("Metadata")
        assetBrowserTextLine("Source resolution: ${asset.metadata["textureResolution"] ?: "unknown"}")
        assetBrowserTextLine("Format: ${asset.metadata["textureColorFormat"] ?: "unknown"}")
    }

    private fun drawTexturePreview(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        when (val preview = context.texturePreviews.preview(asset.path)) {
            is TexturePreviewResult.Unavailable -> {
                assetBrowserTextLine("Preview unavailable.")
                return
            }

            is TexturePreviewResult.Available -> {
                val handle = preview.handle
                if (!context.ui.drawTexturePreview(handle, TexturePreviewSize, TexturePreviewSize)) {
                    assetBrowserTextLine("Preview unavailable.")
                    return
                }
                assetBrowserTextLine("Preview size: ${TexturePreviewSize.toInt()} x ${TexturePreviewSize.toInt()}")
            }
        }
    }

    companion object {
        private const val TexturePreviewSize = 250f
    }
}
