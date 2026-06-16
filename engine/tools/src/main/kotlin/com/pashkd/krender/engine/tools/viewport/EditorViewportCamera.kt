package com.pashkd.krender.engine.tools.viewport

import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.MouseButton
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Runtime-only camera settings mirrored into editor viewport UI.
 */
data class EditorViewportCameraState(
    /** Current editor camera world-space position. */
    var position: Vec3 = Vec3(0f, 2f, 6f),
    /** Current editor camera euler rotation in degrees. */
    var eulerDegrees: Vec3 = Vec3(-10f, 180f, 0f),
    /** Base free-camera movement speed in world units per second. */
    var speed: Float = 6f,
    /** Mouse-look sensitivity in degrees per pixel. */
    var sensitivity: Float = 0.18f,
    /** True while right mouse is actively driving viewport camera navigation. */
    var navigating: Boolean = false,
    /** Runtime-only request to move the camera entity on the next camera-system update. */
    var pendingPosition: Vec3? = null,
    /** Runtime-only request to rotate the camera entity on the next camera-system update. */
    var pendingEulerDegrees: Vec3? = null,
)

/**
 * Runtime-only viewport focus and dimensions used by editor camera and picking systems.
 */
data class EditorViewportState(
    /** True when the viewport panel is hovered or focused and can claim camera input. */
    var focused: Boolean = false,
    /** Top-left viewport panel position in screen pixels. */
    var origin: Vec2 = Vec2.zero(),
    /** Viewport panel size in screen pixels. */
    var size: Vec2 = Vec2.zero(),
)

/**
 * Identifies the runtime camera used by editor-style viewport navigation.
 */
class EditorViewportCameraComponent : Component

/**
 * Runtime-only free camera controller for editor viewports.
 */
class EditorViewportCameraSystem(
    private val input: InputService,
    private val cameraState: EditorViewportCameraState,
    private val viewportState: EditorViewportState,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val cameraEntity =
            world
                .query<TransformComponent, PerspectiveCameraComponent, EditorViewportCameraComponent>()
                .firstOrNull() ?: return
        val transform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val snapshot = input.snapshot()

        consumePendingCameraTeleport(transform)
        camera.lookAt = null

        val mouseAvailable = viewportState.focused || !snapshot.uiCapturesMouse
        val rightMouseDown = snapshot.isMouseDown(MouseButton.Right)
        val navigating = mouseAvailable && rightMouseDown
        cameraState.navigating = navigating
        input.setCursorCaptured(navigating)

        if (mouseAvailable && snapshot.scrollDelta != 0f) {
            val speedScale = CameraSpeedWheelStep.pow(-snapshot.scrollDelta)
            cameraState.speed = (cameraState.speed * speedScale).coerceIn(MinCameraSpeed, MaxCameraSpeed)
        }

        if (navigating && (snapshot.mouseDelta.x != 0f || snapshot.mouseDelta.y != 0f)) {
            transform.eulerDegrees.y -= snapshot.mouseDelta.x * cameraState.sensitivity
            transform.eulerDegrees.x =
                (transform.eulerDegrees.x - snapshot.mouseDelta.y * cameraState.sensitivity)
                    .coerceIn(MinPitchDegrees, MaxPitchDegrees)
        }

        val keyboardAvailable = navigating || viewportState.focused || !snapshot.uiCapturesKeyboard
        if (keyboardAvailable) {
            updatePosition(transform, snapshot, dt)
        }

        cameraState.position = transform.position.copy()
        cameraState.eulerDegrees = transform.eulerDegrees.copy()
    }

    private fun consumePendingCameraTeleport(transform: TransformComponent) {
        val pendingPosition = cameraState.pendingPosition
        val pendingEulerDegrees = cameraState.pendingEulerDegrees
        if (pendingPosition == null && pendingEulerDegrees == null) return

        pendingPosition?.let { position ->
            transform.position.set(position.x, position.y, position.z)
        }
        pendingEulerDegrees?.let { eulerDegrees ->
            transform.eulerDegrees.set(eulerDegrees.x, eulerDegrees.y, eulerDegrees.z)
        }
        cameraState.pendingPosition = null
        cameraState.pendingEulerDegrees = null
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
        val forward =
            Vec3(
                x = (sin(yaw) * cos(pitch)).toFloat(),
                y = sin(pitch).toFloat(),
                z = (cos(yaw) * cos(pitch)).toFloat(),
            )
        val right =
            Vec3(
                x = -cos(yaw).toFloat(),
                y = 0f,
                z = sin(yaw).toFloat(),
            )
        val speed = cameraState.speed * if (snapshot.isShiftDown()) ShiftSpeedMultiplier else 1f
        val distance = speed * dt

        transform.position.x += (forward.x * moveZ + right.x * moveX) * distance
        transform.position.y += (forward.y * moveZ + moveY) * distance
        transform.position.z += (forward.z * moveZ + right.z * moveX) * distance
    }

    private fun InputSnapshot.isShiftDown(): Boolean = isDown(Key.ShiftLeft) || isDown(Key.ShiftRight)

    companion object {
        private const val MinPitchDegrees = -89f
        private const val MaxPitchDegrees = 89f
        private const val ShiftSpeedMultiplier = 3f
        private const val CameraSpeedWheelStep = 1.15f
        private const val MinCameraSpeed = 0.25f
        private const val MaxCameraSpeed = 80f
    }
}
