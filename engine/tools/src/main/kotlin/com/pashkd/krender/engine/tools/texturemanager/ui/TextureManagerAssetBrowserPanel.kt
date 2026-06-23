package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerAssetKind
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureManagerAssetBrowserPanel(
    private val state: TextureManagerState,
    private val operations: TextureManagerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        if (!synced) {
            writeBuffer(queryBuffer, state.assetBrowser.query)
            synced = true
        }
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Assets)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Assets, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Assets, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Search")
        ImGui.sameLine()
        ImGui.setNextItemWidth(220f)
        if (ImGui.inputText("##texture_manager_asset_query", queryBuffer)) {
            state.assetBrowser.query = readBuffer(queryBuffer)
        }
        ImGui.sameLine()
        if (ImGui.button("Clear##texture_manager_asset_query_clear")) {
            state.assetBrowser.query = ""
            writeBuffer(queryBuffer, "")
        }
        ImGui.separator()
        textLine("Textures: ${state.project.discoveredTextureFiles.size}")
        textLine("Atlases: ${state.project.discoveredAtlasFiles.size}")
        textLine(".krmeta: ${state.project.discoveredMetadataFiles.size}")
        ImGui.separator()

        ImGui.beginChild("texture_manager_assets_list", ImVec2(0f, 0f), true)
        visibleAssets().forEach { asset ->
            val selected = state.selectedAssetId == asset.id
            val label = "${kindLabel(asset.kind)} ${asset.displayName}##${asset.id.value}"
            if (ImGui.selectable(label, selected)) {
                operations.selectAsset(asset.id)
            }
            if (selected) {
                ImGui.textWrapped("  ${asset.path}")
            }
        }
        if (visibleAssets().isEmpty()) {
            ImGui.textUnformatted("No texture or atlas assets match the current filter.")
        }
        ImGui.endChild()
        ImGui.end()
    }

    private fun visibleAssets() =
        state.project.assets.filter { asset ->
            state.assetBrowser.query.isBlank() ||
                asset.displayName.contains(state.assetBrowser.query, ignoreCase = true) ||
                asset.path.contains(state.assetBrowser.query, ignoreCase = true)
        }

    private fun kindLabel(kind: TextureManagerAssetKind): String =
        when (kind) {
            TextureManagerAssetKind.Texture -> "[Texture]"
            TextureManagerAssetKind.Atlas -> "[Atlas]"
            TextureManagerAssetKind.Directory -> "[Dir]"
            TextureManagerAssetKind.Unknown -> "[?]"
        }

    companion object {
        private const val BufferSize = 256
    }
}

