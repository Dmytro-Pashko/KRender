package com.pashkd.krender.engine.tools.terraineditor

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.material.TerrainMaterialDescriptor
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.*
import kotlin.math.*

private const val DEFAULT_TERRAIN_MATERIAL_LIBRARY_PATH = "materials/terrain_materials.json"

/**
 * Editor-side terrain workflow controller.
 *
 * This system sits between raw input, persistent editor state, and mutable
 * [TerrainData]. Each frame it:
 * 1. Resolves the active terrain and camera entities.
 * 2. Applies queued UI commands such as create/load/save/undo.
 * 3. Converts mouse input into a hovered terrain hit.
 * 4. Builds and applies brush strokes while the pointer is held down.
 * 5. Keeps renderer/UI preview state synchronized with the underlying terrain.
 *
 * The class is intentionally stateful because a single drag stroke spans many
 * frames and needs transient data such as [flattenHeight] and an accumulating
 * [activePatchBuilder] for undo/redo.
 */
class TerrainEditorSystem(
    private val input: InputService,
    private val logger: Logger,
    private val state: TerrainEditorState,
    private val generatorsById: Map<String, TerrainGenerator> =
        listOf(FlatTerrainGenerator()).associateBy(
            TerrainGenerator::id,
        ),
    private val terrainMaterialsById: Map<String, TerrainMaterialDescriptor> = emptyMap(),
) : System() {
    /**
     * Latest terrain hit under the mouse cursor, if any.
     */
    var hoveredHit: TerrainHit? = null
        private set

    private var brushActive: Boolean = false
    private var flattenHeight: Float? = null
    private var paintLayerWarningShown: Boolean = false
    private val editHistory = TerrainEditHistory()
    private val terrainPersistence = TerrainPersistence(logger)
    private var activePatchBuilder: TerrainEditPatchBuilder? = null
    private var activeLayerPaintSign: Float = 1f
    private var lastObservedSelectedLayerId: Int? = null

    /**
     * Processes one editor frame.
     *
     * The method first resolves command-style state changes (terrain creation,
     * persistence, history operations), then handles interactive viewport input.
     * Brush strokes are assembled incrementally while the pointer remains down so
     * a drag gesture becomes a single history entry instead of many tiny edits.
     */
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val terrainEntity =
            world.query<TransformComponent, TerrainDataComponent, TerrainRendererComponent>().firstOrNull()
                ?: run {
                    resetBrushState()
                    return
                }
        val terrain =
            terrainEntity.get<TerrainDataComponent>()
                ?: run {
                    resetBrushState()
                    return
                }
        val terrainRenderer =
            terrainEntity.get<TerrainRendererComponent>()
                ?: run {
                    resetBrushState()
                    return
                }
        val cameraEntity =
            world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull()
                ?: run {
                    resetBrushState()
                    return
                }
        val cameraTransform =
            cameraEntity.get<TransformComponent>()
                ?: run {
                    resetBrushState()
                    return
                }
        val camera =
            cameraEntity.get<PerspectiveCameraComponent>()
                ?: run {
                    resetBrushState()
                    return
                }
        val terrainTransform =
            terrainEntity.get<TransformComponent>()
                ?: run {
                    resetBrushState()
                    return
                }
        val snapshot = input.snapshot()
        // Tab swaps keyboard/mouse ownership between UI widgets and the viewport.
        // If focus leaves the viewport mid-stroke, the stroke is committed first.
        if (snapshot.wasPressed(Key.Tab)) {
            state.inputFocus =
                when (state.inputFocus) {
                    TerrainEditorInputFocus.Ui -> TerrainEditorInputFocus.Viewport
                    TerrainEditorInputFocus.Viewport -> TerrainEditorInputFocus.Ui
                }
            if (state.inputFocus == TerrainEditorInputFocus.Ui && brushActive) {
                finishBrushStroke()
            }
        }
        if (state.selectedGeneratorId == null || state.selectedGeneratorId !in generatorsById) {
            state.selectedGeneratorId = generatorsById.keys.firstOrNull()
        }

        processTerrainCommands(terrain, terrainRenderer)
        processPersistenceCommands(terrain, terrainRenderer)
        processHistoryCommands(terrain, snapshot)
        syncStateFromTerrain(terrain, terrainRenderer)
        syncSelectedLayerPreviewState(terrain)
        syncHistoryState()

        // Viewport input is allowed either when the viewport explicitly owns focus
        // or when the UI reports that it is not consuming that device.
        val viewportFocus = state.inputFocus == TerrainEditorInputFocus.Viewport
        val keyboardAvailable = viewportFocus || !snapshot.uiCapturesKeyboard
        val mouseAvailable = viewportFocus || !snapshot.uiCapturesMouse

        if (keyboardAvailable && snapshot.wasPressed(Key.G)) {
            state.wireframeEnabled = !state.wireframeEnabled
        }

        updateBrushBindings(snapshot, keyboardAvailable, mouseAvailable)
        syncRendererStateFromControls(terrainRenderer)
        // Mouse hover is only meaningful when the viewport can consume the mouse.
        hoveredHit =
            if (!mouseAvailable) {
                null
            } else {
                TerrainRaycaster.pickTerrain(
                    screenPosition = snapshot.mousePosition,
                    viewportSize = snapshot.viewportSize,
                    cameraTransform = cameraTransform,
                    camera = camera,
                    terrainTransform = terrainTransform,
                    terrain = terrain.data,
                )
            }
        state.hoveredTerrainPosition = hoveredHit?.worldPosition?.let(::formatPosition) ?: "none"

        // Treat both Down and Move as a continuous pressed stroke so painting can
        // continue while the pointer is dragged across the terrain.
        val pointerDown = snapshot.pointers.any { it.phase == PointerPhase.Down || it.phase == PointerPhase.Move }
        val pointerReleased =
            snapshot.pointers.any { it.phase == PointerPhase.Up || it.phase == PointerPhase.Cancelled }

        if ((pointerReleased || hoveredHit == null) && brushActive) {
            finishBrushStroke()
        }

        if (pointerDown && hoveredHit != null) {
            val hit = hoveredHit ?: return
            if (state.brushMode == TerrainBrushMode.PaintLayer && state.selectedLayerId == null) {
                if (!paintLayerWarningShown) {
                    logger.warn(TAG) { "PaintLayer selected without an active terrain layer" }
                    paintLayerWarningShown = true
                }
                finishBrushStroke()
                return
            }

            if (!brushActive) {
                // Capture any per-stroke values exactly once. Flatten must keep a
                // stable target height for the full drag, and history should treat
                // the drag as one named edit.
                brushActive = true
                state.brushActive = true
                flattenHeight = terrain.data.sampleHeight(hit.localX, hit.localZ)
                activeLayerPaintSign = effectiveLayerPaintSign(snapshot)
                activePatchBuilder = TerrainEditPatchBuilder(buildPatchLabel())
            }

            val stroke =
                TerrainBrushStroke(
                    localX = hit.localX,
                    localZ = hit.localZ,
                    radius = state.brushRadius,
                    strength = state.brushStrength,
                    falloff = state.brushFalloff,
                    mode = state.brushMode,
                    deltaSeconds = dt,
                    flattenHeight = flattenHeight,
                    targetLayerId = state.selectedLayerId,
                    layerWeightDeltaSign = if (state.brushMode == TerrainBrushMode.PaintLayer) activeLayerPaintSign else 1f,
                )

            if (TerrainBrushApplier.apply(terrain.data, stroke, activePatchBuilder)) {
                markPreviewDirty(terrain)
            }
        }

        syncHistoryState()
    }

    /**
     * Emits brush preview lines into the scene render command buffer.
     *
     * The preview is drawn as an approximated circle plus a small center cross so
     * the user can see both the current radius and exact hit point.
     */
    override fun debugRender(world: SceneWorld) {
        val terrainEntity = world.query<TransformComponent, TerrainDataComponent>().firstOrNull() ?: return
        val terrainTransform = terrainEntity.get<TransformComponent>() ?: return
        val terrain = terrainEntity.get<TerrainDataComponent>() ?: return
        val hit = hoveredHit ?: return

        val lineColor = if (brushActive) Color(1f, 0.72f, 0.28f, 1f) else Color(0.3f, 0.95f, 0.45f, 1f)
        val segments = 40
        var previous: Vec3? = null

        // Sample points around the brush radius and connect them with line
        // segments. Each point is lifted slightly above the terrain so the guide
        // remains visible without z-fighting against the surface.
        for (segment in 0..segments) {
            val angle = (segment.toFloat() / segments.toFloat()) * (PI.toFloat() * 2f)
            val localX = hit.localX + cos(angle) * state.brushRadius
            val localZ = hit.localZ + sin(angle) * state.brushRadius
            val worldPoint =
                Vec3(
                    terrainTransform.position.x + localX,
                    terrainTransform.position.y + terrain.data.sampleHeight(localX, localZ) + 0.03f,
                    terrainTransform.position.z + localZ,
                )
            previous?.let {
                world.renderCommands.submit(DrawLine(it, worldPoint, lineColor))
            }
            previous = worldPoint
        }

        val crossHalfSize = state.brushRadius.coerceAtMost(0.9f)
        val center = hit.worldPosition
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(center.x - crossHalfSize, center.y + 0.04f, center.z),
                to = Vec3(center.x + crossHalfSize, center.y + 0.04f, center.z),
                color = lineColor,
            ),
        )
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(center.x, center.y + 0.04f, center.z - crossHalfSize),
                to = Vec3(center.x, center.y + 0.04f, center.z + crossHalfSize),
                color = lineColor,
            ),
        )
    }

    /**
     * Updates brush mode and scalar parameters from normalized input.
     *
     * Function keys choose the active mode, while the mouse wheel adjusts either
     * radius or strength depending on whether Shift is held.
     */
    private fun updateBrushBindings(
        snapshot: InputSnapshot,
        keyboardAvailable: Boolean,
        mouseAvailable: Boolean,
    ) {
        if (keyboardAvailable) {
            if (snapshot.wasPressed(Key.F1)) state.brushMode = TerrainBrushMode.Raise
            if (snapshot.wasPressed(Key.F2)) state.brushMode = TerrainBrushMode.Lower
            if (snapshot.wasPressed(Key.F3)) state.brushMode = TerrainBrushMode.Flatten
            if (snapshot.wasPressed(Key.F4)) state.brushMode = TerrainBrushMode.Smooth
            if (snapshot.wasPressed(Key.F5)) state.brushMode = TerrainBrushMode.PaintLayer
        }

        if (mouseAvailable && snapshot.scrollDelta != 0f) {
            if (snapshot.isDown(Key.ShiftLeft)) {
                state.brushStrength = (state.brushStrength - snapshot.scrollDelta).coerceIn(0.1f, 32f)
            } else {
                state.brushRadius = (state.brushRadius - snapshot.scrollDelta).coerceIn(1f, 64f)
            }
        }
    }

    private fun syncStateFromTerrain(
        terrain: TerrainDataComponent,
        renderer: TerrainRendererComponent,
    ) {
        // Rebuild the editor-facing layer list from terrain data every frame so
        // UI widgets always reflect the current terrain ordering and metadata.
        val layers = terrain.data.allLayers()
        state.layers =
            layers.mapIndexed { index, layer ->
                TerrainLayerOption(
                    id = layer.id,
                    name = layer.name,
                    materialId = layer.materialId,
                    color = layer.color,
                    visible = layer.visible,
                    tiling = layer.tiling,
                    index = index,
                )
            }
        state.vertices = renderer.vertexCount
        state.triangles = renderer.triangleCount
        state.terrainSize = "${terrain.data.width} x ${terrain.data.height}"
        state.wireframeEnabled = renderer.displayMode == TerrainDisplayMode.Wireframe
        if (state.selectedLayerId !in layers.map(TerrainLayer::id)) {
            state.selectedLayerId = layers.firstOrNull()?.id
        }
        val selected = layers.firstOrNull { it.id == state.selectedLayerId }
        if (selected != null) {
            state.selectedLayerName = selected.name
            state.selectedLayerMaterialId = selected.materialId ?: ""
            state.selectedLayerMaterialIndex = state.terrainMaterials.indexOfFirst { it.id == selected.materialId }
            state.selectedLayerColor =
                floatArrayOf(selected.color.r, selected.color.g, selected.color.b, selected.color.a)
            state.selectedLayerVisible = selected.visible
            state.selectedLayerTiling = selected.tiling
            if (selected.materialId != null && state.selectedLayerMaterialIndex < 0) {
                state.materialMessage = "Missing material: ${selected.materialId}"
            } else if (state.materialMessage.startsWith("Missing material:")) {
                state.materialMessage = ""
            }
        } else {
            state.selectedLayerName = ""
            state.selectedLayerMaterialId = ""
            state.selectedLayerMaterialIndex = -1
            state.selectedLayerColor = floatArrayOf(1f, 1f, 1f, 1f)
            state.selectedLayerVisible = true
            state.selectedLayerTiling = 1f
        }
    }

    private fun syncHistoryState() {
        // Mirror the history model into the editor state so UI code can stay dumb
        // and simply display the latest derived values.
        state.canUndo = editHistory.canUndo
        state.canRedo = editHistory.canRedo
        state.undoLabel = editHistory.peekUndoLabel()
        state.redoLabel = editHistory.peekRedoLabel()
        state.undoCount = editHistory.undoCount()
        state.redoCount = editHistory.redoCount()
        state.historyMemoryBytes = editHistory.estimatedMemoryBytes()
        state.hasUnsavedChanges = editHistory.hasUnsavedChanges()
        state.currentHistoryRevision = editHistory.currentRevision()
        state.cleanHistoryRevision = editHistory.cleanRevision()
    }

    /**
     * Applies history-related commands from both UI buttons and keyboard shortcuts.
     *
     * Any active brush stroke is committed before undo/redo so partial drag edits
     * cannot be left hanging outside the history stack.
     */
    private fun processHistoryCommands(
        terrain: TerrainDataComponent,
        snapshot: InputSnapshot,
    ) {
        var changed = false
        var commandHandled = false
        if (state.clearHistoryRequested) {
            state.clearHistoryRequested = false
            finishBrushStroke()
            val wasDirty = editHistory.hasUnsavedChanges()
            editHistory.clear()
            if (!wasDirty) {
                editHistory.markClean()
            }
            commandHandled = true
        }
        if (state.undoRequested) {
            state.undoRequested = false
            finishBrushStroke()
            if (editHistory.undo(terrain.data)) {
                changed = true
            }
            commandHandled = true
        }
        if (state.redoRequested) {
            state.redoRequested = false
            finishBrushStroke()
            if (editHistory.redo(terrain.data)) {
                changed = true
            }
            commandHandled = true
        }

        val controlDown = snapshot.isDown(Key.ControlLeft) || snapshot.isDown(Key.ControlRight)
        val shiftDown = snapshot.isDown(Key.ShiftLeft) || snapshot.isDown(Key.ShiftRight)
        val keyboardAvailable = state.inputFocus == TerrainEditorInputFocus.Viewport || !snapshot.uiCapturesKeyboard
        // Support both Ctrl+Y and Ctrl+Shift+Z for redo to match common editor
        // conventions on different platforms.
        if (!commandHandled && keyboardAvailable && controlDown) {
            val redoPressed =
                snapshot.wasPressed(Key.Y) ||
                    (shiftDown && snapshot.wasPressed(Key.Z))
            val undoPressed = !shiftDown && snapshot.wasPressed(Key.Z)
            if (redoPressed) {
                finishBrushStroke()
                if (editHistory.redo(terrain.data)) {
                    changed = true
                }
            } else if (undoPressed) {
                finishBrushStroke()
                if (editHistory.undo(terrain.data)) {
                    changed = true
                }
            }
        }

        if (changed) {
            markPreviewDirty(terrain)
        }
    }

    /**
     * Finalizes the currently active brush stroke and pushes it into undo history.
     *
     * Returns `true` when a non-empty patch was produced and stored.
     */
    private fun finishBrushStroke(): Boolean {
        val pushed = activePatchBuilder?.build()?.let(editHistory::push) == true
        resetBrushState()
        return pushed
    }

    private fun resetBrushState() {
        activePatchBuilder = null
        brushActive = false
        state.brushActive = false
        flattenHeight = null
        activeLayerPaintSign = 1f
    }

    /**
     * Builds the human-readable history label for the current brush stroke.
     */
    private fun buildPatchLabel(): String =
        when (state.brushMode) {
            TerrainBrushMode.Raise -> "Raise terrain"
            TerrainBrushMode.Lower -> "Lower terrain"
            TerrainBrushMode.Flatten -> "Flatten to ${"%.2f".format(flattenHeight ?: 0f)}"
            TerrainBrushMode.Smooth -> "Smooth terrain"
            TerrainBrushMode.PaintLayer -> {
                val layerLabel =
                    state.layers.firstOrNull { it.id == state.selectedLayerId }?.name
                        ?: state.selectedLayerId?.toString()
                        ?: "none"
                if (activeLayerPaintSign < 0f) "Erase layer: $layerLabel" else "Paint layer: $layerLabel"
            }
        }

    /**
     * Pushes display mode toggles from editor state into the terrain renderer.
     */
    private fun syncRendererStateFromControls(renderer: TerrainRendererComponent) {
        val expectedMode =
            if (state.wireframeEnabled) {
                TerrainDisplayMode.Wireframe
            } else {
                TerrainDisplayMode.Solid
            }
        if (renderer.displayMode != expectedMode) {
            renderer.setDisplayMode(expectedMode)
        }
    }

    /**
     * Applies queued terrain-structure and preview commands from the editor state.
     *
     * This includes creating/regenerating the terrain, adding/removing/reordering
     * layers, and handling preview mode changes that require a mesh/material
     * refresh.
     */
    private fun processTerrainCommands(
        terrain: TerrainDataComponent,
        renderer: TerrainRendererComponent,
    ) {
        if (state.previewSettingsChanged) {
            state.previewSettingsChanged = false
            markPreviewDirty(terrain)
            state.previewMessage =
                "Preview: ${formatPreviewMode(state.terrainPreviewMode)} / ${formatBlendMode(state.layerBlendMode)}"
        }

        if (state.createTerrainRequested) {
            state.createTerrainRequested = false
            finishBrushStroke()
            editHistory.clearAndMarkClean()
            logger.info(TAG) {
                "Creating terrain from generator='${activeGenerator().id}' resolution=${state.terrainResolution} spacing=${
                    "%.2f".format(
                        state.vertexSpacing,
                    )
                }"
            }
            regenerateTerrain(terrain, renderer)
            state.persistenceMessage = "Created terrain: ${state.terrainSaveName}"
            state.persistenceError = false
            logger.info(TAG) { "Created terrain '${state.terrainSaveName}' (${terrain.data.describeTerrain()})" }
        }

        if (state.addLayerRequested) {
            state.addLayerRequested = false
            if (terrain.data.allLayers().size >= TerrainLayerLimits.MaxLayers) {
                state.layerMessage = "Maximum terrain layers reached: ${TerrainLayerLimits.MaxLayers}"
                return
            }
            finishBrushStroke()
            editHistory.clear()
            val nextIndex = terrain.data.allLayers().size + 1
            val material =
                state.terrainMaterials.firstOrNull()
                    ?: throw IllegalStateException("Cannot add terrain layer: terrain material library is empty.")
            val layer =
                terrain.data.addLayer(
                    name = "Layer $nextIndex",
                    materialId = material.id,
                    color = material.fallbackColor,
                    visible = true,
                    tiling = material.defaultTiling,
                )
            state.selectedLayerId = layer.id
            markPreviewDirty(terrain)
            state.layerMessage = "Added layer: ${layer.name}"
        }

        if (state.removeLayerRequested) {
            state.removeLayerRequested = false
            finishBrushStroke()
            editHistory.clear()
            val selectedLayerId = state.selectedLayerId
            val selectedIndex = terrain.data.allLayers().indexOfFirst { it.id == selectedLayerId }
            if (selectedLayerId != null && terrain.data.removeLayer(selectedLayerId)) {
                val remainingLayers = terrain.data.allLayers()
                state.selectedLayerId =
                    remainingLayers.getOrNull(selectedIndex.coerceIn(0, remainingLayers.lastIndex.coerceAtLeast(0)))?.id
                markPreviewDirty(terrain)
                state.layerMessage = "Removed layer"
            }
        }

        if (state.moveLayerUpRequested) {
            state.moveLayerUpRequested = false
            val selectedLayerId = state.selectedLayerId
            if (selectedLayerId != null) {
                finishBrushStroke()
                editHistory.clear()
                if (terrain.data.moveLayerUp(selectedLayerId)) {
                    markPreviewDirty(terrain)
                    state.layerMessage = "Moved layer"
                }
            }
        }

        if (state.moveLayerDownRequested) {
            state.moveLayerDownRequested = false
            val selectedLayerId = state.selectedLayerId
            if (selectedLayerId != null) {
                finishBrushStroke()
                editHistory.clear()
                if (terrain.data.moveLayerDown(selectedLayerId)) {
                    markPreviewDirty(terrain)
                    state.layerMessage = "Moved layer"
                }
            }
        }

        processLayerMetadataCommands(terrain)

        if (state.regenerateRequested) {
            state.regenerateRequested = false
            finishBrushStroke()
            editHistory.clearAndMarkClean()
            logger.info(TAG) {
                "Regenerating terrain with generator='${activeGenerator().id}' resolution=${state.terrainResolution} spacing=${
                    "%.2f".format(
                        state.vertexSpacing,
                    )
                }"
            }
            regenerateTerrain(terrain, renderer)
            logger.info(TAG) { "Regenerated terrain (${terrain.data.describeTerrain()})" }
        }
    }

    /**
     * Executes save/load requests for terrain files.
     *
     * Loading replaces the active terrain object and resets transient editor-side
     * state that would otherwise refer to the previous terrain instance.
     */
    private fun processPersistenceCommands(
        terrain: TerrainDataComponent,
        renderer: TerrainRendererComponent,
    ) {
        if (state.saveTerrainRequested) {
            state.saveTerrainRequested = false
            finishBrushStroke()

            try {
                logger.info(TAG) {
                    "Save terrain requested path='${state.terrainFilePath}' name='${state.terrainSaveName}' (${terrain.data.describeTerrain()})"
                }
                terrainPersistence.save(
                    data = terrain.data,
                    filePath = state.terrainFilePath,
                    name = state.terrainSaveName,
                )
                editHistory.markClean()
                state.terrainFileExists = true
                state.persistenceMessage = "Saved terrain: ${state.terrainFilePath}"
                state.persistenceError = false
                logger.info(TAG) { "Save terrain completed path='${state.terrainFilePath}'" }
            } catch (error: Exception) {
                state.persistenceMessage = "Save failed: ${error.message}"
                state.persistenceError = true
                logger.error(TAG, error) { "Failed to save terrain path='${state.terrainFilePath}': ${error.message}" }
            }
        }

        if (state.loadTerrainRequested) {
            state.loadTerrainRequested = false
            finishBrushStroke()

            try {
                logger.info(TAG) { "Load terrain requested path='${state.terrainFilePath}'" }
                val descriptor = terrainPersistence.loadDescriptor(state.terrainFilePath)
                val loaded = TerrainData.fromDescriptor(descriptor.terrain)
                logger.debug(TAG) { "Applying loaded terrain '${descriptor.name}' (${loaded.describeTerrain()})" }

                terrain.data = loaded
                markPreviewDirty(terrain)

                renderer.modelId = "terrain_${loaded.width}x${loaded.height}"
                renderer.model = null
                renderer.vertexCount = 0
                renderer.triangleCount = 0

                editHistory.clearAndMarkClean()

                state.selectedLayerId = loaded.allLayers().firstOrNull()?.id
                state.terrainResolution = loaded.width
                state.vertexSpacing = loaded.vertexSpacing
                state.terrainSaveName = descriptor.name
                state.terrainFileExists = true
                state.persistenceMessage = "Loaded terrain: ${state.terrainFilePath}"
                state.persistenceError = false
                hoveredHit = null
                resetBrushState()
                logger.info(
                    TAG,
                ) { "Load terrain completed path='${state.terrainFilePath}' name='${descriptor.name}' (${loaded.describeTerrain()})" }
            } catch (error: Exception) {
                state.persistenceMessage = "Load failed: ${error.message}"
                state.persistenceError = true
                logger.error(TAG, error) { "Failed to load terrain path='${state.terrainFilePath}': ${error.message}" }
            }
        }
    }

    /**
     * Rebuilds the terrain from the selected generator while preserving layer metadata.
     *
     * Geometry and weights are regenerated, but layer names/materials/colors/
     * visibility/tiling are copied forward so the user's material setup survives
     * a procedural terrain refresh.
     */
    private fun regenerateTerrain(
        terrain: TerrainDataComponent,
        renderer: TerrainRendererComponent,
    ) {
        val currentLayers = terrain.data.allLayers()
        val generator = activeGenerator()
        logger.debug(TAG) {
            "Regenerate start generator='${generator.id}' target=${state.terrainResolution}x${state.terrainResolution} " +
                "spacing=${"%.2f".format(state.vertexSpacing)} preservedLayers=${currentLayers.size}"
        }
        val regenerated =
            TerrainData(
                width = state.terrainResolution,
                height = state.terrainResolution,
                vertexSpacing = state.vertexSpacing,
            )

        // Recreate layers in the new terrain so generator output can reuse the
        // same logical material stack as the previous terrain.
        currentLayers.forEachIndexed { index, layer ->
            val restored =
                regenerated.addLayer(
                    name = layer.name,
                    texture = layer.texture,
                    materialId = layer.materialId,
                    color = layer.color,
                    visible = layer.visible,
                    tiling = layer.tiling,
                )
            if (state.selectedLayerId == layer.id || (state.selectedLayerId == null && index == 0)) {
                state.selectedLayerId = restored.id
            }
        }
        if (regenerated.allLayers().isEmpty()) {
            val material =
                preferredBaseMaterial()
                    ?: state.terrainMaterials.firstOrNull()
                    ?: throw IllegalStateException("Cannot regenerate terrain: terrain material library is empty.")
            val baseLayer =
                regenerated.addLayer(
                    name = "Base Layer",
                    materialId = material.id,
                    color = material.fallbackColor,
                    tiling = material.defaultTiling,
                )
            state.selectedLayerId = baseLayer.id
        }

        generator.generate(regenerated)
        terrain.data = regenerated
        markPreviewDirty(terrain)
        renderer.modelId = "terrain_${regenerated.width}x${regenerated.height}"
        renderer.model = null
        renderer.vertexCount = 0
        renderer.triangleCount = 0
        hoveredHit = null
        resetBrushState()
        logger.debug(TAG) { "Regenerate finished generator='${generator.id}' (${regenerated.describeTerrain()})" }
    }

    /**
     * Resolves the generator selected in the editor state.
     */
    private fun activeGenerator(): TerrainGenerator = generatorsById[state.selectedGeneratorId] ?: generatorsById.values.first()

    /**
     * Picks a sensible default base material for brand-new terrains.
     */
    private fun preferredBaseMaterial(): TerrainMaterialOption? =
        state.terrainMaterials.firstOrNull { it.id == "terrain/grass" }
            ?: state.terrainMaterials.firstOrNull { it.id == "terrain/ground_grass" }

    /**
     * Applies queued edits to the currently selected layer's metadata.
     *
     * Metadata edits are treated separately from brush painting because they act
     * on layer definitions rather than per-vertex content.
     */
    private fun processLayerMetadataCommands(terrain: TerrainDataComponent) {
        val selectedLayerId = state.selectedLayerId
        var changed = false

        // Multiple UI flags can be set in one frame; this helper ensures history
        // is cleared only once before the first successful metadata mutation.
        fun applyUpdate(update: (Int) -> Boolean) {
            if (selectedLayerId == null) return
            if (!changed) {
                finishBrushStroke()
                editHistory.clear()
            }
            changed = update(selectedLayerId) || changed
        }

        if (state.renameLayerRequested) {
            state.renameLayerRequested = false
            applyUpdate { layerId -> terrain.data.renameLayer(layerId, state.selectedLayerName) }
        }
        if (state.updateLayerMaterialRequested) {
            state.updateLayerMaterialRequested = false
            applyUpdate { layerId ->
                applyLayerMaterial(terrain.data, layerId)
            }
        }
        if (state.updateLayerColorRequested) {
            state.updateLayerColorRequested = false
            applyUpdate { layerId ->
                terrain.data.updateLayerColor(
                    layerId,
                    TerrainLayerColorDescriptor(
                        r = state.selectedLayerColor.getOrElse(0) { 1f },
                        g = state.selectedLayerColor.getOrElse(1) { 1f },
                        b = state.selectedLayerColor.getOrElse(2) { 1f },
                        a = state.selectedLayerColor.getOrElse(3) { 1f },
                    ),
                )
            }
        }
        if (state.updateLayerVisibilityRequested) {
            state.updateLayerVisibilityRequested = false
            applyUpdate { layerId ->
                terrain.data.updateLayerVisibility(layerId, state.selectedLayerVisible)
            }
        }
        if (state.updateLayerTilingRequested) {
            state.updateLayerTilingRequested = false
            applyUpdate { layerId ->
                terrain.data.updateLayerTiling(layerId, state.selectedLayerTiling)
            }
        }

        if (changed) {
            markPreviewDirty(terrain)
            state.layerMessage = "Updated layer"
        }
    }

    /**
     * Marks both geometry and material previews as stale after a terrain change.
     */
    private fun markPreviewDirty(terrain: TerrainDataComponent) {
        terrain.markDirty()
        state.materialPreviewDirty = true
    }

    /**
     * Keeps selected-layer preview labels synchronized with the editor selection.
     *
     * When the preview mode isolates a single layer mask, changing the selection
     * must trigger a rebake even if the terrain data itself did not change.
     */
    private fun syncSelectedLayerPreviewState(terrain: TerrainDataComponent) {
        val selected = state.layers.firstOrNull { it.id == state.selectedLayerId }
        state.selectedLayerMaskMessage = selected?.let { "Layer #${it.index + 1}: ${it.name}" } ?: "No layer selected"
        if (lastObservedSelectedLayerId != state.selectedLayerId) {
            lastObservedSelectedLayerId = state.selectedLayerId
            if (state.terrainPreviewMode == TerrainPreviewMode.SelectedLayerMask) {
                markPreviewDirty(terrain)
            }
        }
    }

    /**
     * Assigns the selected material to a layer and mirrors material defaults back
     * into editor controls such as fallback color and tiling.
     */
    private fun applyLayerMaterial(
        terrainData: TerrainData,
        layerId: Int,
    ): Boolean {
        val materialId = state.selectedLayerMaterialId.takeIf(String::isNotBlank)
        val material = materialId?.let(terrainMaterialsById::get)
        val changed = assignTerrainLayerMaterial(terrainData, layerId, materialId, material)
        if (material != null) {
            state.selectedLayerColor =
                floatArrayOf(
                    material.fallbackColor.r,
                    material.fallbackColor.g,
                    material.fallbackColor.b,
                    material.fallbackColor.a,
                )
            state.selectedLayerTiling = material.defaultTiling
            state.materialMessage = "Assigned material: ${material.name}"
        } else if (materialId != null) {
            state.materialMessage = "Missing material: $materialId"
        } else {
            state.materialMessage = "Cleared material"
        }
        return changed
    }

    /**
     * Formats a hovered terrain position for UI/debug display.
     */
    private fun formatPosition(position: Vec3): String = "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)

    /**
     * Resolves whether the current layer paint stroke should add or erase weight.
     *
     * Alt acts as a temporary erase modifier even when the persistent paint mode
     * is set to add.
     */
    private fun effectiveLayerPaintSign(snapshot: InputSnapshot): Float {
        val altErase = snapshot.isDown(Key.AltLeft) || snapshot.isDown(Key.AltRight)
        return if (state.brushMode == TerrainBrushMode.PaintLayer &&
            (state.layerPaintMode == TerrainLayerPaintMode.Erase || altErase)
        ) {
            -1f
        } else {
            1f
        }
    }

    companion object {
        private const val TAG = "TerrainEditorSystem"
    }
}

