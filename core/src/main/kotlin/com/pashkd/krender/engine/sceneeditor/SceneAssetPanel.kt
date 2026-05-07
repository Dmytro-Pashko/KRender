package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetRegistrySnapshot
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import glm_.vec2.Vec2
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider
import imgui.dsl

/**
 * Focused UI state for the embedded Scene Editor asset panel.
 */
data class SceneAssetPanelState(
    var searchQuery: String = "",
    var selectedAssetPath: String? = null,
    var selectedAssetCategory: AssetCategory? = null,
    var statusMessage: String = "",
)

/**
 * Small Scene Editor asset browser model backed by the shared asset registry service.
 */
class SceneAssetBrowserModel(
    private val registry: AssetRegistryService,
    private val tasks: TaskService,
    private val logger: Logger,
    private val state: SceneAssetPanelState,
) {
    private var initialScanRequested = false
    private var scanInFlight = false
    private var assets: List<AssetDescriptor> = emptyList()

    var isScanning: Boolean = false
        private set

    var errorMessage: String? = null
        private set

    fun update() {
        if (!initialScanRequested) {
            initialScanRequested = true
            requestRefresh(reason = "initial")
        }
    }

    fun requestRefresh(reason: String = "refresh") {
        if (scanInFlight) {
            state.statusMessage = "Asset scan already in progress."
            return
        }

        scanInFlight = true
        isScanning = true
        errorMessage = null
        state.statusMessage = "Scanning assets ($reason)..."
        tasks.launchBackground("scene-asset-panel-scan") {
            val snapshot = try {
                registry.scanSnapshot()
            } catch (error: Exception) {
                logger.error(TAG, error) { "Scene asset scan failed: ${error.message}" }
                tasks.postToMain {
                    scanInFlight = false
                    isScanning = false
                    errorMessage = "Asset scan failed: ${error.message}"
                    state.statusMessage = errorMessage.orEmpty()
                }
                return@launchBackground
            }
            tasks.postToMain {
                applyScanResult(snapshot)
            }
        }
    }

    fun modelAssets(): List<AssetDescriptor> =
        assets.filter { asset -> asset.category == AssetCategory.Model }

    fun terrainAssets(): List<AssetDescriptor> =
        assets.filter { asset -> asset.category == AssetCategory.Terrain }

    fun filteredModelAssets(): List<AssetDescriptor> {
        val query = state.searchQuery.trim().lowercase()
        return modelAssets()
            .asSequence()
            .filter { asset ->
                query.isBlank() ||
                    asset.name.lowercase().contains(query) ||
                    asset.path.lowercase().contains(query)
            }
            .sortedWith(compareBy<AssetDescriptor> { it.name.lowercase() }.thenBy { it.path.lowercase() })
            .toList()
    }

    fun filteredTerrainAssets(): List<AssetDescriptor> {
        val query = state.searchQuery.trim().lowercase()
        return terrainAssets()
            .asSequence()
            .filter { asset ->
                query.isBlank() ||
                    asset.name.lowercase().contains(query) ||
                    asset.path.lowercase().contains(query)
            }
            .sortedWith(compareBy<AssetDescriptor> { it.name.lowercase() }.thenBy { it.path.lowercase() })
            .toList()
    }

    private fun applyScanResult(snapshot: AssetRegistrySnapshot) {
        registry.applySnapshot(snapshot)
        assets = snapshot.assets
        errorMessage = snapshot.errors.firstOrNull()?.let { "Scan error: ${it.path} (${it.message})" }
        state.statusMessage = "Indexed ${modelAssets().size} model assets and ${terrainAssets().size} terrain assets."
        isScanning = false
        scanInFlight = false
    }

    companion object {
        private const val TAG = "SceneAssetBrowserModel"
    }
}

/**
 * Drives the embedded asset browser scan lifecycle outside ImGui rendering.
 */
class SceneAssetBrowserSystem(
    private val model: SceneAssetBrowserModel,
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        model.update()
    }
}

/**
 * Embedded Scene Editor asset panel for selecting and placing scene assets.
 */
