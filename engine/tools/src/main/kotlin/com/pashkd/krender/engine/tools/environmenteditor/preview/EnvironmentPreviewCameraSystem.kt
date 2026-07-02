package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorState

/** Updates the preview orbit camera from transient editor controls. */
class EnvironmentPreviewCameraSystem(
    private val state: EnvironmentEditorState,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val preview = state.previewState
        if (preview.autoRotate) {
            preview.cameraYawDegrees = (preview.cameraYawDegrees + dt * AUTO_ROTATE_DEGREES_PER_SECOND) % 360f
        }

        val cameraEntity =
            world
                .query<TransformComponent, PerspectiveCameraComponent, ActiveCameraComponent>()
                .firstOrNull() ?: return
        val transform = checkNotNull(cameraEntity.get<TransformComponent>())
        val camera = checkNotNull(cameraEntity.get<PerspectiveCameraComponent>())
        val orbitPosition = EnvironmentPreviewCamera.orbitPosition(preview)
        transform.position.set(orbitPosition.x, orbitPosition.y, orbitPosition.z)
        camera.lookAt = EnvironmentPreviewCamera.FocusTarget.copy()
    }

    companion object {
        private const val AUTO_ROTATE_DEGREES_PER_SECOND = 18f
    }
}
