package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.DrawDynamicModel
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformSnapshot
import com.pashkd.krender.engine.api.TransformComponent
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

        if (state.previewState.showGround) {
            world.renderCommands.submit(
                DrawDynamicModel(
                    entityId = GroundEntityId,
                    model = controller.groundModel,
                    transform = TransformSnapshot(),
                    material = controller.groundMaterial(),
                ),
            )
        }
    }

    companion object {
        private const val GroundEntityId = -4242L
    }
}
