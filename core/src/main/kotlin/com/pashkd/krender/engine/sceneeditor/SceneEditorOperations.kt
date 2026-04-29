package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.NameComponent
import com.pashkd.krender.engine.api.ParentComponent
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TransformComponent
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

    fun createEmptyEntity() {
        val entity = document.world.createEntity("Empty Entity")
        state.selectedEntityId = entity.id
        markSceneChanged("Created ${entity.name}.")
        context.logger.info(TAG) { "Created empty scene entity id=${entity.id} name='${entity.name}'" }
    }

    fun renameEntity(entityId: EntityId, newName: String) {
        val entity = editableEntity(entityId) ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            state.statusMessage = "Rename failed: entity name cannot be blank."
            context.logger.warn(TAG) { "Rejected blank rename for entity id=$entityId" }
            return
        }

        val nameComponent = entity.get<NameComponent>()
        if (nameComponent != null) {
            nameComponent.name = trimmedName
        } else {
            entity.add(NameComponent(trimmedName))
        }
        state.selectedEntityId = entity.id
        markSceneChanged("Renamed entity to $trimmedName.")
        context.logger.info(TAG) { "Renamed entity id=$entityId name='$trimmedName'" }
    }

    fun deleteEntity(entityId: EntityId) {
        val entity = editableEntity(entityId) ?: return
        val detachedChildren = detachChildren(entity.id)
        document.world.removeEntity(entity.id)
        if (state.selectedEntityId == entity.id) {
            state.selectedEntityId = null
        }
        val childMessage = if (detachedChildren == 0) "" else " Detached $detachedChildren child entity links."
        markSceneChanged("Deleted ${entity.name}.$childMessage")
        context.logger.info(TAG) {
            "Deleted entity id=$entityId name='${entity.name}' detachedChildren=$detachedChildren"
        }
    }

    fun duplicateEntity(entityId: EntityId) {
        val source = editableEntity(entityId) ?: return
        val duplicate = document.world.createEntity("${source.name} Copy")
        duplicate.active = source.active
        duplicateSupportedComponents(source, duplicate)
        state.selectedEntityId = duplicate.id
        markSceneChanged("Duplicated ${source.name}.")
        context.logger.info(TAG) {
            "Duplicated entity id=${source.id} name='${source.name}' as id=${duplicate.id} name='${duplicate.name}'"
        }
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

    private fun editableEntity(entityId: EntityId): Entity? {
        val entity = document.world.getEntity(entityId)
        if (entity == null) {
            state.selectedEntityId = state.selectedEntityId?.takeIf { it != entityId }
            state.statusMessage = "Entity not found."
            context.logger.warn(TAG) { "Scene entity operation ignored because entity id=$entityId was not found" }
            return null
        }
        if (entity.get<EditorOnlyComponent>() != null) {
            state.statusMessage = "Editor-only entities cannot be edited from the hierarchy."
            context.logger.warn(TAG) { "Scene entity operation rejected for editor-only entity id=$entityId" }
            return null
        }
        return entity
    }

    private fun detachChildren(parentId: EntityId): Int {
        var detached = 0
        document.world.all().forEach { child ->
            val parent = child.get<ParentComponent>() ?: return@forEach
            if (parent.parentId == parentId) {
                // MVP hierarchy deletion removes only the selected entity and promotes children to roots.
                child.remove(ParentComponent::class)
                detached += 1
            }
        }
        return detached
    }

    private fun duplicateSupportedComponents(source: Entity, duplicate: Entity) {
        source.get<NameComponent>()?.let { component ->
            duplicate.add(NameComponent("${component.name} Copy"))
        }
        source.get<TransformComponent>()?.let { component ->
            duplicate.add(component.cloneForSceneEntity())
        }
        source.get<ParentComponent>()?.let { component ->
            val parent = document.world.getEntity(component.parentId)
            if (parent != null && parent.get<EditorOnlyComponent>() == null) {
                duplicate.add(ParentComponent(component.parentId))
            }
        }
        source.get<PerspectiveCameraComponent>()?.let { component ->
            duplicate.add(
                PerspectiveCameraComponent(
                    fieldOfViewDegrees = component.fieldOfViewDegrees,
                    near = component.near,
                    far = component.far,
                    lookAt = component.lookAt?.copy(),
                ),
            )
        }
        source.get<LightComponent>()?.let { component ->
            duplicate.add(
                LightComponent(
                    type = component.type,
                    color = component.color.copy(),
                    intensity = component.intensity,
                    direction = component.direction.copy(),
                ),
            )
        }
    }

    private fun TransformComponent.cloneForSceneEntity(): TransformComponent =
        TransformComponent(
            position = position.copy(),
            rotation = rotation.copy(),
            eulerDegrees = eulerDegrees.copy(),
            scale = scale.copy(),
        )

    private fun markSceneChanged(message: String) {
        state.hasUnsavedChanges = true
        state.statusMessage = message
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
