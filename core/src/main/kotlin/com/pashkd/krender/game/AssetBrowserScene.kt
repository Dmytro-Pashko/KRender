package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.AssetBrowserPanel
import com.pashkd.krender.engine.assets.AssetBrowserState
import com.pashkd.krender.engine.assets.AssetBrowserSystem
import com.pashkd.krender.engine.assets.AssetBrowserUiLayoutDefaults
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetDetailsPanel
import com.pashkd.krender.engine.assets.LocalAssetRegistryService
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.LogsPanel
import com.pashkd.krender.engine.ui.UiSystem

/**
 * Main editor tool scene for browsing, inspecting, and opening engine assets.
 */
class AssetBrowserScene : Scene("asset_browser") {
    private lateinit var registry: LocalAssetRegistryService
    private lateinit var browserState: AssetBrowserState

    override fun show() {
        engine.logger.info(TAG) { "Showing Asset Browser scene" }
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = AssetBrowserUiLayoutDefaults.assetPath,
            fallback = AssetBrowserUiLayoutDefaults.config,
        ).load(engine.logger)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "AssetBrowserUi")

        registry = LocalAssetRegistryService(engine.logger)
        browserState = AssetBrowserState()

        world.systems.add(
            AssetBrowserSystem(
                registry = registry,
                assets = engine.assets,
                logger = engine.logger,
                state = browserState,
                onAssetActivated = ::openAsset,
            ),
        )
        world.systems.add(createUiSystem(layoutConfig, panelEventLogger))
    }

    private fun createUiSystem(
        layoutConfig: ImGuiLayoutConfig,
        panelEventLogger: ImGuiWindowEventLogger,
    ): UiSystem =
        UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(
                AssetBrowserPanel(
                    state = browserState,
                    onAssetSelected = { asset -> browserState.selectedAssetId = asset.id },
                    onAssetActivated = { asset -> browserState.activationRequestedAssetId = asset.id },
                    layoutConfig = layoutConfig,
                    eventLogger = panelEventLogger,
                ),
            )
            uiSystem.addPanel(AssetDetailsPanel(browserState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(LogsPanel(engine.logs, layoutConfig, panelEventLogger))
        }

    private fun openAsset(asset: AssetDescriptor) {
        when (asset.category) {
            AssetCategory.Model -> {
                engine.logger.info(TAG) { "Opening model asset '${asset.path}' in ModelViewerScene" }
                engine.scenes.replace(
                    ModelViewerScene(
                        model = AssetRef.model(asset.path),
                        availableModels = registry.byCategory(AssetCategory.Model).map { AssetRef.model(it.path) },
                    ),
                )
            }

            AssetCategory.Terrain -> {
                engine.logger.info(TAG) { "Opening terrain asset '${asset.path}' in TerrainEditorScene" }
                engine.scenes.replace(
                    TerrainEditorScene(
                        terrainFilePath = asset.path,
                    ),
                )
            }

            else -> {
                browserState.statusMessage = "No default editor exists for ${asset.category.displayName} assets."
                engine.logger.info(TAG) {
                    "No default editor exists for asset '${asset.path}' category=${asset.category} type=${asset.type}"
                }
            }
        }
    }

    companion object {
        private const val TAG = "AssetBrowserScene"
    }
}
