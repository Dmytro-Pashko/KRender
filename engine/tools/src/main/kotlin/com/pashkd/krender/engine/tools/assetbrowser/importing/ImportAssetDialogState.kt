package com.pashkd.krender.engine.tools.assetbrowser.importing

import com.pashkd.krender.engine.assets.importing.*
import com.pashkd.krender.engine.tools.assetbrowser.*

import java.io.File

object ImportAssetDialogState {
    fun selectSourcePath(
        state: AssetBrowserState,
        importService: AssetImportService,
        sourcePath: String?,
    ) {
        val selected = sourcePath?.trim()?.trim('"')?.trim('\'') ?: return
        if (selected.isBlank()) return
        state.importSourcePath = selected
        state.importName = defaultImportName(selected)
        replan(state, importService)
    }

    fun updateImportName(
        state: AssetBrowserState,
        importService: AssetImportService,
        importName: String,
    ) {
        state.importName = importName
        replan(state, importService)
    }

    fun replan(
        state: AssetBrowserState,
        importService: AssetImportService,
    ) {
        state.importPlan =
            if (state.importSourcePath.isBlank()) {
                null
            } else {
                importService.planImport(
                    sourcePath = state.importSourcePath,
                    collisionPolicy = state.importCollisionPolicy,
                    importName = state.importName,
                )
            }
    }

    fun canImport(state: AssetBrowserState): Boolean = state.importPlan?.supportedEntries?.isNotEmpty() == true

    fun requiresOverwriteConfirmation(state: AssetBrowserState): Boolean =
        state.importPlan?.requiresOverwriteConfirmation == true

    fun requestImport(state: AssetBrowserState): AssetImportPlan? {
        val plan = state.importPlan ?: return null
        if (plan.requiresOverwriteConfirmation) {
            state.pendingImportPlan = plan
            state.showImportOverwriteConfirmDialog = true
            return null
        }
        return plan
    }

    fun acceptOverwrite(state: AssetBrowserState): AssetImportPlan? {
        val plan = state.pendingImportPlan
        state.pendingImportPlan = null
        state.showImportOverwriteConfirmDialog = false
        return plan
    }

    fun cancelOverwrite(state: AssetBrowserState) {
        state.pendingImportPlan = null
        state.showImportOverwriteConfirmDialog = false
    }

    private fun defaultImportName(sourcePath: String): String {
        val source = File(sourcePath)
        val parent = source.parentFile
        val packageName =
            if (parent?.name.equals("skin", ignoreCase = true)) {
                parent?.parentFile?.name
            } else {
                parent?.name
            }
        return if (source.extension.equals("json", ignoreCase = true) &&
            source.nameWithoutExtension.equals("uiskin", ignoreCase = true)
        ) {
            packageName ?: source.nameWithoutExtension
        } else {
            source.nameWithoutExtension
        }
    }
}
