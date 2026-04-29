package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent

/**
 * Scene Editor UI operations. Keeps panel callbacks out of direct scene mutation details.
 */
class SceneEditorOperations(
    private val document: SceneEditorDocument,
    private val state: SceneEditorState,
    private val logger: Logger,
) {
    fun createNewScene() {
        logger.info(TAG) { "Creating new Scene Editor document" }
        SceneEditorSceneFactory.createNewScene(document.world, state)
    }

    fun requestSave() {
        state.statusMessage = "Scene persistence is not implemented yet."
    }

    companion object {
        private const val TAG = "SceneEditorOperations"
    }
}

/**
 * Creates the default editable scene content for a new scene document.
 */
object SceneEditorSceneFactory {
    fun createNewScene(world: SceneWorld, state: SceneEditorState) {
        world.clear()

        val camera = world.createEntity("Main Camera")
        camera.transform.position.set(0f, 2f, 6f)
        camera.transform.eulerDegrees.set(-10f, 180f, 0f)
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                near = 0.05f,
                far = 250f,
                lookAt = Vec3.zero(),
            ),
        )

        val light = world.createEntity("Directional Light")
        light.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.96f, 0.88f),
                intensity = 1.2f,
                direction = Vec3(-0.45f, -0.8f, -0.35f),
            ),
        )

        state.sceneName = "Untitled Scene"
        state.currentScenePath = null
        state.selectedEntityId = null
        state.hasUnsavedChanges = true
        state.statusMessage = "New scene created"
    }
}