/**
 * Editor adapter around shared terrain mesh and preview bake services.
 *
 * This system converts dirty [TerrainData] into backend-neutral dynamic models
 * through [TerrainMeshBuilder], and handles editor-only preview modes, selected
 * layer masks, preview export, and UI bake statistics. Runtime scenes should use
 * [RuntimeTerrainMeshSystem] instead so they do not depend on Terrain Editor UI
 * state or brush workflow callbacks.
 */
class TerrainEditorMeshSyncBindings {
    var materialColorResolver: (String?) -> TerrainLayerColorDescriptor? = { null }
    var blendModeProvider: () -> TerrainLayerBlendMode = { TerrainLayerBlendMode.WeightedAverage }
    var layerColorPreviewProvider: () -> Boolean = { true }
    var previewModeProvider: () -> TerrainPreviewMode = {
        if (layerColorPreviewProvider()) TerrainPreviewMode.MaterialColor else TerrainPreviewMode.LayerColor
    }
    var materialPreviewResolutionProvider: () -> Int = { 512 }
    var materialPreviewDirtyProvider: () -> Boolean = { false }
    var selectedLayerIdProvider: () -> Int? = { null }
    var materialPreviewStatusSink: (String) -> Unit = {}
    var previewBakeStatsSink: (Float, TerrainPreviewTextureCacheStats) -> Unit = { _, _ -> }
    var materialPreviewCleanSink: () -> Unit = {}
    var materialPreviewExportRequestedProvider: () -> Boolean = { false }
    var materialPreviewExportPathProvider: () -> String = { "terrains/material_preview.png" }
    var materialPreviewExportCompleteSink: (String) -> Unit = {}
    var brushActiveProvider: () -> Boolean = { false }
    var strokeMeshRebuildIntervalMs: Double = STROKE_MESH_REBUILD_INTERVAL_MS
    var nowNanos: () -> Long = java.lang.System::nanoTime
}

