package com.pashkd.krender.engine.assets.creation

import com.pashkd.krender.engine.assets.AssetBrowserOperationsHandler
import com.pashkd.krender.engine.assets.AssetBrowserState
import com.pashkd.krender.engine.assets.CreatableAssetKind
import com.pashkd.krender.engine.assets.createAssetDefaultParams
import com.pashkd.krender.engine.assets.createAssetRelativePath
import com.pashkd.krender.engine.assets.discoveredScene2DSkinAssets
import com.pashkd.krender.engine.assets.withSyncedDefaults
import com.pashkd.krender.engine.assets.assetBrowserReadBuffer
import com.pashkd.krender.engine.assets.assetBrowserTextLine
import com.pashkd.krender.engine.assets.assetBrowserWriteBuffer
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
    private var createBufferSynced = false

    fun resetForOpen() {
        createBufferSynced = false
    }

    fun draw() {
        if (!state.showCreateDialog) return
        if (!createBufferSynced) {
            assetBrowserWriteBuffer(createNameByteBuffer, state.createDraft.name)
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
            state.createDraft = state.createDraft.copy(name = assetBrowserReadBuffer(createNameByteBuffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        assetBrowserTextLine(".${state.createDraft.kind.extension}")
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
        assetBrowserTextLine("File: $path")
        assetBrowserTextLine("Is Already Exist: ${if (exists) "Yes" else "No"}")
        assetBrowserTextLine("Default params:")
        createAssetDefaultParams(draft).forEach { param ->
            assetBrowserTextLine("  $param")
        }
    }

    companion object {
        private const val TextInputBufferSize = 256
    }
}
