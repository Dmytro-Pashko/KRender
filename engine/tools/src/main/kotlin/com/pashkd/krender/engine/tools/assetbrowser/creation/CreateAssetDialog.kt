package com.pashkd.krender.engine.tools.assetbrowser.creation

import com.pashkd.krender.engine.assets.importing.EnvironmentSourceFileDialogFilters
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserOperationsHandler
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserState
import com.pashkd.krender.engine.tools.assetbrowser.CreatableAssetKind
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserReadBuffer
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserWriteBuffer
import com.pashkd.krender.engine.tools.assetbrowser.createAssetDefaultParams
import com.pashkd.krender.engine.tools.assetbrowser.createAssetRelativePath
import com.pashkd.krender.engine.tools.assetbrowser.discoveredScene2DSkinAssets
import com.pashkd.krender.engine.tools.assetbrowser.withEnvironmentSourcePath
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
    private val fileDialogService: FileDialogService,
    private val panelId: String,
) {
    private val createNameByteBuffer = ByteArray(TextInputBufferSize)
    private val environmentSourceBuffer = ByteArray(SourcePathBufferSize)
    private var createBufferSynced = false
    private var sourceBufferSynced = false

    fun resetForOpen() {
        createBufferSynced = false
        sourceBufferSynced = false
    }

    fun draw() {
        if (!state.showCreateDialog) return
        if (!createBufferSynced) {
            assetBrowserWriteBuffer(createNameByteBuffer, state.createDraft.name)
            createBufferSynced = true
        }
        if (!sourceBufferSynced) {
            assetBrowserWriteBuffer(environmentSourceBuffer, state.createDraft.environmentSourcePath)
            sourceBufferSynced = true
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
        drawCreateUiSceneSkinSelector()
        drawCreateEnvironmentSourceSelector()

        ImGui.separator()
        val canCreate = canCreate()
        if (!canCreate) ImGui.beginDisabled(true)
        with(dsl) {
            button("Create##${panelId}_create_ok") {
                operations.create(state.createDraft.withSyncedDefaults(state.assets))
                state.showCreateDialog = false
                createBufferSynced = false
                sourceBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }
        if (!canCreate) ImGui.endDisabled()
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_create_cancel") {
                state.showCreateDialog = false
                createBufferSynced = false
                sourceBufferSynced = false
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
                if (kind == CreatableAssetKind.Environment) {
                    assetBrowserWriteBuffer(environmentSourceBuffer, state.createDraft.environmentSourcePath)
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

    private fun drawCreateEnvironmentSourceSelector() {
        if (state.createDraft.kind != CreatableAssetKind.Environment) return
        ImGui.text("Source")
        ImGui.sameLine()
        ImGui.pushItemWidth(360f)
        if (ImGui.inputText("##${panelId}_create_environment_source", environmentSourceBuffer)) {
            state.createDraft = state.createDraft.withEnvironmentSourcePath(assetBrowserReadBuffer(environmentSourceBuffer))
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        with(dsl) {
            button("Browse...##${panelId}_create_environment_source_browse") {
                val selected = fileDialogService.openFile(EnvironmentSourceFileDialogFilters) ?: return@button
                state.createDraft = state.createDraft.withEnvironmentSourcePath(selected)
                assetBrowserWriteBuffer(environmentSourceBuffer, state.createDraft.environmentSourcePath)
                if (state.createDraft.name.isNotBlank()) {
                    assetBrowserWriteBuffer(createNameByteBuffer, state.createDraft.name)
                }
            }
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

    private fun canCreate(): Boolean =
        when (state.createDraft.kind) {
            CreatableAssetKind.Environment -> state.createDraft.environmentSourcePath.isNotBlank()
            else -> true
        }

    companion object {
        private const val TextInputBufferSize = 256
        private const val SourcePathBufferSize = 512
        private val PageSizeOptions = intArrayOf(128, 256, 512, 1024, 2048, 4096)
    }
}
