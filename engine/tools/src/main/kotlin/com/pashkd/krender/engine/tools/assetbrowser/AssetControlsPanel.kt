package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.*
import com.pashkd.krender.engine.ui.editor.*
import imgui.ImGui
import imgui.dsl

/**
 * Top-level control panel for asset creation, layout persistence, and quick launch shortcuts.
 */
class AssetControlsPanel(
    private val state: AssetBrowserState,
    private val operations: AssetBrowserUiOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
    private val panelId: String = AssetBrowserPanelIds.Controls,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels[panelId] ?: return
        val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
        eventLogger.observe(panelId, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        with(dsl) {
            button("Create Asset##${panelId}_create_asset") {
                state.createDraft = defaultCreateAssetDraft(state.assets)
                state.showCreateDialog = true
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Import Asset##${panelId}_import_asset") {
                state.showImportDialog = true
                state.importPlan = null
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Export Asset##${panelId}_export_asset") {
                state.statusMessage = "Export Asset is not implemented."
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Persist UI##${panelId}_persist_ui") {
                operations.saveUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset UI to Default##${panelId}_reset_ui") {
                operations.restoreUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Woolboy App Info##${panelId}_woolboy_app_info") {
                operations.showWoolboyAppInfo()
            }
        }

        ImGui.separator()
        assetBrowserTextLine("Status: ${state.statusMessage}")
        state.errorMessage?.let { error -> assetBrowserTextLine("Error: $error") }
        assetBrowserTextLine("Selected Asset: ${selectedAssetName()}")
        assetBrowserTextLine("Assets: ${state.filteredAssets.size} / ${state.assets.size}")
        ImGui.end()
    }

    private fun selectedAssetName(): String =
        state.selectedAssetId
            ?.let { selectedId -> state.assets.firstOrNull { asset -> asset.id == selectedId } }
            ?.name
            ?: "None"
}
