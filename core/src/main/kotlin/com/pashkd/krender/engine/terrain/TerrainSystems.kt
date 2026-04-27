package com.pashkd.krender.engine.terrain

import com.badlogic.gdx.graphics.Texture
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.DrawDynamicModel
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.PointerPhase
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.material.TerrainMaterialDescriptor
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Editor tool system that handles terrain picking, brush input, brush preview, and display toggles.
 */
class TerrainEditorSystem(
    private val input: InputService,
    private val logger: Logger,
    private val state: TerrainEditorState,
    private val generatorsById: Map<String, TerrainGenerator> = listOf(FlatTerrainGenerator()).associateBy(TerrainGenerator::id),
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
     * Processes editor input and applies brush strokes to terrain data.
     */
    override fun update(world: SceneWorld, dt: Float) {
        val terrainEntity = world.query<TransformComponent, TerrainComponent, TerrainRendererComponent>().firstOrNull() ?: return
        val terrain = terrainEntity.get<TerrainComponent>() ?: return
        val terrainRenderer = terrainEntity.get<TerrainRendererComponent>() ?: return
        val cameraEntity = world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull() ?: return
        val cameraTransform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val terrainTransform = terrainEntity.get<TransformComponent>() ?: return
        val snapshot = input.snapshot()
        if (snapshot.wasPressed(Key.Tab)) {
            state.inputFocus = when (state.inputFocus) {
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

        val viewportFocus = state.inputFocus == TerrainEditorInputFocus.Viewport
        val keyboardAvailable = viewportFocus || !snapshot.uiCapturesKeyboard
        val mouseAvailable = viewportFocus || !snapshot.uiCapturesMouse

        if (keyboardAvailable && snapshot.wasPressed(Key.G)) {
            state.wireframeEnabled = !state.wireframeEnabled
        }

        updateBrushBindings(snapshot, keyboardAvailable, mouseAvailable)
        syncRendererStateFromControls(terrainRenderer)
        hoveredHit = if (!mouseAvailable) {
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

        val pointerDown = snapshot.pointers.any { it.phase == PointerPhase.Down || it.phase == PointerPhase.Move }
        val pointerReleased = snapshot.pointers.any { it.phase == PointerPhase.Up || it.phase == PointerPhase.Cancelled }

        if ((pointerReleased || hoveredHit == null) && brushActive) {
            finishBrushStroke()
        }

        if (pointerDown && hoveredHit != null) {
            val hit = hoveredHit ?: return
            if (state.brushMode == TerrainBrushMode.PaintLayer && state.selectedLayerId == null) {
                if (!paintLayerWarningShown) {
                    logger.warn("TerrainEditor") { "PaintLayer selected without an active terrain layer" }
                    paintLayerWarningShown = true
                }
                return
            }

            if (!brushActive) {
                brushActive = true
                flattenHeight = terrain.data.sampleHeight(hit.localX, hit.localZ)
                activeLayerPaintSign = effectiveLayerPaintSign(snapshot)
                activePatchBuilder = TerrainEditPatchBuilder(buildPatchLabel())
            }

            val stroke = TerrainBrushStroke(
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
     */
    override fun debugRender(world: SceneWorld) {
        val terrainEntity = world.query<TransformComponent, TerrainComponent>().firstOrNull() ?: return
        val terrainTransform = terrainEntity.get<TransformComponent>() ?: return
        val terrain = terrainEntity.get<TerrainComponent>() ?: return
        val hit = hoveredHit ?: return

        val lineColor = if (brushActive) Color(1f, 0.72f, 0.28f, 1f) else Color(0.3f, 0.95f, 0.45f, 1f)
        val segments = 40
        var previous: Vec3? = null

        for (segment in 0..segments) {
            val angle = (segment.toFloat() / segments.toFloat()) * (PI.toFloat() * 2f)
            val localX = hit.localX + cos(angle) * state.brushRadius
            val localZ = hit.localZ + sin(angle) * state.brushRadius
            val worldPoint = Vec3(
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
     */
    private fun updateBrushBindings(
        snapshot: com.pashkd.krender.engine.api.InputSnapshot,
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
        terrain: TerrainComponent,
        renderer: TerrainRendererComponent,
    ) {
        val layers = terrain.data.allLayers()
        state.layers = layers.mapIndexed { index, layer ->
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
            state.selectedLayerColor = floatArrayOf(selected.color.r, selected.color.g, selected.color.b, selected.color.a)
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

    private fun processHistoryCommands(
        terrain: TerrainComponent,
        snapshot: com.pashkd.krender.engine.api.InputSnapshot,
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
        if (!commandHandled && keyboardAvailable && controlDown) {
            val redoPressed = snapshot.wasPressed(Key.Y) ||
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

    private fun finishBrushStroke(): Boolean {
        val pushed = activePatchBuilder?.build()?.let(editHistory::push) == true
        activePatchBuilder = null
        brushActive = false
        flattenHeight = null
        activeLayerPaintSign = 1f
        return pushed
    }

    private fun buildPatchLabel(): String =
        when (state.brushMode) {
            TerrainBrushMode.Raise -> "Raise terrain"
            TerrainBrushMode.Lower -> "Lower terrain"
            TerrainBrushMode.Flatten -> "Flatten to ${"%.2f".format(flattenHeight ?: 0f)}"
            TerrainBrushMode.Smooth -> "Smooth terrain"
            TerrainBrushMode.PaintLayer -> {
                val layerLabel = state.layers.firstOrNull { it.id == state.selectedLayerId }?.name
                    ?: state.selectedLayerId?.toString()
                    ?: "none"
                if (activeLayerPaintSign < 0f) "Erase layer: $layerLabel" else "Paint layer: $layerLabel"
            }
        }

    private fun syncRendererStateFromControls(renderer: TerrainRendererComponent) {
        val expectedMode = if (state.wireframeEnabled) {
            TerrainDisplayMode.Wireframe
        } else {
            TerrainDisplayMode.Solid
        }
        if (renderer.displayMode != expectedMode) {
            renderer.setDisplayMode(expectedMode)
        }
    }

    private fun processTerrainCommands(
        terrain: TerrainComponent,
        renderer: TerrainRendererComponent,
    ) {
        if (state.previewSettingsChanged) {
            state.previewSettingsChanged = false
            markPreviewDirty(terrain)
            state.previewMessage = "Preview: ${formatPreviewMode(state.terrainPreviewMode)} / ${formatBlendMode(state.layerBlendMode)}"
        }

        if (state.createTerrainRequested) {
            state.createTerrainRequested = false
            finishBrushStroke()
            editHistory.clearAndMarkClean()
            logger.info(TAG) {
                "Creating terrain from generator='${activeGenerator().id}' resolution=${state.terrainResolution} spacing=${"%.2f".format(state.vertexSpacing)}"
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
            val material = state.terrainMaterials.firstOrNull()
            val layer = terrain.data.addLayer(
                name = "Layer $nextIndex",
                materialId = material?.id ?: "terrain/layer_$nextIndex",
                color = material?.fallbackColor ?: defaultLayerColor(nextIndex - 1),
                visible = true,
                tiling = material?.defaultTiling ?: 1f,
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
                state.selectedLayerId = remainingLayers.getOrNull(selectedIndex.coerceIn(0, remainingLayers.lastIndex.coerceAtLeast(0)))?.id
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
                "Regenerating terrain with generator='${activeGenerator().id}' resolution=${state.terrainResolution} spacing=${"%.2f".format(state.vertexSpacing)}"
            }
            regenerateTerrain(terrain, renderer)
            logger.info(TAG) { "Regenerated terrain (${terrain.data.describeTerrain()})" }
        }
    }

    private fun processPersistenceCommands(
        terrain: TerrainComponent,
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
                activePatchBuilder = null
                brushActive = false
                flattenHeight = null
                activeLayerPaintSign = 1f
                logger.info(TAG) { "Load terrain completed path='${state.terrainFilePath}' name='${descriptor.name}' (${loaded.describeTerrain()})" }
            } catch (error: Exception) {
                state.persistenceMessage = "Load failed: ${error.message}"
                state.persistenceError = true
                logger.error(TAG, error) { "Failed to load terrain path='${state.terrainFilePath}': ${error.message}" }
            }
        }
    }

    private fun regenerateTerrain(
        terrain: TerrainComponent,
        renderer: TerrainRendererComponent,
    ) {
        val currentLayers = terrain.data.allLayers()
        val generator = activeGenerator()
        logger.debug(TAG) {
            "Regenerate start generator='${generator.id}' target=${state.terrainResolution}x${state.terrainResolution} " +
                "spacing=${"%.2f".format(state.vertexSpacing)} preservedLayers=${currentLayers.size}"
        }
        val regenerated = TerrainData(
            width = state.terrainResolution,
            height = state.terrainResolution,
            vertexSpacing = state.vertexSpacing,
        )

        currentLayers.forEachIndexed { index, layer ->
            val restored = regenerated.addLayer(
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
            val material = preferredBaseMaterial() ?: state.terrainMaterials.firstOrNull()
            val baseLayer = regenerated.addLayer(
                name = "Base Layer",
                materialId = material?.id ?: "terrain/base",
                color = material?.fallbackColor ?: TerrainLayerColorDescriptor(),
                tiling = material?.defaultTiling ?: 1f,
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
        activePatchBuilder = null
        brushActive = false
        flattenHeight = null
        activeLayerPaintSign = 1f
        logger.debug(TAG) { "Regenerate finished generator='${generator.id}' (${regenerated.describeTerrain()})" }
    }

    /**
     * Resolves the generator selected in the editor state.
     */
    private fun activeGenerator(): TerrainGenerator =
        generatorsById[state.selectedGeneratorId] ?: generatorsById.values.first()

    private fun preferredBaseMaterial(): TerrainMaterialOption? =
        state.terrainMaterials.firstOrNull { it.id == "terrain/grass" }
            ?: state.terrainMaterials.firstOrNull { it.id == "terrain/ground_grass" }

    private fun processLayerMetadataCommands(terrain: TerrainComponent) {
        val selectedLayerId = state.selectedLayerId
        var changed = false

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

    private fun markPreviewDirty(terrain: TerrainComponent) {
        terrain.markDirty()
        state.materialPreviewDirty = true
    }

    private fun syncSelectedLayerPreviewState(terrain: TerrainComponent) {
        val selected = state.layers.firstOrNull { it.id == state.selectedLayerId }
        state.selectedLayerMaskMessage = selected?.let { "Layer #${it.index + 1}: ${it.name}" } ?: "No layer selected"
        if (lastObservedSelectedLayerId != state.selectedLayerId) {
            lastObservedSelectedLayerId = state.selectedLayerId
            if (state.terrainPreviewMode == TerrainPreviewMode.SelectedLayerMask) {
                markPreviewDirty(terrain)
            }
        }
    }

    private fun applyLayerMaterial(
        terrainData: TerrainData,
        layerId: Int,
    ): Boolean {
        val materialId = state.selectedLayerMaterialId.takeIf(String::isNotBlank)
        val material = materialId?.let(terrainMaterialsById::get)
        val changed = assignTerrainLayerMaterial(terrainData, layerId, materialId, material)
        if (material != null) {
            state.selectedLayerColor = floatArrayOf(
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
    private fun formatPosition(position: Vec3): String =
        "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)

    private fun effectiveLayerPaintSign(snapshot: com.pashkd.krender.engine.api.InputSnapshot): Float {
        val altErase = snapshot.isDown(Key.AltLeft) || snapshot.isDown(Key.AltRight)
        return if (state.brushMode == TerrainBrushMode.PaintLayer &&
            (state.layerPaintMode == TerrainLayerPaintMode.Erase || altErase)
        ) {
            -1f
        } else {
            1f
        }
    }

    private fun defaultLayerColor(index: Int): TerrainLayerColorDescriptor {
        val colors = listOf(
            TerrainLayerColorDescriptor(0.25f, 0.65f, 0.2f, 1f),
            TerrainLayerColorDescriptor(0.55f, 0.42f, 0.28f, 1f),
            TerrainLayerColorDescriptor(0.55f, 0.55f, 0.58f, 1f),
            TerrainLayerColorDescriptor(0.82f, 0.78f, 0.62f, 1f),
            TerrainLayerColorDescriptor(0.35f, 0.5f, 0.75f, 1f),
            TerrainLayerColorDescriptor(0.8f, 0.85f, 0.9f, 1f),
            TerrainLayerColorDescriptor(0.45f, 0.32f, 0.2f, 1f),
            TerrainLayerColorDescriptor(0.8f, 0.45f, 0.25f, 1f),
        )
        return colors[index % colors.size]
    }

    companion object {
        private const val TAG = "TerrainEditor"
    }
}

/**
 * Synchronizes dirty terrain data into runtime dynamic mesh models.
 */
class TerrainMeshSyncSystem(
    private val materialColorResolver: (String?) -> TerrainLayerColorDescriptor? = { null },
    private val blendModeProvider: () -> TerrainLayerBlendMode = { TerrainLayerBlendMode.WeightedAverage },
    private val layerColorPreviewProvider: () -> Boolean = { true },
    private val previewModeProvider: () -> TerrainPreviewMode = {
        if (layerColorPreviewProvider()) TerrainPreviewMode.MaterialColor else TerrainPreviewMode.LayerColor
    },
    private val materialPreviewResolutionProvider: () -> Int = { 512 },
    private val materialPreviewDirtyProvider: () -> Boolean = { false },
    private val selectedLayerIdProvider: () -> Int? = { null },
    private val materialPreviewStatusSink: (String) -> Unit = {},
    private val previewBakeStatsSink: (Float, TerrainPreviewTextureCacheStats) -> Unit = { _, _ -> },
    private val materialPreviewCleanSink: () -> Unit = {},
    private val materialPreviewExportRequestedProvider: () -> Boolean = { false },
    private val materialPreviewExportPathProvider: () -> String = { "terrains/material_preview.png" },
    private val materialPreviewExportCompleteSink: (String) -> Unit = {},
    materialLibrary: TerrainMaterialLibrary? = null,
    logger: Logger? = null,
) : System() {
    private val materialPreviewBaker = materialLibrary?.let { TerrainMaterialPreviewBaker(it, logger) }

    /**
     * Rebuilds full terrain meshes for dirty terrain entities.
     */
    override fun update(world: SceneWorld, dt: Float) {
        world.query<TerrainComponent, TerrainRendererComponent>().forEach { entity ->
            val terrain = entity.get<TerrainComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            val previewMode = previewModeProvider()
            val previewResolution = materialPreviewResolutionProvider().coerceIn(1, MAX_MATERIAL_PREVIEW_RESOLUTION)
            val exportRequested = materialPreviewExportRequestedProvider()
            val previewResolutionChanged =
                previewMode.usesTexturePreview() &&
                    renderer.previewResolution != previewResolution
            if (
                !terrain.dirty &&
                !materialPreviewDirtyProvider() &&
                !exportRequested &&
                renderer.previewMode == previewMode &&
                !previewResolutionChanged
            ) {
                return@forEach
            }

            val blendMode = blendModeProvider()
            val mesh = TerrainMeshBuilder.build(
                data = terrain.data,
                materialColorResolver = when (previewMode) {
                    TerrainPreviewMode.LayerColor -> { _: String? -> null }
                    TerrainPreviewMode.MaterialColor,
                    TerrainPreviewMode.MaterialTexture,
                    TerrainPreviewMode.SelectedLayerMask,
                    -> materialColorResolver
                },
                blendMode = blendMode,
                enableLayerColorPreview = !previewMode.usesTexturePreview() && layerColorPreviewProvider(),
            )
            renderer.meshRevision += 1L
            renderer.model = com.pashkd.krender.engine.api.DynamicModel(
                id = renderer.modelId,
                mesh = mesh.toDynamicMesh(),
                revision = renderer.meshRevision,
            )
            renderer.vertexCount = mesh.vertexCount
            renderer.triangleCount = mesh.triangleCount
            renderer.previewMode = previewMode
            syncMaterialPreviewTexture(
                renderer = renderer,
                terrain = terrain.data,
                previewMode = previewMode,
                resolution = previewResolution,
                blendMode = blendMode,
                selectedLayerId = selectedLayerIdProvider(),
            )
            if (exportRequested) {
                exportMaterialPreviewPng(terrain.data, previewMode, previewResolution, blendMode, selectedLayerIdProvider())
            }
            terrain.clearDirty()

            // TODO: Replace the full rebuild with chunked/partial uploads once terrain chunks exist.
        }
    }

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
            materialPreviewCleanSink()
            return
        }

        val baker = materialPreviewBaker
        if (baker == null) {
            renderer.replacePreviewDiffuseTexture(null)
            renderer.previewResolution = 0
            materialPreviewStatusSink("Material preview failed: material library unavailable")
            materialPreviewCleanSink()
            return
        }

        try {
            val startNs = java.lang.System.nanoTime()
            val pixmap = when (previewMode) {
                TerrainPreviewMode.MaterialTexture -> baker.bakePixmap(terrain, resolution, blendMode)
                TerrainPreviewMode.SelectedLayerMask -> baker.bakeSelectedLayerMaskPixmap(terrain, selectedLayerId, resolution)
                TerrainPreviewMode.LayerColor,
                TerrainPreviewMode.MaterialColor,
                -> error("Unsupported texture preview mode: $previewMode")
            }
            val elapsedMs = (java.lang.System.nanoTime() - startNs) / 1_000_000f
            previewBakeStatsSink(elapsedMs, baker.cacheStats())
            val texture = try {
                Texture(pixmap).also {
                    it.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                    it.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                }
            } finally {
                pixmap.dispose()
            }
            renderer.replacePreviewDiffuseTexture(texture)
            renderer.previewResolution = resolution
            materialPreviewStatusSink(
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
            materialPreviewStatusSink("Material preview failed: ${error.message ?: error::class.simpleName}")
        } finally {
            materialPreviewCleanSink()
        }
    }

    private fun exportMaterialPreviewPng(
        terrain: TerrainData,
        previewMode: TerrainPreviewMode,
        resolution: Int,
        blendMode: TerrainLayerBlendMode,
        selectedLayerId: Int?,
    ) {
        val baker = materialPreviewBaker
        if (baker == null) {
            materialPreviewExportCompleteSink("Material preview export failed: material library unavailable")
            return
        }
        if (!previewMode.usesTexturePreview()) {
            materialPreviewExportCompleteSink("Material preview export failed: select Material Texture or Selected Layer Mask")
            return
        }

        try {
            val startNs = java.lang.System.nanoTime()
            val pixmap = when (previewMode) {
                TerrainPreviewMode.MaterialTexture -> baker.bakePixmap(terrain, resolution, blendMode)
                TerrainPreviewMode.SelectedLayerMask -> baker.bakeSelectedLayerMaskPixmap(terrain, selectedLayerId, resolution)
                TerrainPreviewMode.LayerColor,
                TerrainPreviewMode.MaterialColor,
                -> error("Unsupported texture preview mode: $previewMode")
            }
            val elapsedMs = (java.lang.System.nanoTime() - startNs) / 1_000_000f
            previewBakeStatsSink(elapsedMs, baker.cacheStats())
            val path = try {
                baker.writePng(pixmap, materialPreviewExportPathProvider())
            } finally {
                pixmap.dispose()
            }
            materialPreviewExportCompleteSink("Material preview exported: $path")
        } catch (error: Exception) {
            materialPreviewExportCompleteSink("Material preview export failed: ${error.message ?: error::class.simpleName}")
        }
    }

    fun dispose() {
        materialPreviewBaker?.dispose()
    }
}

/**
 * Submits terrain dynamic mesh draw commands to the render pipeline.
 */
class TerrainRenderSystem : System() {
    /**
     * Emits one draw command per renderable terrain entity.
     */
    override fun render(world: SceneWorld, alpha: Float) {
        world.query<TransformComponent, TerrainRendererComponent>().forEach { entity ->
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            val model = renderer.model ?: return@forEach
            val material = if (renderer.previewDiffuseTexture != null) {
                renderer.material.copy(
                    baseColor = Color.white(),
                    diffuseTexture = renderer.previewDiffuseTexture,
                )
            } else if (model.mesh.colors != null) {
                renderer.material.copy(
                    baseColor = Color.white(),
                    diffuseTexture = null,
                )
            } else {
                renderer.material.copy(diffuseTexture = null)
            }
            world.renderCommands.submit(
                DrawDynamicModel(
                    entityId = entity.id,
                    model = model,
                    transform = transform.snapshot(),
                    material = material,
                ),
            )
        }
    }
}

private fun TerrainPreviewMode.usesTexturePreview(): Boolean =
    this == TerrainPreviewMode.MaterialTexture || this == TerrainPreviewMode.SelectedLayerMask

/**
 * Camera controller for the terrain editor viewport.
 */
class TerrainCameraControllerSystem(
    private val input: InputService,
    private val state: TerrainEditorState? = null,
) : System() {
    /**
     * Updates terrain camera pan, vertical movement, and rotation around its target.
     */
    override fun update(world: SceneWorld, dt: Float) {
        val cameraEntity = world.query<TransformComponent, PerspectiveCameraComponent, TerrainCameraControllerComponent>()
            .firstOrNull() ?: return
        val transform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val controller = cameraEntity.get<TerrainCameraControllerComponent>() ?: return
        val lookAt = camera.lookAt ?: Vec3.zero().also { camera.lookAt = it }
        val snapshot = input.snapshot()
        if (state?.inputFocus != TerrainEditorInputFocus.Viewport && snapshot.isCapturedByUI()) return
        val forward = horizontalForward(transform.position, lookAt)
        val right = Vec3(-forward.z, 0f, forward.x)

        val panX = when {
            snapshot.isDown(Key.A) -> -1f
            snapshot.isDown(Key.D) -> 1f
            else -> 0f
        }
        val panZ = when {
            snapshot.isDown(Key.W) -> 1f
            snapshot.isDown(Key.S) -> -1f
            else -> 0f
        }
        val panY = when {
            snapshot.isDown(Key.R) -> 1f
            snapshot.isDown(Key.F) -> -1f
            else -> 0f
        }

        if (panX != 0f || panY != 0f || panZ != 0f) {
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

        val rotationInput = when {
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
        val offsetX = transform.position.x - lookAt.x
        val offsetZ = transform.position.z - lookAt.z
        val radius = sqrt(offsetX * offsetX + offsetZ * offsetZ)
            .coerceIn(1e-4f, Float.MAX_VALUE)
        val currentAngle = kotlin.math.atan2(offsetZ, offsetX)
        val nextAngle = currentAngle + Math.toRadians(deltaDegrees.toDouble()).toFloat()

        transform.position.x = lookAt.x + cos(nextAngle) * radius
        transform.position.z = lookAt.z + sin(nextAngle) * radius
    }

    /**
     * Computes the XZ movement direction from camera position to look-at target.
     */
    private fun horizontalForward(position: Vec3, lookAt: Vec3): Vec3 {
        val x = lookAt.x - position.x
        val z = lookAt.z - position.z
        val length = sqrt(x * x + z * z)
        if (length <= 1e-6f) return Vec3(0f, 0f, -1f)
        return Vec3(x / length, 0f, z / length)
    }
}

/**
 * Terrain picking helpers for editor tools.
 */
object TerrainRaycaster {
    /**
     * Projects a screen-space point onto the terrain surface.
     *
     * The current implementation uses a temporary ray-plane intersection and then samples
     * the heightfield at that X/Z coordinate.
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
        if (kotlin.math.abs(ray.direction.y) <= 1e-5f) return null

        val distance = (planeY - ray.origin.y) / ray.direction.y
        if (distance <= 0f) return null

        val planeHit = ray.origin + ray.direction * distance
        val projectedLocalX = planeHit.x - terrainTransform.position.x
        val projectedLocalZ = planeHit.z - terrainTransform.position.z
        val localX = projectedLocalX.coerceIn(terrain.minLocalX, terrain.minLocalX + terrain.worldWidth)
        val localZ = projectedLocalZ.coerceIn(terrain.minLocalZ, terrain.minLocalZ + terrain.worldHeight)

        val surfaceY = terrainTransform.position.y + terrain.sampleHeight(localX, localZ)
        val sampleX = (((localX - terrain.minLocalX) / terrain.vertexSpacing).roundToInt())
            .coerceIn(0, terrain.width - 1)
        val sampleY = (((localZ - terrain.minLocalZ) / terrain.vertexSpacing).roundToInt())
            .coerceIn(0, terrain.height - 1)

        // TODO: Replace the temporary plane projection with a real heightfield/triangle ray test.
        return TerrainHit(
            worldPosition = Vec3(
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

    private fun rayFromScreen(
        screenPosition: Vec2,
        viewportSize: Vec2,
        cameraTransform: TransformComponent,
        camera: PerspectiveCameraComponent,
    ): CameraRay? {
        if (viewportSize.x <= 0f || viewportSize.y <= 0f) return null

        val aspect = viewportSize.x / viewportSize.y
        val ndcX = (screenPosition.x / viewportSize.x) * 2f - 1f
        val ndcY = 1f - (screenPosition.y / viewportSize.y) * 2f
        val forward = forwardVector(cameraTransform, camera)
        val right = normalize(cross(forward, Vec3(0f, 1f, 0f)))
        val up = normalize(cross(right, forward))
        val tanHalfFov = tan(Math.toRadians((camera.fieldOfViewDegrees * 0.5f).toDouble())).toFloat()

        val direction = normalize(
            forward +
                right * (ndcX * aspect * tanHalfFov) +
                up * (ndcY * tanHalfFov),
        )
        return CameraRay(cameraTransform.position.copy(), direction)
    }

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

private data class CameraRay(
    val origin: Vec3,
    val direction: Vec3,
)

private const val MAX_MATERIAL_PREVIEW_RESOLUTION = 8192

private fun Vec3.copy(): Vec3 = Vec3(x, y, z)

private operator fun Vec3.minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

private operator fun Vec3.plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

private operator fun Vec3.times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

private fun cross(a: Vec3, b: Vec3): Vec3 =
    Vec3(
        x = a.y * b.z - a.z * b.y,
        y = a.z * b.x - a.x * b.z,
        z = a.x * b.y - a.y * b.x,
    )

private fun normalize(vector: Vec3): Vec3 {
    val length = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)
    if (length <= 1e-6f) return Vec3.zero()
    return Vec3(vector.x / length, vector.y / length, vector.z / length)
}

private fun distance(a: Vec3, b: Vec3): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun TerrainData.describeTerrain(): String =
    "size=${width}x${height} spacing=${"%.2f".format(vertexSpacing)} layers=${allLayers().size} [${allLayers().joinToString { layer -> "${layer.id}:${layer.name}" }}]"

private fun formatBlendMode(mode: TerrainLayerBlendMode): String =
    when (mode) {
        TerrainLayerBlendMode.WeightedAverage -> "Weighted Average"
        TerrainLayerBlendMode.OrderedAlpha -> "Ordered Alpha"
        TerrainLayerBlendMode.MaxWeight -> "Max Weight"
    }

private fun formatPreviewMode(mode: TerrainPreviewMode): String =
    when (mode) {
        TerrainPreviewMode.LayerColor -> "Layer Color"
        TerrainPreviewMode.MaterialColor -> "Material Color"
        TerrainPreviewMode.MaterialTexture -> "Material Texture"
        TerrainPreviewMode.SelectedLayerMask -> "Selected Layer Mask"
    }

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
