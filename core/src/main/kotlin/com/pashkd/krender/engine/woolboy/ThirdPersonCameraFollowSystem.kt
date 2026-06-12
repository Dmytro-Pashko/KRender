package com.pashkd.krender.engine.woolboy

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.*

/**
 * Third-person orbit camera for the Woolboy sandbox.
 *
 * RMB drag rotates around the active player and the mouse wheel controls the
 * orbit distance. The system deliberately avoids OS cursor capture because the
 * current backend recenters captured cursors at screen edges. Runtime UI can
 * disable camera input while menus are active; the camera still follows the
 * player transform so gameplay resumes from a consistent view.
 */
class ThirdPersonCameraFollowSystem(
    private val input: InputService,
    private val logger: Logger,
    private val inputEnabled: () -> Boolean = { true },
) : System() {
    companion object {
        private const val TAG = "ThirdPersonCameraFollowSystem"
        private const val InitialOrbitYawDegrees = 180f
        private const val InitialOrbitPitchDegrees = 24f
        private const val InitialCameraDistance = 7.0f
        private const val MinCameraDistance = 3.0f
        private const val MaxCameraDistance = 18.0f
        private const val MinOrbitPitchDegrees = 5.0f
        private const val MaxOrbitPitchDegrees = 70.0f
        private const val OrbitSensitivityDegreesPerPixel = 0.18f
        private const val ZoomStep = 0.12f
        const val CameraLookAtHeight = 1.2f
    }

    private var hasLoggedFollow = false
    private var orbitInitialized = false
    private var orbiting = false
    private var orbitYawDegrees = InitialOrbitYawDegrees
    private var orbitPitchDegrees = InitialOrbitPitchDegrees
    private var orbitDistance = InitialCameraDistance

    override fun lateUpdate(world: SceneWorld, dt: Float) {
        val player = world.query<TransformComponent, PlayerControllerComponent>()
            .firstOrNull { entity -> entity.get<PlayerControllerComponent>()?.isActive == true }
            ?: return
        val playerTransform = player.get<TransformComponent>() ?: return
        val cameraEntity =
            world.query<TransformComponent, PerspectiveCameraComponent, ActiveCameraComponent>().firstOrNull()
                ?: world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull()
                ?: return
        val cameraTransform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return

        val target = Vec3(
            playerTransform.position.x,
            playerTransform.position.y + CameraLookAtHeight,
            playerTransform.position.z,
        )
        initializeOrbitFromCamera(cameraTransform, target)
        if (inputEnabled()) {
            updateOrbitInput()
        } else {
            orbiting = false
            input.setCursorCaptured(false)
        }

        val yaw = Math.toRadians(orbitYawDegrees.toDouble())
        val pitch = Math.toRadians(orbitPitchDegrees.toDouble())
        val horizontalDistance = orbitDistance * cos(pitch).toFloat()
        val offsetX = sin(yaw).toFloat() * horizontalDistance
        val offsetY = sin(pitch).toFloat() * orbitDistance
        val offsetZ = cos(yaw).toFloat() * horizontalDistance
        cameraTransform.position.set(
            target.x + offsetX,
            target.y + offsetY,
            target.z + offsetZ,
        )
        camera.lookAt = target

        if (!hasLoggedFollow) {
            logger.info(TAG) {
                "Woolboy orbit camera following playerId=${player.id} cameraId=${cameraEntity.id} " +
                    "distance=${"%.2f".format(orbitDistance)} pitch=${"%.2f".format(orbitPitchDegrees)}"
            }
            hasLoggedFollow = true
        }
    }

    private fun updateOrbitInput() {
        val snapshot = input.snapshot()
        val mouseAvailable = !snapshot.uiCapturesMouse
        if (mouseAvailable && (snapshot.wasMousePressed(MouseButton.Right) || snapshot.isMouseDown(MouseButton.Right))) {
            orbiting = true
        }
        if (snapshot.wasMouseReleased(MouseButton.Right)) {
            orbiting = false
        }
        // Avoid cursor capture because the GDX backend recenters captured cursors at window edges.
        input.setCursorCaptured(false)

        if (orbiting) {
            orbitYawDegrees -= snapshot.mouseDelta.x * OrbitSensitivityDegreesPerPixel
            orbitPitchDegrees = (orbitPitchDegrees + snapshot.mouseDelta.y * OrbitSensitivityDegreesPerPixel)
                .coerceIn(MinOrbitPitchDegrees, MaxOrbitPitchDegrees)
        }

        if (mouseAvailable && snapshot.scrollDelta != 0f) {
            orbitDistance = (orbitDistance * (1f - snapshot.scrollDelta * ZoomStep))
                .coerceIn(MinCameraDistance, MaxCameraDistance)
        }
    }

    private fun initializeOrbitFromCamera(
        cameraTransform: TransformComponent,
        target: Vec3,
    ) {
        if (orbitInitialized) return

        val offsetX = cameraTransform.position.x - target.x
        val offsetY = cameraTransform.position.y - target.y
        val offsetZ = cameraTransform.position.z - target.z
        val distance = sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ)
        if (distance >= MinCameraDistance) {
            orbitDistance = distance.coerceIn(MinCameraDistance, MaxCameraDistance)
            orbitYawDegrees = (atan2(offsetX.toDouble(), offsetZ.toDouble()) * 180.0 / PI).toFloat()
            orbitPitchDegrees = (asin((offsetY / distance).coerceIn(-1f, 1f)) * 180.0 / PI).toFloat()
                .coerceIn(MinOrbitPitchDegrees, MaxOrbitPitchDegrees)
        }
        orbitInitialized = true
    }
}
