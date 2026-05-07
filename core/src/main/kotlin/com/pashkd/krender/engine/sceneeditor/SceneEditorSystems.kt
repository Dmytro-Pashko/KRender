package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.DrawWorldGrid
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MouseButton
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.TerrainAssetRuntimeSync
import com.pashkd.krender.engine.terrain.TerrainRenderCommands
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Emits editor-only viewport guide draw commands from Scene Editor display state.
 */
class SceneEditorViewportGuideSystem(
    private val state: SceneEditorState,
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        val halfExtentCells = state.gridHalfExtentCells.coerceAtLeast(1)
        val cellSize = state.gridCellSize.coerceAtLeast(MinCellSize)
        if (state.showGrid) {
            world.renderCommands.submit(
                DrawWorldGrid(
                    halfExtentCells = halfExtentCells,
                    cellSize = cellSize,
                ),
            )
        }
        if (state.showAxes) {
            world.renderCommands.submit(DrawWorldAxes(length = halfExtentCells * cellSize))
        }
    }

    companion object {
        private const val MinCellSize = 0.01f
    }
}

/**
 * Bridges renderable entities from the editable document world into the editor runtime render command buffer.
 */
class SceneEditorDocumentRenderSystem(
    private val document: SceneEditorDocument,
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        document.world.all().forEach { entity ->
            if (!entity.active || entity.get<EditorOnlyComponent>() != null) return@forEach
            val transform = entity.get<TransformComponent>() ?: return@forEach
            entity.get<ModelComponent>()?.let { model ->
                world.renderCommands.submit(
                    DrawModel(
                        entityId = entity.id,
                        model = model.model,
                        transform = transform.snapshot(),
                        material = model.material,
                    ),
                )
            }
        }
        TerrainRenderCommands.submit(document.world, world.renderCommands::submit)
    }
}

class SceneEditorBoundingBoxSystem(
    private val document: SceneEditorDocument,
    private val state: SceneEditorState,
    private val boundsProvider: SceneEditorBoundsProvider = SceneEditorBoundsProvider(),
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        drawSelectedBoundingBox(world)
    }

    private fun drawSelectedBoundingBox(world: SceneWorld) {
        if (!state.showSelectedBoundingBox) return
        val selected = state.selectedEntityId?.let(document.world::getEntity) ?: return
        if (!selected.active || selected.get<EditorOnlyComponent>() != null) return
        val transform = selected.get<TransformComponent>() ?: return
        val bounds = boundsProvider.boundsFor(selected) ?: return
        val corners = transformedBoundsCorners(bounds, transform)
        BoxEdges.forEach { (fromIndex, toIndex) ->
            world.renderCommands.submit(
                DrawLine(
                    from = corners[fromIndex],
                    to = corners[toIndex],
                    color = BoundingBoxColor,
                ),
            )
        }
    }

    companion object {
        private val BoundingBoxColor = Color(1f, 0.85f, 0.1f, 1f)
        private val BoxEdges = listOf(
            0 to 1,
            1 to 2,
            2 to 3,
            3 to 0,
            4 to 5,
            5 to 6,
            6 to 7,
            7 to 4,
            0 to 4,
            1 to 5,
            2 to 6,
            3 to 7,
        )
    }
}

/**
 * Prepares terrain asset meshes in the editable document world outside render collection.
 */
class SceneEditorDocumentTerrainSyncSystem(
    private val document: SceneEditorDocument,
    logger: Logger,
) : System() {
    private val terrainSync = TerrainAssetRuntimeSync(logger)

    override fun update(world: SceneWorld, dt: Float) {
        terrainSync.update(document.world)
    }
}

class SceneEditorMirroredLightComponent(
    val sourceEntityId: Long,
) : com.pashkd.krender.engine.api.Component

/**
 * Mirrors scene-document lights into the editor runtime world so the viewport renderer uses scene lighting.
 */
