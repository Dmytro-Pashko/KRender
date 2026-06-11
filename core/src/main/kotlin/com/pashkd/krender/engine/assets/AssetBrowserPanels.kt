package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.MouseButton
import imgui.dsl
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reusable ImGui asset browser panel.
 */
class AssetBrowserPanel(
    private val state: AssetBrowserState,
    private val onAssetSelected: (AssetDescriptor) -> Unit,
    private val onAssetActivated: (AssetDescriptor) -> Unit,
    private val acceptedCategories: Set<AssetCategory>? = null,
    private val mode: AssetBrowserMode = AssetBrowserMode.Full,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker? = null,
    private val eventLogger: ImGuiWindowEventLogger,
    private val panelId: String = AssetBrowserPanelIds.Browser,
    private val operations: AssetBrowserOperationsHandler = AssetBrowserOperationsHandler.NoOp,
) : UiPanel {
    private val searchBuffer = ByteArray(TextInputBufferSize)
    private val createNameByteBuffer = ByteArray(TextInputBufferSize)
    private val renameByteBuffer = ByteArray(TextInputBufferSize)
    private var searchInputActive = false
    private var createBufferSynced = false
    private var renameBufferSynced = false

    override fun draw() {
        syncSearchBuffer()
        val layout = layoutConfig.panels[panelId] ?: return
        val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
        eventLogger.observe(panelId, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawToolbar()
        ImGui.separator()
        drawBrowserBody()

        ImGui.end()

        if (mode == AssetBrowserMode.Full) {
            drawCreateDialog()
            drawRenameDialog()
            drawDeleteDialog()
        }
    }

    private fun drawToolbar() {
        ImGui.text("Search")
        ImGui.sameLine()
        if (ImGui.inputText("##${panelId}_search", searchBuffer)) {
            state.searchQuery = readBuffer(searchBuffer)
        }
        searchInputActive = ImGui.isItemActive

        ImGui.sameLine()
        with(dsl) {
            button("[x]##${panelId}_clear_search") {
                state.searchQuery = ""
                writeBuffer(searchBuffer, "")
            }
        }

        ImGui.sameLine()
        with(dsl) {
            button("Refresh##$panelId") {
                state.refreshRequested = true
                state.statusMessage = "Refresh requested."
            }
        }

        if (mode == AssetBrowserMode.Full) {
            ImGui.sameLine()
            drawSortModeCombo()
        }

        if (state.isScanning) {
            ImGui.sameLine()
            ImGui.textUnformatted("Scanning...")
        }
    }

    private fun drawBrowserBody() {
        if (mode != AssetBrowserMode.Picker) {
            ImGui.beginChild("${panelId}_categories", Vec2(150f, 0f), true)
            drawCategoryList()
            ImGui.endChild()
            ImGui.sameLine()
        }

        ImGui.beginChild("${panelId}_assets", Vec2(0f, 0f), true)
        val assets = visibleAssets()
        if (assets.isEmpty()) {
            ImGui.text("No assets match the current filters.")
        } else {
            drawList(assets)
        }
        ImGui.endChild()
    }

    private fun drawCategoryList() {
        if (ImGui.selectable("All (${visibleCategoryCount(null)})##${panelId}_cat_all", state.selectedCategory == null)) {
            state.selectedCategory = null
        }

        SupportedBrowserCategories
            .sortedBy(AssetCategory::sortOrder)
            .filter(::categoryAccepted)
            .forEach { category ->
                val label = "${category.displayName} (${visibleCategoryCount(category)})##${panelId}_cat_${category.name}"
                if (ImGui.selectable(label, state.selectedCategory == category)) {
                    state.selectedCategory = category
                }
            }
    }

    private fun drawList(assets: List<AssetDescriptor>) {
        assets.forEach { asset ->
            val label = "${assetIcon(asset)} ${asset.name}  ${asset.type.name}  ${asset.path} (${formatByteCount(asset.sizeBytes)})##asset_${asset.id.value}"
            drawSelectableAsset(asset, label)
        }
    }

    private fun drawSelectableAsset(asset: AssetDescriptor, label: String) {
        if (ImGui.selectable(label, state.selectedAssetId == asset.id)) {
            onAssetSelected(asset)
        }
        if (ImGui.isItemHovered() && ImGui.run { MouseButton.Left.isDoubleClicked }) {
            onAssetSelected(asset)
            onAssetActivated(asset)
        }
        drawAssetContextMenu(asset)
    }

    private fun drawAssetContextMenu(asset: AssetDescriptor) {
        if (mode != AssetBrowserMode.Full) return
        if (!ImGui.beginPopupContextItem("assetCtx_${asset.id.value}")) return
        if (ImGui.menuItem("Open")) {
            onAssetSelected(asset)
            onAssetActivated(asset)
        }
        val tools = operations.toolsFor(asset)
        if (tools.isNotEmpty() && ImGui.beginMenu("Open With")) {
            tools.forEach { toolId ->
                if (ImGui.menuItem(toolId.label)) {
                    onAssetSelected(asset)
                    operations.openWith(asset, toolId.id)
                }
            }
            ImGui.endMenu()
        }
        ImGui.separator()
        if (ImGui.menuItem("Rename...")) {
            onAssetSelected(asset)
            state.renameBuffer = asset.name
            renameBufferSynced = false
            state.showRenameDialog = true
        }
        if (ImGui.menuItem("Duplicate")) {
            onAssetSelected(asset)
            operations.duplicate(asset, "${baseName(asset)}_copy")
        }
        if (ImGui.menuItem("Delete")) {
            onAssetSelected(asset)
            state.showDeleteDialog = true
        }
        ImGui.separator()
        if (ImGui.menuItem("Reveal in Files")) {
            operations.reveal(asset)
        }
        ImGui.endPopup()
    }

    private fun baseName(asset: AssetDescriptor): String =
        asset.name

    private fun drawSortModeCombo() {
        if (!ImGui.beginCombo("Sort##${panelId}_sort", sortModeLabel(state.sortMode))) return
        AssetSortMode.entries.forEach { sortMode ->
            if (ImGui.selectable("${sortModeLabel(sortMode)}##${panelId}_sort_$sortMode", state.sortMode == sortMode)) {
                state.sortMode = sortMode
            }
        }
        ImGui.endCombo()
    }

    private fun visibleAssets(): List<AssetDescriptor> =
        state.filteredAssets.filter { asset -> categoryAccepted(asset.category) }

    private fun visibleCategoryCount(category: AssetCategory?): Int =
        state.assets.count { asset ->
            categoryAccepted(asset.category) && (category == null || asset.category == category)
        }

    private fun categoryAccepted(category: AssetCategory): Boolean =
        category in SupportedBrowserCategories && (acceptedCategories == null || category in acceptedCategories)

    private fun syncSearchBuffer() {
        if (!searchInputActive && readBuffer(searchBuffer) != state.searchQuery) {
            writeBuffer(searchBuffer, state.searchQuery)
        }
    }

    private fun drawCreateDialog() {
        if (!state.showCreateDialog) return
        if (!createBufferSynced) {
            writeBuffer(createNameByteBuffer, state.createDraft.name)
            createBufferSynced = true
        }
        ImGui.openPopup("Create Asset##${panelId}_create")
        ImGui.setNextWindowSize(Vec2(500f, 250f), Cond.Always)
        if (!ImGui.beginPopupModal("Create Asset##${panelId}_create")) return

        drawCreateAssetKindSelector()
        ImGui.text("Name")
        ImGui.sameLine()
        ImGui.pushItemWidth(330f)
        if (ImGui.inputText("##${panelId}_create_name", createNameByteBuffer)) {
            state.createDraft = state.createDraft.copy(name = readBuffer(createNameByteBuffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        textLine(".${state.createDraft.kind.extension}")
        drawCreateUiSceneSkinSelector()

        ImGui.separator()
        with(dsl) {
            button("Create##${panelId}_create_ok") {
                operations.create(state.createDraft.withSyncedDefaults(state.assets))
                state.showCreateDialog = false
                createBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_create_cancel") {
                state.showCreateDialog = false
                createBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }

        ImGui.separator()
        drawCreateAssetMetadata()
        ImGui.endPopup()
    }

    private fun drawCreateAssetKindSelector() {
        if (!ImGui.beginCombo("Asset Type##${panelId}_create_kind", state.createDraft.kind.displayName)) return
        CreatableAssetKind.entries.forEach { kind ->
            if (ImGui.selectable(kind.displayName, state.createDraft.kind == kind)) {
                state.createDraft = state.createDraft.copy(kind = kind).withSyncedDefaults(state.assets)
                if (kind == CreatableAssetKind.UiScene) {
                    writeBuffer(createNameByteBuffer, state.createDraft.name)
                }
            }
        }
        ImGui.endCombo()
    }

    private fun drawCreateUiSceneSkinSelector() {
        if (state.createDraft.kind != CreatableAssetKind.UiScene) return
        state.createDraft = state.createDraft.withSyncedDefaults(state.assets)
        val skinAssets = discoveredScene2DSkinAssets(state.assets)
        ImGui.text("Skin")
        ImGui.sameLine()
        val currentLabel = skinAssets
            .firstOrNull { asset -> asset.path == state.createDraft.uiSceneSkinPath }
            ?.let { asset -> "${asset.name} (${asset.path})" }
            ?: state.createDraft.uiSceneSkinPath
        if (!ImGui.beginCombo("##${panelId}_create_ui_skin", currentLabel)) return
        skinAssets.forEach { asset ->
            val label = "${asset.name} (${asset.path})##${panelId}_create_ui_skin_${asset.id.value}"
            if (ImGui.selectable(label, state.createDraft.uiSceneSkinPath == asset.path)) {
                state.createDraft = state.createDraft.copy(uiSceneSkinPath = asset.path)
            }
        }
        if (skinAssets.isEmpty()) {
            ImGui.textUnformatted("No Scene2D Skin assets indexed.")
        }
        ImGui.endCombo()
    }

    private fun drawCreateAssetMetadata() {
        val draft = state.createDraft.withSyncedDefaults(state.assets)
        val path = createAssetRelativePath(draft)
        val exists = state.assets.any { asset -> asset.path.equals(path, ignoreCase = true) }
        ImGui.text("Metadata")
        textLine("File: $path")
        textLine("Is Already Exist: ${if (exists) "Yes" else "No"}")
        textLine("Default params:")
        createAssetDefaultParams(draft).forEach { param ->
            textLine("  $param")
        }
    }

    private fun drawRenameDialog() {
        if (!state.showRenameDialog) return
        val asset = state.selectedAssetId?.let { id -> state.assets.firstOrNull { it.id == id } }
        if (asset == null) {
            state.showRenameDialog = false
            return
        }
        if (!renameBufferSynced) {
            writeBuffer(renameByteBuffer, state.renameBuffer)
            renameBufferSynced = true
        }
        ImGui.openPopup("Rename Asset##${panelId}_rename")
        if (!ImGui.beginPopupModal("Rename Asset##${panelId}_rename")) return
        textLine("Path: ${asset.path}")
        ImGui.text("New name")
        ImGui.sameLine()
        if (ImGui.inputText("##${panelId}_rename_name", renameByteBuffer)) {
            state.renameBuffer = readBuffer(renameByteBuffer)
        }
        ImGui.separator()
        with(dsl) {
            button("Rename##${panelId}_rename_ok") {
                operations.rename(asset, state.renameBuffer)
                state.showRenameDialog = false
                renameBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_rename_cancel") {
                state.showRenameDialog = false
                renameBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    private fun drawDeleteDialog() {
        if (!state.showDeleteDialog) return
        val asset = state.selectedAssetId?.let { id -> state.assets.firstOrNull { it.id == id } }
        if (asset == null) {
            state.showDeleteDialog = false
            return
        }
        ImGui.openPopup("Delete Asset##${panelId}_delete")
        if (!ImGui.beginPopupModal("Delete Asset##${panelId}_delete")) return
        ImGui.textUnformatted("Permanently delete '${asset.name}'?")
        textLine("Path: ${asset.path}")
        ImGui.separator()
        with(dsl) {
            button("Delete##${panelId}_delete_ok") {
                operations.delete(asset)
                state.showDeleteDialog = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_delete_cancel") {
                state.showDeleteDialog = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    companion object {
        private const val TextInputBufferSize = 256
    }
}

/**
 * Top-level control panel for layout persistence and sandbox launch shortcuts.
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
                state.statusMessage = "Import Asset is not implemented."
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
            button("Play Woolboy Scene MVP##${panelId}_play_woolboy") {
                operations.playWoolboyScene()
            }
        }

        ImGui.separator()
        textLine("Status: ${state.statusMessage}")
        state.errorMessage?.let { error -> textLine("Error: $error") }
        textLine("Selected Asset: ${selectedAssetName()}")
        textLine("Assets: ${state.filteredAssets.size} / ${state.assets.size}")
        ImGui.end()
    }

    private fun selectedAssetName(): String =
        state.selectedAssetId
            ?.let { selectedId -> state.assets.firstOrNull { asset -> asset.id == selectedId } }
            ?.name
            ?: "None"
}

/**
 * Shows details for the currently selected asset.
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
        if (asset.category == AssetCategory.Model) {
            ImGui.separator()
            drawModelInfo(state.selectedModelInfo)
        }

        ImGui.end()
    }

    private fun drawActions(asset: AssetDescriptor) {
        if (asset.category == AssetCategory.Scene) return
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

    private fun drawBaseInfo(asset: AssetDescriptor) {
        textLine("Name: ${asset.name}")
        textLine("ID: ${asset.id.value}")
        textLine("Category: ${asset.category.displayName}")
        textLine("Type: ${asset.type.name}")
        textLine("Path: ${asset.path}")
        textLine("Extension: ${asset.extension.ifBlank { "none" }}")
        textLine("Size: ${formatByteCount(asset.sizeBytes)}")
        textLine("Last modified: ${formatTimestamp(asset.modifiedAtMillis)}")
        textLine("Tags: ${asset.tags.ifEmpty { listOf("none") }.joinToString(", ")}")

        ImGui.separator()
        if (asset.category == AssetCategory.Texture) {
            drawTextureMetadata(asset)
            return
        }
        if (asset.category == AssetCategory.Terrain) {
            drawTerrainMetadata(asset)
            return
        }
        if (asset.category == AssetCategory.UI) {
            if (asset.type == AssetType.Scene2DSkin) {
                drawScene2DSkinMetadata(asset)
            } else {
                drawUiSceneMetadata(asset)
            }
            return
        }
        if (asset.category == AssetCategory.Scene) {
            drawSceneMetadata(asset)
            return
        }

        if (asset.metadata.isEmpty()) {
            ImGui.text("none")
        } else {
            asset.metadata.forEach { (key, value) ->
                textLine("$key: $value")
            }
        }
    }

    private fun drawModelInfo(info: ModelAssetInfo?) {
        ImGui.text("Model runtime metadata")
        textLine("Status: ${state.selectedModelStatus}")
        if (info == null) {
            ImGui.text("Metadata is available after the model finishes loading.")
            return
        }

        textLine("Format: ${info.format}")
        textLine("Nodes: ${info.nodeCount}")
        textLine("Meshes: ${info.meshCount}")
        textLine("Mesh parts: ${info.meshPartCount}")
        textLine("Materials: ${info.materialCount}")
        textLine("Vertices: ${info.vertexCount}")
        textLine("Triangles: ${info.triangleCount}")
        textLine("Textures: ${info.textureCount} unique / ${info.textureSlotCount} slots")
        textLine("Animations: ${info.animationCount}")
        textLine("Skeleton: ${if (info.hasSkeleton) "yes" else "no"}")
        info.size?.let { size ->
            ImGui.text("Bounds: %.2f x %.2f x %.2f", size.x, size.y, size.z)
        }
    }

    private fun drawTextureMetadata(asset: AssetDescriptor) {
        ImGui.text("Preview")
        drawTexturePreview(asset)
        ImGui.separator()
        ImGui.text("Metadata")
        val resolution = asset.metadata["textureResolution"] ?: "unknown"
        val colorFormat = asset.metadata["textureColorFormat"] ?: "unknown"
        textLine("Source resolution: $resolution")
        textLine("Format: $colorFormat")
    }

    private fun drawTexturePreview(asset: AssetDescriptor) {
        val handle = assets.texturePreviewHandle(asset.path)
        if (handle == null) {
            textLine("Preview unavailable.")
            return
        }
        if (!ui.drawTexturePreview(handle, AssetTexturePreviewSize, AssetTexturePreviewSize)) {
            textLine("Preview unavailable.")
            return
        }
        textLine("Preview size: ${AssetTexturePreviewSize.toInt()} x ${AssetTexturePreviewSize.toInt()}")
    }

    private fun drawTerrainMetadata(asset: AssetDescriptor) {
        ImGui.text("Metadata")
        textLine("Size: ${asset.metadata["terrainSize"] ?: "unknown"}")
        textLine("Layers: ${asset.metadata["terrainLayerCount"] ?: "unknown"}")
    }

    /**
     * Shows lightweight `.krui` indexing data without attempting UI Composer preview or editing.
     */
    private fun drawUiSceneMetadata(asset: AssetDescriptor) {
        ImGui.text("Metadata")
        textLine("Document ID: ${asset.metadata["uiSceneDocumentId"] ?: "unknown"}")
        textLine("Skin: ${asset.metadata["uiSceneSkinPath"] ?: "unknown"}")
        textLine("Schema: ${asset.metadata["uiSceneSchemaVersion"] ?: "unknown"}")
        textLine("Status: ${asset.metadata["uiSceneStatus"] ?: "unknown"}")
        asset.metadata["uiSceneParseError"]?.let { error -> textLine("Parse error: $error") }

        ImGui.separator()
        ImGui.text("Diagnostics")
        textLine("Validation warnings: ${asset.metadata["uiSceneValidationWarningCount"] ?: "0"}")
        asset.metadata["uiSceneValidationIssuePreview"]?.let { preview -> textLine("Issues: $preview") }
    }

    private fun drawScene2DSkinMetadata(asset: AssetDescriptor) {
        ImGui.text("Metadata")
        textLine("Status: ${asset.metadata["skinStatus"] ?: "unknown"}")
        asset.metadata["skinParseError"]?.let { error -> textLine("Parse error: $error") }

        ImGui.separator()
        textLine("Preview: ${asset.metadata["skinPreview"] ?: "unknown"}")
        textLine("Colors: ${asset.metadata["skinColorCount"] ?: "0"}")
        textLine("Drawables: ${asset.metadata["skinDrawableCount"] ?: "0"}")
        textLine("Texture regions: ${asset.metadata["skinTextureRegionCount"] ?: "0"}")
        textLine("Style classes: ${asset.metadata["skinStyleClassCount"] ?: "0"}")

        ImGui.separator()
        ImGui.text("Styles")
        textLine("Labels: ${asset.metadata["skinLabelStyleCount"] ?: "0"}")
        textLine("Text buttons: ${asset.metadata["skinTextButtonStyleCount"] ?: "0"}")
        textLine("Progress bars: ${asset.metadata["skinProgressBarStyleCount"] ?: "0"}")
        textLine("Image buttons: ${asset.metadata["skinImageButtonStyleCount"] ?: "0"}")
        textLine("Check boxes: ${asset.metadata["skinCheckBoxStyleCount"] ?: "0"}")
        textLine("Text fields: ${asset.metadata["skinTextFieldStyleCount"] ?: "0"}")
        textLine("Scroll panes: ${asset.metadata["skinScrollPaneStyleCount"] ?: "0"}")
        textLine("Select boxes: ${asset.metadata["skinSelectBoxStyleCount"] ?: "0"}")
        textLine("Windows: ${asset.metadata["skinWindowStyleCount"] ?: "0"}")
    }

    private fun drawSceneMetadata(asset: AssetDescriptor) {
        ImGui.text("Metadata")
        textLine("Scene Name: ${asset.metadata["sceneName"] ?: asset.name}")
        textLine("Schema: ${asset.metadata["sceneSchemaVersion"] ?: "unknown"}")

        val sceneTools = operations.toolsFor(asset)
        if (sceneTools.isNotEmpty()) {
            ImGui.separator()
            ImGui.text("Scene actions")
            sceneTools.forEachIndexed { index, tool ->
                with(dsl) {
                    button("${tool.label}##${panelId}_scene_tool_${tool.id}") {
                        operations.openWith(asset, tool.id)
                    }
                }
                if (index < sceneTools.lastIndex) {
                    ImGui.sameLine()
                }
            }
        }

        ImGui.separator()
        ImGui.text("Contents")
        textLine(
            "Entities: ${asset.metadata["sceneEntityCount"] ?: "0"} " +
                "(${asset.metadata["sceneActiveEntityCount"] ?: "0"} active, " +
                "${asset.metadata["sceneInactiveEntityCount"] ?: "0"} inactive)",
        )
        textLine("Root entities: ${asset.metadata["sceneRootEntityCount"] ?: "0"}")
        textLine("Cameras: ${asset.metadata["sceneCameraCount"] ?: "0"}")
        textLine(
            "Lights: ${asset.metadata["sceneLightCount"] ?: "0"} " +
                "(${asset.metadata["sceneDirectionalLightCount"] ?: "0"} directional, " +
                "${asset.metadata["scenePointLightCount"] ?: "0"} point)",
        )
        textLine("Models: ${asset.metadata["sceneModelCount"] ?: "0"}")
        textLine("Terrains: ${asset.metadata["sceneTerrainCount"] ?: "0"}")
        asset.metadata["sceneBounds"]?.let { bounds ->
            textLine("Scene bounds: $bounds")
        }

        ImGui.separator()
        ImGui.text("Environment")
        textLine("Skybox: ${asset.metadata["sceneSkyboxPath"] ?: "none"}")
        textLine("Skybox visible: ${asset.metadata["sceneSkyboxVisible"] ?: "true"}")
        textLine("Environment intensity: ${asset.metadata["sceneEnvironmentIntensity"] ?: "1.00"}")
        textLine("Ambient intensity: ${asset.metadata["sceneAmbientIntensity"] ?: "0.00"}")

        ImGui.separator()
        ImGui.text("Diagnostics")
        textLine(
            "Validation: ${asset.metadata["sceneValidationErrorCount"] ?: "0"} errors, " +
                "${asset.metadata["sceneValidationWarningCount"] ?: "0"} warnings",
        )
        textLine(
            "Dependencies: ${asset.metadata["sceneDependencyCount"] ?: "0"} total, " +
                "${asset.metadata["sceneMissingDependencyCount"] ?: "0"} missing",
        )
        asset.metadata["sceneValidationIssuePreview"]?.let { preview ->
            textLine("Issues: $preview")
        }

        ImGui.separator()
        ImGui.text("Scene bindings")
        textLine("Active camera: ${asset.metadata["sceneActiveCameraName"] ?: "none"}")
        textLine("Active terrain: ${asset.metadata["sceneActiveTerrainName"] ?: "none"}")
        asset.metadata["sceneActiveTerrainPath"]?.let { path ->
            textLine("Terrain asset: $path")
        }
        asset.metadata["sceneTerrainSize"]?.let { size ->
            textLine("Terrain size: $size")
        }
        asset.metadata["sceneTerrainLayerCount"]?.let { layers ->
            textLine("Terrain layers: $layers")
        }
        asset.metadata["sceneTerrainBakedResolution"]?.let { resolution ->
            textLine("Terrain baked resolution: ${resolution}px")
        }
        textLine(
            "Terrain material library: ${asset.metadata["sceneTerrainMaterialLibraryPath"] ?: "unknown"}",
        )
    }
}

/**
 * Identifier + display label for a tool exposed in the "Open With" menu.
 */
data class AssetToolDescriptor(val id: String, val label: String)

/**
 * UI-facing handler for asset operations and tool resolution.
 *
 * Implementations bridge [AssetBrowserPanel] to the asset operations service and tool registry
 * without requiring panels to perform IO directly.
 */
interface AssetBrowserOperationsHandler {
    fun create(draft: CreateAssetDraft)
    fun rename(asset: AssetDescriptor, newName: String)
    fun duplicate(asset: AssetDescriptor, targetName: String)
    fun delete(asset: AssetDescriptor)
    fun reveal(asset: AssetDescriptor)
    fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor>
    fun openWith(asset: AssetDescriptor, toolId: String)

    companion object {
        /** Default no-op handler used when the panel runs without operations support (e.g. picker mode). */
        val NoOp: AssetBrowserOperationsHandler = object : AssetBrowserOperationsHandler {
            override fun create(draft: CreateAssetDraft) = Unit
            override fun rename(asset: AssetDescriptor, newName: String) = Unit
            override fun duplicate(asset: AssetDescriptor, targetName: String) = Unit
            override fun delete(asset: AssetDescriptor) = Unit
            override fun reveal(asset: AssetDescriptor) = Unit
            override fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor> = emptyList()
            override fun openWith(asset: AssetDescriptor, toolId: String) = Unit
        }
    }
}

private fun textLine(value: String) {
    ImGui.textUnformatted(value)
}

private fun assetIcon(asset: AssetDescriptor): String =
    when {
        asset.type == AssetType.Scene2DSkin -> "[Skin]"
        else -> when (asset.category) {
            AssetCategory.Model -> "[M]"
            AssetCategory.Texture -> "[T]"
            AssetCategory.Skybox -> "[Sky]"
            AssetCategory.Material -> "[Mat]"
            AssetCategory.Terrain -> "[Ter]"
            AssetCategory.UI -> "[UI]"
            AssetCategory.Scene -> "[Sc]"
            AssetCategory.Other -> "[?]"
        }
    }

private val SupportedBrowserCategories = setOf(
    AssetCategory.Model,
    AssetCategory.Texture,
    AssetCategory.Skybox,
    AssetCategory.Material,
    AssetCategory.Terrain,
    AssetCategory.UI,
    AssetCategory.Scene,
    AssetCategory.Other,
)

private const val AssetTexturePreviewSize = 250f

private fun sortModeLabel(mode: AssetSortMode): String =
    when (mode) {
        AssetSortMode.NameAsc -> "Name A-Z"
        AssetSortMode.NameDesc -> "Name Z-A"
        AssetSortMode.TypeAsc -> "Type"
        AssetSortMode.ModifiedDesc -> "Modified"
        AssetSortMode.SizeDesc -> "Size"
    }

private fun readBuffer(buffer: ByteArray): String {
    val length = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, length, StandardCharsets.UTF_8)
}

private fun writeBuffer(buffer: ByteArray, value: String) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val length = minOf(bytes.size, buffer.size - 1)
    bytes.copyInto(buffer, endIndex = length)
}

private fun formatByteCount(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "%.2f MB".format(bytes / (1024f * 1024f))
    }

private fun formatTimestamp(millis: Long): String =
    TimestampFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val TimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