class SceneAssetPanel(
    private val panelState: SceneAssetPanelState,
    private val editorState: SceneEditorState,
    private val assetBrowser: SceneAssetBrowserModel,
    private val operations: SceneEditorOperations,
    private val engine: EngineContext,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val searchBuffer = ByteArray(TextInputBufferSize)
    private var searchInputActive = false

    override fun draw() {
        syncSearchBuffer()

        val expanded = beginSceneEditorPanel(SceneEditorPanelIds.Assets, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawToolbar()
        ImGui.separator()
        drawAssetsList()
        ImGui.separator()
        drawPlacementControls()
        drawStatus()

        ImGui.end()
    }

    private fun drawToolbar() {
        ImGui.text("Search")
        ImGui.sameLine()
        if (safeInputText("##scene_assets_search", searchBuffer)) {
            panelState.searchQuery = readBuffer(searchBuffer)
        }
        searchInputActive = ImGui.isItemActive

        ImGui.sameLine()
        with(dsl) {
            button("Refresh##scene_assets_refresh") {
                assetBrowser.requestRefresh()
            }
        }
        if (assetBrowser.isScanning) {
            ImGui.sameLine()
            ImGui.text("Scanning...")
        }
    }

    private fun drawAssetsList() {
        ImGui.text("Models:")
        ImGui.beginChild("scene_assets_model_list", Vec2(0f, AssetListHeight), true)
        val allModels = assetBrowser.modelAssets()
        val visibleModels = assetBrowser.filteredModelAssets()
        when {
            allModels.isEmpty() -> {
                ImGui.text("No model assets found.")
                with(dsl) {
                    button("Refresh##scene_assets_empty_refresh") {
                        assetBrowser.requestRefresh()
                    }
                }
            }
            visibleModels.isEmpty() -> {
                ImGui.text("No model assets match the current search.")
            }
            else -> visibleModels.forEach(::drawAssetRow)
        }
        ImGui.endChild()
        drawModelSelectionDetails()

        ImGui.separator()

        ImGui.text("Terrains:")
        ImGui.beginChild("scene_assets_terrain_list", Vec2(0f, AssetListHeight), true)
        val allTerrains = assetBrowser.terrainAssets()
        val visibleTerrains = assetBrowser.filteredTerrainAssets()
        when {
            allTerrains.isEmpty() -> {
                ImGui.text("No terrain assets found.")
                with(dsl) {
                    button("Refresh##scene_assets_empty_terrain_refresh") {
                        assetBrowser.requestRefresh()
                    }
                }
            }
            visibleTerrains.isEmpty() -> {
                ImGui.text("No terrain assets match the current search.")
            }
            else -> visibleTerrains.forEach(::drawAssetRow)
        }
        ImGui.endChild()
        drawTerrainSelectionDetails()
    }

    private fun drawAssetRow(asset: AssetDescriptor) {
        val selected = panelState.selectedAssetPath == asset.path && panelState.selectedAssetCategory == asset.category
        if (ImGui.selectable("${asset.name}##scene_asset_${asset.id.value}", selected)) {
            selectAsset(asset)
        }
        ImGui.text("  ${asset.path}")
    }

    private fun drawPlacementControls() {
        ImGui.text("Placement Options:")
        slider(
            "Distance##scene_assets_place_model_distance",
            editorState::placeModelDistance,
            MinPlaceModelDistance,
            MaxPlaceModelDistance,
            "%.1f",
            SliderFlag.AlwaysClamp,
        )
    }

    private fun drawModelSelectionDetails() {
        val modelPath = selectedModelPath()
        ImGui.text("Selected model: ${selectedModelName()}")
        ImGui.textWrapped("Model Path: ${modelPath ?: "<none>"}")
        with(dsl) {
            button("Place Selected Model##scene_assets_place_selected") {
                placeSelectedModel()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Open in Model Viewer##scene_assets_open_model_viewer") {
                openSelectedInModelViewer()
            }
        }
        editorState.modelPlacementError?.let { error ->
            ImGui.text("Last error: $error")
        }
    }

    private fun drawTerrainSelectionDetails() {
        val terrainPath = selectedTerrainPath()
        ImGui.text("Selected terrain: ${selectedTerrainName()}")
        ImGui.textWrapped("Terrain Path: ${terrainPath ?: "<none>"}")
        with(dsl) {
            button("Place Terrain##scene_assets_place_selected_terrain") {
                placeSelectedTerrain()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Open in Terrain Editor##scene_assets_open_terrain_editor") {
                openSelectedInTerrainEditor()
            }
        }
        editorState.terrainPlacementError?.let { error ->
            ImGui.text("Last terrain error: $error")
        }
    }

    private fun drawStatus() {
        if (panelState.statusMessage.isNotBlank()) {
            ImGui.text("Status: ${panelState.statusMessage}")
        }
        assetBrowser.errorMessage?.let { error ->
            ImGui.text("Error: $error")
        }
    }

    private fun selectAsset(asset: AssetDescriptor) {
        panelState.selectedAssetPath = asset.path
        panelState.selectedAssetCategory = asset.category
        when (asset.category) {
            AssetCategory.Model -> {
                editorState.modelPlacementPath = asset.path
                editorState.modelPlacementError = null
                panelState.statusMessage = "Selected model: ${asset.path}"
            }
            AssetCategory.Terrain -> {
                editorState.terrainPlacementPath = asset.path
                editorState.terrainPlacementError = null
                panelState.statusMessage = "Selected terrain: ${asset.path}"
            }
            else -> {
                panelState.statusMessage = "Selected asset: ${asset.path}"
            }
        }
        editorState.statusMessage = panelState.statusMessage
    }

    private fun placeSelectedModel() {
        val selectedPath = selectedModelPath()
        if (selectedPath.isNullOrBlank()) {
            panelState.statusMessage = "Select a model first."
            editorState.statusMessage = panelState.statusMessage
            return
        }

        operations.placeModel(selectedPath)
        panelState.statusMessage = editorState.statusMessage
    }

    private fun openSelectedInModelViewer() {
        val selectedPath = selectedModelPath()
        if (selectedPath.isNullOrBlank()) {
            panelState.statusMessage = "Select a model first."
            editorState.statusMessage = panelState.statusMessage
            return
        }

        try {
            engine.editorToolLauncher.launchModelViewer(selectedPath)
            panelState.statusMessage = "Opened in Model Viewer: $selectedPath"
            editorState.statusMessage = panelState.statusMessage
        } catch (error: Exception) {
            panelState.statusMessage = "Failed to open Model Viewer: ${error.message}"
            editorState.statusMessage = panelState.statusMessage
            engine.logger.error(TAG, error) { "Failed to open model '$selectedPath' in Model Viewer: ${error.message}" }
        }
    }

    private fun placeSelectedTerrain() {
        val selectedPath = selectedTerrainPath()
        if (selectedPath.isNullOrBlank()) {
            panelState.statusMessage = "Select a terrain first."
            editorState.statusMessage = panelState.statusMessage
            return
        }

        operations.placeTerrain(selectedPath)
        panelState.statusMessage = editorState.statusMessage
    }

    private fun openSelectedInTerrainEditor() {
        val terrainPath = selectedTerrainPath()
        if (terrainPath.isNullOrBlank()) {
            panelState.statusMessage = "Select a terrain first."
            editorState.statusMessage = panelState.statusMessage
            return
        }

        operations.openTerrainInEditor(terrainPath)
        panelState.statusMessage = editorState.statusMessage
    }

    private fun syncSearchBuffer() {
        if (!searchInputActive && readBuffer(searchBuffer) != panelState.searchQuery) {
            writeBuffer(searchBuffer, panelState.searchQuery)
        }
    }

    private fun selectedModelPath(): String? =
        when (panelState.selectedAssetCategory) {
            AssetCategory.Model -> panelState.selectedAssetPath
            else -> editorState.modelPlacementPath.takeIf(String::isNotBlank)
        }

    private fun selectedTerrainPath(): String? =
        when (panelState.selectedAssetCategory) {
            AssetCategory.Terrain -> panelState.selectedAssetPath
            else -> editorState.terrainPlacementPath.takeIf(String::isNotBlank)
        }

    private fun selectedModelName(): String =
        selectedAssetName(AssetCategory.Model, selectedModelPath())

    private fun selectedTerrainName(): String =
        selectedAssetName(AssetCategory.Terrain, selectedTerrainPath())

    private fun selectedAssetName(category: AssetCategory, path: String?): String {
        val assetPath = path ?: return "<none>"
        val asset = when (category) {
            AssetCategory.Model -> assetBrowser.modelAssets().firstOrNull { descriptor -> descriptor.path == assetPath }
            AssetCategory.Terrain -> assetBrowser.terrainAssets().firstOrNull { descriptor -> descriptor.path == assetPath }
            else -> null
        }
        return asset?.name?.takeIf(String::isNotBlank) ?: assetNameFromPath(assetPath)
    }

    private fun assetNameFromPath(path: String): String {
        val fileName = path.substringAfterLast('/').substringAfterLast('\\')
        return fileName.ifBlank { path }
    }

    companion object {
        private const val TAG = "SceneAssetPanel"
        private const val TextInputBufferSize = 256
        private const val AssetListHeight = 88f
        private const val MinPlaceModelDistance = 0f
        private const val MaxPlaceModelDistance = 100f
    }
}