class SceneEditorLightSyncSystem(
    private val document: SceneEditorDocument,
    private val logger: Logger,
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        syncSceneLights(world)
    }

    private fun syncSceneLights(world: SceneWorld) {
        val sourceLights = document.world.all()
            .filter { entity -> entity.active && entity.get<EditorOnlyComponent>() == null }
            .mapNotNull { entity ->
                val transform = entity.get<TransformComponent>() ?: return@mapNotNull null
                val light = entity.get<LightComponent>() ?: return@mapNotNull null
                if (light.type != LightType.Directional && light.type != LightType.Point) {
                    logger.debug(TAG) { "Ignoring unsupported scene light type ${light.type} entityId=${entity.id}" }
                    return@mapNotNull null
                }
                Triple(entity, transform, light)
            }

        val desiredIds = sourceLights.map { (entity, _, _) -> entity.id }.toSet()
        world.all()
            .filter { entity ->
                val mirror = entity.get<SceneEditorMirroredLightComponent>() ?: return@filter false
                mirror.sourceEntityId !in desiredIds
            }
            .forEach { stale ->
                world.removeEntity(stale.id)
            }

        sourceLights.forEach { (source, sourceTransform, sourceLight) ->
            val mirrored = world.all().firstOrNull { entity ->
                entity.get<SceneEditorMirroredLightComponent>()?.sourceEntityId == source.id
            } ?: world.createEntity("${source.name} (Editor Light)").also { entity ->
                entity.add(EditorOnlyComponent())
                entity.add(SceneEditorMirroredLightComponent(sourceEntityId = source.id))
            }

            mirrored.active = source.active
            mirrored.transform.position.set(
                sourceTransform.position.x,
                sourceTransform.position.y,
                sourceTransform.position.z,
            )
            mirrored.transform.eulerDegrees.set(
                sourceTransform.eulerDegrees.x,
                sourceTransform.eulerDegrees.y,
                sourceTransform.eulerDegrees.z,
            )
            mirrored.transform.scale.set(
                sourceTransform.scale.x,
                sourceTransform.scale.y,
                sourceTransform.scale.z,
            )
            mirrored.add(
                LightComponent(
                    type = sourceLight.type,
                    color = sourceLight.color.copy(),
                    intensity = sourceLight.intensity,
                    direction = sourceLight.direction.copy(),
                ),
            )
        }
    }

    companion object {
        private const val TAG = "SceneEditorLightSyncSystem"
    }
}

/**
 * Handles viewport click selection against the editable document and draws editor-only selection feedback.
 */
