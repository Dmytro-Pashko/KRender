package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.*

import com.pashkd.krender.engine.tools.assetbrowser.creation.CreateAssetDialog
import com.pashkd.krender.engine.assets.importing.AssetImportService
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.tools.assetbrowser.importing.ImportAssetDialog
import com.pashkd.krender.engine.ui.editor.*
import glm_.vec2.Vec2
import imgui.ImGui
import imgui.MouseButton
import imgui.dsl

/**
 * List-only Asset Browser panel: search, category filters, sorting, selection, and context menu.
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
    private val importService: AssetImportService,
    private val fileDialogService: FileDialogService,
) : UiPanel {
    private val searchBuffer = ByteArray(TextInputBufferSize)
    private val renameByteBuffer = ByteArray(TextInputBufferSize)
    private val createDialog = CreateAssetDialog(state, operations, panelId)
    private val importDialog = ImportAssetDialog(state, importService, fileDialogService, panelId)
    private var searchInputActive = false
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
            createDialog.draw()
            importDialog.draw()
            drawRenameDialog()
            drawDeleteDialog()
        }
    }

    fun resetCreateDialogForOpen() {
        createDialog.resetForOpen()
    }

    private fun drawToolbar() {
        ImGui.text("Search")
        ImGui.sameLine()
        if (ImGui.inputText("##${panelId}_search", searchBuffer)) {
            state.searchQuery = assetBrowserReadBuffer(searchBuffer)
        }
        searchInputActive = ImGui.isItemActive

        ImGui.sameLine()
        with(dsl) {
            button("[x]##${panelId}_clear_search") {
                state.searchQuery = ""
                assetBrowserWriteBuffer(searchBuffer, "")
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
        if (ImGui.selectable(
                "All (${visibleCategoryCount(null)})##${panelId}_cat_all",
                state.selectedCategory == null,
            )
        ) {
            state.selectedCategory = null
        }

        SupportedBrowserCategories
            .sortedBy(AssetCategory::sortOrder)
            .filter(::categoryAccepted)
            .forEach { category ->
                val label =
                    "${category.displayName} (${visibleCategoryCount(category)})##${panelId}_cat_${category.name}"
                if (ImGui.selectable(label, state.selectedCategory == category)) {
                    state.selectedCategory = category
                }
            }
    }

    private fun drawList(assets: List<AssetDescriptor>) {
        assets.forEach { asset ->
            val label =
                "${assetBrowserIcon(asset)} ${asset.name}  ${asset.type.name}  " +
                    "${asset.path} (${assetBrowserFormatByteCount(asset.sizeBytes)})##asset_${asset.id.value}"
            drawSelectableAsset(asset, label)
        }
    }

    private fun drawSelectableAsset(
        asset: AssetDescriptor,
        label: String,
    ) {
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
        val capabilities = asset.assetCapabilities()
        val tools = operations.toolsFor(asset)
        if (tools.isNotEmpty() && ImGui.menuItem("Open")) {
            onAssetSelected(asset)
            onAssetActivated(asset)
        }
        if (capabilities.canOpenWith && tools.isNotEmpty() && ImGui.beginMenu("Open With")) {
            tools.forEach { toolId ->
                if (ImGui.menuItem(toolId.label)) {
                    onAssetSelected(asset)
                    operations.openWith(asset, toolId.id)
                }
            }
            ImGui.endMenu()
        }
        ImGui.separator()
        if (capabilities.canRename && ImGui.menuItem("Rename...")) {
            onAssetSelected(asset)
            state.renameBuffer = asset.name
            renameBufferSynced = false
            state.showRenameDialog = true
        }
        if (capabilities.canDuplicate && ImGui.menuItem("Duplicate")) {
            onAssetSelected(asset)
            operations.duplicate(asset, "${asset.name}_copy")
        }
        if (capabilities.canDelete && ImGui.menuItem("Delete")) {
            onAssetSelected(asset)
            state.showDeleteDialog = true
        }
        ImGui.separator()
        if (capabilities.canReveal && ImGui.menuItem("Reveal in Files")) {
            operations.reveal(asset)
        }
        ImGui.endPopup()
    }

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
        if (!searchInputActive && assetBrowserReadBuffer(searchBuffer) != state.searchQuery) {
            assetBrowserWriteBuffer(searchBuffer, state.searchQuery)
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
            assetBrowserWriteBuffer(renameByteBuffer, state.renameBuffer)
            renameBufferSynced = true
        }
        ImGui.openPopup("Rename Asset##${panelId}_rename")
        if (!ImGui.beginPopupModal("Rename Asset##${panelId}_rename")) return
        assetBrowserTextLine("Path: ${asset.path}")
        ImGui.text("New name")
        ImGui.sameLine()
        if (ImGui.inputText("##${panelId}_rename_name", renameByteBuffer)) {
            state.renameBuffer = assetBrowserReadBuffer(renameByteBuffer)
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
        val skinFolder = scene2dSkinFolderPath(asset)
        if (skinFolder != null) {
            ImGui.textWrapped(
                "Delete Scene2D Skin '${asset.name}'? This will permanently delete the entire skin folder and all dependencies inside it.",
            )
            assetBrowserTextLine("Folder: $skinFolder")
        } else {
            ImGui.textUnformatted("Permanently delete '${asset.name}'?")
        }
        assetBrowserTextLine("Path: ${asset.path}")
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

    private fun sortModeLabel(mode: AssetSortMode): String =
        when (mode) {
            AssetSortMode.NameAsc -> "Name A-Z"
            AssetSortMode.NameDesc -> "Name Z-A"
            AssetSortMode.TypeAsc -> "Type"
            AssetSortMode.ModifiedDesc -> "Modified"
            AssetSortMode.SizeDesc -> "Size"
        }

    private fun scene2dSkinFolderPath(asset: AssetDescriptor): String? {
        if (asset.type != AssetType.Scene2DSkin) return null
        val normalized = assetBrowserNormalizePath(asset.path)
        if (!normalized.startsWith("ui/skins/")) return null
        val suffix = normalized.removePrefix("ui/skins/")
        val firstSegment = suffix.substringBefore('/', "")
        return if (firstSegment.isBlank() || !suffix.contains('/')) null else "ui/skins/$firstSegment"
    }

    companion object {
        private const val TextInputBufferSize = 256
    }
}
