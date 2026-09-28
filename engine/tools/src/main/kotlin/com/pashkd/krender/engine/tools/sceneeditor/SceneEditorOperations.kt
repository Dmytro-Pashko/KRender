package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.*
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

class SceneEditorOperations(
    private val document: SceneEditorDocument,
    private val state: SceneEditorState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun open(path: String): Boolean = try {
        val normalized = ScenePathUtils.normalizeScenePath(path)
        val descriptor = SceneSerializer.decode(context.sceneFiles.readText(normalized))
        val world = SceneWorld()
        SceneSerializer.applyToWorld(descriptor, world, context.logger)
        document.world = world
        document.descriptor = descriptor
        document.terrainPreviewEntityId = null
        syncTerrainPreview()
        state.currentScenePath = normalized
        state.sceneName = descriptor.name
        state.selectedEntityId = null
        state.hasUnsavedChanges = false
        state.openErrorMessage = null
        validateScene()
        context.logger.info(TAG) { "Scene opened path='$normalized'" }
        true
    } catch (error: Exception) {
        state.openErrorMessage = error.message ?: "Scene could not be opened."
        context.logger.error(TAG, error) { "Scene open failed path='$path': ${error.message}" }
        false
    }

    fun reload(): Boolean = state.currentScenePath?.let(::open) ?: false

    fun exit() = context.requestExit()

    fun save(): Boolean = try {
        requireNotNull(document.descriptor) { "Load a scene before saving." }
        val path = requireNotNull(state.currentScenePath) { "Scene file path is missing." }
        val descriptor = snapshot()
        context.sceneFiles.writeText(path, SceneSerializer.encode(descriptor))
        document.descriptor = descriptor
        state.hasUnsavedChanges = false
        state.saveErrorMessage = null
        validateScene()
        context.logger.info(TAG) { "Scene saved path='$path'" }
        true
    } catch (error: Exception) {
        state.saveErrorMessage = error.message ?: "Scene could not be saved."
        context.logger.error(TAG, error) { "Scene save failed: ${error.message}" }
        false
    }

    fun playInNewWindow() {
        if (document.descriptor == null) return
        val path = state.currentScenePath ?: return
        if (state.hasUnsavedChanges && !save()) return
        try {
            context.runtimeLauncher.launchRuntimeScene(path)
        } catch (error: Exception) {
            state.saveErrorMessage = "Play failed: ${error.message}"
            context.logger.error(TAG, error) { "Scene play failed path='$path': ${error.message}" }
        }
    }

    fun saveUiLayout() {
        runCatching {
            ImGuiLayoutConfigCodec.save(SceneEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), context.sceneFiles)
        }.onFailure {
            state.saveErrorMessage = "Scene layout save failed: ${it.message}"
            context.logger.error(TAG, it) { "Scene layout save failed: ${it.message}" }
        }
    }

    fun restoreUiLayout() {
        val layout = runCatching {
            ImGuiLayoutConfigCodec.decodeResult(
                context.sceneFiles.readText(SceneEditorUiLayoutDefaults.assetPath),
                SceneEditorUiLayoutDefaults.config,
                context.logger,
                SceneEditorUiLayoutDefaults.assetPath,
            ).config
        }.getOrElse {
            state.saveErrorMessage = "Scene layout restore failed: ${it.message}"
            context.logger.error(TAG, it) { "Scene layout restore failed: ${it.message}" }
            return
        }
        layoutTracker.requestRestore(layout)
    }

    fun addEntity(type: String, modelPath: String? = null) {
        val name = when (type) {
            "Model" -> modelPath?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Model"
            "Camera" -> "Camera"
            "Directional Light", "Point Light" -> type
            else -> "Empty Entity"
        }
        if (type == "Model" && modelPath.isNullOrBlank()) return
        val entity = document.world.createEntity(name)
        when (type) {
            "Model" -> addComponent(entity.id, SceneComponentTypes.Model, mapOf("model" to modelPath.orEmpty()))
            "Camera" -> {
                entity.transform.position = state.camera.position.copy()
                entity.transform.eulerDegrees = state.camera.eulerDegrees.copy()
                addComponent(entity.id, SceneComponentTypes.Camera)
                if (document.descriptor?.settings?.activeCameraEntityId == null) setActiveCamera(entity.id)
            }
            "Directional Light" -> addComponent(entity.id, SceneComponentTypes.Light)
            "Point Light" -> addComponent(entity.id, SceneComponentTypes.Light, mapOf("type" to "Point", "intensity" to "10"))
        }
        state.selectedEntityId = entity.id
        changed()
    }

    fun deleteEntity(id: EntityId) {
        val entity = editable(id) ?: return
        document.world.all().filter { it.get<ParentComponent>()?.parentId == id }.forEach { it.remove(ParentComponent::class) }
        document.world.removeEntity(id)
        if (state.selectedEntityId == id) state.selectedEntityId = null
        if (document.descriptor?.settings?.activeCameraEntityId == id) {
            document.descriptor = document.descriptor?.copy(settings = document.descriptor!!.settings.copy(activeCameraEntityId = null))
        }
        changed()
        context.logger.info(TAG) { "Deleted scene entity id=${entity.id}" }
    }

    fun duplicateEntity(id: EntityId) {
        val source = editable(id) ?: return
        val target = document.world.createEntity("${source.name} Copy")
        target.active = source.active
        source.components.all().filterNot { it is NameComponent }.forEach { component ->
            SceneComponentRegistry.copy(component)?.let(target::add)
        }
        val original = document.descriptor?.entities?.firstOrNull { it.id == id }
        val unknown = original?.components.orEmpty().filter { SceneComponentRegistry.find(it.type) == null }
        if (unknown.isNotEmpty()) {
            document.descriptor = document.descriptor?.copy(entities = document.descriptor!!.entities + EntityDescriptor(target.id, target.name, components = unknown))
        }
        state.selectedEntityId = target.id
        changed()
    }

    fun setEntityName(id: EntityId, name: String) {
        if (name.isBlank()) return
        editable(id)?.get<NameComponent>()?.name = name.trim()
        changed()
    }

    fun setEntityActive(id: EntityId, active: Boolean) {
        editable(id)?.active = active
        changed()
    }

    fun setParent(id: EntityId, parentId: EntityId?) {
        val entity = editable(id) ?: return
        if (parentId == null) {
            entity.remove(ParentComponent::class)
            changed()
            return
        }
        if (parentId == id || editable(parentId) == null) return
        var ancestor: EntityId? = parentId
        while (ancestor != null) {
            if (ancestor == id) return
            ancestor = document.world.getEntity(ancestor)?.get<ParentComponent>()?.parentId
        }
        entity.add(ParentComponent(parentId))
        changed()
    }

    fun addComponent(id: EntityId, type: String, overrides: Map<String, String> = emptyMap()): Boolean {
        val entity = editable(id) ?: return false
        val definition = SceneComponentRegistry.find(type)?.takeIf { it.addable } ?: return false
        if (entity.get(definition.componentClass) != null) return false
        val values = definition.defaultProperties + overrides
        val error = definition.validate(values)
        if (error != null) { state.saveErrorMessage = error; return false }
        definition.decode(values)?.let(entity::add) ?: return false
        if (definition.requiresTransform) entity.transform
        changed()
        return true
    }

    fun removeComponent(id: EntityId, type: String): Boolean {
        if (type == SceneComponentTypes.Transform) return false
        val entity = editable(id) ?: return false
        val definition = SceneComponentRegistry.find(type)?.takeIf { it.addable } ?: return false
        if (entity.remove(definition.componentClass) == null) return false
        if (type == SceneComponentTypes.Camera && document.descriptor?.settings?.activeCameraEntityId == id) {
            document.descriptor = document.descriptor?.copy(settings = document.descriptor!!.settings.copy(activeCameraEntityId = null))
        }
        changed()
        return true
    }

    fun setComponentField(id: EntityId, type: String, field: String, value: String): Boolean {
        val entity = editable(id) ?: return false
        val definition = SceneComponentRegistry.find(type) ?: return false
        val component = entity.get(definition.componentClass) ?: return false
        val values = definition.encode(component) + (field to value)
        val error = definition.validate(values)
        if (error != null) { state.saveErrorMessage = error; return false }
        definition.decode(values)?.let(entity::add) ?: return false
        changed()
        return true
    }

    fun setActiveCamera(id: EntityId) {
        if (editable(id)?.get<PerspectiveCameraComponent>() == null) return
        editSettings { it.copy(activeCameraEntityId = id) }
    }

    fun cameraToView(id: EntityId) {
        val camera = editable(id)?.takeIf { it.get<PerspectiveCameraComponent>() != null } ?: return
        val transform = camera.get<TransformComponent>() ?: return
        state.camera.pendingPosition = transform.position.copy()
        state.camera.pendingEulerDegrees = transform.eulerDegrees.copy()
    }

    fun viewToCamera(id: EntityId) {
        val camera = editable(id)?.takeIf { it.get<PerspectiveCameraComponent>() != null } ?: return
        val transform = camera.get<TransformComponent>() ?: return
        transform.position = state.camera.position.copy()
        transform.eulerDegrees = state.camera.eulerDegrees.copy()
        changed()
    }

    fun setSceneName(name: String) {
        if (name.isBlank()) return
        state.sceneName = name.trim()
        changed()
    }

    fun setEnvironmentAsset(path: String?) = editSettings {
        it.copy(environment = it.environment.copy(environmentAssetPath = path))
    }

    fun setAmbientLightColor(color: Color) = editSettings {
        it.copy(lighting = it.lighting.copy(ambientColor = color))
    }

    fun setAmbientLightIntensity(intensity: Float) {
        if (!intensity.isFinite() || intensity < 0f) return
        editSettings { it.copy(lighting = it.lighting.copy(ambientIntensity = intensity)) }
    }

    fun setTerrain(terrain: SceneTerrainSettingsDescriptor) {
        editSettings { it.copy(terrain = terrain) }
        syncTerrainPreview()
    }

    fun openTerrainInEditor(path: String) {
        try {
            context.editorToolLauncher.launchTerrainEditor(path)
        } catch (error: Exception) {
            state.saveErrorMessage = error.message
            context.logger.error(TAG, error) { "Terrain Editor launch failed: ${error.message}" }
        }
    }

    fun validateScene(): SceneValidationReport {
        val descriptor = snapshot()
        val report = RuntimeSceneValidator.validate(descriptor, SceneDependencyCollector(context.sceneFiles).collect(descriptor))
        state.validationReport = report
        state.validationDirty = false
        return report
    }

    private fun snapshot(): SceneDescriptor = SceneSerializer.toDescriptor(
        document.world,
        state.sceneName,
        document.descriptor,
        includeEntity = { it.get<EditorOnlyComponent>() == null },
    )

    private fun editSettings(change: (SceneSettingsDescriptor) -> SceneSettingsDescriptor) {
        val descriptor = document.descriptor ?: return
        document.descriptor = descriptor.copy(settings = change(descriptor.settings))
        changed()
    }

    private fun syncTerrainPreview() {
        document.terrainPreviewEntityId?.let(document.world::removeEntity)
        document.terrainPreviewEntityId = SceneTerrainFactory.create(document.world, document.descriptor?.settings?.terrain ?: return)
            ?.also { it.add(EditorOnlyComponent()) }?.id
    }

    private fun editable(id: EntityId): Entity? = document.world.getEntity(id)?.takeIf { it.get<EditorOnlyComponent>() == null }

    private fun changed() {
        state.hasUnsavedChanges = true
        state.validationDirty = true
    }

    companion object { private const val TAG = "SceneEditorOperations" }
}
