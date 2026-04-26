package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.DrawDynamicModel
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.PointerPhase
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
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
    private val terrainPersistence = TerrainPersistence()
    private var activePatchBuilder: TerrainEditPatchBuilder? = null

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
        if (state.selectedGeneratorId == null || state.selectedGeneratorId !in generatorsById) {
            state.selectedGeneratorId = generatorsById.keys.firstOrNull()
        }

        processTerrainCommands(terrain, terrainRenderer)
        processPersistenceCommands(terrain, terrainRenderer)
        processHistoryCommands(terrain, snapshot)
        syncStateFromTerrain(terrain, terrainRenderer)
        syncHistoryState()

        if (!snapshot.uiCapturesKeyboard && snapshot.wasPressed(Key.G)) {
            state.wireframeEnabled = !state.wireframeEnabled
        }

        updateBrushBindings(snapshot)
        syncRendererStateFromControls(terrainRenderer)
        hoveredHit = if (snapshot.uiCapturesMouse) {
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
            )

            if (TerrainBrushApplier.apply(terrain.data, stroke, activePatchBuilder)) {
                terrain.markDirty()
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
    private fun updateBrushBindings(snapshot: com.pashkd.krender.engine.api.InputSnapshot) {
        if (!snapshot.uiCapturesKeyboard) {
            if (snapshot.wasPressed(Key.F1)) state.brushMode = TerrainBrushMode.Raise
            if (snapshot.wasPressed(Key.F2)) state.brushMode = TerrainBrushMode.Lower
            if (snapshot.wasPressed(Key.F3)) state.brushMode = TerrainBrushMode.Flatten
            if (snapshot.wasPressed(Key.F4)) state.brushMode = TerrainBrushMode.Smooth
            if (snapshot.wasPressed(Key.Space)) state.brushMode = TerrainBrushMode.PaintLayer
        }

        if (!snapshot.uiCapturesMouse && snapshot.scrollDelta != 0f) {
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
        state.layers = layers.map { TerrainLayerOption(it.id, it.name) }
        state.vertices = renderer.vertexCount
        state.triangles = renderer.triangleCount
        state.terrainSize = "${terrain.data.width} x ${terrain.data.height}"
        state.wireframeEnabled = renderer.displayMode == TerrainDisplayMode.Wireframe
        if (state.selectedLayerId !in layers.map(TerrainLayer::id)) {
            state.selectedLayerId = layers.firstOrNull()?.id
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
        state.undoPreview = editHistory.getUndoPreview()
        state.redoPreview = editHistory.getRedoPreview()
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
        if (!commandHandled && !snapshot.uiCapturesKeyboard && controlDown) {
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
            terrain.markDirty()
        }
    }

    private fun finishBrushStroke(): Boolean {
        val pushed = activePatchBuilder?.build()?.let(editHistory::push) == true
        activePatchBuilder = null
        brushActive = false
        flattenHeight = null
        return pushed
    }

    private fun buildPatchLabel(): String =
        when (state.brushMode) {
            TerrainBrushMode.Raise -> "Raise terrain"
            TerrainBrushMode.Lower -> "Lower terrain"
            TerrainBrushMode.Flatten -> "Flatten to ${"%.2f".format(flattenHeight ?: 0f)}"
            TerrainBrushMode.Smooth -> "Smooth terrain"
            TerrainBrushMode.PaintLayer -> "Paint layer: ${state.selectedLayerId ?: "none"}"
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
        if (state.createTerrainRequested) {
            state.createTerrainRequested = false
            finishBrushStroke()
            editHistory.clearAndMarkClean()
            regenerateTerrain(terrain, renderer)
            state.persistenceMessage = "Created terrain: ${state.terrainSaveName}"
            state.persistenceError = false
        }

        if (state.addLayerRequested) {
            state.addLayerRequested = false
            val nextIndex = terrain.data.allLayers().size + 1
            val layer = terrain.data.addLayer(
                name = "Layer $nextIndex",
                materialId = "terrain/layer_$nextIndex",
            )
            state.selectedLayerId = layer.id
        }

        if (state.removeLayerRequested) {
            state.removeLayerRequested = false
            finishBrushStroke()
            editHistory.clear()
            val selectedLayerId = state.selectedLayerId
            if (selectedLayerId != null && terrain.data.removeLayer(selectedLayerId)) {
                state.selectedLayerId = terrain.data.allLayers().firstOrNull()?.id
            }
        }

        if (state.regenerateRequested) {
            state.regenerateRequested = false
            finishBrushStroke()
            editHistory.clearAndMarkClean()
            regenerateTerrain(terrain, renderer)
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
                terrainPersistence.save(
                    data = terrain.data,
                    filePath = state.terrainFilePath,
                    name = state.terrainSaveName,
                )
                editHistory.markClean()
                state.terrainFileExists = true
                state.persistenceMessage = "Saved terrain: ${state.terrainFilePath}"
                state.persistenceError = false
            } catch (error: Exception) {
                state.persistenceMessage = "Save failed: ${error.message}"
                state.persistenceError = true
                logger.error("TerrainEditor", error) { "Failed to save terrain: ${error.message}" }
            }
        }

        if (state.loadTerrainRequested) {
            state.loadTerrainRequested = false
            finishBrushStroke()

            try {
                val descriptor = terrainPersistence.loadDescriptor(state.terrainFilePath)
                val loaded = TerrainData.fromDescriptor(descriptor.terrain)

                terrain.data = loaded
                terrain.markDirty()

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
            } catch (error: Exception) {
                state.persistenceMessage = "Load failed: ${error.message}"
                state.persistenceError = true
                logger.error("TerrainEditor", error) { "Failed to load terrain: ${error.message}" }
            }
        }
    }

    private fun regenerateTerrain(
        terrain: TerrainComponent,
        renderer: TerrainRendererComponent,
    ) {
        val currentLayers = terrain.data.allLayers()
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
            )
            if (state.selectedLayerId == layer.id || (state.selectedLayerId == null && index == 0)) {
                state.selectedLayerId = restored.id
            }
        }
        if (regenerated.allLayers().isEmpty()) {
            val baseLayer = regenerated.addLayer(name = "Base Layer", materialId = "terrain/base")
            state.selectedLayerId = baseLayer.id
        }

        activeGenerator().generate(regenerated)
        terrain.data = regenerated
        terrain.markDirty()
        renderer.modelId = "terrain_${regenerated.width}x${regenerated.height}"
        renderer.model = null
        renderer.vertexCount = 0
        renderer.triangleCount = 0
        hoveredHit = null
        activePatchBuilder = null
        brushActive = false
        flattenHeight = null
    }

    /**
     * Resolves the generator selected in the editor state.
     */
    private fun activeGenerator(): TerrainGenerator =
        generatorsById[state.selectedGeneratorId] ?: generatorsById.values.first()

    /**
     * Formats a hovered terrain position for UI/debug display.
     */
    private fun formatPosition(position: Vec3): String =
        "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)
}

