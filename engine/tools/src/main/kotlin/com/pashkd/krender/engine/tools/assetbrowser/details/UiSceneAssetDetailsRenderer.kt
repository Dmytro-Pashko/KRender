package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.*
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserState

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui

class UiSceneAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean =
        asset.category == AssetCategory.UI && asset.type == AssetType.UiScene

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Metadata")
        assetBrowserTextLine("Document ID: ${asset.metadata["uiSceneDocumentId"] ?: "unknown"}")
        assetBrowserTextLine("Skin: ${asset.metadata["uiSceneSkinPath"] ?: "unknown"}")
        assetBrowserTextLine("Schema: ${asset.metadata["uiSceneSchemaVersion"] ?: "unknown"}")
        assetBrowserTextLine("Status: ${asset.metadata["uiSceneStatus"] ?: "unknown"}")
        asset.metadata["uiSceneParseError"]?.let { error -> assetBrowserTextLine("Parse error: $error") }

        ImGui.separator()
        ImGui.text("Diagnostics")
        assetBrowserTextLine("Validation warnings: ${asset.metadata["uiSceneValidationWarningCount"] ?: "0"}")
        asset.metadata["uiSceneValidationIssuePreview"]?.let { preview -> assetBrowserTextLine("Issues: $preview") }
    }
}
