package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui

class ModelAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.Model

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Model runtime metadata")
        assetBrowserTextLine("Status: ${context.state.selectedModelStatus}")
        drawModelInfo(context.state.selectedModelInfo)
    }

    private fun drawModelInfo(info: ModelAssetInfo?) {
        if (info == null) {
            ImGui.text("Metadata is available after the model finishes loading.")
            return
        }

        assetBrowserTextLine("Format: ${info.format}")
        assetBrowserTextLine("Nodes: ${info.nodeCount}")
        assetBrowserTextLine("Meshes: ${info.meshCount}")
        assetBrowserTextLine("Mesh parts: ${info.meshPartCount}")
        assetBrowserTextLine("Materials: ${info.materialCount}")
        assetBrowserTextLine("Vertices: ${info.vertexCount}")
        assetBrowserTextLine("Triangles: ${info.triangleCount}")
        assetBrowserTextLine("Textures: ${info.textureCount} unique / ${info.textureSlotCount} slots")
        assetBrowserTextLine("Animations: ${info.animationCount}")
        assetBrowserTextLine("Skeleton: ${if (info.hasSkeleton) "yes" else "no"}")
        info.size?.let { size ->
            ImGui.text("Bounds: %.2f x %.2f x %.2f", size.x, size.y, size.z)
        }
    }
}
