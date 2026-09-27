package com.pashkd.krender.engine.tools.materialeditor

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TextureAsset
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.common.EditorTexturePreviewService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

class MaterialEditorScene(private val path: String) : Scene("material_editor") {
    override val config: SceneConfig = SceneConfigPresets.MaterialEditor

    private lateinit var editorState: MaterialEditorState
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private lateinit var previewSystem: MaterialEditorTexturePreviewSystem

    override fun show() {
        editorState = MaterialEditorState(path)
        val controller = MaterialEditorController(editorState, engine)
        controller.reload()
        val loadedLayout = ImGuiLayoutConfigLoader(LayoutPath, Defaults).load(engine.logger, engine.sceneFiles)
        val layout = ImGuiLayoutConfig(loadedLayout.panels.filterKeys { it in Defaults.panels })
        layoutTracker = ImGuiLayoutRuntimeTracker(layout)
        val events = ImGuiWindowEventLogger(engine.logger, "MaterialEditorUi")
        previewSystem = MaterialEditorTexturePreviewSystem(editorState, engine)
        world.systems.add(previewSystem)
        world.systems.add(UiSystem(engine.ui).also { ui ->
            ui.addPanel(MaterialEditorControlPanel(
                editorState, controller, layout, layoutTracker, events,
                ::saveUiLayout, ::restoreUiLayout, engine::requestExit,
            ))
            ui.addPanel(MaterialsPanel(editorState, controller, layout, layoutTracker, events))
            ui.addPanel(MaterialPropertiesPanel(
                editorState, EditorTexturePreviewService(engine.assets), engine.ui,
                layout, layoutTracker, events,
            ))
            ui.addPanel(LogsPanel(engine.logs, layout, events, layoutTracker = layoutTracker))
        })
    }

    override fun hide() {
        if (::previewSystem.isInitialized) previewSystem.release()
    }

    private fun saveUiLayout() {
        runCatching { ImGuiLayoutConfigCodec.save(LayoutPath, layoutTracker.currentConfig(), engine.sceneFiles) }
            .onSuccess { editorState.status = "Panel layout saved" }
            .onFailure { error ->
                editorState.status = "Layout save failed: ${error.message}"
                engine.logger.error(TAG, error) { editorState.status }
            }
    }

    private fun restoreUiLayout() {
        layoutTracker.requestRestore(Defaults)
        editorState.status = "Default panel layout restored"
    }

    companion object {
        private const val TAG = "MaterialEditorScene"
        private const val LayoutPath = "ui/material_editor_layout.json"

        private val Defaults = ImGuiLayoutConfig(linkedMapOf(
            MaterialEditorPanelIds.Control to ImGuiPanelLayout("Material Editor Control Panel", 16f, 16f, 1740f, 110f),
            MaterialEditorPanelIds.Materials to ImGuiPanelLayout("Materials", 16f, 140f, 350f, 880f),
            MaterialEditorPanelIds.Properties to ImGuiPanelLayout("Material Properties", 380f, 140f, 850f, 880f),
            "runtimeLogs" to ImGuiPanelLayout("Runtime Logs", 1244f, 140f, 512f, 880f),
        ))
    }
}

internal object MaterialEditorPanelIds {
    const val Control = "materialEditorControl"
    const val Materials = "materials"
    const val Properties = "materialProperties"
}

private class MaterialEditorTexturePreviewSystem(
    private val state: MaterialEditorState,
    private val engine: EngineContext,
) : System() {
    private var activePath: String? = null
    private var queuedRef: AssetRef<TextureAsset>? = null

    override fun update(world: SceneWorld, dt: Float) {
        val selectedPath = state.materials.getOrNull(state.selectedIndex)?.albedoTexture?.trim()?.takeIf(String::isNotBlank)
        if (selectedPath != activePath) {
            release()
            activePath = selectedPath
            state.previewError = null
            if (selectedPath != null) {
                val ref = AssetRef.texture(selectedPath)
                if (!engine.assets.isLoaded(ref)) {
                    runCatching { engine.assets.queue(ref) }
                        .onSuccess { queuedRef = ref }
                        .onFailure { error -> state.previewError = error.message ?: "Texture could not be queued" }
                }
            }
        }
        if (selectedPath != null && state.previewError == null) {
            state.previewError = engine.assets.loadFailure(AssetRef.texture(selectedPath))
        }
        state.previewLoading = selectedPath != null && state.previewError == null &&
            !engine.assets.isLoaded(AssetRef.texture(selectedPath))
    }

    fun release() {
        queuedRef?.let(engine.assets::unload)
        queuedRef = null
        activePath = null
    }
}