class TerrainEditorMeshSyncSystem(
    private val bindings: TerrainEditorMeshSyncBindings = TerrainEditorMeshSyncBindings(),
    materialLibrary: TerrainMaterialLibrary? = null,
    logger: Logger? = null,
) : System() {
    private val materialPreviewBaker = materialLibrary?.let { TerrainMaterialPreviewBaker(it, logger) }
    private var lastMeshRebuildNanos: Long = 0L

    /**
     * Rebuilds full terrain meshes for dirty terrain entities.
     *
     * The early-return gate skips expensive work unless geometry, preview mode,
     * preview resolution, or preview export state changed.
     */
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val brushActive = bindings.brushActiveProvider()
        val now = bindings.nowNanos()
        world.query<TerrainDataComponent, TerrainRendererComponent>().forEach { entity ->
            val terrain = entity.get<TerrainDataComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            val previewMode = bindings.previewModeProvider()
            val previewResolution = bindings.materialPreviewResolutionProvider().coerceIn(1, MAX_MATERIAL_PREVIEW_RESOLUTION)
            val exportRequested = bindings.materialPreviewExportRequestedProvider()
            val previewResolutionChanged =
                previewMode.usesTexturePreview() &&
                    renderer.previewResolution != previewResolution
            if (
                shouldSkipMeshSync(
                    terrainDirty = terrain.dirty,
                    previewDirty = bindings.materialPreviewDirtyProvider(),
                    exportRequested = exportRequested,
                    rendererPreviewMode = renderer.previewMode,
                    previewMode = previewMode,
                    previewResolutionChanged = previewResolutionChanged,
                )
            ) {
                return@forEach
            }

            // While the brush is being dragged, mesh and CPU material-preview
            // bakes both run on every paint frame and dominate frame time,
            // which causes UI panels (FPS, profiler, stats text) to visibly
            // jitter. Coalesce mesh rebuilds at a fixed cadence and skip the
            // preview bake entirely; the dirty flags survive across frames so
            // the next non-stroke frame produces the final, accurate mesh and
            // preview texture.
            val withinStrokeThrottleWindow =
                brushActive &&
                    lastMeshRebuildNanos != 0L &&
                    (now - lastMeshRebuildNanos) / 1_000_000.0 < bindings.strokeMeshRebuildIntervalMs &&
                    renderer.previewMode == previewMode &&
                    !previewResolutionChanged &&
                    !exportRequested
            if (withinStrokeThrottleWindow) {
                return@forEach
            }

            // Mesh colors and texture previews are driven by the selected preview
            // mode, so the mesh build and preview bake must use the same mode.
            val blendMode = bindings.blendModeProvider()
            val mesh =
                TerrainMeshBuilder.build(
                    data = terrain.data,
                    materialColorResolver =
                        when (previewMode) {
                            TerrainPreviewMode.LayerColor -> { _: String? -> null }
                            TerrainPreviewMode.MaterialColor,
                            TerrainPreviewMode.MaterialTexture,
                            TerrainPreviewMode.SelectedLayerMask,
                            -> bindings.materialColorResolver
                        },
                    blendMode = blendMode,
                    enableLayerColorPreview = !previewMode.usesTexturePreview() && bindings.layerColorPreviewProvider(),
                )
            renderer.meshRevision += 1L
            renderer.model =
                DynamicModel(
                    id = renderer.modelId,
                    mesh = mesh.toDynamicMesh(),
                    revision = renderer.meshRevision,
                )
            renderer.vertexCount = mesh.vertexCount
            renderer.triangleCount = mesh.triangleCount
            renderer.previewMode = previewMode
            lastMeshRebuildNanos = now
            if (brushActive) {
                // Defer the expensive CPU pixmap bake + GL texture upload until
                // the stroke ends. The mesh revision still updated above so the
                // 3D viewport reflects the in-progress edit at throttled rate.
                terrain.clearDirty()
                return@forEach
            }
            syncMaterialPreviewTexture(
                renderer = renderer,
                terrain = terrain.data,
                previewMode = previewMode,
                resolution = previewResolution,
                blendMode = blendMode,
                selectedLayerId = bindings.selectedLayerIdProvider(),
            )
            if (exportRequested) {
                exportMaterialPreviewPng(
                    terrain.data,
                    previewMode,
                    previewResolution,
                    blendMode,
                    bindings.selectedLayerIdProvider(),
                )
            }
            terrain.clearDirty()

            // TODO: Replace the full rebuild with chunked/partial uploads once terrain chunks exist.
        }
    }

    private fun shouldSkipMeshSync(
        terrainDirty: Boolean,
        previewDirty: Boolean,
        exportRequested: Boolean,
        rendererPreviewMode: TerrainPreviewMode,
        previewMode: TerrainPreviewMode,
        previewResolutionChanged: Boolean,
    ): Boolean {
        val noPendingChanges = !terrainDirty && !previewDirty
        val previewConfigUnchanged = rendererPreviewMode == previewMode && !previewResolutionChanged
        return noPendingChanges && !exportRequested && previewConfigUnchanged
    }

    /**
     * Synchronizes the renderer's optional preview texture with the active preview mode.
     *
     * Texture previews are baked only for modes that require them. Non-texture
     * modes explicitly clear any previous preview texture so stale images cannot
     * leak into later renders.
     */
    private fun syncMaterialPreviewTexture(
        renderer: TerrainRendererComponent,
        terrain: TerrainData,
        previewMode: TerrainPreviewMode,
        resolution: Int,
        blendMode: TerrainLayerBlendMode,
        selectedLayerId: Int?,
    ) {
        if (!previewMode.usesTexturePreview()) {
            renderer.replacePreviewDiffuseTexture(null)
            renderer.previewResolution = 0
            bindings.materialPreviewCleanSink()
            return
        }

        val baker = materialPreviewBaker
        if (baker == null) {
            renderer.replacePreviewDiffuseTexture(null)
            renderer.previewResolution = 0
            bindings.materialPreviewStatusSink("Material preview failed: material library unavailable")
            bindings.materialPreviewCleanSink()
            return
        }

        try {
            val startNs = java.lang.System.nanoTime()
            val pixmap =
                when (previewMode) {
                    TerrainPreviewMode.MaterialTexture -> baker.bakePixmap(terrain, resolution, blendMode)
                    TerrainPreviewMode.SelectedLayerMask ->
                        baker.bakeSelectedLayerMaskPixmap(
                            terrain,
                            selectedLayerId,
                            resolution,
                        )

                    TerrainPreviewMode.LayerColor,
                    TerrainPreviewMode.MaterialColor,
                    -> error("Unsupported texture preview mode: $previewMode")
                }
            val elapsedMs = (java.lang.System.nanoTime() - startNs) / 1_000_000f
            bindings.previewBakeStatsSink(elapsedMs, baker.cacheStats())
            val texture =
                try {
                    runtimeTerrainPreviewTexture(
                        renderer = renderer,
                        pixmap = pixmap,
                        previewMode = previewMode,
                        resolution = resolution,
                    )
                } finally {
                    pixmap.dispose()
                }
            renderer.replacePreviewDiffuseTexture(texture)
            renderer.previewResolution = resolution
            bindings.materialPreviewStatusSink(
                when (previewMode) {
                    TerrainPreviewMode.MaterialTexture -> "Material preview baked: ${resolution}x$resolution"
                    TerrainPreviewMode.SelectedLayerMask -> "Selected layer mask baked: ${resolution}x$resolution"
                    TerrainPreviewMode.LayerColor,
                    TerrainPreviewMode.MaterialColor,
                    -> ""
                },
            )
        } catch (error: Exception) {
            renderer.replacePreviewDiffuseTexture(null)
            renderer.previewResolution = 0
            bindings.materialPreviewStatusSink("Material preview failed: ${error.message ?: error::class.simpleName}")
        } finally {
            bindings.materialPreviewCleanSink()
        }
    }

    /**
     * Exports the current texture-based terrain preview to a PNG file.
     */
    private fun exportMaterialPreviewPng(
        terrain: TerrainData,
        previewMode: TerrainPreviewMode,
        resolution: Int,
        blendMode: TerrainLayerBlendMode,
        selectedLayerId: Int?,
    ) {
        val baker = materialPreviewBaker
        if (baker == null) {
            bindings.materialPreviewExportCompleteSink("Material preview export failed: material library unavailable")
            return
        }
        if (!previewMode.usesTexturePreview()) {
            bindings.materialPreviewExportCompleteSink("Material preview export failed: select Material Texture or Selected Layer Mask")
            return
        }

        try {
            val startNs = java.lang.System.nanoTime()
            val pixmap =
                when (previewMode) {
                    TerrainPreviewMode.MaterialTexture -> baker.bakePixmap(terrain, resolution, blendMode)
                    TerrainPreviewMode.SelectedLayerMask ->
                        baker.bakeSelectedLayerMaskPixmap(
                            terrain,
                            selectedLayerId,
                            resolution,
                        )

                    TerrainPreviewMode.LayerColor,
                    TerrainPreviewMode.MaterialColor,
                    -> error("Unsupported texture preview mode: $previewMode")
                }
            val elapsedMs = (java.lang.System.nanoTime() - startNs) / 1_000_000f
            bindings.previewBakeStatsSink(elapsedMs, baker.cacheStats())
            val path =
                try {
                    baker.writePng(pixmap, bindings.materialPreviewExportPathProvider())
                } finally {
                    pixmap.dispose()
                }
            bindings.materialPreviewExportCompleteSink("Material preview exported: $path")
        } catch (error: Exception) {
            bindings.materialPreviewExportCompleteSink("Material preview export failed: ${error.message ?: error::class.simpleName}")
        }
    }

    /**
     * Releases any cached preview baking resources.
     */
    fun dispose() {
        materialPreviewBaker?.dispose()
    }
}

