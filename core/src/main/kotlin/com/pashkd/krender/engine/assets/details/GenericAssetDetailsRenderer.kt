package com.pashkd.krender.engine.assets.details

import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.assetBrowserTextLine
import imgui.ImGui

class GenericAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = true

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Metadata")
        if (asset.metadata.isEmpty()) {
            ImGui.text("none")
            return
        }
        asset.metadata.forEach { (key, value) ->
            assetBrowserTextLine("$key: $value")
        }
    }
}
