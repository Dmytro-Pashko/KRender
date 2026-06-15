package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.tools.uicomposer.gdx.GdxUiComposerSkinMetadataReader
import com.pashkd.krender.engine.tools.uicomposer.gdx.GdxUiScenePreview
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.ui.editor.*
import com.pashkd.krender.engine.tools.uicomposer.*
import kotlin.math.pow

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
    override val config: SceneConfig = SceneConfigPresets.UiComposer

    private lateinit var composerState: UiComposerState
    private lateinit var loader: UiComposerDocumentLoader
    private lateinit var preview: GdxUiScenePreview
    private lateinit var skinMetadataReader: GdxUiComposerSkinMetadataReader

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
        textureOptionsProvider = UiComposerTextureOptionsProvider(engine.assetRegistry)
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = UiComposerUiLayoutDefaults.assetPath,
                fallback = UiComposerUiLayoutDefaults.config,
            ).load(engine.logger)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        operations = UiComposerOperations(composerState, engine, layoutTracker)

        refreshTextureOptions(reason = "initial")
        reloadDocumentAndPreview()

        world.systems.add(UiComposerCanvasInteractionSystem(composerState, preview, engine.input))
        world.systems.add(createUiSystem())
        world.systems.add(UiComposerPreviewUpdateSystem(composerState, preview))
    }

    /**
     * Handles deferred save/reload/rebuild requests and then lets the registered systems update.
     */
    override fun update(dt: Float) {
        handleUndoRedoShortcuts()
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

    private fun handleUndoRedoShortcuts() {
        val snapshot = engine.input.snapshot()
        if (snapshot.uiCapturesKeyboard) return

        val controlDown = snapshot.isDown(Key.ControlLeft) || snapshot.isDown(Key.ControlRight)
        if (!controlDown) return

        val shiftDown = snapshot.isDown(Key.ShiftLeft) || snapshot.isDown(Key.ShiftRight)
        val redoPressed = snapshot.wasPressed(Key.Y) || (shiftDown && snapshot.wasPressed(Key.Z))
        val undoPressed = !shiftDown && snapshot.wasPressed(Key.Z)

        if (redoPressed) {
            operations.redo()
        } else if (undoPressed) {
            operations.undo()
        }
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
    override fun resize(
        width: Int,
        height: Int,
    ) {
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
            composerState.guideSnapshot = UiComposerGuideSnapshot()
            composerState.skinMetadata = null
            composerState.styleValidationIssues = emptyList()
            composerState.textureValidationIssues = emptyList()
            composerState.bindingValidationIssues = emptyList()
            composerState.missingBindingKeys = emptyList()
            preview.rebuild(null)
            return
        }
        refreshSkinMetadata()
        val status =
            if (discardedUnsavedChanges) {
                "Reloaded document; unsaved changes were discarded."
            } else {
                "Document reloaded."
            }
        composerState.statusMessage = status
        rebuildPreviewOnly(statusOnSuccess = status)
    }

    private fun rebuildPreviewOnly(statusOnSuccess: String = "Preview rebuilt.") {
        composerState.previewRebuildRequested = false
        refreshBindingDiagnostics()
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
            composerState.guideSnapshot =
                preview.guideSnapshot(
                    selectedNodeId = composerState.selectedNodeId,
                    hoveredNodeId = composerState.hoveredNodeId,
                )
            composerState.parseError = null
            composerState.statusMessage = statusOnSuccess
        } catch (error: Exception) {
            composerState.parseError = error.message ?: error::class.simpleName ?: "Unknown UI scene preview error."
            composerState.selectedActorInfo = null
            composerState.guideSnapshot = UiComposerGuideSnapshot()
            composerState.statusMessage = "Failed to build preview."
            engine.logger.error(TAG, error) {
                "UI Composer preview rebuild failed path='${composerState.uiScenePath}': ${composerState.parseError}"
            }
            preview.rebuild(null)
        }
    }

    private fun refreshSkinMetadata() {
        val document =
            composerState.document ?: run {
                composerState.skinMetadata = null
                composerState.styleValidationIssues = emptyList()
                composerState.bindingValidationIssues = emptyList()
                composerState.missingBindingKeys = emptyList()
                return
            }
        composerState.skinMetadata = skinMetadataReader.read(document.skin)
        refreshUiComposerValidationBuckets(composerState, document)
    }

    private fun refreshTextureOptions(reason: String) {
        composerState.textureOptionsReloadRequested = false
        try {
            val snapshot = engine.assetRegistry.scanSnapshot()
            engine.assetRegistry.applySnapshot(snapshot)
            composerState.textureOptions = textureOptionsProvider.listTextureOptions()
            val queuedTexturePreviewCount = composerState.textureOptions.size
            composerState.textureOptions.forEach { option ->
                engine.assets.queue(AssetRef.texture(option.path))
            }
            composerState.textureAssetTypesByPath =
                engine.assetRegistry.assets.associate { asset -> asset.path to asset.type }
            composerState.document?.let { document ->
                refreshUiComposerValidationBuckets(composerState, document)
            }
            composerState.statusMessage = "Indexed ${composerState.textureOptions.size} texture assets."
            engine.logger.info(TAG) {
                "UiComposer texture options refreshed reason='$reason' options=${composerState.textureOptions.size} " +
                    "queuedTexturePreviews=$queuedTexturePreviewCount assets=${snapshot.assets.size} errors=${snapshot.errors.size}"
            }
        } catch (error: Exception) {
            composerState.statusMessage = "Texture refresh failed: ${error.message}"
            engine.logger.warn(TAG, error) {
                "UiComposer texture option refresh failed reason='$reason': ${error.message}"
            }
        }
    }

    private fun refreshBindingDiagnostics() {
        val document =
            composerState.document ?: run {
                composerState.bindingValidationIssues = emptyList()
                composerState.missingBindingKeys = emptyList()
                return
            }
        refreshUiComposerValidationBuckets(composerState, document)
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "UiComposerUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(
                uiSystem,
                "Toolbar",
                UiComposerToolbarPanel(composerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "PreviewCanvas",
                UiComposerPreviewCanvasPanel(composerState, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Hierarchy",
                UiComposerHierarchyPanel(composerState, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Structure",
                UiComposerStructurePanel(composerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Inspector",
                UiComposerInspectorPanel(
                    composerState,
                    operations,
                    engine.assets,
                    engine.ui,
                    engine.logger,
                    layoutConfig,
                    layoutTracker,
                    panelEventLogger,
                ),
            )
            addPanel(
                uiSystem,
                "SceneBindings",
                UiComposerSceneBindingsPanel(composerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Diagnostics",
                UiComposerDiagnosticsPanel(composerState, layoutConfig, layoutTracker, panelEventLogger),
            )
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

    private fun addPanel(
        uiSystem: UiSystem,
        name: String,
        panel: UiPanel,
    ) {
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
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val rect = state.canvasPreviewRect
        if (rect.isValid) {
            preview.setCanvasViewport(
                x = rect.x.toInt(),
                y = rect.y.toInt(),
                width = rect.width.toInt(),
                height = rect.height.toInt(),
                logicalWidth = state.previewLogicalWidth,
                logicalHeight = state.previewLogicalHeight,
                cameraOffsetX = state.previewCameraOffsetX,
                cameraOffsetY = state.previewCameraOffsetY,
                previewZoom = state.previewZoom,
            )
        } else {
            preview.clearCanvasViewport()
        }
        preview.updateDebugOverlay(
            showBounds = state.showBounds,
            highlightSelected = state.highlightSelected,
            selectedNodeId = state.selectedNodeId,
            highlightHovered = state.highlightHovered,
            hoveredNodeId = state.hoveredNodeId,
        )
        preview.updateVisualGuideOptions(
            UiComposerVisualGuideOptions(
                showSelectedGuide = state.showSelectedGuide,
                showHoveredGuide = state.showHoveredGuide,
                showParentChainGuides = state.showParentChainGuides,
                showPaddingGuides = state.showPaddingGuides,
                showAlignmentGuide = state.showAlignmentGuide,
                showTableOrientationGuide = state.showTableOrientationGuide,
                showNodeIdLabel = state.showNodeIdLabel,
                showLocalMouseCoordinates = state.showLocalMouseCoordinates,
                canvasLocalMouseX = state.canvasLocalMouseX,
                canvasLocalMouseY = state.canvasLocalMouseY,
            ),
        )
        preview.update(dt)
        state.selectedActorInfo = preview.actorInfo(state.selectedNodeId)
        state.guideSnapshot =
            preview.guideSnapshot(
                selectedNodeId = state.selectedNodeId,
                hoveredNodeId = state.hoveredNodeId,
            )
    }
}

/**
 * Selection-only mouse interaction layer for the UiComposer Scene2D preview canvas.
 *
 * This system belongs to editor canvas interaction, not the backend preview
 * builder, shared `.krui` model, or runtime UI action system. It reads the
 * current engine input snapshot, scopes hit-testing to the Preview Canvas effective
 * preview rectangle, updates hover state, supports Ctrl+left-drag camera panning,
 * applies mouse-wheel zoom, and selects the hit node on left click. It
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
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val snapshot = input.snapshot()
        val previewRect = state.canvasPreviewRect
        if (!previewRect.isValid) {
            state.hoveredNodeId = null
            state.canvasLocalMouseX = null
            state.canvasLocalMouseY = null
            state.canvasWorldMouseX = null
            state.canvasWorldMouseY = null
            state.canvasPanning = false
            state.canvasStatusMessage = "Preview canvas is not visible."
            return
        }

        val mouseX = snapshot.mousePosition.x
        val mouseY = snapshot.mousePosition.y
        if (!previewRect.contains(mouseX, mouseY)) {
            state.hoveredNodeId = null
            state.canvasLocalMouseX = null
            state.canvasLocalMouseY = null
            state.canvasWorldMouseX = null
            state.canvasWorldMouseY = null
            state.canvasPanning = false
            state.canvasStatusMessage = "Mouse outside preview canvas."
            return
        }

        val localX = mouseX - previewRect.x
        val localY = mouseY - previewRect.y
        state.canvasLocalMouseX = localX
        state.canvasLocalMouseY = localY

        if (snapshot.scrollDelta != 0f) {
            state.previewZoom = clampPreviewZoom(state.previewZoom * ZoomWheelStep.pow(-snapshot.scrollDelta))
            state.statusMessage = "Preview zoom set to ${(state.previewZoom * 100f).toInt()}%."
        }
        updateWorldMouse(localX, localY, previewRect)

        val controlDown = snapshot.isDown(Key.ControlLeft) || snapshot.isDown(Key.ControlRight)
        val panning = controlDown && snapshot.isMouseDown(MouseButton.Left)
        state.canvasPanning = panning
        if (panning) {
            val worldPerPixelX =
                previewWorldUnitsPerScreenPixel(
                    logicalSize = state.previewLogicalWidth,
                    screenSize = previewRect.width,
                    zoom = state.previewZoom,
                )
            val worldPerPixelY =
                previewWorldUnitsPerScreenPixel(
                    logicalSize = state.previewLogicalHeight,
                    screenSize = previewRect.height,
                    zoom = state.previewZoom,
                )
            state.previewCameraOffsetX -= snapshot.mouseDelta.x * worldPerPixelX
            state.previewCameraOffsetY += snapshot.mouseDelta.y * worldPerPixelY
            updateWorldMouse(localX, localY, previewRect)
            state.hoveredNodeId = null
            state.canvasStatusMessage = "Panning preview canvas."
            return
        }

        if (!state.canvasSelectionEnabled) {
            state.hoveredNodeId = null
            state.canvasStatusMessage = "Canvas selection disabled."
            return
        }

        val hit =
            preview.hitTestLocal(
                localX = localX.toInt(),
                localY = localY.toInt(),
            )
        state.hoveredNodeId = hit?.nodeId
        state.canvasStatusMessage = hit?.let { "Hovered '${it.nodeId}'." } ?: "No preview actor hovered."

        if (!controlDown && snapshot.wasMousePressed(MouseButton.Left) && hit != null) {
            state.selectedNodeId = hit.nodeId
            state.statusMessage = "Selected '${hit.nodeId}' from preview."
            state.canvasStatusMessage = "Selected '${hit.nodeId}' from preview canvas."
        }
    }

    private companion object {
        private const val ZoomWheelStep = 1.1f
    }

    private fun updateWorldMouse(
        localX: Float,
        localY: Float,
        previewRect: UiComposerCanvasRect,
    ) {
        val visibleWorldWidth = state.previewLogicalWidth.toFloat() / state.previewZoom
        val visibleWorldHeight = state.previewLogicalHeight.toFloat() / state.previewZoom
        val cameraCenterX = state.previewLogicalWidth.toFloat() * 0.5f + state.previewCameraOffsetX
        val cameraCenterY = state.previewLogicalHeight.toFloat() * 0.5f + state.previewCameraOffsetY
        state.canvasWorldMouseX = cameraCenterX - visibleWorldWidth * 0.5f +
            localX / previewRect.width.coerceAtLeast(1f) * visibleWorldWidth
        state.canvasWorldMouseY = cameraCenterY + visibleWorldHeight * 0.5f -
            localY / previewRect.height.coerceAtLeast(1f) * visibleWorldHeight
    }
}
