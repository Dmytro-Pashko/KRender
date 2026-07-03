package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorState

/**
 * Creates the preview entities and installs the systems that own their behavior.
 *
 * The bundled model contains the material-sphere geometry and glTF material values.
 * This assembler only owns scene placement; renderer adaptation remains in
 * [EnvironmentPreviewController].
 */
class EnvironmentPreviewSceneAssembler(
    private val world: SceneWorld,
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentPreviewController,
) {
    fun install() {
        createCamera()
        createTestModel()
        world.systems.add(EnvironmentPreviewCameraSystem(state))
        world.systems.add(EnvironmentPreviewRenderSystem(state, controller))
    }

    private fun createCamera() {
        val orbitPosition = EnvironmentPreviewCamera.orbitPosition(state.previewState)
        val camera = world.createEntity("Environment Preview Camera")
        camera.transform.position.set(orbitPosition.x, orbitPosition.y, orbitPosition.z)
        camera.add(ActiveCameraComponent())
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 50f,
                near = 0.05f,
                far = 200f,
                lookAt = EnvironmentPreviewCamera.FocusTarget.copy(),
            ),
        )
    }

    private fun createTestModel() {
        val preview = world.createEntity("Environment Material Spheres")
        state.previewModelEntityId = preview.id
        preview.transform.position.set(
            EnvironmentPreviewController.PreviewModelPosition.x,
            EnvironmentPreviewController.PreviewModelPosition.y,
            EnvironmentPreviewController.PreviewModelPosition.z,
        )
        preview.transform.scale.set(
            EnvironmentPreviewController.PreviewModelScale.x,
            EnvironmentPreviewController.PreviewModelScale.y,
            EnvironmentPreviewController.PreviewModelScale.z,
        )
        preview.add(ModelComponent(model = controller.previewModel, material = Material()))
    }
}
