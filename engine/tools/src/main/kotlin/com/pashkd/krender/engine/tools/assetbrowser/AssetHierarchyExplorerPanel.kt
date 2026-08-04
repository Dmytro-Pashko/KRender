package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.assetCapabilities
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.MouseButton
import imgui.dsl

class AssetHierarchyExplorerPanel(
    private val state: AssetBrowserState,
    private val operations: AssetBrowserOperationsHandler,
    private val onAssetSelected: (AssetDescriptor) -> Unit,
    private val onAssetActivated: (AssetDescriptor) -> Unit,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
    private val panelId: String = AssetBrowserPanelIds.Hierarchy,
) : UiPanel {
    private val createFolderBuffer = ByteArray(TextInputBufferSize)
    private val renameDirectoryBuffer = ByteArray(TextInputBufferSize)
    private var createFolderParentPath: String? = null
    private var renameDirectoryPath: String? = null
    private var deleteDirectoryPath: String? = null

    override fun draw() {
        val layout = layoutConfig.panels[panelId] ?: return
        val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
        eventLogger.observe(panelId, layout.title)
        if (!expanded) {
            ImGui.end()
            drawDialogs()
            return
        }

        drawToolbar()
        ImGui.separator()
        ImGui.beginChild("${panelId}_tree", Vec2(0f, 0f), true)
        val root = state.hierarchyRoot
        if (root == null) {
            ImGui.textUnformatted("Asset hierarchy is not indexed yet.")
        } else {
            drawDirectory(root, isRoot = true)
        }
        ImGui.endChild()
        ImGui.end()
        drawDialogs()
    }

    private fun drawToolbar() {
        with(dsl) {
            button("Refresh##${panelId}_refresh") {
                state.refreshRequested = true
                state.statusMessage = "Refresh requested."
            }
        }
        ImGui.sameLine()
        val root = state.hierarchyRoot
        val total = root?.assetCount ?: state.assets.size
        ImGui.textUnformatted("Assets: $total")
        if (state.searchQuery.isNotBlank() || state.selectedCategory != null) {
            ImGui.sameLine()
            ImGui.textUnformatted("Filtered: ${state.filteredAssets.size}")
        }
    }

    private fun drawDirectory(
        node: AssetHierarchyNode,
        isRoot: Boolean = false,
    ) {
        if (!directoryVisible(node, isRoot)) return
        val forceOpen = isRoot || isFilteringActive()
        val openBefore = forceOpen || node.path in state.hierarchyExpandedPaths
        ImGui.setNextItemOpen(openBefore, Cond.Always)
        val label = directoryLabel(node, isRoot)
        val opened = ImGui.treeNode("$label##${panelId}_dir_${imguiId(node.path)}")
        if (!isRoot && ImGui.isItemToggledOpen) {
            if (opened) {
                state.hierarchyExpandedPaths += node.path
            } else {
                state.hierarchyExpandedPaths -= node.path
            }
        }
        drawDirectoryContextMenu(node, isRoot)
        if (opened) {
            node.children.forEach { child ->
                when (child.kind) {
                    AssetHierarchyNodeKind.Directory -> drawDirectory(child)
                    AssetHierarchyNodeKind.Asset -> drawAsset(child)
                }
            }
            ImGui.treePop()
        }
    }

    private fun drawAsset(node: AssetHierarchyNode) {
        val asset = node.asset ?: return
        if (!assetVisible(asset)) return
        val label =
            "${assetBrowserIcon(asset)} ${asset.name}  ${asset.type.name}  " +
                "(${assetBrowserFormatByteCount(asset.sizeBytes)})##${panelId}_asset_${imguiId(asset.id.value)}"
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
        if (!ImGui.beginPopupContextItem("${panelId}_asset_ctx_${imguiId(asset.id.value)}")) return
        val capabilities = asset.assetCapabilities()
        val tools = operations.toolsFor(asset)
        val actions = operations.actionsFor(asset)
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
        actions.forEach { action ->
            if (ImGui.menuItem(action.label)) {
                onAssetSelected(asset)
                operations.runAction(asset, action.id)
            }
        }
        if (tools.isNotEmpty() || actions.isNotEmpty()) {
            ImGui.separator()
        }
        if (capabilities.canRename && ImGui.menuItem("Rename...")) {
            onAssetSelected(asset)
            state.renameBuffer = asset.name
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

    private fun drawDirectoryContextMenu(
        node: AssetHierarchyNode,
        isRoot: Boolean,
    ) {
        if (!ImGui.beginPopupContextItem("${panelId}_dir_ctx_${imguiId(node.path)}")) return
        if (ImGui.menuItem("New Folder...")) {
            createFolderParentPath = node.path
            assetBrowserWriteBuffer(createFolderBuffer, "New Folder")
        }
        if (!isRoot) {
            if (ImGui.menuItem("Rename...")) {
                renameDirectoryPath = node.path
                assetBrowserWriteBuffer(renameDirectoryBuffer, node.name)
            }
            if (ImGui.menuItem("Duplicate Folder")) {
                operations.duplicateDirectory(node.path, "${node.name}_copy")
            }
            if (ImGui.menuItem("Delete to Trash")) {
                deleteDirectoryPath = node.path
            }
            ImGui.separator()
            if (ImGui.menuItem("Collapse Children")) {
                collapseChildren(node.path)
            }
        }
        if (ImGui.menuItem("Refresh")) {
            state.refreshRequested = true
            state.statusMessage = "Refresh requested."
        }
        if (ImGui.menuItem("Reveal in Files")) {
            operations.revealDirectory(node.path)
        }
        ImGui.endPopup()
    }

    private fun drawDialogs() {
        drawCreateFolderDialog()
        drawRenameDirectoryDialog()
        drawDeleteDirectoryDialog()
    }

    private fun drawCreateFolderDialog() {
        val parentPath = createFolderParentPath ?: return
        ImGui.openPopup("New Folder##${panelId}_create_folder")
        if (!ImGui.beginPopupModal("New Folder##${panelId}_create_folder")) return
        assetBrowserTextLine("Parent: ${displayDirectoryPath(parentPath)}")
        ImGui.text("Name")
        ImGui.sameLine()
        ImGui.inputText("##${panelId}_create_folder_name", createFolderBuffer)
        ImGui.separator()
        with(dsl) {
            button("Create##${panelId}_create_folder_ok") {
                operations.createFolder(parentPath, assetBrowserReadBuffer(createFolderBuffer))
                createFolderParentPath = null
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_create_folder_cancel") {
                createFolderParentPath = null
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    private fun drawRenameDirectoryDialog() {
        val directoryPath = renameDirectoryPath ?: return
        ImGui.openPopup("Rename Folder##${panelId}_rename_folder")
        if (!ImGui.beginPopupModal("Rename Folder##${panelId}_rename_folder")) return
        assetBrowserTextLine("Path: $directoryPath")
        ImGui.text("New name")
        ImGui.sameLine()
        ImGui.inputText("##${panelId}_rename_folder_name", renameDirectoryBuffer)
        ImGui.separator()
        with(dsl) {
            button("Rename##${panelId}_rename_folder_ok") {
                operations.renameDirectory(directoryPath, assetBrowserReadBuffer(renameDirectoryBuffer))
                renameDirectoryPath = null
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_rename_folder_cancel") {
                renameDirectoryPath = null
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    private fun drawDeleteDirectoryDialog() {
        val directoryPath = deleteDirectoryPath ?: return
        ImGui.openPopup("Delete Folder##${panelId}_delete_folder")
        if (!ImGui.beginPopupModal("Delete Folder##${panelId}_delete_folder")) return
        ImGui.textWrapped("Move folder '$directoryPath' and all files inside it to .trash?")
        ImGui.separator()
        with(dsl) {
            button("Move to Trash##${panelId}_delete_folder_ok") {
                operations.trashDirectory(directoryPath)
                deleteDirectoryPath = null
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_delete_folder_cancel") {
                deleteDirectoryPath = null
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    private fun directoryVisible(
        node: AssetHierarchyNode,
        isRoot: Boolean,
    ): Boolean {
        if (isRoot) return true
        if (!isFilteringActive()) return true
        return node.children.any { child ->
            when (child.kind) {
                AssetHierarchyNodeKind.Directory -> directoryVisible(child, isRoot = false)
                AssetHierarchyNodeKind.Asset -> child.asset?.let(::assetVisible) == true
            }
        }
    }

    private fun assetVisible(asset: AssetDescriptor): Boolean = state.filteredAssets.any { it.id == asset.id }

    private fun isFilteringActive(): Boolean = state.searchQuery.isNotBlank() || state.selectedCategory != null

    private fun directoryLabel(
        node: AssetHierarchyNode,
        isRoot: Boolean,
    ): String {
        val name = if (isRoot) "Assets" else "[Dir] ${node.name}"
        return "$name (${node.assetCount})"
    }

    private fun displayDirectoryPath(path: String): String = path.ifBlank { "." }

    private fun collapseChildren(path: String) {
        val prefix = if (path.isBlank()) "" else "$path/"
        state.hierarchyExpandedPaths.removeAll { expanded -> expanded.startsWith(prefix) && expanded != path }
    }

    private fun imguiId(value: String): String =
        value.ifBlank { "root" }.replace(Regex("[^A-Za-z0-9_.:-]"), "_")

    companion object {
        private const val TextInputBufferSize = 256
    }
}