/**
 * Keeps file-backed terrain asset entities ready for the shared dynamic terrain renderer.
 */
class TerrainAssetSyncSystem(
    private val logger: Logger? = null,
    materialLibraryPath: String = DEFAULT_TERRAIN_MATERIAL_LIBRARY_PATH,
) : System() {
    private val sync = TerrainAssetRuntimeSync(logger, materialLibraryPath)

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        sync.update(world)
    }
}

/**
 * Reusable terrain asset loader used by both runtime worlds and embedded editor document worlds.
 */
class TerrainAssetRuntimeSync(
    private val logger: Logger? = null,
    materialLibraryPath: String = DEFAULT_TERRAIN_MATERIAL_LIBRARY_PATH,
) {
    private val terrainPersistence = TerrainPersistence(logger)
    private val materialLibrary =
        TerrainMaterialLibrary(logger).also { library ->
            library.load(materialLibraryPath)
        }
    private val bakeService = TerrainMaterialBakeService(materialLibrary, logger)
    private val failedPaths = mutableSetOf<String>()

    fun update(world: SceneWorld) {
        world.query<TransformComponent, TerrainComponent>().forEach { entity ->
            if (!entity.active) return@forEach
            val component = entity.get<TerrainComponent>() ?: return@forEach
            val path =
                component.terrain.path
                    .trim()
                    .replace('\\', '/')
            if (path.isBlank()) return@forEach
            val renderer = entity.get<TerrainRendererComponent>()
            val previewMode = sceneTerrainPreviewMode(component.previewMode)
            val bakedTextureResolution = component.bakedTextureResolution.coerceIn(2, MAX_MATERIAL_PREVIEW_RESOLUTION)
            if (renderer?.isSyncedForSceneTerrain(path, previewMode, bakedTextureResolution) == true) {
                return@forEach
            }

            try {
                val data = terrainPersistence.load(path)
                val usesTexturePreview = previewMode == TerrainPreviewMode.MaterialTexture
                val mesh =
                    TerrainMeshBuilder.build(
                        data = data,
                        materialColorResolver = { null },
                        blendMode = TerrainLayerBlendMode.OrderedAlpha,
                        enableLayerColorPreview = !usesTexturePreview,
                    )
                val nextRenderer =
                    renderer ?: TerrainRendererComponent(
                        modelId = modelId(path),
                        material = Material(),
                    ).also(entity::add)
                nextRenderer.modelId = modelId(path)
                nextRenderer.meshRevision += 1L
                nextRenderer.model =
                    DynamicModel(
                        id = nextRenderer.modelId,
                        mesh = mesh.toDynamicMesh(),
                        revision = nextRenderer.meshRevision,
                    )
                nextRenderer.vertexCount = mesh.vertexCount
                nextRenderer.triangleCount = mesh.triangleCount
                nextRenderer.previewMode = previewMode
                nextRenderer.previewResolution = if (usesTexturePreview) bakedTextureResolution else 0
                if (usesTexturePreview) {
                    val texture =
                        bakeService.bakeFinalSplatTexture(
                            terrain = data,
                            resolution = bakedTextureResolution,
                            textureId = "runtime:scene-terrain-preview:${nextRenderer.modelId}",
                            revision = nextRenderer.meshRevision * 31L + bakedTextureResolution,
                            blendMode = TerrainLayerBlendMode.OrderedAlpha,
                        )
                    nextRenderer.replacePreviewDiffuseTexture(texture)
                    nextRenderer.material =
                        Material(
                            baseColor = Color.white(),
                            diffuseTextureRef =
                                MaterialTextureRef(
                                    id = texture.id,
                                    channel = "diffuse",
                                    uvChannel = 0,
                                ),
                        )
                } else {
                    nextRenderer.replacePreviewDiffuseTexture(null)
                    nextRenderer.material = Material()
                }
                failedPaths.remove(path)
                logger?.info(TAG) {
                    "Loaded terrain asset '$path' for entityId=${entity.id} previewMode=$previewMode " +
                        "bakedTextureResolution=${if (usesTexturePreview) bakedTextureResolution else "<none>"}"
                }
            } catch (error: Exception) {
                if (failedPaths.add(path)) {
                    logger?.warn(TAG) { "Failed to load terrain asset '$path' for entityId=${entity.id}: ${error.message}" }
                }
            }
        }
    }

    private fun TerrainRendererComponent.isSyncedForSceneTerrain(
        path: String,
        previewMode: TerrainPreviewMode,
        bakedTextureResolution: Int,
    ): Boolean {
        if (model == null || modelId != this@TerrainAssetRuntimeSync.modelId(path) || this.previewMode != previewMode) {
            return false
        }
        return if (previewMode == TerrainPreviewMode.MaterialTexture) {
            previewDiffuseTexture != null && previewResolution == bakedTextureResolution
        } else {
            previewDiffuseTexture == null
        }
    }

    private fun sceneTerrainPreviewMode(mode: TerrainPreviewMode): TerrainPreviewMode =
        if (mode == TerrainPreviewMode.MaterialTexture) {
            TerrainPreviewMode.MaterialTexture
        } else {
            TerrainPreviewMode.LayerColor
        }

    private fun modelId(path: String): String = "terrain_asset_" + path.replace(Regex("[^A-Za-z0-9_\\-]+"), "_")

    companion object {
        private const val TAG = "TerrainAssetRuntimeSync"
    }
}

