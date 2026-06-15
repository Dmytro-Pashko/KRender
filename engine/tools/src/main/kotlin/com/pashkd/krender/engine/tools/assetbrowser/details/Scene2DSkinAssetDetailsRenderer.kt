package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.*
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserState

import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui

class Scene2DSkinAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.type == AssetType.Scene2DSkin

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Metadata")
        assetBrowserTextLine("Status: ${asset.metadata["skinStatus"] ?: "unknown"}")
        asset.metadata["skinParseError"]?.let { error -> assetBrowserTextLine("Parse error: $error") }

        ImGui.separator()
        assetBrowserTextLine("Preview: ${asset.metadata["skinPreview"] ?: "unknown"}")
        assetBrowserTextLine("Colors: ${asset.metadata["skinColorCount"] ?: "0"}")
        assetBrowserTextLine("Drawables: ${asset.metadata["skinDrawableCount"] ?: "0"}")
        assetBrowserTextLine("Texture regions: ${asset.metadata["skinTextureRegionCount"] ?: "0"}")
        assetBrowserTextLine("Style classes: ${asset.metadata["skinStyleClassCount"] ?: "0"}")

        ImGui.separator()
        ImGui.text("Styles")
        assetBrowserTextLine("Labels: ${asset.metadata["skinLabelStyleCount"] ?: "0"}")
        assetBrowserTextLine("Text buttons: ${asset.metadata["skinTextButtonStyleCount"] ?: "0"}")
        assetBrowserTextLine("Progress bars: ${asset.metadata["skinProgressBarStyleCount"] ?: "0"}")
        assetBrowserTextLine("Image buttons: ${asset.metadata["skinImageButtonStyleCount"] ?: "0"}")
        assetBrowserTextLine("Check boxes: ${asset.metadata["skinCheckBoxStyleCount"] ?: "0"}")
        assetBrowserTextLine("Text fields: ${asset.metadata["skinTextFieldStyleCount"] ?: "0"}")
        assetBrowserTextLine("Scroll panes: ${asset.metadata["skinScrollPaneStyleCount"] ?: "0"}")
        assetBrowserTextLine("Select boxes: ${asset.metadata["skinSelectBoxStyleCount"] ?: "0"}")
        assetBrowserTextLine("Windows: ${asset.metadata["skinWindowStyleCount"] ?: "0"}")
    }
}
