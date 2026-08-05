package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui

class TextureAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.type == AssetType.Texture

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Preview")
        drawAssetTexturePreview(asset, context)
        ImGui.separator()
        ImGui.text("Metadata")
        assetBrowserTextLine("Source resolution: ${asset.metadata["textureResolution"] ?: "unknown"}")
        assetBrowserTextLine("Format: ${asset.metadata["textureColorFormat"] ?: "unknown"}")
    }
}
