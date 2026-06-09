package com.pashkd.krender.game

import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.MouseButton
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.backend.gdx.ui.composer.GdxUiComposerSkinMetadataReader
import com.pashkd.krender.engine.backend.gdx.ui.composer.GdxUiScenePreview
import com.pashkd.krender.engine.assets.AssetImporterRegistry
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.LocalAssetRegistryService
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
import com.pashkd.krender.engine.uicomposer.UiComposerStructurePanel
import com.pashkd.krender.engine.uicomposer.UiComposerToolbarPanel
import com.pashkd.krender.engine.uicomposer.UiComposerTextureOptionsProvider
import com.pashkd.krender.engine.uicomposer.UiComposerUiLayoutDefaults
import com.pashkd.krender.engine.uicomposer.validateTextureReferences
import com.pashkd.krender.engine.uicomposer.validateStyleReferences
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

/**
 * Editor scene opened for `.krui` UiScene assets from Asset Browser.
 *
 * This scene belongs to editor/tool UI and backend preview plumbing, not RuntimeUiService or
 * gameplay UI. It can decode and validate `.krui`, render a Scene2D preview, inspect and edit
 * selected-node scalar fields, perform hierarchy-driven structure editing, save the document,
 * select nodes through hierarchy or selection-only preview canvas hit-testing, and provide
 * Skin-backed style/background pickers plus Asset Registry-backed Image texture picking.
 *
 * It intentionally does not implement canvas drag/drop, actor resizing on canvas, multi-select,
 * canvas-based structure editing, Skin editing, texture import/copy, atlas region picking, texture
 * thumbnails, Asset Browser drag/drop, asset-id references in `.krui`, snapping, transform gizmos,
 * JSON source editing, generic visual layout solving, or full Scene2D actor serialization. Reload
 * dirty confirmation is a simple toolbar state, and selected/hover highlights are best-effort
 * Scene2D debug drawing.
 */
