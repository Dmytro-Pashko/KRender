package com.pashkd.krender.engine.tools.assetbrowser.creation

import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserOperationsHandler
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserState
import com.pashkd.krender.engine.tools.assetbrowser.CreatableAssetKind
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserReadBuffer
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserWriteBuffer
import com.pashkd.krender.engine.tools.assetbrowser.createAssetDefaultParams
import com.pashkd.krender.engine.tools.assetbrowser.createAssetRelativePath
import com.pashkd.krender.engine.tools.assetbrowser.discoveredScene2DSkinAssets
import com.pashkd.krender.engine.tools.assetbrowser.terrainSize
import com.pashkd.krender.engine.tools.assetbrowser.withSyncedDefaults
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.dsl

/**
 * ImGui modal for creating the supported Asset Browser document assets.
 */
class CreateAssetDialog(
    private val state: AssetBrowserState,
    private val operations: AssetBrowserOperationsHandler,
    private val panelId: String,
) {
    private val createNameByteBuffer = ByteArray(TextInputBufferSize)
    private val customSizeBuffer = ByteArray(8)
    private var createBufferSynced = false

    fun resetForOpen() {
        createBufferSynced = false
    }

    fun draw() {
        if (!state.showCreateDialog) return
        if (!createBufferSynced) {
            assetBrowserWriteBuffer(createNameByteBuffer, state.createDraft.name)
            assetBrowserWriteBuffer(customSizeBuffer, state.createDraft.terrainCustomSize.toString())
            createBufferSynced = true
        }
        ImGui.openPopup("Create Asset##${panelId}_create")
        ImGui.setNextWindowSize(Vec2(620f, 320f), Cond.Always)
        if (!ImGui.beginPopupModal("Create Asset##${panelId}_create")) return

        drawCreateAssetKindSelector()
        ImGui.text("Name")
        ImGui.sameLine()
        ImGui.pushItemWidth(330f)
        if (ImGui.inputText("##${panelId}_create_name", createNameByteBuffer)) {
            state.createDraft = state.createDraft.copy(name = assetBrowserReadBuffer(createNameByteBuffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        assetBrowserTextLine(".${state.createDraft.kind.extension}")
        drawCreateAtlasSizeSelector()
        drawCreateTerrainSizeSelector()
        drawCreateUiSceneSkinSelector()

        ImGui.separator()
        val canCreate = state.createDraft.kind != CreatableAssetKind.Terrain ||
            state.createDraft.terrainSize() in 2..512
        if (!canCreate) ImGui.beginDisabled(true)
        with(dsl) {
            button("Create##${panelId}_create_ok") {
                operations.create(state.createDraft.withSyncedDefaults(state.assets))
                state.showCreateDialog = false
                createBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }
        if (!canCreate) ImGui.endDisabled()
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
                    assetBrowserWriteBuffer(createNameByteBuffer, state.createDraft.name)
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
        val currentLabel =
            skinAssets
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

    private fun drawCreateAtlasSizeSelector() {
        if (state.createDraft.kind != CreatableAssetKind.Atlas) return
        drawPageSizeCombo(
            label = "Width##${panelId}_create_atlas_width",
            selected = state.createDraft.atlasWidth,
        ) { value ->
            state.createDraft = state.createDraft.copy(atlasWidth = value)
        }
        drawPageSizeCombo(
            label = "Height##${panelId}_create_atlas_height",
            selected = state.createDraft.atlasHeight,
        ) { value ->
            state.createDraft = state.createDraft.copy(atlasHeight = value)
        }
    }

    private fun drawCreateTerrainSizeSelector() {
        if (state.createDraft.kind != CreatableAssetKind.Terrain) return
        val preset = state.createDraft.terrainSizePreset
        if (ImGui.beginCombo("Size##${panelId}_terrain_size", if (preset == 0) "Custom" else preset.toString())) {
            TerrainSizeOptions.forEach { option ->
                if (ImGui.selectable(if (option == 0) "Custom" else option.toString(), preset == option)) {
                    state.createDraft = state.createDraft.copy(terrainSizePreset = option)
                }
            }
            ImGui.endCombo()
        }
        if (state.createDraft.terrainSizePreset == 0) {
            if (ImGui.inputText("Custom size##${panelId}_terrain_custom_size", customSizeBuffer)) {
                state.createDraft = state.createDraft.copy(
                    terrainCustomSize = assetBrowserReadBuffer(customSizeBuffer).toIntOrNull() ?: 0,
                )
            }
            if (state.createDraft.terrainCustomSize !in 2..512) ImGui.text("Enter a size from 2 to 512")
        }
    }

    private fun drawPageSizeCombo(
        label: String,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        if (!ImGui.beginCombo(label, selected.toString())) return
        PageSizeOptions.forEach { option ->
            if (ImGui.selectable(option.toString(), option == selected)) {
                onSelect(option)
            }
        }
        ImGui.endCombo()
    }

    private fun drawCreateAssetMetadata() {
        val draft = state.createDraft.withSyncedDefaults(state.assets)
        val path = createAssetRelativePath(draft)
        val exists = state.assets.any { asset -> asset.path.equals(path, ignoreCase = true) }
        ImGui.text("Metadata")
        assetBrowserTextLine("File: $path")
        assetBrowserTextLine("Is Already Exist: ${if (exists) "Yes" else "No"}")
        assetBrowserTextLine("Default params:")
        createAssetDefaultParams(draft).forEach { param ->
            assetBrowserTextLine("  $param")
        }
    }

    companion object {
        private const val TextInputBufferSize = 256
        private val PageSizeOptions = intArrayOf(128, 256, 512, 1024, 2048, 4096)
        private val TerrainSizeOptions = intArrayOf(64, 128, 256, 512, 0)
    }
}