class SceneEditorSelectionSystem(
    private val input: InputService,
    private val document: SceneEditorDocument,
    private val state: SceneEditorState,
    private val logger: Logger,
    private val boundsProvider: SceneEditorBoundsProvider = SceneEditorBoundsProvider(),
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        val snapshot = input.snapshot()
        if (!shouldProcessSelection(snapshot)) return

        val cameraEntity = world.query<TransformComponent, PerspectiveCameraComponent, SceneEditorCameraComponent>()
            .firstOrNull() ?: return
        val cameraTransform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val ray = rayFromScreen(snapshot.mousePosition, snapshot.viewportSize, cameraTransform, camera) ?: return
        val selected = pickEntity(ray)

        if (selected == null) {
            state.selectedEntityId = null
            state.statusMessage = "Selection cleared."
            logger.debug(TAG) { "Scene Editor viewport selection cleared" }
            return
        }

        state.selectedEntityId = selected.id
        state.statusMessage = "Selected ${selected.name}."
        logger.info(TAG) { "Selected scene entity id=${selected.id} name='${selected.name}' from viewport" }
    }

    override fun render(world: SceneWorld, alpha: Float) {
        val selected = state.selectedEntityId?.let(document.world::getEntity) ?: return
        if (selected.get<LightComponent>() != null) return
        val transform = selected.get<TransformComponent>() ?: return
        val position = transform.position
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(position.x - SelectionMarkerSize, position.y, position.z),
                to = Vec3(position.x + SelectionMarkerSize, position.y, position.z),
                color = SelectionColor,
            ),
        )
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(position.x, position.y, position.z - SelectionMarkerSize),
                to = Vec3(position.x, position.y, position.z + SelectionMarkerSize),
                color = SelectionColor,
            ),
        )
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(position.x, position.y - SelectionMarkerSize, position.z),
                to = Vec3(position.x, position.y + SelectionMarkerSize, position.z),
                color = SelectionColor,
            ),
        )
    }

    private fun shouldProcessSelection(snapshot: InputSnapshot): Boolean =
        state.viewportFocused &&
            !state.camera.navigating &&
            !snapshot.isMouseDown(MouseButton.Right) &&
            snapshot.wasMousePressed(MouseButton.Left) &&
            (!snapshot.uiCapturesMouse || state.viewportFocused)

    private fun pickEntity(ray: CameraRay): Entity? {
        var picked: Entity? = null
        var bestCandidateDistance = Float.MAX_VALUE

        document.world.all().forEach { entity ->
            if (!entity.active || entity.get<EditorOnlyComponent>() != null) return@forEach
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val candidateDistance = boundsProvider.boundsFor(entity)?.let { bounds ->
                val corners = transformedBoundsCorners(bounds, transform)
                rayWorldAabbHitDistance(ray, corners)
            } ?: fallbackPickDistance(ray, transform)

            if (candidateDistance != null && candidateDistance < bestCandidateDistance) {
                picked = entity
                bestCandidateDistance = candidateDistance
            }
        }

        return picked
    }

    private fun fallbackPickDistance(ray: CameraRay, transform: TransformComponent): Float? {
        val toEntity = transform.position - ray.origin
        val depth = dot(toEntity, ray.direction)
        if (depth < 0f) return null

        val distance = distanceFromRay(ray, transform.position)
        return if (distance <= PickRadius) depth else null
    }

    private fun rayWorldAabbHitDistance(ray: CameraRay, corners: List<Vec3>): Float? {
        if (corners.isEmpty()) return null
        var minX = corners.first().x
        var minY = corners.first().y
        var minZ = corners.first().z
        var maxX = minX
        var maxY = minY
        var maxZ = minZ
        corners.forEach { corner ->
            minX = minOf(minX, corner.x)
            minY = minOf(minY, corner.y)
            minZ = minOf(minZ, corner.z)
            maxX = maxOf(maxX, corner.x)
            maxY = maxOf(maxY, corner.y)
            maxZ = maxOf(maxZ, corner.z)
        }

        var tMin = 0f
        var tMax = Float.MAX_VALUE
        fun intersectAxis(origin: Float, direction: Float, min: Float, max: Float): Boolean {
            if (kotlin.math.abs(direction) <= 1e-6f) {
                return origin in min..max
            }
            val inverse = 1f / direction
            var near = (min - origin) * inverse
            var far = (max - origin) * inverse
            if (near > far) {
                val swap = near
                near = far
                far = swap
            }
            tMin = maxOf(tMin, near)
            tMax = minOf(tMax, far)
            return tMin <= tMax
        }

        if (!intersectAxis(ray.origin.x, ray.direction.x, minX, maxX)) return null
        if (!intersectAxis(ray.origin.y, ray.direction.y, minY, maxY)) return null
        if (!intersectAxis(ray.origin.z, ray.direction.z, minZ, maxZ)) return null
        return if (tMin >= 0f) tMin else tMax.takeIf { it >= 0f }
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

    private fun distanceFromRay(ray: CameraRay, point: Vec3): Float {
        val toPoint = point - ray.origin
        val depth = dot(toPoint, ray.direction)
        val closestPoint = ray.origin + ray.direction * depth
        return distance(point, closestPoint)
    }

    private data class CameraRay(
        val origin: Vec3,
        val direction: Vec3,
    )

    companion object {
        private const val TAG = "SceneEditorSelectionSystem"
        private const val PickRadius = 0.75f
        private const val SelectionMarkerSize = 0.6f
        private val SelectionColor = Color(1f, 0.78f, 0.12f, 1f)
    }
}

class SceneEditorLightGizmoSystem(
    private val document: SceneEditorDocument,
    private val state: SceneEditorState,
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        val selected = state.selectedEntityId?.let(document.world::getEntity) ?: return
        val transform = selected.get<TransformComponent>() ?: return
        val light = selected.get<LightComponent>() ?: return
        if (light.type != LightType.Directional && light.type != LightType.Point) return

        drawCross(world, transform.position)
        if (light.type == LightType.Directional) {
            val direction = normalize(light.direction)
            if (direction != Vec3.zero()) {
                world.renderCommands.submit(
                    DrawLine(
                        from = transform.position.copy(),
                        to = transform.position + direction * DirectionLineLength,
                        color = LightGizmoColor,
                    ),
                )
            }
        }
    }

    private fun drawCross(world: SceneWorld, position: Vec3) {
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(position.x - LightMarkerSize, position.y, position.z),
                to = Vec3(position.x + LightMarkerSize, position.y, position.z),
                color = LightGizmoColor,
            ),
        )
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(position.x, position.y - LightMarkerSize, position.z),
                to = Vec3(position.x, position.y + LightMarkerSize, position.z),
                color = LightGizmoColor,
            ),
        )
        world.renderCommands.submit(
            DrawLine(
                from = Vec3(position.x, position.y, position.z - LightMarkerSize),
                to = Vec3(position.x, position.y, position.z + LightMarkerSize),
                color = LightGizmoColor,
            ),
        )
    }

    companion object {
        private const val LightMarkerSize = 0.35f
        private const val DirectionLineLength = 1.6f
        private val LightGizmoColor = Color(0.98f, 0.88f, 0.36f, 1f)
    }
}

/**
 * Runtime-only free camera controller for the Scene Editor viewport.
 */
