package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.FreeCameraControllerComponent
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.cos
import kotlin.math.sin

/**
 * Syncs ModelViewer UI state with assets, render settings, and scene actions.
 */
class ModelViewerSystem(
    private val input: InputService,
    private val assets: AssetService,
    private val logger: Logger,
    private val state: ModelViewerState,
    private val onReloadSelection: (AssetRef<ModelAsset>) -> Unit,
    private val onExitRequested: () -> Unit,
) : System() {
    /**
     * Updates render toggles, status text, and queued scene actions.
     */
    override fun update(world: SceneWorld, dt: Float) {
        val snapshot = input.snapshot()
        input.setCursorCaptured(false)

        if (!snapshot.uiCapturesKeyboard && snapshot.wasPressed(Key.F1)) {
            state.wireframeEnabled = !state.wireframeEnabled
        }

        syncStatus(world)
        handleRequests()
    }

    /**
     * Mirrors asset and camera state into the shared UI state object.
     */
    private fun syncStatus(world: SceneWorld) {
        state.errorMessage = if (state.availableModels.isEmpty()) {
            "No models are available for this scene."
        } else {
            null
        }
        state.assetProgress = assets.progress()
        state.assetLoaded = state.loadedModel?.let(assets::isLoaded) ?: false
        state.loadingStatus = when {
            state.loadedModel == null -> "No model loaded"
            state.assetLoaded -> "Loaded"
            else -> "Loading ${"%.0f".format(state.assetProgress * 100f)}%"
        }
        state.triangleCount = state.loadedModel?.let(assets::triangleCount)

        world.query<ModelComponent>().firstOrNull()?.get<ModelComponent>()?.let { model ->
            if (model.material.wireframe != state.wireframeEnabled) {
                model.material = model.material.copy(wireframe = state.wireframeEnabled)
            }
        }

        world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull()?.transform?.position?.let { position ->
            state.cameraPosition = "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)
        }
    }

    /**
     * Executes deferred actions requested by the UI.
     */
    private fun handleRequests() {
        if (state.loadSelectedModelRequested) {
            state.loadSelectedModelRequested = false
            val selectedModel = state.selectedModel
            if (selectedModel == null) {
                state.errorMessage = "Select a model before loading."
            } else {
                logger.info("ModelViewer") { "Reloading scene with '${selectedModel.path}'" }
                onReloadSelection(selectedModel)
                return
            }
        }

        if (state.exitRequested) {
            state.exitRequested = false
            onExitRequested()
        }
    }
}

/**
 * Applies free-camera look and movement when ImGui is not capturing input.
 */
class ModelViewerCameraSystem(
    private val input: InputService,
) : System() {
    /**
     * Advances the viewer camera from mouse look and WASD-style input.
     */
    override fun update(world: SceneWorld, dt: Float) {
        val camera = world.query<TransformComponent, PerspectiveCameraComponent, FreeCameraControllerComponent>()
            .firstOrNull() ?: return
        val transform = camera.get<TransformComponent>() ?: return
        val controller = camera.get<FreeCameraControllerComponent>() ?: return
        val snapshot = input.snapshot()

        if (snapshot.uiCapturesMouse || snapshot.uiCapturesKeyboard) return

        transform.eulerDegrees.y -= snapshot.mouseDelta.x * controller.mouseSensitivity
        transform.eulerDegrees.x = (transform.eulerDegrees.x - snapshot.mouseDelta.y * controller.mouseSensitivity)
            .coerceIn(controller.minPitchDegrees, controller.maxPitchDegrees)

        val yaw = Math.toRadians(transform.eulerDegrees.y.toDouble())
        val forward = Vec3(
            x = sin(yaw).toFloat(),
            y = 0f,
            z = cos(yaw).toFloat(),
        )
        val right = Vec3(
            x = -cos(yaw).toFloat(),
            y = 0f,
            z = sin(yaw).toFloat(),
        )

        var moveX = 0f
        var moveY = 0f
        var moveZ = 0f
        if (snapshot.isDown(Key.W)) moveZ += 1f
        if (snapshot.isDown(Key.S)) moveZ -= 1f
        if (snapshot.isDown(Key.D)) moveX += 1f
        if (snapshot.isDown(Key.A)) moveX -= 1f
        if (snapshot.isDown(Key.ShiftLeft)) moveY += 1f
        if (snapshot.isDown(Key.ControlLeft)) moveY -= 1f

        if (moveX != 0f || moveY != 0f || moveZ != 0f) {
            val speed = controller.moveSpeed
            transform.position.x += (forward.x * moveZ + right.x * moveX) * speed * dt
            transform.position.y += moveY * speed * dt
            transform.position.z += (forward.z * moveZ + right.z * moveX) * speed * dt
        }
    }
}
