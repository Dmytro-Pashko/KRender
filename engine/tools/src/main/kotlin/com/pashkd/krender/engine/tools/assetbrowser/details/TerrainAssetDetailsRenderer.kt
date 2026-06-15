package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.*
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui

class TerrainAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.Terrain

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Metadata")
        assetBrowserTextLine("Size: ${asset.metadata["terrainSize"] ?: "unknown"}")
        assetBrowserTextLine("Layers: ${asset.metadata["terrainLayerCount"] ?: "unknown"}")
    }
}
