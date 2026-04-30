package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
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
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, panelId))
        eventLogger.observe(panelId, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawToolbar()
        ImGui.separator()
        drawBrowserBody()
        ImGui.separator()
        drawStatus()

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
            button("Refresh##$panelId") {
                state.refreshRequested = true
                state.statusMessage = "Refresh requested."
            }
        }

        if (mode == AssetBrowserMode.Full) {
            ImGui.sameLine()
            with(dsl) {
                button("Create##$panelId") {
                    state.createNameBuffer = ""
                    createBufferSynced = false
                    state.showCreateDialog = true
                }
            }
            ImGui.sameLine()
            drawViewModeCombo()
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
        } else if (state.viewMode == AssetBrowserViewMode.Grid) {
            drawGrid(assets)
        } else {
            drawList(assets)
        }
        ImGui.endChild()
    }

    private fun drawCategoryList() {
        ImGui.text("Categories")
        if (ImGui.selectable("All (${visibleCategoryCount(null)})##${panelId}_cat_all", state.selectedCategory == null)) {
            state.selectedCategory = null
        }

        AssetCategory.entries
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
            val label = "${assetIcon(asset)} ${asset.name}  ${asset.type.name}  ${asset.path}##asset_${asset.id.value}"
            drawSelectableAsset(asset, label)
        }
    }

    private fun drawGrid(assets: List<AssetDescriptor>) {
        assets.forEachIndexed { index, asset ->
            if (index > 0 && index % GridColumns != 0) {
                ImGui.sameLine()
            }
            val label = "${assetIcon(asset)} ${asset.name}##asset_grid_${asset.id.value}"
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

    private fun drawStatus() {
        textLine("Assets: ${state.filteredAssets.size} / ${state.assets.size}")
        if (state.statusMessage.isNotBlank()) {
            ImGui.sameLine()
            textLine("Status: ${state.statusMessage}")
        }
        state.errorMessage?.let { error ->
            textLine("Error: $error")
        }
    }

    private fun drawViewModeCombo() {
        if (!ImGui.beginCombo("View##${panelId}_view", state.viewMode.name)) return
        AssetBrowserViewMode.entries.forEach { viewMode ->
            if (ImGui.selectable("${viewMode.name}##${panelId}_view_$viewMode", state.viewMode == viewMode)) {
                state.viewMode = viewMode
            }
        }
        ImGui.endCombo()
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
        acceptedCategories == null || category in acceptedCategories

    private fun syncSearchBuffer() {
        if (!searchInputActive && readBuffer(searchBuffer) != state.searchQuery) {
            writeBuffer(searchBuffer, state.searchQuery)
        }
    }

    private fun drawCreateDialog() {
        if (!state.showCreateDialog) return
        if (!createBufferSynced) {
            writeBuffer(createNameByteBuffer, state.createNameBuffer)
            createBufferSynced = true
        }
        ImGui.openPopup("Create Asset##${panelId}_create")
        if (!ImGui.beginPopupModal("Create Asset##${panelId}_create")) return
        ImGui.text("Name")
        ImGui.sameLine()
        if (ImGui.inputText("##${panelId}_create_name", createNameByteBuffer)) {
            state.createNameBuffer = readBuffer(createNameByteBuffer)
        }
        if (ImGui.beginCombo("Category##${panelId}_create_cat", state.createCategory.displayName)) {
            AssetCategory.entries.forEach { c ->
                if (ImGui.selectable(c.displayName, state.createCategory == c)) {
                    state.createCategory = c
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.beginCombo("Type##${panelId}_create_type", state.createType.name)) {
            AssetType.entries.forEach { t ->
                if (ImGui.selectable(t.name, state.createType == t)) {
                    state.createType = t
                }
            }
            ImGui.endCombo()
        }
        ImGui.separator()
        with(dsl) {
            button("Create##${panelId}_create_ok") {
                operations.create(state.createNameBuffer, state.createType, state.createCategory)
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
        ImGui.endPopup()
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
        private const val GridColumns = 3
    }
}

/**
 * Shows details for the currently selected asset.
 */
class AssetDetailsPanel(
    private val state: AssetBrowserState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
    private val panelId: String = AssetBrowserPanelIds.Details,
    private val operations: AssetBrowserOperationsHandler = AssetBrowserOperationsHandler.NoOp,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels[panelId] ?: return
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, panelId))
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
        ImGui.text("Metadata")
        if (asset.category == AssetCategory.Texture) {
            drawTextureMetadata(asset)
            return
        }
        if (asset.category == AssetCategory.Terrain) {
            drawTerrainMetadata(asset)
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
        textLine("Display Name: ${asset.metadata["displayName"] ?: asset.name}")
        ImGui.text("Settings:")
        val resolution = asset.metadata["textureResolution"] ?: "unknown"
        val colorFormat = asset.metadata["textureColorFormat"] ?: "unknown"
        textLine("Resolution: $resolution")
        textLine("Format: $colorFormat")
    }

    private fun drawTerrainMetadata(asset: AssetDescriptor) {
        textLine("Display Name: ${asset.metadata["displayName"] ?: asset.name}")
        ImGui.text("Settings:")
        textLine("Size: ${asset.metadata["terrainSize"] ?: "unknown"}")
        textLine("Layers: ${asset.metadata["terrainLayerCount"] ?: "unknown"}")
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
    fun create(name: String, type: AssetType, category: AssetCategory)
    fun rename(asset: AssetDescriptor, newName: String)
    fun duplicate(asset: AssetDescriptor, targetName: String)
    fun delete(asset: AssetDescriptor)
    fun reveal(asset: AssetDescriptor)
    fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor>
    fun openWith(asset: AssetDescriptor, toolId: String)

    companion object {
        /** Default no-op handler used when the panel runs without operations support (e.g. picker mode). */
        val NoOp: AssetBrowserOperationsHandler = object : AssetBrowserOperationsHandler {
            override fun create(name: String, type: AssetType, category: AssetCategory) = Unit
            override fun rename(asset: AssetDescriptor, newName: String) = Unit
            override fun duplicate(asset: AssetDescriptor, targetName: String) = Unit
            override fun delete(asset: AssetDescriptor) = Unit
            override fun reveal(asset: AssetDescriptor) = Unit
            override fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor> = emptyList()
            override fun openWith(asset: AssetDescriptor, toolId: String) = Unit
        }
    }
}

private fun applyWindowDefaults(layout: ImGuiPanelLayout) {
    ImGui.setNextWindowPos(Vec2(layout.x, layout.y), Cond.FirstUseEver, Vec2())
    ImGui.setNextWindowSize(Vec2(layout.width, layout.height), Cond.FirstUseEver)
}

private fun imguiWindowName(title: String, id: String): String = "$title###$id"

private fun textLine(value: String) {
    ImGui.textUnformatted(value)
}

private fun assetIcon(asset: AssetDescriptor): String =
    when (asset.category) {
        AssetCategory.Model -> "[M]"
        AssetCategory.Texture -> "[T]"
        AssetCategory.Material -> "[Mat]"
        AssetCategory.Terrain -> "[Ter]"
        AssetCategory.Shader -> "[S]"
        AssetCategory.Scene -> "[Sc]"
        AssetCategory.Audio -> "[A]"
        AssetCategory.Script -> "[C]"
        AssetCategory.Unknown -> "[?]"
    }

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
