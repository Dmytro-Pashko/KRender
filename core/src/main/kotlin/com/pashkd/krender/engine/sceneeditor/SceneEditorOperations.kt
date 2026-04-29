package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import java.util.UUID

/**
 * Scene Editor UI operations. Keeps panel callbacks out of direct scene mutation details.
 */
class SceneEditorOperations(
    private val document: SceneEditorDocument,
    private val state: SceneEditorState,
    private val context: EngineContext,
) {
    fun createNewScene() {
        context.logger.info(TAG) { "New scene creation started" }
        SceneEditorSceneFactory.createNewScene(document, state)
        context.logger.info(TAG) {
            "New scene created id='${document.descriptor?.id ?: "<missing>"}' entities=${document.world.all().size}"
        }
    }

    fun requestSave() {
        context.logger.info(TAG) { "Scene save requested currentPath='${state.currentScenePath ?: "<none>"}'" }
        val path = state.currentScenePath
        if (path.isNullOrBlank()) {
            requestSaveAs()
            return
        }
        saveToPath(path)
    }

    fun requestSaveAs() {
        if (state.saveAsPath.isBlank()) {
            state.saveAsPath = defaultSavePath()
        }
        state.saveAsRequested = true
        state.statusMessage = "Choose a path for Save As."
    }

    fun saveAs(path: String) {
        saveToPath(path)
    }

    fun requestOpen() {
        context.logger.info(TAG) { "Scene open requested currentPath='${state.currentScenePath ?: "<none>"}'" }
        if (state.openPath.isBlank()) {
            state.openPath = state.currentScenePath ?: defaultSavePath()
        }
        state.openErrorMessage = null
        state.openRequested = true
        state.statusMessage = "Choose a scene path to open."
    }

    fun open(path: String) {
        try {
            val normalizedPath = ScenePathUtils.normalizeScenePath(path)
            context.logger.info(TAG) { "Opening scene path='$normalizedPath'" }
            val text = context.sceneFiles.readText(normalizedPath)
            val descriptor = SceneSerializer.decode(text)
            context.logger.info(TAG) {
                "Decoded scene id='${descriptor.id}' name='${descriptor.name}' entities=${descriptor.entities.size}"
            }

            val loadedWorld = SceneWorld()
            SceneSerializer.applyToWorld(descriptor, loadedWorld, context.logger)

            document.world = loadedWorld
            document.descriptor = descriptor
            state.currentScenePath = normalizedPath
            state.sceneName = descriptor.name
            state.selectedEntityId = descriptor.settings.activeCameraEntityId
                ?.takeIf { entityId -> document.world.getEntity(entityId) != null }
            state.hasUnsavedChanges = false
            state.openRequested = false
            state.openPath = normalizedPath
            state.openErrorMessage = null
            state.statusMessage = "Scene opened: $normalizedPath"
            context.logger.info(TAG) {
                "Scene opened path='$normalizedPath' id='${descriptor.id}' entities=${descriptor.entities.size}"
            }
        } catch (error: Exception) {
            state.openErrorMessage = error.message
            state.statusMessage = "Open failed: ${error.message}"
            context.logger.error(TAG, error) { "Failed to open scene path='$path': ${error.message}" }
        }
    }

    fun requestPlayPlaceholder() {
        state.statusMessage = "Play Mode is not implemented yet."
    }

    fun readSceneText(path: String): String =
        context.sceneFiles.readText(path)

    private fun saveToPath(rawPath: String) {
        try {
            val path = ScenePathUtils.normalizeScenePath(rawPath)
            context.logger.info(TAG) { "Saving scene path='$path'" }
            val descriptor = SceneSerializer.toDescriptor(document, state)
            context.logger.info(TAG) {
                "Scene descriptor prepared id='${descriptor.id}' entities=${descriptor.entities.size}"
            }
            val encoded = SceneSerializer.encode(descriptor)
            context.sceneFiles.ensureDirectories(path)
            context.sceneFiles.writeText(path, encoded)

            document.descriptor = descriptor
            state.currentScenePath = path
            state.saveAsPath = path
            state.saveAsRequested = false
            state.saveErrorMessage = null
            state.hasUnsavedChanges = false
            state.statusMessage = "Scene saved: $path"
            context.logger.info(TAG) { "Scene saved path='$path' id='${descriptor.id}' entities=${descriptor.entities.size}" }
        } catch (error: Exception) {
            state.saveErrorMessage = error.message
            state.statusMessage = "Save failed: ${error.message}"
            context.logger.error(TAG, error) { "Failed to save scene path='$rawPath': ${error.message}" }
        }
    }

    private fun defaultSavePath(): String = "scenes/${sanitizeSceneName(state.sceneName)}.krscene"

    private fun sanitizeSceneName(name: String): String {
        val sanitized = name.trim().replace(Regex("\\s+"), "_")
        return sanitized.takeIf(String::isNotBlank) ?: "Untitled_Scene"
    }

    companion object {
        private const val TAG = "SceneEditorOperations"
    }
}

/**
 * Creates the default editable scene content for a new scene document.
 */
object SceneEditorSceneFactory {
    fun createNewScene(
        document: SceneEditorDocument,
        state: SceneEditorState,
        sceneName: String = "Untitled Scene",
    ) {
        val world = document.world
        world.clear()

        val camera = createDefaultCamera(world)
        createDefaultDirectionalLight(world)

        document.descriptor = SceneDescriptor(
            id = generateSceneId(),
            name = sceneName,
            entities = emptyList(),
            settings = SceneSettingsDescriptor(
                activeCameraEntityId = camera.id,
                ambientLightEntityId = null,
            ),
        )

        state.sceneName = sceneName
        state.currentScenePath = null
        state.selectedEntityId = camera.id
        state.hasUnsavedChanges = true
        state.statusMessage = "New scene created"
    }

    private fun createDefaultCamera(world: SceneWorld): Entity {
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
        return camera
    }

    private fun createDefaultDirectionalLight(world: SceneWorld): Entity {
        val light = world.createEntity("Directional Light")
        light.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.96f, 0.88f),
                intensity = 1.2f,
                direction = Vec3(-0.45f, -0.8f, -0.35f),
            ),
        )
        return light
    }

    private fun generateSceneId(): String = "scene:${UUID.randomUUID()}"
}
