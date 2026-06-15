package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.*
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainPreviewMode
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Scene Editor UI operations. Keeps panel callbacks out of direct scene mutation details.
 */
class SceneEditorOperations(
    private val document: SceneEditorDocument,
    private val state: SceneEditorState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun createNewScene() {
        context.logger.info(TAG) { "New scene creation started" }
        SceneEditorSceneFactory.createNewScene(document, state)
        refreshValidation(updateStatusMessage = false)
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

    fun saveUiLayout() {
        try {
            val config = layoutTracker.currentConfig()
            ImGuiLayoutConfigCodec.save(SceneEditorUiLayoutDefaults.assetPath, config, context.sceneFiles)
            state.statusMessage = "UI layout saved."
            context.logger.info(
                TAG,
            ) { "Scene Editor UI layout saved path='${SceneEditorUiLayoutDefaults.assetPath}' panels=${config.panels.size}" }
        } catch (error: Exception) {
            state.statusMessage = "UI layout save failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to save Scene Editor UI layout path='${SceneEditorUiLayoutDefaults.assetPath}': ${error.message}"
            }
        }
    }

    fun restoreUiLayout() {
        try {
            val text = context.sceneFiles.readText(SceneEditorUiLayoutDefaults.assetPath)
            val result =
                ImGuiLayoutConfigCodec.decodeResult(
                    text = text,
                    fallback = SceneEditorUiLayoutDefaults.config,
                    logger = context.logger,
                    source = SceneEditorUiLayoutDefaults.assetPath,
                )
            layoutTracker.requestRestore(result.config)
            state.statusMessage =
                if (result.usedFallback) {
                    "UI layout restored to default."
                } else {
                    "UI layout restored."
                }
            context.logger.info(TAG) {
                "Scene Editor UI layout restore requested path='${SceneEditorUiLayoutDefaults.assetPath}' panels=${result.config.panels.size} fallback=${result.usedFallback}"
            }
        } catch (error: Exception) {
            layoutTracker.requestRestore(SceneEditorUiLayoutDefaults.config)
            state.statusMessage = "UI layout restored to default."
            context.logger.warn(TAG, error) {
                "Scene Editor UI layout '${SceneEditorUiLayoutDefaults.assetPath}' missing or unreadable. Restoring defaults."
            }
        }
    }

    fun createEmptyEntity() {
        val entity = document.world.createEntity("Empty Entity")
        state.selectedEntityId = entity.id
        markSceneChanged("Created ${entity.name}.")
        context.logger.info(TAG) { "Created empty scene entity id=${entity.id} name='${entity.name}'" }
    }

    fun placeModel(path: String) {
        val normalizedPath = normalizeModelPath(path)
        if (normalizedPath.isBlank()) {
            state.modelPlacementError = "Model path cannot be blank."
            state.statusMessage = "Place model failed: model path cannot be blank."
            context.logger.warn(TAG) { "Rejected model placement with blank path" }
            return
        }

        val entity = document.world.createEntity(modelEntityName(normalizedPath))
        entity.transform.position = placementPositionInFrontOfCamera()
        entity.add(ModelComponent(model = AssetRef.model(normalizedPath)))

        state.modelPlacementPath = normalizedPath
        state.modelPlacementError = null
        state.selectedEntityId = entity.id
        markSceneChanged("Placed model: $normalizedPath")
        context.logger.info(TAG) { "Placed model entity id=${entity.id} name='${entity.name}' model='$normalizedPath'" }
    }

    fun placeTerrain(path: String) {
        val normalizedPath = normalizeAssetPath(path)
        if (normalizedPath.isBlank()) {
            state.terrainPlacementError = "Terrain path cannot be blank."
            state.statusMessage = "Place terrain failed: terrain path cannot be blank."
            context.logger.warn(TAG) { "Rejected terrain placement with blank path" }
            return
        }

        val entity = document.world.createEntity(terrainEntityName(normalizedPath))
        entity.transform.position.set(0f, 0f, 0f)
        entity.add(TerrainComponent(terrain = AssetRef.terrain(normalizedPath)))

        state.terrainPlacementPath = normalizedPath
        state.terrainPlacementError = null
        state.selectedEntityId = entity.id
        if (!activeTerrainExists()) {
            updateActiveTerrainSetting(entity.id)
        }
        markSceneChanged("Placed terrain: $normalizedPath")
        context.logger.info(TAG) { "Placed terrain entity id=${entity.id} name='${entity.name}' terrain='$normalizedPath'" }
    }

    fun createCameraFromView() {
        val entity = document.world.createEntity(uniqueCameraName())
        entity.transform.position.set(
            state.camera.position.x,
            state.camera.position.y,
            state.camera.position.z,
        )
        entity.transform.eulerDegrees.set(
            state.camera.eulerDegrees.x,
            state.camera.eulerDegrees.y,
            state.camera.eulerDegrees.z,
        )
        entity.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                near = 0.05f,
                far = 250f,
            ),
        )

        state.selectedEntityId = entity.id
        if (!activeCameraExists()) {
            updateActiveCameraSetting(entity.id)
        }
        markSceneChanged("Camera created.")
        context.logger.info(TAG) { "Created camera entity id=${entity.id} name='${entity.name}' from editor view" }
    }

    fun createDirectionalLight() {
        val entity = document.world.createEntity(uniqueLightName("Directional Light"))
        val direction = normalizedOrFallback(DefaultLightDirection, DefaultLightDirection)
        entity.add(
            LightComponent(
                type = LightType.Directional,
                color = DefaultDirectionalLightColor.copy(),
                intensity = DefaultDirectionalLightIntensity,
                direction = direction,
            ),
        )
        state.selectedEntityId = entity.id
        markSceneChanged("Created ${entity.name}.")
        context.logger.info(TAG) {
            "Created directional light entity id=${entity.id} name='${entity.name}' direction=${direction.x},${direction.y},${direction.z}"
        }
    }

    fun createPointLight() {
        val entity = document.world.createEntity(uniqueLightName("Point Light"))
        entity.transform.position = placementPositionInFrontOfCamera(DefaultPointLightPlacementDistance)
        entity.add(
            LightComponent(
                type = LightType.Point,
                color = Color.white(),
                intensity = DefaultPointLightIntensity,
                direction = DefaultLightDirection.copy(),
            ),
        )
        state.selectedEntityId = entity.id
        markSceneChanged("Created ${entity.name}.")
        context.logger.info(TAG) {
            "Created point light entity id=${entity.id} name='${entity.name}' position=${entity.transform.position.x},${entity.transform.position.y},${entity.transform.position.z}"
        }
    }

    fun setActiveCamera(entityId: EntityId) {
        val entity = cameraEntity(entityId) ?: return
        updateActiveCameraSetting(entity.id)
        markSceneChanged("Active camera set to ${entity.name}.")
        context.logger.info(TAG) { "Active scene camera set entityId=${entity.id} name='${entity.name}'" }
    }

    fun setActiveTerrain(entityId: EntityId) {
        val entity = terrainEntity(entityId) ?: return
        updateActiveTerrainSetting(entity.id)
        markSceneChanged("Active terrain set to ${entity.name}.")
        context.logger.info(TAG) {
            "Active scene terrain set entityId=${entity.id} name='${entity.name}' path='${entity.get<TerrainComponent>()?.terrain?.path ?: "<missing>"}'"
        }
    }

    fun clearActiveTerrain(entityId: EntityId) {
        val activeTerrainEntityId = document.descriptor?.settings?.activeTerrainEntityId
        if (activeTerrainEntityId != entityId) return
        updateActiveTerrainSetting(null)
        markSceneChanged("Active terrain cleared.")
        context.logger.info(TAG) { "Active scene terrain cleared entityId=$entityId" }
    }

    fun alignCameraToView(entityId: EntityId) {
        val entity = cameraEntity(entityId) ?: return
        val transform = entity.get<TransformComponent>() ?: return transformMissing(entityId)

        transform.position.set(
            state.camera.position.x,
            state.camera.position.y,
            state.camera.position.z,
        )
        transform.eulerDegrees.set(
            state.camera.eulerDegrees.x,
            state.camera.eulerDegrees.y,
            state.camera.eulerDegrees.z,
        )
        markSceneChanged("Aligned ${entity.name} to editor view.")
        context.logger.info(TAG) { "Aligned camera entityId=${entity.id} name='${entity.name}' to editor view" }
    }

    fun alignViewToCamera(entityId: EntityId) {
        val entity = cameraEntity(entityId) ?: return
        val transform = entity.get<TransformComponent>() ?: return transformMissing(entityId)
        val position = transform.position.copy()
        val eulerDegrees = transform.eulerDegrees.copy()

        state.camera.position = position.copy()
        state.camera.eulerDegrees = eulerDegrees.copy()
        state.pendingCameraPosition = position
        state.pendingCameraEulerDegrees = eulerDegrees
        state.statusMessage = "Aligned editor view to ${entity.name}."
        context.logger.info(TAG) { "Aligned editor view to camera entityId=${entity.id} name='${entity.name}'" }
    }

    fun setLightType(
        entityId: EntityId,
        type: LightType,
    ) {
        if (type != LightType.Directional && type != LightType.Point) {
            rejectLightEdit(entityId, "Only Directional and Point lights are supported in the editor.")
            return
        }
        val entity = lightEntity(entityId) ?: return
        val light = entity.get<LightComponent>() ?: return
        if (light.type == type) return

        entity.add(
            LightComponent(
                type = type,
                color = light.color.copy(),
                intensity = light.intensity,
                direction = normalizedOrFallback(light.direction, DefaultLightDirection),
            ),
        )
        markSceneChanged("Updated ${entity.name} light type.")
        context.logger.info(TAG) { "Updated light type entityId=$entityId type=${type.name}" }
    }

    fun setLightIntensity(
        entityId: EntityId,
        intensity: Float,
    ) {
        val entity = lightEntity(entityId) ?: return
        val light = entity.get<LightComponent>() ?: return
        if (!intensity.isFinite() || intensity < 0f) {
            rejectLightEdit(entityId, "Light intensity must be finite and greater than or equal to 0.")
            return
        }
        if (light.intensity == intensity) return

        light.intensity = intensity
        markSceneChanged("Updated ${entity.name} light intensity.")
        context.logger.debug(TAG) { "Updated light intensity entityId=$entityId value=$intensity" }
    }

    fun setLightColor(
        entityId: EntityId,
        color: Color,
    ) {
        val entity = lightEntity(entityId) ?: return
        val light = entity.get<LightComponent>() ?: return
        if (!color.isFinite()) {
            rejectLightEdit(entityId, "Light color channels must be finite.")
            return
        }

        val clamped = color.clamped()
        if (light.color == clamped) return

        light.color.r = clamped.r
        light.color.g = clamped.g
        light.color.b = clamped.b
        light.color.a = clamped.a
        markSceneChanged("Updated ${entity.name} light color.")
        context.logger.debug(TAG) {
            "Updated light color entityId=$entityId value=${clamped.r},${clamped.g},${clamped.b},${clamped.a}"
        }
    }

    fun setLightDirection(
        entityId: EntityId,
        direction: Vec3,
    ) {
        val entity = lightEntity(entityId) ?: return
        val light = entity.get<LightComponent>() ?: return
        if (light.type != LightType.Directional) {
            rejectLightEdit(entityId, "Only Directional lights use a direction vector.")
            return
        }

        val normalized = normalizedOrNull(direction)
        if (normalized == null) {
            rejectLightEdit(entityId, "Light direction must be finite and non-zero.")
            return
        }
        if (light.direction == normalized) return

        light.direction.set(normalized.x, normalized.y, normalized.z)
        markSceneChanged("Updated ${entity.name} light direction.")
        context.logger.debug(TAG) {
            "Updated light direction entityId=$entityId value=${normalized.x},${normalized.y},${normalized.z}"
        }
    }

    fun alignLightDirectionToView(entityId: EntityId) {
        val entity = lightEntity(entityId) ?: return
        val light = entity.get<LightComponent>() ?: return
        if (light.type != LightType.Directional) {
            rejectLightEdit(entityId, "Only Directional lights can align direction to the view.")
            return
        }

        val direction = normalizedOrFallback(cameraForward(), DefaultLightDirection)
        if (light.direction == direction) return

        light.direction.set(direction.x, direction.y, direction.z)
        markSceneChanged("Aligned ${entity.name} direction to editor view.")
        context.logger.info(TAG) { "Aligned light direction to view entityId=$entityId name='${entity.name}'" }
    }

    fun setAmbientLightColor(color: Color) {
        if (!color.isFinite()) {
            state.statusMessage = "Ambient light color channels must be finite."
            context.logger.warn(TAG) { "Rejected ambient light color edit because color was not finite" }
            return
        }
        val clamped = color.clamped()
        val current =
            document.descriptor
                ?.settings
                ?.lighting
                ?.ambientColor ?: defaultAmbientLightColor()
        if (current == clamped) return

        updateSceneSettings { settings ->
            settings.copy(
                lighting = settings.lighting.copy(ambientColor = clamped),
            )
        }
        markSceneChanged("Updated ambient light color.")
        context.logger.debug(TAG) {
            "Updated ambient light color value=${clamped.r},${clamped.g},${clamped.b},${clamped.a}"
        }
    }

    fun setAmbientLightIntensity(intensity: Float) {
        if (!intensity.isFinite() || intensity < 0f) {
            state.statusMessage = "Ambient light intensity must be finite and greater than or equal to 0."
            context.logger.warn(TAG) { "Rejected ambient light intensity edit value=$intensity" }
            return
        }
        val current =
            document.descriptor
                ?.settings
                ?.lighting
                ?.ambientIntensity ?: DefaultAmbientLightIntensity
        if (current == intensity) return

        updateSceneSettings { settings ->
            settings.copy(
                lighting = settings.lighting.copy(ambientIntensity = intensity),
            )
        }
        markSceneChanged("Updated ambient light intensity.")
        context.logger.debug(TAG) { "Updated ambient light intensity value=$intensity" }
    }

    fun setSkyboxAsset(path: String?) {
        val normalizedPath = normalizeOptionalAssetPath(path)
        val currentPath =
            document.descriptor
                ?.settings
                ?.environment
                ?.skyboxAssetPath
        if (currentPath == normalizedPath) return

        updateSceneSettings { settings ->
            settings.copy(
                environment = settings.environment.copy(skyboxAssetPath = normalizedPath),
            )
        }
        markSceneChanged(
            if (normalizedPath == null) {
                "Cleared scene skybox."
            } else {
                "Scene skybox set to $normalizedPath"
            },
        )
        context.logger.info(TAG) { "Updated scene skybox asset path='${normalizedPath ?: "<none>"}'" }
    }

    fun setSkyboxVisible(visible: Boolean) {
        val current =
            document.descriptor
                ?.settings
                ?.environment
                ?.showSkybox ?: SceneEnvironmentDescriptor().showSkybox
        if (current == visible) return

        updateSceneSettings { settings ->
            settings.copy(
                environment = settings.environment.copy(showSkybox = visible),
            )
        }
        markSceneChanged(if (visible) "Scene skybox enabled." else "Scene skybox hidden.")
        context.logger.info(TAG) { "Updated scene skybox visibility visible=$visible" }
    }

    fun setEnvironmentIntensity(intensity: Float) {
        if (!intensity.isFinite() || intensity < 0f) {
            state.statusMessage = "Environment intensity must be finite and greater than or equal to 0."
            context.logger.warn(TAG) { "Rejected environment intensity edit value=$intensity" }
            return
        }
        val current =
            document.descriptor
                ?.settings
                ?.environment
                ?.environmentIntensity
                ?: SceneEnvironmentDescriptor().environmentIntensity
        if (current == intensity) return

        updateSceneSettings { settings ->
            settings.copy(
                environment = settings.environment.copy(environmentIntensity = intensity),
            )
        }
        markSceneChanged("Updated environment intensity.")
        context.logger.debug(TAG) { "Updated environment intensity value=$intensity" }
    }

    fun setCameraFov(
        entityId: EntityId,
        fov: Float,
    ) {
        val entity = cameraEntity(entityId) ?: return
        val camera = entity.get<PerspectiveCameraComponent>() ?: return
        if (!fov.isFinite()) {
            rejectCameraEdit(entityId, "Camera FOV must be finite.")
            return
        }

        val clampedFov = fov.coerceIn(MinCameraFovDegrees, MaxCameraFovDegrees)
        if (camera.fieldOfViewDegrees == clampedFov) return

        camera.fieldOfViewDegrees = clampedFov
        markSceneChanged("Updated ${entity.name} camera FOV.")
        context.logger.debug(TAG) { "Updated camera FOV entityId=$entityId value=$clampedFov" }
    }

    fun setCameraNear(
        entityId: EntityId,
        near: Float,
    ) {
        val entity = cameraEntity(entityId) ?: return
        val camera = entity.get<PerspectiveCameraComponent>() ?: return
        if (!near.isFinite() || near <= MinCameraNear) {
            rejectCameraEdit(entityId, "Camera near must be greater than $MinCameraNear.")
            return
        }
        if (camera.far <= near + CameraPlaneEpsilon) {
            rejectCameraEdit(entityId, "Camera near must be less than far.")
            return
        }
        if (camera.near == near) return

        camera.near = near
        markSceneChanged("Updated ${entity.name} camera near plane.")
        context.logger.debug(TAG) { "Updated camera near entityId=$entityId value=$near" }
    }

    fun setCameraFar(
        entityId: EntityId,
        far: Float,
    ) {
        val entity = cameraEntity(entityId) ?: return
        val camera = entity.get<PerspectiveCameraComponent>() ?: return
        if (!far.isFinite() || far <= camera.near + CameraPlaneEpsilon) {
            rejectCameraEdit(entityId, "Camera far must be greater than near.")
            return
        }
        if (camera.far == far) return

        camera.far = far
        markSceneChanged("Updated ${entity.name} camera far plane.")
        context.logger.debug(TAG) { "Updated camera far entityId=$entityId value=$far" }
    }

    fun renameEntity(
        entityId: EntityId,
        newName: String,
    ) {
        val entity = editableEntity(entityId) ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            state.statusMessage = "Rename failed: entity name cannot be blank."
            context.logger.warn(TAG) { "Rejected blank rename for entity id=$entityId" }
            return
        }
        if (entity.name == trimmedName) return

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

    fun setEntityName(
        entityId: EntityId,
        name: String,
    ) {
        renameEntity(entityId, name)
    }

    fun setEntityActive(
        entityId: EntityId,
        active: Boolean,
    ) {
        val entity = editableEntity(entityId) ?: return
        if (entity.active == active) return

        entity.active = active
        markSceneChanged("${entity.name} is now ${if (active) "active" else "inactive"}.")
        context.logger.info(TAG) { "Set entity active id=$entityId active=$active" }
    }

    fun setTerrainVisible(
        entityId: EntityId,
        visible: Boolean,
    ) {
        val entity = editableEntity(entityId) ?: return
        val terrain = entity.get<TerrainComponent>() ?: return
        if (terrain.visible == visible) return

        terrain.visible = visible
        markSceneChanged("Updated ${entity.name} terrain visibility.")
        context.logger.info(TAG) { "Set terrain visible entityId=$entityId visible=$visible" }
    }

    fun setTerrainPreviewMode(
        entityId: EntityId,
        mode: TerrainPreviewMode,
    ) {
        val entity = terrainEntity(entityId) ?: return
        val terrain = entity.get<TerrainComponent>() ?: return
        val supportedMode =
            when (mode) {
                TerrainPreviewMode.MaterialTexture -> TerrainPreviewMode.MaterialTexture
                else -> TerrainPreviewMode.LayerColor
            }
        if (terrain.previewMode == supportedMode) return

        terrain.previewMode = supportedMode
        markSceneChanged("Updated ${entity.name} terrain preview mode.")
        context.logger.info(TAG) { "Set terrain preview mode entityId=$entityId mode=$supportedMode" }
    }

    fun setTerrainBakedTextureResolution(
        entityId: EntityId,
        resolution: Int,
    ) {
        val entity = terrainEntity(entityId) ?: return
        val terrain = entity.get<TerrainComponent>() ?: return
        val clamped = resolution.coerceIn(2, MaxTerrainBakedTextureResolution)
        if (terrain.bakedTextureResolution == clamped) return

        terrain.bakedTextureResolution = clamped
        markSceneChanged("Updated ${entity.name} baked texture resolution.")
        context.logger.info(TAG) { "Set terrain baked texture resolution entityId=$entityId resolution=$clamped" }
    }

    fun openTerrainInEditor(path: String) {
        val normalizedPath = normalizeAssetPath(path)
        if (normalizedPath.isBlank()) {
            state.statusMessage = "Open Terrain Editor failed: terrain path cannot be blank."
            context.logger.warn(TAG) { "Rejected Terrain Editor launch with blank path" }
            return
        }

        try {
            context.editorToolLauncher.launchTerrainEditor(normalizedPath)
            state.statusMessage = "Opened in Terrain Editor: $normalizedPath"
            context.logger.info(TAG) { "Opened terrain '$normalizedPath' in Terrain Editor" }
        } catch (error: Exception) {
            state.statusMessage = "Failed to open Terrain Editor: ${error.message}"
            context.logger.error(
                TAG,
                error,
            ) { "Failed to open terrain '$normalizedPath' in Terrain Editor: ${error.message}" }
        }
    }

    fun setTransformPosition(
        entityId: EntityId,
        position: Vec3,
    ) {
        val entity = editableEntity(entityId) ?: return
        val transform = entity.get<TransformComponent>() ?: return transformMissing(entityId)
        if (transform.position == position) return

        transform.position.set(position.x, position.y, position.z)
        markSceneChanged("Updated ${entity.name} position.")
        context.logger.debug(TAG) { "Updated transform position entityId=$entityId value=$position" }
    }

    fun setTransformRotation(
        entityId: EntityId,
        eulerDegrees: Vec3,
    ) {
        val entity = editableEntity(entityId) ?: return
        val transform = entity.get<TransformComponent>() ?: return transformMissing(entityId)
        if (transform.eulerDegrees == eulerDegrees) return

        transform.eulerDegrees.set(eulerDegrees.x, eulerDegrees.y, eulerDegrees.z)
        markSceneChanged("Updated ${entity.name} rotation.")
        context.logger.debug(TAG) { "Updated transform rotation entityId=$entityId value=$eulerDegrees" }
    }

    fun setTransformScale(
        entityId: EntityId,
        scale: Vec3,
    ) {
        val entity = editableEntity(entityId) ?: return
        val transform = entity.get<TransformComponent>() ?: return transformMissing(entityId)
        if (transform.scale == scale) return

        transform.scale.set(scale.x, scale.y, scale.z)
        markSceneChanged("Updated ${entity.name} scale.")
        context.logger.debug(TAG) { "Updated transform scale entityId=$entityId value=$scale" }
    }

    fun addTransformComponent(entityId: EntityId) {
        val entity = editableEntity(entityId) ?: return
        if (entity.get<TransformComponent>() != null) {
            state.statusMessage = "${entity.name} already has a TransformComponent."
            return
        }

        entity.add(TransformComponent())
        markSceneChanged("Added TransformComponent to ${entity.name}.")
        context.logger.info(TAG) { "Added TransformComponent entityId=$entityId" }
    }

    fun removeTransformComponent(entityId: EntityId) {
        val entity = editableEntity(entityId) ?: return
        if (entity.get<TransformComponent>() == null) {
            state.statusMessage = "${entity.name} does not have a TransformComponent."
            return
        }
        if (requiresTransform(entity)) {
            state.statusMessage = "TransformComponent is required by ${entity.name}."
            context.logger.warn(TAG) { "Rejected TransformComponent removal for required entityId=$entityId" }
            return
        }

        entity.remove(TransformComponent::class)
        markSceneChanged("Removed TransformComponent from ${entity.name}.")
        context.logger.info(TAG) { "Removed TransformComponent entityId=$entityId" }
    }

    fun deleteEntity(entityId: EntityId) {
        val entity = editableEntity(entityId) ?: return
        val detachedChildren = detachChildren(entity.id)
        val wasActiveTerrain = document.descriptor?.settings?.activeTerrainEntityId == entity.id
        document.world.removeEntity(entity.id)
        if (state.selectedEntityId == entity.id) {
            state.selectedEntityId = null
        }
        if (wasActiveTerrain) {
            updateActiveTerrainSetting(null)
        }
        val childMessage = if (detachedChildren == 0) "" else " Detached $detachedChildren child entity links."
        markSceneChanged("Deleted ${entity.name}.$childMessage")
        context.logger.info(TAG) {
            "Deleted entity id=$entityId name='${entity.name}' detachedChildren=$detachedChildren wasActiveTerrain=$wasActiveTerrain"
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
            applyValidation(descriptor, updateStatusMessage = false)
            state.currentScenePath = normalizedPath
            state.sceneName = descriptor.name
            state.selectedEntityId =
                descriptor.settings.activeCameraEntityId
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

    fun playInNewWindow() {
        context.logger.info(TAG) {
            "Play requested currentPath='${state.currentScenePath ?: "<none>"}' dirty=${state.hasUnsavedChanges}"
        }
        val path = state.currentScenePath
        if (path.isNullOrBlank()) {
            requestSaveAs()
            state.statusMessage = "Save scene before Play."
            context.logger.warn(TAG) { "Play blocked because the scene has not been saved." }
            return
        }

        if (state.hasUnsavedChanges) {
            context.logger.info(TAG) { "Auto-save before play path='$path'" }
            if (!saveToPath(path)) {
                context.logger.warn(TAG) { "Play blocked because auto-save failed path='$path'" }
                return
            }
        }

        try {
            context.logger.info(TAG) { "Launching runtime scene path='${state.currentScenePath ?: path}'" }
            context.runtimeLauncher.launchRuntimeScene(state.currentScenePath ?: path)
            state.statusMessage = "Playing scene: ${state.currentScenePath ?: path}"
        } catch (error: Exception) {
            state.statusMessage = "Play failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Launch failure path='${state.currentScenePath ?: path}': ${error.message}"
            }
        }
    }

    fun readSceneText(path: String): String = context.sceneFiles.readText(path)

    fun validateScene(): SceneValidationReport = refreshValidation(updateStatusMessage = true)

    private fun saveToPath(rawPath: String): Boolean =
        try {
            val path = ScenePathUtils.normalizeScenePath(rawPath)
            context.logger.info(TAG) { "Saving scene path='$path'" }
            val descriptor =
                SceneSerializer.toDescriptor(
                    world = document.world,
                    sceneName = state.sceneName,
                    existingDescriptor = document.descriptor,
                    includeEntity = { entity -> entity.get<EditorOnlyComponent>() == null },
                )
            context.logger.info(TAG) {
                "Scene descriptor prepared id='${descriptor.id}' entities=${descriptor.entities.size}"
            }
            val encoded = SceneSerializer.encode(descriptor)
            context.sceneFiles.ensureDirectories(path)
            context.sceneFiles.writeText(path, encoded)

            document.descriptor = descriptor
            applyValidation(descriptor, updateStatusMessage = false)
            state.currentScenePath = path
            state.saveAsPath = path
            state.saveAsRequested = false
            state.saveErrorMessage = null
            state.hasUnsavedChanges = false
            state.statusMessage = "Scene saved: $path"
            context.logger.info(TAG) { "Scene saved path='$path' id='${descriptor.id}' entities=${descriptor.entities.size}" }
            true
        } catch (error: Exception) {
            state.saveErrorMessage = error.message
            state.statusMessage = "Save failed: ${error.message}"
            context.logger.error(TAG, error) { "Failed to save scene path='$rawPath': ${error.message}" }
            false
        }

    private fun defaultSavePath(): String = "scenes/${sanitizeSceneName(state.sceneName)}.krscene"

    private fun currentSerializableDescriptor(): SceneDescriptor =
        SceneSerializer.toDescriptor(
            world = document.world,
            sceneName = state.sceneName,
            existingDescriptor = document.descriptor,
            includeEntity = { entity -> entity.get<EditorOnlyComponent>() == null },
        )

    private fun refreshValidation(updateStatusMessage: Boolean): SceneValidationReport {
        val descriptor = currentSerializableDescriptor()
        document.descriptor = descriptor
        return applyValidation(descriptor, updateStatusMessage)
    }

    private fun applyValidation(
        descriptor: SceneDescriptor,
        updateStatusMessage: Boolean,
    ): SceneValidationReport {
        val resolvedSkybox =
            RuntimeSceneValidator
                .skyboxPath(descriptor)
                ?.let { path ->
                    runCatching {
                        SkyboxAssetService(
                            context.sceneFiles,
                            context.logger,
                        ).loadRequired(path)
                    }.getOrNull()
                }
        val dependencyGraph = SceneDependencyCollector(context.sceneFiles).collect(descriptor, resolvedSkybox)
        val report = RuntimeSceneValidator.validate(descriptor, dependencyGraph)
        state.validationReport = report
        state.validationDirty = false
        if (updateStatusMessage) {
            state.statusMessage =
                "Scene validation completed: ${report.errors.size} errors, ${report.warnings.size} warnings."
        }
        context.logger.info(TAG) {
            "Scene validation completed scene='${descriptor.name}' errors=${report.errors.size} warnings=${report.warnings.size} dependencies=${dependencyGraph.dependencies.size} missing=${dependencyGraph.missing.size}"
        }
        return report
    }

    private fun generateSceneId(): String = "scene:${UUID.randomUUID()}"

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

    private fun transformMissing(entityId: EntityId) {
        state.statusMessage = "Entity has no TransformComponent."
        context.logger.warn(TAG) { "Scene transform edit ignored because entity id=$entityId has no TransformComponent" }
    }

    private fun cameraEntity(entityId: EntityId): Entity? {
        val entity = editableEntity(entityId) ?: return null
        if (entity.get<PerspectiveCameraComponent>() == null) {
            state.statusMessage = "Entity has no PerspectiveCameraComponent."
            context.logger.warn(TAG) { "Scene camera operation ignored because entity id=$entityId has no PerspectiveCameraComponent" }
            return null
        }
        return entity
    }

    private fun lightEntity(entityId: EntityId): Entity? {
        val entity = editableEntity(entityId) ?: return null
        val light = entity.get<LightComponent>()
        if (light == null) {
            state.statusMessage = "Entity has no LightComponent."
            context.logger.warn(TAG) { "Scene light operation ignored because entity id=$entityId has no LightComponent" }
            return null
        }
        if (light.type != LightType.Directional && light.type != LightType.Point) {
            state.statusMessage = "Only Directional and Point lights are supported in the editor."
            context.logger.warn(
                TAG,
            ) { "Scene light operation ignored because entity id=$entityId has unsupported light type ${light.type}" }
            return null
        }
        return entity
    }

    private fun rejectCameraEdit(
        entityId: EntityId,
        message: String,
    ) {
        state.statusMessage = message
        context.logger.warn(TAG) { "Rejected camera edit entityId=$entityId: $message" }
    }

    private fun rejectLightEdit(
        entityId: EntityId,
        message: String,
    ) {
        state.statusMessage = message
        context.logger.warn(TAG) { "Rejected light edit entityId=$entityId: $message" }
    }

    private fun updateActiveCameraSetting(entityId: EntityId) {
        updateSceneSettings { settings ->
            settings.copy(activeCameraEntityId = entityId)
        }
    }

    private fun updateActiveTerrainSetting(entityId: EntityId?) {
        updateSceneSettings { settings ->
            settings.copy(activeTerrainEntityId = entityId)
        }
    }

    private fun updateSceneSettings(update: (SceneSettingsDescriptor) -> SceneSettingsDescriptor) {
        val descriptor = document.descriptor
        document.descriptor =
            if (descriptor == null) {
                SceneDescriptor(
                    id = generateSceneId(),
                    name = state.sceneName,
                    entities = emptyList(),
                    settings = update(SceneSettingsDescriptor()),
                )
            } else {
                descriptor.copy(settings = update(descriptor.settings))
            }
    }

    private fun activeCameraExists(): Boolean {
        val activeCameraEntityId = document.descriptor?.settings?.activeCameraEntityId ?: return false
        return document.world
            .getEntity(activeCameraEntityId)
            ?.takeUnless { entity -> entity.get<EditorOnlyComponent>() != null }
            ?.get<PerspectiveCameraComponent>() != null
    }

    private fun activeTerrainExists(): Boolean {
        val activeTerrainEntityId = document.descriptor?.settings?.activeTerrainEntityId ?: return false
        return document.world
            .getEntity(activeTerrainEntityId)
            ?.takeUnless { entity -> entity.get<EditorOnlyComponent>() != null }
            ?.get<TerrainComponent>() != null
    }

    private fun terrainEntity(entityId: EntityId): Entity? {
        val entity = editableEntity(entityId) ?: return null
        if (entity.get<TerrainComponent>() == null) {
            state.statusMessage = "Entity has no TerrainComponent."
            context.logger.warn(TAG) { "Scene terrain operation ignored because entity id=$entityId has no TerrainComponent" }
            return null
        }
        return entity
    }

    private fun requiresTransform(entity: Entity): Boolean =
        entity.get<PerspectiveCameraComponent>() != null ||
            entity.get<LightComponent>() != null ||
            entity.get<ModelComponent>() != null ||
            entity.get<TerrainComponent>() != null

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

    private fun duplicateSupportedComponents(
        source: Entity,
        duplicate: Entity,
    ) {
        // NameComponent is not duplicated explicitly because createEntity already sets it.
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
        source.get<ModelComponent>()?.let { component ->
            duplicate.add(ModelComponent(model = AssetRef.model(component.model.path)))
        }
        source.get<TerrainComponent>()?.let { component ->
            duplicate.add(
                TerrainComponent(
                    terrain = AssetRef.terrain(component.terrain.path),
                    visible = component.visible,
                    previewMode = component.previewMode,
                    bakedTextureResolution = component.bakedTextureResolution,
                ),
            )
        }
    }

    private fun normalizeModelPath(path: String): String = normalizeAssetPath(path)

    private fun normalizeAssetPath(path: String): String = path.trim().replace('\\', '/')

    private fun normalizeOptionalAssetPath(path: String?): String? =
        path
            ?.trim()
            ?.replace('\\', '/')
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { value -> value.equals("null", ignoreCase = true) }

    private fun modelEntityName(path: String): String {
        val fileName = path.substringAfterLast('/').ifBlank { "Model" }
        val baseName = fileName.substringBeforeLast('.', fileName)
        val words =
            baseName
                .replace(Regex("[_\\-]+"), " ")
                .trim()
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)
        if (words.isEmpty()) return "Model"
        return words.joinToString(" ") { word -> word.first().uppercaseChar().toString() + word.drop(1) }
    }

    private fun terrainEntityName(path: String): String {
        val name = modelEntityName(path)
        return if (name == "Model") "Terrain" else name
    }

    private fun uniqueLightName(baseName: String): String {
        val existingNames =
            document.world
                .all()
                .filter { entity -> entity.get<EditorOnlyComponent>() == null }
                .map(Entity::name)
                .toSet()
        if (baseName !in existingNames) return baseName
        var index = 2
        while ("$baseName $index" in existingNames) {
            index += 1
        }
        return "$baseName $index"
    }

    private fun uniqueCameraName(): String {
        val existingNames =
            document.world
                .all()
                .filter { entity -> entity.get<EditorOnlyComponent>() == null }
                .map(Entity::name)
                .toSet()
        if ("Camera" !in existingNames) return "Camera"
        var index = 2
        while ("Camera $index" in existingNames) {
            index += 1
        }
        return "Camera $index"
    }

    private fun placementPositionInFrontOfCamera(distance: Float = state.placeModelDistance.coerceAtLeast(0f)): Vec3 {
        val origin = state.camera.position
        return origin + cameraForward() * distance
    }

    private fun cameraForward(): Vec3 {
        val rotation = state.camera.eulerDegrees
        val pitch = Math.toRadians(rotation.x.toDouble())
        val yaw = Math.toRadians(rotation.y.toDouble())
        return Vec3(
            x = (sin(yaw) * cos(pitch)).toFloat(),
            y = sin(pitch).toFloat(),
            z = (cos(yaw) * cos(pitch)).toFloat(),
        )
    }

    private fun normalizedOrFallback(
        vector: Vec3,
        fallback: Vec3,
    ): Vec3 = normalizedOrNull(vector) ?: fallback.copy()

    private fun normalizedOrNull(vector: Vec3): Vec3? {
        if (!vector.isFinite()) return null
        val length = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)
        if (length <= 1e-6f) return null
        return Vec3(vector.x / length, vector.y / length, vector.z / length)
    }

    private fun Color.clamped(): Color =
        Color(
            r = r.coerceIn(0f, 1f),
            g = g.coerceIn(0f, 1f),
            b = b.coerceIn(0f, 1f),
            a = a.coerceIn(0f, 1f),
        )

    private fun Color.isFinite(): Boolean = r.isFinite() && g.isFinite() && b.isFinite() && a.isFinite()

    private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    private fun TransformComponent.cloneForSceneEntity(): TransformComponent =
        TransformComponent(
            position = position.copy(),
            rotation = rotation.copy(),
            eulerDegrees = eulerDegrees.copy(),
            scale = scale.copy(),
        )

    private fun markSceneChanged(message: String) {
        state.hasUnsavedChanges = true
        state.validationDirty = true
        state.statusMessage = message
    }

    companion object {
        private const val TAG = "SceneEditorOperations"
        private const val MinCameraFovDegrees = 1f
        private const val MaxCameraFovDegrees = 160f
        private const val MinCameraNear = 0.001f
        private const val MaxTerrainBakedTextureResolution = 8192
        private const val CameraPlaneEpsilon = 0.001f
        private val DefaultDirectionalLightColor = Color(1f, 0.96f, 0.88f, 1f)
        private const val DefaultDirectionalLightIntensity = 1.2f
        private const val DefaultPointLightIntensity = 10f
        private const val DefaultPointLightPlacementDistance = 3f
        private val DefaultLightDirection = Vec3(-0.45f, -0.8f, -0.35f)
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

        document.descriptor =
            SceneDescriptor(
                id = generateSceneId(),
                name = sceneName,
                entities = emptyList(),
                settings =
                    SceneSettingsDescriptor(
                        activeCameraEntityId = camera.id,
                        activeTerrainEntityId = null,
                        lighting =
                            SceneLightingDescriptor(
                                ambientColor = defaultAmbientLightColor(),
                                ambientIntensity = DefaultAmbientLightIntensity,
                            ),
                        environment =
                            SceneEnvironmentDescriptor(
                                skyboxAssetPath = DefaultSceneSkyboxAssetPath,
                                showSkybox = true,
                                environmentIntensity = 1f,
                            ),
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

    private const val DefaultSceneSkyboxAssetPath = "skyboxes/default_skybox_studio.krskybox"
}
