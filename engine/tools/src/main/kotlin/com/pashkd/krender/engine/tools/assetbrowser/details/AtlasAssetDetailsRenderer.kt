package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui

class AtlasAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.type == AssetType.Atlas

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.textUnformatted("Atlas")
        assetBrowserTextLine("Atlas pages and regions are edited in Texture Atlas Editor.")
        assetBrowserTextLine("Open with Texture Atlas Editor to inspect, pack, and save regions.")
        asset.metadata["sourcePath"]?.let { sourcePath ->
            assetBrowserTextLine("Source path: $sourcePath")
        }
    }
}