/**
 * Shared terrain draw-command emission for runtime worlds and editor document worlds.
 *
 * Final runtime material takes priority over editor preview texture. When a
 * texture is selected, the material receives a [MaterialTextureRef] whose id
 * matches the submitted [RuntimeTextureData], allowing backend upload and bind
 * to happen without exposing backend texture types to terrain code.
 */
object TerrainRenderCommands {
    fun submit(
        world: SceneWorld,
        submit: (DrawDynamicModel) -> Unit,
    ) {
        world.query<TransformComponent, TerrainRendererComponent>().forEach { entity ->
            if (!entity.active) return@forEach
            val terrainAsset = entity.get<TerrainComponent>()
            if (terrainAsset != null && !terrainAsset.visible) return@forEach
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            val model = renderer.model ?: return@forEach
            val textureForMaterial = renderer.finalSplatTexture ?: renderer.previewDiffuseTexture
            val material =
                if (textureForMaterial != null) {
                    renderer.material.copy(
                        baseColor = Color.white(),
                        diffuseTextureRef =
                            MaterialTextureRef(
                                id = textureForMaterial.id,
                                channel = "baseColor",
                                uvChannel = 0,
                            ),
                    )
                } else if (model.mesh.colors != null) {
                    renderer.material.copy(
                        baseColor = Color.white(),
                        diffuseTextureRef = null,
                    )
                } else {
                    renderer.material.copy(diffuseTextureRef = null)
                }
            submit(
                DrawDynamicModel(
                    entityId = entity.id,
                    model = model,
                    transform = transform.snapshot(),
                    material = material,
                    runtimeTextures = listOfNotNull(textureForMaterial),
                ),
            )
        }
    }
}

