package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorState

class EnvironmentPreviewCameraSystem(
    private val state: EnvironmentEditorState,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val preview = state.previewState
        if (preview.autoRotate) {
            preview.cameraYawDegrees = (preview.cameraYawDegrees + dt * AutoRotateDegreesPerSecond) % 360f
        }

        val cameraEntity =
            world
                .query<TransformComponent, PerspectiveCameraComponent, ActiveCameraComponent>()
                .firstOrNull() ?: return
        val transform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val orbitPosition = EnvironmentPreviewCamera.orbitPosition(preview)
        transform.position.set(orbitPosition.x, orbitPosition.y, orbitPosition.z)
        camera.lookAt = EnvironmentPreviewCamera.FocusTarget.copy()
    }

    companion object {
        private const val AutoRotateDegreesPerSecond = 18f
    }
}

class EnvironmentPreviewLiveUpdateSystem(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentPreviewController,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val env = state.environment
        state.previewState.previewStatusMessage =
            if (env == null) {
                "Preview unavailable: no environment is loaded."
            } else {
                controller.liveStatusMessage(env, state.previewState)
            }
    }
}

class EnvironmentEditorStateLoggingSystem(
    private val state: EnvironmentEditorState,
    private val logger: Logger,
) : System() {
    private var lastSnapshot: EnvironmentEditorLogSnapshot? = null

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val snapshot = EnvironmentEditorLogSnapshot.capture(state)
        if (snapshot == lastSnapshot) return
        val previous = lastSnapshot
        lastSnapshot = snapshot
        if (previous == null) {
            logger.info(TAG) { "Environment Editor state initialized ${snapshot.describe()}" }
            return
        }
        logger.info(TAG) {
            "Environment Editor state changed " +
                "dirty ${previous.dirty} -> ${snapshot.dirty}; " +
                "backgroundVisible ${previous.backgroundVisible} -> ${snapshot.backgroundVisible}; " +
                "backgroundMode ${previous.backgroundMode} -> ${snapshot.backgroundMode}; " +
                "backgroundColor ${previous.backgroundColor} -> ${snapshot.backgroundColor}; " +
                "autoRotate ${previous.autoRotate} -> ${snapshot.autoRotate}; " +
                "exposure ${previous.exposure} -> ${snapshot.exposure}; " +
                "rotation ${previous.rotationDegrees} -> ${snapshot.rotationDegrees}; " +
                "diffuse ${previous.diffuseIntensity} -> ${snapshot.diffuseIntensity}; " +
                "specular ${previous.specularIntensity} -> ${snapshot.specularIntensity}; " +
                "loadError ${previous.loadError} -> ${snapshot.loadError}"
        }
    }

    companion object {
        private const val TAG = "EnvironmentEditorState"
    }
}

class EnvironmentPreviewRenderSystem(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentPreviewController,
) : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        val previewEntity = state.previewModelEntityId?.let(world::getEntity) ?: return
        val model = previewEntity.get<ModelComponent>() ?: return
        val transform = previewEntity.get<TransformComponent>() ?: return
        world.renderCommands.submit(
            DrawModel(
                entityId = previewEntity.id,
                model = model.model,
                transform = transform.snapshot(),
                material = model.material,
                gltfRenderer = controller.gltfRendererSettings(state),
            ),
        )
    }
}

private data class EnvironmentEditorLogSnapshot(
    val dirty: Boolean,
    val loadError: String?,
    val backgroundVisible: Boolean?,
    val backgroundMode: String?,
    val backgroundColor: String?,
    val autoRotate: Boolean,
    val exposure: Float?,
    val rotationDegrees: Float?,
    val diffuseIntensity: Float?,
    val specularIntensity: Float?,
) {
    fun describe(): String =
        "dirty=$dirty loadError=$loadError backgroundVisible=$backgroundVisible " +
            "backgroundMode=$backgroundMode backgroundColor=$backgroundColor " +
            "autoRotate=$autoRotate " +
            "exposure=$exposure rotation=$rotationDegrees diffuse=$diffuseIntensity specular=$specularIntensity"

    companion object {
        fun capture(state: EnvironmentEditorState): EnvironmentEditorLogSnapshot {
            val environment = state.environment
            return EnvironmentEditorLogSnapshot(
                dirty = state.dirty,
                loadError = state.loadError,
                backgroundVisible = environment?.settings?.backgroundMode != BackgroundMode.None,
                backgroundMode = environment?.settings?.backgroundMode?.name,
                backgroundColor = environment.backgroundColorString(),
                autoRotate = state.previewState.autoRotate,
                exposure = environment?.settings?.exposure,
                rotationDegrees = environment?.settings?.rotationDegrees,
                diffuseIntensity = environment?.settings?.diffuseIntensity,
                specularIntensity = environment?.settings?.specularIntensity,
            )
        }
    }
}

private fun EnvironmentAsset?.backgroundColorString(): String? {
    val color = this?.settings?.backgroundColor ?: return null
    return "(${color.r},${color.g},${color.b},${color.a})"
}
