package com.pashkd.krender.game

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.backend.gdx.ui.composer.GdxUiScenePreview
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.uicomposer.UiComposerDiagnosticsPanel
import com.pashkd.krender.engine.uicomposer.UiComposerDocumentLoader
import com.pashkd.krender.engine.uicomposer.UiComposerHierarchyPanel
import com.pashkd.krender.engine.uicomposer.UiComposerInspectorPanel
import com.pashkd.krender.engine.uicomposer.UiComposerOperations
import com.pashkd.krender.engine.uicomposer.UiComposerPanelIds
import com.pashkd.krender.engine.uicomposer.UiComposerPreviewPayloadPanel
import com.pashkd.krender.engine.uicomposer.UiComposerState
import com.pashkd.krender.engine.uicomposer.UiComposerToolbarPanel
import com.pashkd.krender.engine.uicomposer.UiComposerUiLayoutDefaults
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

/**
 * Read-only editor scene opened for `.krui` UiScene assets from Asset Browser.
 *
 * This scene belongs to editor/tool UI and backend preview plumbing, not RuntimeUiService or
 * gameplay UI. Phase 4 decodes, validates, previews, inspects, and diagnoses the loaded `.krui`
 * document while intentionally omitting saving, node/property editing, add/delete/reorder,
 * drag/drop canvas editing, Skin editing, Asset Browser pickers, asset-id references, JSON text
 * editing, and full Scene2D actor serialization. The selected-node highlight is best-effort.
 */
class UiComposerScene(
    private val uiScenePath: String,
) : Scene("ui_composer") {
    override val config: SceneConfig = SceneConfigPresets.EditorTool

    private lateinit var composerState: UiComposerState
    private lateinit var loader: UiComposerDocumentLoader
    private lateinit var preview: GdxUiScenePreview
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private lateinit var operations: UiComposerOperations

    /**
     * Creates the read-only document state, Scene2D preview, and ImGui panels.
     */
    override fun show() {
        engine.logger.info(TAG) { "Showing UI Composer preview path='$uiScenePath'" }
        composerState = UiComposerState(uiScenePath = uiScenePath)
        loader = UiComposerDocumentLoader(engine.sceneFiles::readText)
        preview = GdxUiScenePreview(engine.logger)
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = UiComposerUiLayoutDefaults.assetPath,
            fallback = UiComposerUiLayoutDefaults.config,
        ).load(engine.logger)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        operations = UiComposerOperations(composerState, engine, layoutTracker)

        reloadDocumentAndPreview()

        world.systems.add(UiComposerPreviewUpdateSystem(composerState, preview))
        world.systems.add(createUiSystem())
    }

    /**
     * Handles deferred reload requests and then lets the registered systems update.
     */
    override fun update(dt: Float) {
        if (composerState.reloadRequested) {
            reloadDocumentAndPreview()
        } else if (composerState.previewRebuildRequested) {
            rebuildPreviewOnly()
        }
        super.update(dt)
    }

    /**
     * Draws the backend Scene2D preview after the main renderer and before ImGui.
     */
    override fun overlayRender() {
        if (::preview.isInitialized) {
            preview.render()
        }
    }

    /**
     * Keeps the Scene2D preview viewport matched to the editor tool window.
     */
    override fun resize(width: Int, height: Int) {
        if (::preview.isInitialized) {
            preview.resize(width, height)
        }
    }

    /**
     * Releases preview-owned Stage, Skin, and Texture resources.
     */
    override fun dispose() {
        if (::preview.isInitialized) {
            preview.dispose()
        }
        super.dispose()
    }

    private fun reloadDocumentAndPreview() {
        engine.logger.info(TAG) { "Reloading UI Composer document path='${composerState.uiScenePath}'" }
        loader.reload(composerState)
        if (composerState.document == null) {
            composerState.statusMessage = "Failed to load document."
            composerState.selectedActorInfo = null
            preview.rebuild(null)
            return
        }
        composerState.statusMessage = "Document reloaded."
        rebuildPreviewOnly(statusOnSuccess = "Document reloaded.")
    }

    private fun rebuildPreviewOnly(statusOnSuccess: String = "Preview rebuilt.") {
        composerState.previewRebuildRequested = false
        try {
            preview.rebuild(composerState.document, composerState.previewPayload)
            preview.updateDebugOverlay(
                showBounds = composerState.showBounds,
                highlightSelected = composerState.highlightSelected,
                selectedNodeId = composerState.selectedNodeId,
            )
            composerState.selectedActorInfo = preview.actorInfo(composerState.selectedNodeId)
            composerState.parseError = null
            composerState.statusMessage = statusOnSuccess
        } catch (error: Exception) {
            composerState.parseError = error.message ?: error::class.simpleName ?: "Unknown UI scene preview error."
            composerState.selectedActorInfo = null
            composerState.statusMessage = "Failed to build preview."
            engine.logger.error(TAG, error) {
                "UI Composer preview rebuild failed path='${composerState.uiScenePath}': ${composerState.parseError}"
            }
            preview.rebuild(null)
        }
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "UiComposerUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(uiSystem, "Toolbar", UiComposerToolbarPanel(composerState, operations, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(uiSystem, "Hierarchy", UiComposerHierarchyPanel(composerState, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(uiSystem, "Inspector", UiComposerInspectorPanel(composerState, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(uiSystem, "PreviewPayload", UiComposerPreviewPayloadPanel(composerState, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(uiSystem, "Diagnostics", UiComposerDiagnosticsPanel(composerState, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(
                uiSystem,
                "Logs",
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    panelEventLogger,
                    panelId = UiComposerPanelIds.Logs,
                    layoutTracker = layoutTracker,
                    initialAutoScrollToLatest = true,
                ),
            )
        }
    }

    private fun addPanel(uiSystem: UiSystem, name: String, panel: UiPanel) {
        uiSystem.addPanel(
            UiPanel {
                try {
                    panel.draw()
                } catch (error: Exception) {
                    engine.logger.error(TAG, error) {
                        "UI Composer panel draw failed panel='$name' path='$uiScenePath': ${error.message}"
                    }
                    throw error
                }
            },
        )
    }

    companion object {
        private const val TAG = "UiComposerScene"
    }
}

private class UiComposerPreviewUpdateSystem(
    private val state: UiComposerState,
    private val preview: GdxUiScenePreview,
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        preview.updateDebugOverlay(state.showBounds, state.highlightSelected, state.selectedNodeId)
        preview.update(dt)
        state.selectedActorInfo = preview.actorInfo(state.selectedNodeId)
    }
}