/**
 * Synchronizes dirty terrain data into runtime dynamic mesh models.
 */
class TerrainMeshSyncSystem : System() {
    /**
     * Rebuilds full terrain meshes for dirty terrain entities.
     */
    override fun update(world: SceneWorld, dt: Float) {
        world.query<TerrainComponent, TerrainRendererComponent>().forEach { entity ->
            val terrain = entity.get<TerrainComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            if (!terrain.dirty) return@forEach

            val mesh = TerrainMeshBuilder.build(terrain.data)
            renderer.meshRevision += 1L
            renderer.model = com.pashkd.krender.engine.api.DynamicModel(
                id = renderer.modelId,
                mesh = mesh.toDynamicMesh(),
                revision = renderer.meshRevision,
            )
            renderer.vertexCount = mesh.vertexCount
            renderer.triangleCount = mesh.triangleCount
            terrain.clearDirty()

            // TODO: Replace the full rebuild with chunked/partial uploads once terrain chunks exist.
        }
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
            world.renderCommands.submit(
                DrawDynamicModel(
                    entityId = entity.id,
                    model = model,
                    transform = transform.snapshot(),
                    material = renderer.material,
                ),
            )
        }
    }
}

class TerrainViewportDebugRenderSystem(
    private val state: TerrainEditorState,
    private val axisLength: Float = 24f,
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        if (!state.showAxes) return
        world.renderCommands.submit(DrawWorldAxes(length = axisLength))
    }
}

/**
 * Camera controller for the terrain editor viewport.
 */
class TerrainCameraControllerSystem(
    private val input: InputService,
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
        if (snapshot.isCapturedByUI()) return
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
        val localX = planeHit.x - terrainTransform.position.x
        val localZ = planeHit.z - terrainTransform.position.z
        if (!terrain.containsLocal(localX, localZ)) return null

        val surfaceY = terrainTransform.position.y + terrain.sampleHeight(localX, localZ)
        val sampleX = (((localX - terrain.minLocalX) / terrain.vertexSpacing).roundToInt())
            .coerceIn(0, terrain.width - 1)
        val sampleY = (((localZ - terrain.minLocalZ) / terrain.vertexSpacing).roundToInt())
            .coerceIn(0, terrain.height - 1)

        // TODO: Replace the temporary plane projection with a real heightfield/triangle ray test.
        return TerrainHit(
            worldPosition = Vec3(planeHit.x, surfaceY, planeHit.z),
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