private fun runtimeTerrainPreviewTexture(
    renderer: TerrainRendererComponent,
    pixmap: com.badlogic.gdx.graphics.Pixmap,
    previewMode: TerrainPreviewMode,
    resolution: Int,
): RuntimeTextureData {
    val pixels = IntArray(pixmap.width * pixmap.height)
    var offset = 0
    for (y in 0 until pixmap.height) {
        for (x in 0 until pixmap.width) {
            pixels[offset++] = pixmap.getPixel(x, y)
        }
    }
    return RuntimeTextureData(
        id = "runtime:terrain-preview:${renderer.modelId}",
        revision = renderer.meshRevision * 31L + previewMode.ordinal * 17L + resolution,
        width = pixmap.width,
        height = pixmap.height,
        rgba8888 = pixels,
        minFilter = RuntimeTextureFilter.Nearest,
        magFilter = RuntimeTextureFilter.Nearest,
        uWrap = RuntimeTextureWrap.ClampToEdge,
        vWrap = RuntimeTextureWrap.ClampToEdge,
    )
}

/**
 * Submits terrain dynamic mesh draw commands to the render pipeline.
 *
 * The renderer is shared by editor and runtime terrain flows. Runtime final
 * splat textures are preferred; editor preview textures are used only when no
 * final material texture exists; otherwise vertex colors or the base material
 * color render the terrain without crashing.
 */