class SceneEditorCameraSystem(
    private val input: InputService,
    private val state: SceneEditorState,
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        val cameraEntity = world.query<TransformComponent, PerspectiveCameraComponent, SceneEditorCameraComponent>()
            .firstOrNull() ?: return
        val transform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val snapshot = input.snapshot()

        consumePendingCameraTeleport(transform)
        camera.lookAt = null

        val mouseAvailable = state.viewportFocused || !snapshot.uiCapturesMouse
        val rightMouseDown = snapshot.isMouseDown(MouseButton.Right)
        val navigating = mouseAvailable && rightMouseDown
        state.camera.navigating = navigating
        input.setCursorCaptured(navigating)

        if (mouseAvailable && snapshot.scrollDelta != 0f) {
            val speedScale = CameraSpeedWheelStep.pow(-snapshot.scrollDelta)
            state.camera.speed = (state.camera.speed * speedScale).coerceIn(MinCameraSpeed, MaxCameraSpeed)
        }

        if (navigating && (snapshot.mouseDelta.x != 0f || snapshot.mouseDelta.y != 0f)) {
            transform.eulerDegrees.y -= snapshot.mouseDelta.x * state.camera.sensitivity
            transform.eulerDegrees.x = (transform.eulerDegrees.x - snapshot.mouseDelta.y * state.camera.sensitivity)
                .coerceIn(MinPitchDegrees, MaxPitchDegrees)
        }

        val keyboardAvailable = navigating || state.viewportFocused || !snapshot.uiCapturesKeyboard
        if (keyboardAvailable) {
            updatePosition(transform, snapshot, dt)
        }

        state.camera.position = transform.position.copy()
        state.camera.eulerDegrees = transform.eulerDegrees.copy()
    }

    private fun consumePendingCameraTeleport(transform: TransformComponent) {
        val pendingPosition = state.pendingCameraPosition
        val pendingEulerDegrees = state.pendingCameraEulerDegrees
        if (pendingPosition == null && pendingEulerDegrees == null) return

        pendingPosition?.let { position ->
            transform.position.set(position.x, position.y, position.z)
        }
        pendingEulerDegrees?.let { eulerDegrees ->
            transform.eulerDegrees.set(eulerDegrees.x, eulerDegrees.y, eulerDegrees.z)
        }
        state.pendingCameraPosition = null
        state.pendingCameraEulerDegrees = null
    }

    private fun updatePosition(
        transform: TransformComponent,
        snapshot: InputSnapshot,
        dt: Float,
    ) {
        var moveX = 0f
        var moveY = 0f
        var moveZ = 0f
        if (snapshot.isDown(Key.W)) moveZ += 1f
        if (snapshot.isDown(Key.S)) moveZ -= 1f
        if (snapshot.isDown(Key.D)) moveX += 1f
        if (snapshot.isDown(Key.A)) moveX -= 1f
        if (snapshot.isDown(Key.E)) moveY += 1f
        if (snapshot.isDown(Key.Q)) moveY -= 1f

        val length = sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ)
        if (length <= 0f) return
        moveX /= length
        moveY /= length
        moveZ /= length

        val pitch = Math.toRadians(transform.eulerDegrees.x.toDouble())
        val yaw = Math.toRadians(transform.eulerDegrees.y.toDouble())
        val forward = Vec3(
            x = (sin(yaw) * cos(pitch)).toFloat(),
            y = sin(pitch).toFloat(),
            z = (cos(yaw) * cos(pitch)).toFloat(),
        )
        val right = Vec3(
            x = -cos(yaw).toFloat(),
            y = 0f,
            z = sin(yaw).toFloat(),
        )
        val speed = state.camera.speed * if (snapshot.isShiftDown()) ShiftSpeedMultiplier else 1f
        val distance = speed * dt

        transform.position.x += (forward.x * moveZ + right.x * moveX) * distance
        transform.position.y += (forward.y * moveZ + moveY) * distance
        transform.position.z += (forward.z * moveZ + right.z * moveX) * distance
    }

    private fun InputSnapshot.isShiftDown(): Boolean =
        isDown(Key.ShiftLeft) || isDown(Key.ShiftRight)

    companion object {
        private const val MinPitchDegrees = -89f
        private const val MaxPitchDegrees = 89f
        private const val ShiftSpeedMultiplier = 3f
        private const val CameraSpeedWheelStep = 1.15f
        private const val MinCameraSpeed = 0.25f
        private const val MaxCameraSpeed = 80f
    }
}

private fun dot(a: Vec3, b: Vec3): Float =
    a.x * b.x + a.y * b.y + a.z * b.z

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
