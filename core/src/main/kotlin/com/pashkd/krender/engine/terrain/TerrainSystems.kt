package com.pashkd.krender.engine.terrain

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
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class TerrainEditorSystem(
    private val input: InputService,
    private val logger: Logger,
) : System() {
    val brush = TerrainBrush()
    var hoveredHit: TerrainHit? = null
        private set
    var activeBrushMode: TerrainBrushMode = TerrainBrushMode.Raise
        private set

    private var brushActive: Boolean = false
    private var flattenHeight: Float? = null
    private var paintLayerWarningShown: Boolean = false

    override fun update(world: SceneWorld, dt: Float) {
        val terrainEntity = world.query<TransformComponent, TerrainComponent, TerrainRendererComponent>().firstOrNull() ?: return
        val terrain = terrainEntity.get<TerrainComponent>() ?: return
        val terrainRenderer = terrainEntity.get<TerrainRendererComponent>() ?: return
        val cameraEntity = world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull() ?: return
        val cameraTransform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val terrainTransform = terrainEntity.get<TransformComponent>() ?: return
        val snapshot = input.snapshot()

        if (snapshot.wasPressed(Key.G)) {
            terrainRenderer.toggleDisplayMode()
        }

        updateBrushBindings(snapshot)
        hoveredHit = TerrainRaycaster.pickTerrain(
            screenPosition = snapshot.mousePosition,
            viewportSize = snapshot.viewportSize,
            cameraTransform = cameraTransform,
            camera = camera,
            terrainTransform = terrainTransform,
            terrain = terrain.data,
        )

        val pointerDown = snapshot.pointers.any { it.phase == PointerPhase.Down || it.phase == PointerPhase.Move }
        val pointerReleased = snapshot.pointers.any { it.phase == PointerPhase.Up || it.phase == PointerPhase.Cancelled }

        if (pointerReleased || hoveredHit == null) {
            brushActive = false
            flattenHeight = null
        }

        if (pointerDown && hoveredHit != null) {
            val hit = hoveredHit ?: return
            if (!brushActive) {
                brushActive = true
                flattenHeight = terrain.data.sampleHeight(hit.localX, hit.localZ)
            }

            val stroke = TerrainBrushStroke(
                localX = hit.localX,
                localZ = hit.localZ,
                radius = brush.radius,
                strength = brush.strength,
                falloff = brush.falloff,
                mode = activeBrushMode,
                deltaSeconds = dt,
                flattenHeight = flattenHeight,
                targetLayerId = brush.targetLayerId,
            )

            if (activeBrushMode == TerrainBrushMode.PaintLayer && brush.targetLayerId == null) {
                if (!paintLayerWarningShown) {
                    logger.warn("TerrainEditor") { "PaintLayer selected without an active terrain layer" }
                    paintLayerWarningShown = true
                }
                return
            }

            if (TerrainBrushApplier.apply(terrain.data, stroke)) {
                terrain.markDirty()
            }
        }
    }

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
            val localX = hit.localX + cos(angle) * brush.radius
            val localZ = hit.localZ + sin(angle) * brush.radius
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

        val crossHalfSize = brush.radius.coerceAtMost(0.9f)
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

    private fun updateBrushBindings(snapshot: com.pashkd.krender.engine.api.InputSnapshot) {
        if (snapshot.wasPressed(Key.F1)) activeBrushMode = TerrainBrushMode.Raise
        if (snapshot.wasPressed(Key.F2)) activeBrushMode = TerrainBrushMode.Lower
        if (snapshot.wasPressed(Key.F3)) activeBrushMode = TerrainBrushMode.Flatten
        if (snapshot.wasPressed(Key.F4)) activeBrushMode = TerrainBrushMode.Smooth
        if (snapshot.wasPressed(Key.Space)) activeBrushMode = TerrainBrushMode.PaintLayer

        if (snapshot.scrollDelta != 0f) {
            if (snapshot.isDown(Key.ShiftLeft)) {
                brush.strength = (brush.strength - snapshot.scrollDelta).coerceIn(0.1f, 32f)
            } else {
                brush.radius = (brush.radius - snapshot.scrollDelta).coerceIn(1f, 64f)
            }
        }
        brush.mode = activeBrushMode
    }
}

class TerrainMeshSyncSystem : System() {
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

class TerrainRenderSystem : System() {
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

class TerrainCameraControllerSystem(
    private val input: InputService,
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        val cameraEntity = world.query<TransformComponent, PerspectiveCameraComponent, TerrainCameraControllerComponent>()
            .firstOrNull() ?: return
        val transform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val controller = cameraEntity.get<TerrainCameraControllerComponent>() ?: return
        val lookAt = camera.lookAt ?: Vec3.zero().also { camera.lookAt = it }
        val snapshot = input.snapshot()
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
            snapshot.isDown(Key.ControlLeft) -> 1f
            snapshot.isDown(Key.ShiftLeft) -> -1f
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

    private fun horizontalForward(position: Vec3, lookAt: Vec3): Vec3 {
        val x = lookAt.x - position.x
        val z = lookAt.z - position.z
        val length = sqrt(x * x + z * z)
        if (length <= 1e-6f) return Vec3(0f, 0f, -1f)
        return Vec3(x / length, 0f, z / length)
    }
}

object TerrainRaycaster {
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
