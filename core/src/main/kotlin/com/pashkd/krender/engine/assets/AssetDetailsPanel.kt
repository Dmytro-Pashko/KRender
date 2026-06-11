package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.assets.details.AssetDetailsRenderContext
import com.pashkd.krender.engine.assets.details.GenericAssetDetailsRenderer
import com.pashkd.krender.engine.assets.details.ModelAssetDetailsRenderer
import com.pashkd.krender.engine.assets.details.Scene2DSkinAssetDetailsRenderer
import com.pashkd.krender.engine.assets.details.SceneAssetDetailsRenderer
import com.pashkd.krender.engine.assets.details.TerrainAssetDetailsRenderer
import com.pashkd.krender.engine.assets.details.TextureAssetDetailsRenderer
import com.pashkd.krender.engine.assets.details.UiSceneAssetDetailsRenderer
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.dsl

/**
 * Shows details for the currently selected asset and dispatches type-specific metadata rendering.
 */
class AssetDetailsPanel(
    private val state: AssetBrowserState,
    private val assets: AssetService,
    private val ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
    private val panelId: String = AssetBrowserPanelIds.Details,
    private val layoutTracker: ImGuiLayoutRuntimeTracker? = null,
    private val operations: AssetBrowserOperationsHandler = AssetBrowserOperationsHandler.NoOp,
) : UiPanel {
    private val renderers = listOf(
        TextureAssetDetailsRenderer(),
        ModelAssetDetailsRenderer(),
        TerrainAssetDetailsRenderer(),
        UiSceneAssetDetailsRenderer(),
        Scene2DSkinAssetDetailsRenderer(),
        SceneAssetDetailsRenderer(),
        GenericAssetDetailsRenderer(),
    )

    override fun draw() {
        val layout = layoutConfig.panels[panelId] ?: return
        val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
        eventLogger.observe(panelId, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val asset = state.selectedAssetId?.let { id -> state.assets.firstOrNull { it.id == id } }
        if (asset == null) {
            ImGui.text("No asset selected.")
            ImGui.end()
            return
        }

        drawBaseInfo(asset)
        drawActions(asset)
        ImGui.separator()
        val context = AssetDetailsRenderContext(
            state = state,
            assets = assets,
            ui = ui,
            operations = operations,
            panelId = panelId,
        )
        renderers.first { renderer -> renderer.supports(asset) }.render(asset, context)
        ImGui.end()
    }

    private fun drawBaseInfo(asset: AssetDescriptor) {
        assetBrowserTextLine("Name: ${asset.name}")
        assetBrowserTextLine("ID: ${asset.id.value}")
        assetBrowserTextLine("Category: ${asset.category.displayName}")
        assetBrowserTextLine("Type: ${asset.type.name}")
        assetBrowserTextLine("Path: ${asset.path}")
        assetBrowserTextLine("Extension: ${asset.extension.ifBlank { "none" }}")
        assetBrowserTextLine("Size: ${assetBrowserFormatByteCount(asset.sizeBytes)}")
        assetBrowserTextLine("Last modified: ${assetBrowserFormatTimestamp(asset.modifiedAtMillis)}")
        assetBrowserTextLine("Tags: ${asset.tags.ifEmpty { listOf("none") }.joinToString(", ")}")
    }

    private fun drawActions(asset: AssetDescriptor) {
        if (!asset.canOpenWithTools() || asset.category == AssetCategory.Scene) return
        val tools = operations.toolsFor(asset)
        if (tools.isEmpty()) return

        ImGui.separator()
        ImGui.text("Actions")
        tools.forEach { tool ->
            with(dsl) {
                button("${tool.label}##${panelId}_tool_${tool.id}") {
                    operations.openWith(asset, tool.id)
                }
            }
        }
    }
}