class TerrainRenderSystem : System() {
    /**
     * Emits one draw command per renderable terrain entity.
     *
     * Material selection is intentionally lightweight here: this system assumes
     * mesh generation and preview baking have already prepared the correct data.
     */
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        TerrainRenderCommands.submit(world, world.renderCommands::submit)
    }
}

/** Returns `true` when the preview mode requires a baked texture image. */
private fun TerrainPreviewMode.usesTexturePreview(): Boolean = this == TerrainPreviewMode.MaterialTexture || this == TerrainPreviewMode.SelectedLayerMask

/**
 * Camera controller for the terrain editor viewport.
 *
 * Movement is target-centric: the camera translates together with its look-at
 * point and rotates around that look-at point on the horizontal plane.
 */
class TerrainCameraControllerSystem(
    private val input: InputService,
    private val state: TerrainEditorState? = null,
) : System() {
    /**
     * Updates terrain camera pan, vertical movement, and rotation around its target.
     *
     * WASD pans along the camera's horizontal forward/right axes, R/F move the
     * camera vertically, and Q/E orbit around the current look-at target.
     */
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val cameraEntity =
            world
                .query<TransformComponent, PerspectiveCameraComponent, TerrainCameraControllerComponent>()
                .firstOrNull() ?: return
        val transform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val controller = cameraEntity.get<TerrainCameraControllerComponent>() ?: return
        val lookAt = camera.lookAt ?: Vec3.zero().also { camera.lookAt = it }
        val snapshot = input.snapshot()
        if (state?.inputFocus != TerrainEditorInputFocus.Viewport && snapshot.isCapturedByUI()) return
        // Movement ignores camera pitch so WASD remains parallel to the ground.
        val forward = horizontalForward(transform.position, lookAt)
        val right = Vec3(-forward.z, 0f, forward.x)

        val panX =
            when {
                snapshot.isDown(Key.A) -> -1f
                snapshot.isDown(Key.D) -> 1f
                else -> 0f
            }
        val panZ =
            when {
                snapshot.isDown(Key.W) -> 1f
                snapshot.isDown(Key.S) -> -1f
                else -> 0f
            }
        val panY =
            when {
                snapshot.isDown(Key.R) -> 1f
                snapshot.isDown(Key.F) -> -1f
                else -> 0f
            }

        if (panX != 0f || panY != 0f || panZ != 0f) {
            // Translate both eye and target together so the relative viewing
            // direction stays unchanged while panning.
            val speed = controller.panSpeed * dt
            val deltaX = (right.x * panX + forward.x * panZ) * speed
            val deltaY = panY * speed
            val deltaZ = (right.z * panX + forward.z * panZ) * speed
            transform.position.x += deltaX
            transform.position.y += deltaY
            transform.position.z += deltaZ
            lookAt.x += deltaX
            lookAt.y += deltaY
            lookAt.z += deltaZ
        }

        val rotationInput =
            when {
                snapshot.isDown(Key.Q) -> -1f
                snapshot.isDown(Key.E) -> 1f
                else -> 0f
            }

        if (rotationInput != 0f) {
            rotateAroundLookAt(transform, lookAt, controller.rotationSpeedDegrees * rotationInput * dt)
        }
    }

    /**
     * Rotates the camera position around the active look-at target on the horizontal plane.
     */
    private fun rotateAroundLookAt(
        transform: TransformComponent,
        lookAt: Vec3,
        deltaDegrees: Float,
    ) {
        // Convert the camera-to-target offset into polar form, rotate the angle,
        // then reconstruct the position while preserving orbit radius.
        val offsetX = transform.position.x - lookAt.x
        val offsetZ = transform.position.z - lookAt.z
        val radius =
            sqrt(offsetX * offsetX + offsetZ * offsetZ)
                .coerceIn(1e-4f, Float.MAX_VALUE)
        val currentAngle = atan2(offsetZ, offsetX)
        val nextAngle = currentAngle + Math.toRadians(deltaDegrees.toDouble()).toFloat()

        transform.position.x = lookAt.x + cos(nextAngle) * radius
        transform.position.z = lookAt.z + sin(nextAngle) * radius
    }

    /**
     * Computes the XZ movement direction from camera position to look-at target.
     */
    private fun horizontalForward(
        position: Vec3,
        lookAt: Vec3,
    ): Vec3 {
        val x = lookAt.x - position.x
        val z = lookAt.z - position.z
        val length = sqrt(x * x + z * z)
        if (length <= 1e-6f) return Vec3(0f, 0f, -1f)
        return Vec3(x / length, 0f, z / length)
    }
}