class UiComposerScene(
    private val uiScenePath: String,
) : Scene("ui_composer") {
    override val config: SceneConfig = SceneConfigPresets.EditorTool

    private lateinit var composerState: UiComposerState
    private lateinit var loader: UiComposerDocumentLoader
    private lateinit var preview: GdxUiScenePreview
    private lateinit var skinMetadataReader: GdxUiComposerSkinMetadataReader

    /**
     * Local Asset Registry snapshot used only for the Image texture picker.
     *
     * This is intentionally local because [EngineContext] currently exposes the runtime
     * [AssetService], not a shared [AssetRegistryService] suitable for editor picker scans. It
     * should be replaced with the shared engine asset registry once Asset Browser and Composer use
     * the same registry service.
     */
    private lateinit var textureRegistry: LocalAssetRegistryService
    private lateinit var textureOptionsProvider: UiComposerTextureOptionsProvider
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private lateinit var operations: UiComposerOperations

    /**
     * Creates the document state, Scene2D preview, editor operations, and ImGui panels.
     */
    override fun show() {
        engine.logger.info(TAG) { "Showing UI Composer preview path='$uiScenePath'" }
        composerState = UiComposerState(uiScenePath = uiScenePath)
        loader = UiComposerDocumentLoader(engine.sceneFiles::readText)
        preview = GdxUiScenePreview(engine.logger)
        skinMetadataReader = GdxUiComposerSkinMetadataReader(engine.logger)
        textureRegistry = LocalAssetRegistryService(
            logger = engine.logger,
            importers = AssetImporterRegistry.withDefaults(engine.logger),
        )
        textureOptionsProvider = UiComposerTextureOptionsProvider(textureRegistry)
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = UiComposerUiLayoutDefaults.assetPath,
            fallback = UiComposerUiLayoutDefaults.config,
        ).load(engine.logger)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        operations = UiComposerOperations(composerState, engine, layoutTracker)

        refreshTextureOptions(reason = "initial")
        reloadDocumentAndPreview()

        world.systems.add(UiComposerCanvasInteractionSystem(composerState, preview, engine.input))
        world.systems.add(UiComposerPreviewUpdateSystem(composerState, preview))
        world.systems.add(createUiSystem())
    }

    /**
     * Handles deferred save/reload/rebuild requests and then lets the registered systems update.
     */
    override fun update(dt: Float) {
        if (composerState.saveRequested) {
            operations.saveDocument()
        }
        if (composerState.textureOptionsReloadRequested) {
            refreshTextureOptions(reason = "manual")
        }
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
        if (::skinMetadataReader.isInitialized) {
            skinMetadataReader.dispose()
        }
        super.dispose()
    }

    private fun reloadDocumentAndPreview() {
        val discardedUnsavedChanges = composerState.dirty
        engine.logger.info(TAG) { "Reloading UI Composer document path='${composerState.uiScenePath}'" }
        loader.reload(composerState)
        composerState.dirty = false
        composerState.pendingReloadConfirmation = false
        composerState.saveStatusMessage = null
        if (composerState.document == null) {
            composerState.statusMessage = "Failed to load document."
            composerState.selectedActorInfo = null
            composerState.skinMetadata = null
            composerState.styleValidationIssues = emptyList()
            composerState.textureValidationIssues = emptyList()
            preview.rebuild(null)
            return
        }
        refreshSkinMetadata()
        val status = if (discardedUnsavedChanges) {
            "Reloaded document; unsaved changes were discarded."
        } else {
            "Document reloaded."
        }
        composerState.statusMessage = status
        rebuildPreviewOnly(statusOnSuccess = status)
    }

    private fun rebuildPreviewOnly(statusOnSuccess: String = "Preview rebuilt.") {
        composerState.previewRebuildRequested = false
        try {
            preview.rebuild(composerState.document, composerState.previewPayload)
            preview.updateDebugOverlay(
                showBounds = composerState.showBounds,
                highlightSelected = composerState.highlightSelected,
                selectedNodeId = composerState.selectedNodeId,
                highlightHovered = composerState.highlightHovered,
                hoveredNodeId = composerState.hoveredNodeId,
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

    private fun refreshSkinMetadata() {
        val document = composerState.document ?: run {
            composerState.skinMetadata = null
            composerState.styleValidationIssues = emptyList()
            return
        }
        composerState.skinMetadata = skinMetadataReader.read(document.skin)
        composerState.styleValidationIssues = validateStyleReferences(document, composerState.skinMetadata)
        composerState.textureValidationIssues = validateTextureReferences(
            document = document,
            textureOptions = composerState.textureOptions,
            assetTypeByPath = composerState.textureAssetTypesByPath,
        )
    }

    private fun refreshTextureOptions(reason: String) {
        composerState.textureOptionsReloadRequested = false
        try {
            val snapshot = textureRegistry.scanSnapshot()
            textureRegistry.applySnapshot(snapshot)
            composerState.textureOptions = textureOptionsProvider.listTextureOptions()
            composerState.textureAssetTypesByPath = textureRegistry.assets.associate { asset -> asset.path to asset.type }
            composerState.document?.let { document ->
                composerState.textureValidationIssues = validateTextureReferences(
                    document = document,
                    textureOptions = composerState.textureOptions,
                    assetTypeByPath = composerState.textureAssetTypesByPath,
                )
            }
            composerState.statusMessage = "Indexed ${composerState.textureOptions.size} texture assets."
            engine.logger.info(TAG) {
                "UiComposer texture options refreshed reason='$reason' options=${composerState.textureOptions.size} " +
                    "assets=${snapshot.assets.size} errors=${snapshot.errors.size}"
            }
        } catch (error: Exception) {
            composerState.statusMessage = "Texture refresh failed: ${error.message}"
            engine.logger.warn(TAG, error) {
                "UiComposer texture option refresh failed reason='$reason': ${error.message}"
            }
        }
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "UiComposerUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(uiSystem, "Toolbar", UiComposerToolbarPanel(composerState, operations, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(uiSystem, "Hierarchy", UiComposerHierarchyPanel(composerState, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(uiSystem, "Structure", UiComposerStructurePanel(composerState, operations, layoutConfig, layoutTracker, panelEventLogger))
            addPanel(uiSystem, "Inspector", UiComposerInspectorPanel(composerState, operations, layoutConfig, layoutTracker, panelEventLogger))
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
        preview.updateDebugOverlay(
            showBounds = state.showBounds,
            highlightSelected = state.highlightSelected,
            selectedNodeId = state.selectedNodeId,
            highlightHovered = state.highlightHovered,
            hoveredNodeId = state.hoveredNodeId,
        )
        preview.update(dt)
        state.selectedActorInfo = preview.actorInfo(state.selectedNodeId)
    }
}

/**
 * Selection-only mouse interaction layer for the UiComposer Scene2D preview canvas.
 *
 * This system belongs to editor canvas interaction, not the backend preview
 * builder, shared `.krui` model, or runtime UI action system. It reads the
 * current engine input snapshot, respects ImGui mouse capture, hit-tests the
 * preview, updates hover state, and selects the hit node on left click. It
 * intentionally does not mutate `.krui`, save files, dispatch Woolboy runtime
 * actions, drag/drop, resize, edit properties by dragging, add/delete/reorder
 * nodes from the canvas, support multi-select, snap, create transform gizmos,
 * edit Skins, pick assets, or solve layout.
 */
private class UiComposerCanvasInteractionSystem(
    private val state: UiComposerState,
    private val preview: GdxUiScenePreview,
    private val input: InputService,
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        val snapshot = input.snapshot()
        if (!state.canvasSelectionEnabled) {
            state.hoveredNodeId = null
            state.canvasStatusMessage = "Canvas selection disabled."
            return
        }
        if (snapshot.uiCapturesMouse) {
            state.hoveredNodeId = null
            state.canvasStatusMessage = "Canvas input paused while ImGui captures mouse."
            return
        }

        val hit = preview.hitTest(
            screenX = snapshot.mousePosition.x.toInt(),
            screenY = snapshot.mousePosition.y.toInt(),
        )
        state.hoveredNodeId = hit?.nodeId
        state.canvasStatusMessage = hit?.let { "Hovered '${it.nodeId}'." } ?: "No preview actor hovered."

        if (snapshot.wasMousePressed(MouseButton.Left) && hit != null) {
            state.selectedNodeId = hit.nodeId
            state.statusMessage = "Selected '${hit.nodeId}' from preview."
            state.canvasStatusMessage = "Selected '${hit.nodeId}' from preview canvas."
        }
    }
}
