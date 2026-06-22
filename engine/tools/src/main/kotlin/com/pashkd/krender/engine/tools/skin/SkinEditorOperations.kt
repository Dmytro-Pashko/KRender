package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

class SkinEditorOperations(
    private val state: SkinEditorState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun requestReload() {
        state.reloadRequested = true
    }

    fun openPath(path: String) {
        state.currentInputPath = path.trim().replace('\\', '/').ifBlank { null }
        state.pendingPathInput = state.currentInputPath.orEmpty()
        state.reloadRequested = true
    }

    fun selectLayout(layoutId: String) {
        state.previewLayoutId = layoutId
        state.previewDirty = true
        state.statusMessage = "Preview layout set to '$layoutId'."
    }

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(SkinEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(SkinEditorUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }
}