/**
 * Terrain picking helpers for editor tools.
 *
 * These helpers convert a 2D screen position into an approximate terrain hit by
 * constructing a camera ray and projecting it onto the terrain heightfield.
 */
object TerrainRaycaster {
    /**
     * Projects a screen-space point onto the terrain surface.
     *
     * The current implementation first intersects the camera ray with the terrain
     * base plane, then clamps the projected X/Z into terrain bounds and samples
     * the heightfield at that location.
     *
     * This is an approximation: steep cliffs or overhang-like geometry would
     * require a real triangle/heightfield ray cast instead.
     */
    fun pickTerrain(
        screenPosition: Vec2,
        viewportSize: Vec2,
        cameraTransform: TransformComponent,
        camera: PerspectiveCameraComponent,
        terrainTransform: TransformComponent,
        terrain: TerrainData,
    ): TerrainHit? {
        val ray = rayFromScreen(screenPosition, viewportSize, cameraTransform, camera) ?: return null
        val planeY = terrainTransform.position.y
        if (abs(ray.direction.y) <= 1e-5f) return null

        // Intersect the view ray with the terrain's base Y plane. The resulting
        // X/Z becomes the sample position used to query terrain height.
        val distance = (planeY - ray.origin.y) / ray.direction.y
        if (distance <= 0f) return null

        val planeHit = ray.origin + ray.direction * distance
        val projectedLocalX = planeHit.x - terrainTransform.position.x
        val projectedLocalZ = planeHit.z - terrainTransform.position.z
        val localX = projectedLocalX.coerceIn(terrain.minLocalX, terrain.minLocalX + terrain.worldWidth)
        val localZ = projectedLocalZ.coerceIn(terrain.minLocalZ, terrain.minLocalZ + terrain.worldHeight)

        // After finding the projected local position, lift the hit onto the real
        // heightfield surface and compute the nearest discrete sample indices.
        val surfaceY = terrainTransform.position.y + terrain.sampleHeight(localX, localZ)
        val sampleX =
            (((localX - terrain.minLocalX) / terrain.vertexSpacing).roundToInt())
                .coerceIn(0, terrain.width - 1)
        val sampleY =
            (((localZ - terrain.minLocalZ) / terrain.vertexSpacing).roundToInt())
                .coerceIn(0, terrain.height - 1)

        // TODO: Replace the temporary plane projection with a real heightfield/triangle ray test.
        return TerrainHit(
            worldPosition =
                Vec3(
                    terrainTransform.position.x + localX,
                    surfaceY,
                    terrainTransform.position.z + localZ,
                ),
            localX = localX,
            localZ = localZ,
            sampleX = sampleX,
            sampleY = sampleY,
        )
    }

    /**
     * Builds a world-space camera ray from a screen-space cursor position.
     */
    private fun rayFromScreen(
        screenPosition: Vec2,
        viewportSize: Vec2,
        cameraTransform: TransformComponent,
        camera: PerspectiveCameraComponent,
    ): CameraRay? {
        if (viewportSize.x <= 0f || viewportSize.y <= 0f) return null

        // Convert the cursor into normalized device coordinates, then expand from
        // camera forward using the view basis vectors and tangent of half-FOV.
        val aspect = viewportSize.x / viewportSize.y
        val ndcX = (screenPosition.x / viewportSize.x) * 2f - 1f
        val ndcY = 1f - (screenPosition.y / viewportSize.y) * 2f
        val forward = forwardVector(cameraTransform, camera)
        val right = normalize(cross(forward, Vec3(0f, 1f, 0f)))
        val up = normalize(cross(right, forward))
        val tanHalfFov = tan(Math.toRadians((camera.fieldOfViewDegrees * 0.5f).toDouble())).toFloat()

        val direction =
            normalize(
                forward +
                    right * (ndcX * aspect * tanHalfFov) +
                    up * (ndcY * tanHalfFov),
            )
        return CameraRay(cameraTransform.position.copy(), direction)
    }

    /**
     * Resolves the camera's forward direction from either its explicit look-at
     * target or fallback Euler rotation.
     */
    private fun forwardVector(
        cameraTransform: TransformComponent,
        camera: PerspectiveCameraComponent,
    ): Vec3 {
        camera.lookAt?.let { target ->
            return normalize(target - cameraTransform.position)
        }

        val pitch = Math.toRadians(cameraTransform.eulerDegrees.x.toDouble())
        val yaw = Math.toRadians(cameraTransform.eulerDegrees.y.toDouble())
        return normalize(
            Vec3(
                x = (sin(yaw) * cos(pitch)).toFloat(),
                y = sin(pitch).toFloat(),
                z = (cos(yaw) * cos(pitch)).toFloat(),
            ),
        )
    }
}

/** Simple ray representation used only during terrain picking calculations. */
private data class CameraRay(
    val origin: Vec3,
    val direction: Vec3,
)

/** Upper safety limit for editor-generated material preview textures. */
private const val MAX_MATERIAL_PREVIEW_RESOLUTION = 8192

/**
 * Default minimum interval between full terrain mesh rebuilds while the brush
 * is being dragged. ~33 ms ≈ 30 Hz, enough for responsive paint feedback while
 * keeping per-frame cost low so ImGui panels stay smooth.
 */
private const val STROKE_MESH_REBUILD_INTERVAL_MS: Double = 33.0

/** Returns a shallow copy of the vector. */
private fun Vec3.copy(): Vec3 = Vec3(x, y, z)

/** Vector subtraction helper for local picking math. */
private operator fun Vec3.minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

/** Vector addition helper for local picking math. */
private operator fun Vec3.plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

/** Scalar multiply helper for ray marching and basis-vector math. */
private operator fun Vec3.times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

/** Computes the cross product used to derive camera right/up basis vectors. */
private fun cross(
    a: Vec3,
    b: Vec3,
): Vec3 =
    Vec3(
        x = a.y * b.z - a.z * b.y,
        y = a.z * b.x - a.x * b.z,
        z = a.x * b.y - a.y * b.x,
    )

/** Returns the normalized direction of [vector], or zero when its length is tiny. */
private fun normalize(vector: Vec3): Vec3 {
    val length = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)
    if (length <= 1e-6f) return Vec3.zero()
    return Vec3(vector.x / length, vector.y / length, vector.z / length)
}

/** Computes 3D Euclidean distance between two points. */
private fun distance(
    a: Vec3,
    b: Vec3,
): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/** Produces a compact terrain description for logs and status messages. */
private fun TerrainData.describeTerrain(): String =
    "size=${width}x$height spacing=${"%.2f".format(vertexSpacing)} layers=${allLayers().size} [${
        allLayers().joinToString { layer ->
            "${layer.id}:${layer.name}"
        }
    }]"

/** Converts a blend mode enum into a user-facing label. */
private fun formatBlendMode(mode: TerrainLayerBlendMode): String =
    when (mode) {
        TerrainLayerBlendMode.WeightedAverage -> "Weighted Average"
        TerrainLayerBlendMode.OrderedAlpha -> "Ordered Alpha"
        TerrainLayerBlendMode.MaxWeight -> "Max Weight"
    }

/** Converts a terrain preview mode enum into a user-facing label. */
private fun formatPreviewMode(mode: TerrainPreviewMode): String =
    when (mode) {
        TerrainPreviewMode.LayerColor -> "Layer Color"
        TerrainPreviewMode.MaterialColor -> "Material Color"
        TerrainPreviewMode.MaterialTexture -> "Material Texture"
        TerrainPreviewMode.SelectedLayerMask -> "Selected Layer Mask"
    }

/**
 * Applies a material assignment to a terrain layer and, when available, copies
 * the material's default preview color and tiling into the layer metadata.
 */
internal fun assignTerrainLayerMaterial(
    terrainData: TerrainData,
    layerId: Int,
    materialId: String?,
    material: TerrainMaterialDescriptor?,
): Boolean {
    var changed = terrainData.updateLayerMaterial(layerId, materialId)
    if (material != null) {
        changed = terrainData.updateLayerColor(layerId, material.fallbackColor) || changed
        changed = terrainData.updateLayerTiling(layerId, material.defaultTiling) || changed
    }
    return changed
}
