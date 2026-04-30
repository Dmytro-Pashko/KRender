package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.DrawWorldGrid
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.MouseButton
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
