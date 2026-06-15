package com.pashkd.krender.engine.tools.assetbrowser.importing

import com.pashkd.krender.engine.assets.importing.*
import com.pashkd.krender.engine.tools.assetbrowser.*
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.dsl

/**
 * ImGui modal for MVP single-file asset import.
 */
class ImportAssetDialog(
    private val state: AssetBrowserState,
    private val importService: AssetImportService,
    private val fileDialogService: FileDialogService,
    private val panelId: String,
) {
    private val sourcePathBuffer = ByteArray(TextInputBufferSize)
    private val importNameBuffer = ByteArray(TextInputBufferSize)
    private var sourcePathBufferSynced = false
    private var importNameBufferSynced = false

    fun resetForOpen() {
        sourcePathBufferSynced = false
        importNameBufferSynced = false
        state.importPlan = null
        state.pendingImportPlan = null
        state.showImportOverwriteConfirmDialog = false
    }

    fun draw() {
        if (!state.showImportDialog) return
        syncSourceBuffer()
        ImGui.openPopup("Import Asset##${panelId}_import")
        ImGui.setNextWindowSize(Vec2(620f, 290f), Cond.Always)
        if (!ImGui.beginPopupModal("Import Asset##${panelId}_import")) return

        ImGui.text("Source path")
        ImGui.sameLine()
        ImGui.pushItemWidth(360f)
        if (ImGui.inputText("##${panelId}_import_source", sourcePathBuffer)) {
            ImportAssetDialogState.selectSourcePath(
                state = state,
                importService = importService,
                sourcePath = assetBrowserReadBuffer(sourcePathBuffer),
            )
            assetBrowserWriteBuffer(importNameBuffer, state.importName)
            importNameBufferSynced = true
        }
        ImGui.popItemWidth()
        ImGui.sameLine()
        with(dsl) {
            button("Browse...##${panelId}_import_browse") {
                val selected = fileDialogService.openFile(AssetImportFileDialogFilters)
                ImportAssetDialogState.selectSourcePath(state, importService, selected)
                assetBrowserWriteBuffer(sourcePathBuffer, state.importSourcePath)
                assetBrowserWriteBuffer(importNameBuffer, state.importName)
                importNameBufferSynced = true
            }
        }

        drawImportNameInput()
        drawPlan()

        ImGui.separator()
        val canImport = ImportAssetDialogState.canImport(state)
        if (!canImport) ImGui.beginDisabled(true)
        with(dsl) {
            button("Import##${panelId}_import_ok") {
                ImportAssetDialogState.replan(state, importService)
                val plan = ImportAssetDialogState.requestImport(state)
                if (plan != null) finishImport(plan)
            }
        }
        if (!canImport) ImGui.endDisabled()
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_import_cancel") {
                state.showImportDialog = false
                state.importPlan = null
                state.pendingImportPlan = null
                state.showImportOverwriteConfirmDialog = false
                sourcePathBufferSynced = false
                importNameBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }

        ImGui.endPopup()
        drawOverwriteConfirmDialog()
    }

    private fun syncSourceBuffer() {
        if (!sourcePathBufferSynced) {
            assetBrowserWriteBuffer(sourcePathBuffer, state.importSourcePath)
            sourcePathBufferSynced = true
            ImportAssetDialogState.replan(state, importService)
        }
    }

    private fun drawImportNameInput() {
        if (!isJsonSource()) return
        if (!importNameBufferSynced) {
            assetBrowserWriteBuffer(importNameBuffer, state.importName)
            importNameBufferSynced = true
        }
        ImGui.text("Name")
        ImGui.sameLine()
        ImGui.pushItemWidth(360f)
        if (ImGui.inputText("##${panelId}_import_name", importNameBuffer)) {
            ImportAssetDialogState.updateImportName(
                state = state,
                importService = importService,
                importName = assetBrowserReadBuffer(importNameBuffer),
            )
        }
        ImGui.popItemWidth()
    }

    private fun drawPlan() {
        ImGui.separator()
        ImGui.text("Plan")
        val plan = state.importPlan
        if (plan == null || state.importSourcePath.isBlank()) {
            assetBrowserTextLine("Enter a source file path.")
            return
        }

        plan.entries.forEach { entry ->
            assetBrowserTextLine("Source: ${entry.sourcePath}")
            assetBrowserTextLine("Status: ${entry.status}")
            assetBrowserTextLine("Supported: ${if (entry.supported) "Yes" else "No"}")
            if (entry.supported) {
                assetBrowserTextLine("Type: ${entry.type}")
                assetBrowserTextLine("Target: ${entry.targetPath.orEmpty()}")
                assetBrowserTextLine("Dependencies: ${if (entry.dependencies.isEmpty()) "none" else entry.dependencies.size.toString()}")
                entry.dependencies.forEach { dependency ->
                    val overwrite = if (dependency.targetExists) " (will overwrite)" else ""
                    assetBrowserTextLine("  ${dependency.sourcePath} -> ${dependency.targetPath}$overwrite")
                }
                entry.warnings.forEach { warning -> assetBrowserTextLine("Warning: $warning") }
            }
        }
    }

    private fun finishImport(plan: AssetImportPlan) {
        val result = importService.importAssets(plan)
        state.statusMessage =
            when {
                result.imported.isNotEmpty() -> "Imported ${result.imported.size} asset(s)."
                result.errors.isNotEmpty() -> result.errors.first()
                else -> "No supported assets imported."
            }
        state.errorMessage = result.errors.firstOrNull()
        state.importPlan = null
        state.pendingImportPlan = null
        state.showImportDialog = false
        state.showImportOverwriteConfirmDialog = false
        sourcePathBufferSynced = false
        importNameBufferSynced = false
        ImGui.closeCurrentPopup()
    }

    private fun isJsonSource(): Boolean = state.importSourcePath.substringAfterLast('.', "").equals("json", ignoreCase = true)

    private fun drawOverwriteConfirmDialog() {
        if (!state.showImportOverwriteConfirmDialog) return
        val plan =
            state.pendingImportPlan ?: run {
                state.showImportOverwriteConfirmDialog = false
                return
            }
        val entry = plan.supportedEntries.firstOrNull(AssetImportEntry::mainTargetExists) ?: return
        ImGui.openPopup("Overwrite Existing Asset?##${panelId}_import_overwrite")
        ImGui.setNextWindowSize(Vec2(520f, 210f), Cond.Always)
        if (!ImGui.beginPopupModal("Overwrite Existing Asset?##${panelId}_import_overwrite")) return

        assetBrowserTextLine("Source: ${entry.sourcePath}")
        assetBrowserTextLine("Target already exists:")
        assetBrowserTextLine(entry.targetPath.orEmpty())
        assetBrowserTextLine("Importing this file will replace the existing asset file and update metadata.")
        ImGui.separator()
        with(dsl) {
            button("Overwrite##${panelId}_import_overwrite_ok") {
                val confirmed = ImportAssetDialogState.acceptOverwrite(state)
                if (confirmed != null) finishImport(confirmed)
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##${panelId}_import_overwrite_cancel") {
                ImportAssetDialogState.cancelOverwrite(state)
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    companion object {
        private const val TextInputBufferSize = 512
    }
}
