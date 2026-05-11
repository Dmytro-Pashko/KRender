package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.DefaultAmbientLightIntensity
import com.pashkd.krender.engine.scene.defaultAmbientLightColor
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainPreviewMode
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import com.pashkd.krender.engine.ui.beginImGuiPanel
import glm_.vec2.Vec2 as ImVec2
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider
import imgui.api.colorEdit4
import imgui.dsl
import java.nio.charset.StandardCharsets

/**
 * Top-level scene editor action/status panel.
 */
class SceneEditorToolbarPanel(
    private val state: SceneEditorState,
    private val operations: SceneEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val saveAsPathBuffer = ByteArray(TextInputBufferSize)
    private val openPathBuffer = ByteArray(TextInputBufferSize)
    private var saveAsBufferSynced = false
    private var openBufferSynced = false

    override fun draw() {
        val expanded = beginSceneEditorPanel(SceneEditorPanelIds.Toolbar, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        with(dsl) {
            button("New##scene_editor_new") {
                operations.createNewScene()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Open##scene_editor_open") {
                operations.requestOpen()
                openBufferSynced = false
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Save##scene_editor_save") {
                operations.requestSave()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Save As##scene_editor_save_as") {
                operations.requestSaveAs()
                saveAsBufferSynced = false
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Play##scene_editor_play") {
                operations.playInNewWindow()
                saveAsBufferSynced = false
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Save UI Position##scene_editor_save_ui_position") {
                operations.saveUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Restore UI##scene_editor_restore_ui") {
                operations.restoreUiLayout()
            }
        }

        ImGui.separator()
        ImGui.text("Scene: ${state.sceneName}")
        ImGui.sameLine()
        ImGui.text("Path: ${state.currentScenePath ?: "<memory>"}")
        ImGui.sameLine()
        ImGui.text("Dirty: ${if (state.hasUnsavedChanges) "yes" else "no"}")
        ImGui.sameLine()
        ImGui.text("Camera speed: ${"%.2f".format(state.camera.speed)}")
        ImGui.text("Status: ${state.statusMessage}")

        ImGui.end()
        drawOpenDialog()
        drawSaveAsDialog()
    }

    private fun drawOpenDialog() {
        if (!state.openRequested) return
        if (!openBufferSynced) {
            writeBuffer(openPathBuffer, state.openPath.ifBlank { DefaultScenePath })
            state.openPath = readBuffer(openPathBuffer)
            openBufferSynced = true
        }
        ImGui.openPopup("Open Scene##scene_editor_open_popup")
        if (!ImGui.beginPopupModal("Open Scene##scene_editor_open_popup")) return

        ImGui.text("Path")
        ImGui.sameLine()
        if (safeInputText("##scene_editor_open_path", openPathBuffer)) {
            state.openPath = readBuffer(openPathBuffer)
        }
        state.openErrorMessage?.let { error ->
            ImGui.text("Last error: $error")
        }

        ImGui.separator()
        with(dsl) {
            button("Open##scene_editor_open_ok") {
                operations.open(state.openPath)
                if (!state.openRequested) {
                    openBufferSynced = false
                    ImGui.closeCurrentPopup()
                }
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##scene_editor_open_cancel") {
                state.openRequested = false
                state.openErrorMessage = null
                openBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    private fun drawSaveAsDialog() {
        if (!state.saveAsRequested) return
        if (!saveAsBufferSynced) {
            writeBuffer(saveAsPathBuffer, state.saveAsPath)
            saveAsBufferSynced = true
        }
        ImGui.openPopup("Save Scene As##scene_editor_save_as_popup")
        if (!ImGui.beginPopupModal("Save Scene As##scene_editor_save_as_popup")) return

        ImGui.text("Path")
        ImGui.sameLine()
        if (safeInputText("##scene_editor_save_as_path", saveAsPathBuffer)) {
            state.saveAsPath = readBuffer(saveAsPathBuffer)
        }
        state.saveErrorMessage?.let { error ->
            ImGui.text("Last error: $error")
        }

        ImGui.separator()
        with(dsl) {
            button("Save##scene_editor_save_as_ok") {
                operations.saveAs(state.saveAsPath)
                if (!state.saveAsRequested) {
                    saveAsBufferSynced = false
                    ImGui.closeCurrentPopup()
                }
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##scene_editor_save_as_cancel") {
                state.saveAsRequested = false
                saveAsBufferSynced = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    companion object {
        private const val TextInputBufferSize = 256
        private const val DefaultScenePath = "scenes/Untitled_Scene.krscene"
    }
}

/**
 * Lists entities from the edited world and controls the active selection.
 */
class SceneHierarchyPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val operations: SceneEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
    private val logger: Logger,
) : UiPanel {
    private var lastFilteredEditorOnlyIds: Set<EntityId> = emptySet()
    private val renameBuffer = ByteArray(RenameInputBufferSize)
    private var renameRequested = false

    override fun draw() {
        val expanded = beginSceneEditorPanel(SceneEditorPanelIds.Hierarchy, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        clearInvalidSelection()
        drawEntityToolbar()
        ImGui.separator()

        val entities = visibleSceneEntities()
        if (entities.isEmpty()) {
            ImGui.text("No entities.")
        } else {
            entities.forEach { entity ->
                val activeCameraMarker = if (entity.id == document.descriptor?.settings?.activeCameraEntityId) {
                    " [Active Camera]"
                } else {
                    ""
                }
                val activeTerrainMarker = if (entity.id == document.descriptor?.settings?.activeTerrainEntityId) {
                    " [Active Terrain]"
                } else {
                    ""
                }
                val activeMarker = if (entity.active) "" else " (inactive)"
                val label = "${entity.name}$activeCameraMarker$activeTerrainMarker$activeMarker##scene_entity_${entity.id}"
                if (ImGui.selectable(label, state.selectedEntityId == entity.id)) {
                    state.selectedEntityId = entity.id
                    state.statusMessage = "Selected ${entity.name}."
                }
            }
        }

        ImGui.end()
        drawRenameDialog()
    }

    private fun drawEntityToolbar() {
        with(dsl) {
            button("Create Empty##scene_hierarchy_create_empty") {
                operations.createEmptyEntity()
            }
        }
        with(dsl) {
            button("Create Camera##scene_hierarchy_create_camera") {
                operations.createCameraFromView()
            }
        }
        with(dsl) {
            button("Create Directional Light##scene_hierarchy_create_directional_light") {
                operations.createDirectionalLight()
            }
        }
        with(dsl) {
            button("Create Point Light##scene_hierarchy_create_point_light") {
                operations.createPointLight()
            }
        }
        with(dsl) {
            button("Rename##scene_hierarchy_rename") {
                val entity = selectedEditableEntity()
                if (entity == null) {
                    state.statusMessage = "Select an entity to rename."
                } else {
                    writeBuffer(renameBuffer, entity.name)
                    renameRequested = true
                }
            }
        }
        with(dsl) {
            button("Delete##scene_hierarchy_delete") {
                val entityId = state.selectedEntityId
                if (entityId == null || selectedEditableEntity() == null) {
                    state.statusMessage = "Select an entity to delete."
                } else {
                    operations.deleteEntity(entityId)
                }
            }
        }
        with(dsl) {
            button("Duplicate##scene_hierarchy_duplicate") {
                val entityId = state.selectedEntityId
                if (entityId == null || selectedEditableEntity() == null) {
                    state.statusMessage = "Select an entity to duplicate."
                } else {
                    operations.duplicateEntity(entityId)
                }
            }
        }
    }

    private fun drawRenameDialog() {
        if (!renameRequested) return
        ImGui.openPopup("Rename Entity##scene_hierarchy_rename_popup")
        if (!ImGui.beginPopupModal("Rename Entity##scene_hierarchy_rename_popup")) return

        ImGui.text("Name")
        ImGui.sameLine()
        safeInputText("##scene_hierarchy_rename_name", renameBuffer)

        ImGui.separator()
        with(dsl) {
            button("Rename##scene_hierarchy_rename_ok") {
                val entityId = state.selectedEntityId
                if (entityId == null || selectedEditableEntity() == null) {
                    state.statusMessage = "Select an entity to rename."
                } else {
                    operations.renameEntity(entityId, readBuffer(renameBuffer))
                    renameRequested = false
                    ImGui.closeCurrentPopup()
                }
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Cancel##scene_hierarchy_rename_cancel") {
                renameRequested = false
                ImGui.closeCurrentPopup()
            }
        }
        ImGui.endPopup()
    }

    private fun clearInvalidSelection() {
        val selectedId = state.selectedEntityId ?: return
        val entity = document.world.getEntity(selectedId)
        if (entity == null || entity.get<EditorOnlyComponent>() != null) {
            state.selectedEntityId = null
        }
    }

    private fun selectedEditableEntity(): Entity? {
        val selectedId = state.selectedEntityId ?: return null
        return document.world.getEntity(selectedId)
            ?.takeUnless { entity -> entity.get<EditorOnlyComponent>() != null }
    }

    private fun visibleSceneEntities(): List<Entity> {
        val allEntities = document.world.all()
        val filteredIds = allEntities
            .filter { entity -> entity.get<EditorOnlyComponent>() != null }
            .map(Entity::id)
            .toSet()
        if (filteredIds.isNotEmpty() && filteredIds != lastFilteredEditorOnlyIds) {
            logger.debug(TAG) { "Filtering editor-only entities from hierarchy: ${filteredIds.joinToString()}" }
        }
        lastFilteredEditorOnlyIds = filteredIds
        return allEntities.filter { entity -> entity.get<EditorOnlyComponent>() == null }
    }

    companion object {
        private const val TAG = "SceneHierarchyPanel"
        private const val RenameInputBufferSize = 128
    }
}

/**
 * Edits the selected entity's supported MVP fields and lists its current components.
 */
class SceneInspectorPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val operations: SceneEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
    private val logger: Logger,
) : UiPanel {
    private val nameBuffer = ByteArray(NameInputBufferSize)
    private var bufferedEntityId: EntityId? = null
    private var nameInputActive = false

    override fun draw() {
        val expanded = beginSceneEditorPanel(SceneEditorPanelIds.Inspector, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawSceneSettings()
        ImGui.separator()

        val entity = state.selectedEntityId?.let(document.world::getEntity)
        if (entity?.get<EditorOnlyComponent>() != null) {
            logger.debug(TAG) { "Clearing editor-only selection entityId=${entity.id}" }
            state.selectedEntityId = null
            ImGui.text("No entity selected.")
            ImGui.end()
            return
        }
        if (entity == null) {
            if (state.selectedEntityId != null) {
                logger.debug(TAG) { "Clearing missing selection entityId=${state.selectedEntityId}" }
                state.selectedEntityId = null
            }
            ImGui.text("No entity selected.")
            ImGui.end()
            return
        }

        syncNameBuffer(entity)
        drawEntity(entity)
        ImGui.end()
    }

    private fun drawEntity(entity: Entity) {
        ImGui.text("Name")
        ImGui.sameLine()
        if (safeInputText("##scene_inspector_entity_name", nameBuffer)) {
            operations.setEntityName(entity.id, readBuffer(nameBuffer))
        }
        nameInputActive = ImGui.isItemActive

        ImGui.text("Id: ${entity.id}")
        val active = booleanArrayOf(entity.active)
        if (ImGui.checkbox("Active##scene_inspector_entity_active", active)) {
            operations.setEntityActive(entity.id, active[0])
        }

        ImGui.separator()
        drawTransform(entity)

        entity.get<PerspectiveCameraComponent>()?.let { camera ->
            ImGui.separator()
            drawCamera(entity, camera)
        }

        entity.get<LightComponent>()?.let { light ->
            ImGui.separator()
            drawLight(entity, light)
        }

        entity.get<ModelComponent>()?.let { model ->
            ImGui.separator()
            ImGui.text("Model")
            ImGui.text("Path: ${model.model.path}")
        }

        entity.get<TerrainComponent>()?.let { terrain ->
            ImGui.separator()
            ImGui.text("Terrain")
            ImGui.text("Path: ${terrain.terrain.path}")
            val activeTerrain = booleanArrayOf(document.descriptor?.settings?.activeTerrainEntityId == entity.id)
            if (ImGui.checkbox("Active Terrain##scene_inspector_terrain_active", activeTerrain)) {
                if (activeTerrain[0]) {
                    operations.setActiveTerrain(entity.id)
                } else {
                    operations.clearActiveTerrain(entity.id)
                }
            }
            val visible = booleanArrayOf(terrain.visible)
            if (ImGui.checkbox("Visible##scene_inspector_terrain_visible", visible)) {
                operations.setTerrainVisible(entity.id, visible[0])
            }
            ImGui.text("Preview")
            terrainPreviewModeButton(entity, terrain, "Layer Color", TerrainPreviewMode.LayerColor)
            ImGui.sameLine()
            terrainPreviewModeButton(entity, terrain, "Texture Preview", TerrainPreviewMode.MaterialTexture)
            ImGui.text("Baked Texture Resolution")
            TerrainTextureResolutionOptions.forEachIndexed { index, resolution ->
                if (index > 0) {
                    ImGui.sameLine()
                }
                terrainResolutionButton(entity, terrain, resolution)
            }
            ImGui.text("Current: ${formatTextureResolution(terrain.bakedTextureResolution)}")
            with(dsl) {
                button("Open in Terrain Editor##scene_inspector_terrain_open") {
                    operations.openTerrainInEditor(terrain.terrain.path)
                }
            }
        }

        ImGui.separator()
        ImGui.text("Components")
        entity.components.all().forEach { component ->
            ImGui.bulletText(component::class.simpleName ?: component::class.toString())
        }
        drawComponentActions(entity)
    }

    private fun terrainPreviewModeButton(
        entity: Entity,
        terrain: TerrainComponent,
        label: String,
        mode: TerrainPreviewMode,
    ) {
        val selected = terrain.previewMode == mode
        ImGui.beginDisabled(selected)
        with(dsl) {
            button("$label##scene_inspector_terrain_preview_${mode.name}") {
                operations.setTerrainPreviewMode(entity.id, mode)
            }
        }
        ImGui.endDisabled()
    }

    private fun terrainResolutionButton(
        entity: Entity,
        terrain: TerrainComponent,
        resolution: Int,
    ) {
        val selected = terrain.bakedTextureResolution == resolution
        ImGui.beginDisabled(selected)
        with(dsl) {
            button("${formatTextureResolution(resolution)}##scene_inspector_terrain_baked_resolution_$resolution") {
                operations.setTerrainBakedTextureResolution(entity.id, resolution)
            }
        }
        ImGui.endDisabled()
    }

    private fun drawSceneSettings() {
        val settings = document.descriptor?.settings
        val ambientColor = settings?.ambientLightColor?.copy() ?: defaultAmbientLightColor()
        val ambientIntensity = settings?.ambientLightIntensity ?: DefaultAmbientLightIntensity

        ImGui.text("Scene Settings")
        val activeTerrain = settings?.activeTerrainEntityId?.let(document.world::getEntity)
        ImGui.text("Active Terrain: ${activeTerrain?.name ?: "<none>"}")
        ImGui.text("Lighting")
        if (
            colorEdit4(
                "Ambient Color##scene_inspector_ambient_color",
                ambientColor.r,
                ambientColor.g,
                ambientColor.b,
                ambientColor.a,
            ) { r, g, b, a ->
                ambientColorBuffer[0] = r
                ambientColorBuffer[1] = g
                ambientColorBuffer[2] = b
                ambientColorBuffer[3] = a
            }
        ) {
            operations.setAmbientLightColor(
                Color(
                    ambientColorBuffer[0],
                    ambientColorBuffer[1],
                    ambientColorBuffer[2],
                    ambientColorBuffer[3],
                ),
            )
        }

        ambientValueBuffer[0] = ambientIntensity
        if (
            ImGui.drag(
                "Ambient Intensity##scene_inspector_ambient_intensity",
                ambientValueBuffer,
                CameraPlaneDragSpeed,
                0f,
                0f,
                "%.2f",
                SliderFlag.AlwaysClamp,
            )
        ) {
            operations.setAmbientLightIntensity(ambientValueBuffer[0])
        }
    }

    private fun drawTransform(entity: Entity) {
        val transform = entity.get<TransformComponent>()
        ImGui.text("Transform")
        if (transform == null) {
            ImGui.text("No TransformComponent.")
            with(dsl) {
                button("Add Transform##scene_inspector_add_transform") {
                    operations.addTransformComponent(entity.id)
                }
            }
            return
        }

        if (drawVec3Editor("Position##scene_inspector_transform_position", transform.position)) {
            operations.setTransformPosition(entity.id, transformBuffer.toVec3())
        }
        if (drawVec3Editor("Rotation##scene_inspector_transform_rotation", transform.eulerDegrees)) {
            operations.setTransformRotation(entity.id, transformBuffer.toVec3())
        }
        if (drawVec3Editor("Scale##scene_inspector_transform_scale", transform.scale)) {
            operations.setTransformScale(entity.id, transformBuffer.toVec3())
        }
    }

    private fun drawCamera(entity: Entity, camera: PerspectiveCameraComponent) {
        ImGui.text("Camera")
        if (document.descriptor?.settings?.activeCameraEntityId == entity.id) {
            ImGui.text("Active Camera")
        } else {
            with(dsl) {
                button("Set Active Camera##scene_inspector_camera_set_active") {
                    operations.setActiveCamera(entity.id)
                }
            }
        }

        cameraValueBuffer[0] = camera.fieldOfViewDegrees
        if (ImGui.drag(
                "Field of View##scene_inspector_camera_fov",
                cameraValueBuffer,
                CameraDragSpeed,
                MinCameraFovDegrees,
                MaxCameraFovDegrees,
                "%.1f",
                SliderFlag.AlwaysClamp,
            )
        ) {
            operations.setCameraFov(entity.id, cameraValueBuffer[0])
        }

        cameraValueBuffer[0] = camera.near
        if (ImGui.drag("Near##scene_inspector_camera_near", cameraValueBuffer, CameraPlaneDragSpeed, 0f, 0f, "%.4f")) {
            operations.setCameraNear(entity.id, cameraValueBuffer[0])
        }

        cameraValueBuffer[0] = camera.far
        if (ImGui.drag("Far##scene_inspector_camera_far", cameraValueBuffer, CameraDragSpeed, 0f, 0f, "%.2f")) {
            operations.setCameraFar(entity.id, cameraValueBuffer[0])
        }

        with(dsl) {
            button("Align Camera to View##scene_inspector_camera_align_to_view") {
                operations.alignCameraToView(entity.id)
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Align View to Camera##scene_inspector_camera_align_view") {
                operations.alignViewToCamera(entity.id)
            }
        }
    }

    private fun drawLight(entity: Entity, light: LightComponent) {
        ImGui.text("Light")
        drawLightTypeSelector(entity, light)

        lightColorBuffer[0] = light.color.r
        lightColorBuffer[1] = light.color.g
        lightColorBuffer[2] = light.color.b
        lightColorBuffer[3] = light.color.a
        if (
            colorEdit4(
                "Color##scene_inspector_light_color",
                lightColorBuffer[0],
                lightColorBuffer[1],
                lightColorBuffer[2],
                lightColorBuffer[3],
            ) { r, g, b, a ->
                lightColorBuffer[0] = r
                lightColorBuffer[1] = g
                lightColorBuffer[2] = b
                lightColorBuffer[3] = a
            }
        ) {
            operations.setLightColor(
                entity.id,
                Color(lightColorBuffer[0], lightColorBuffer[1], lightColorBuffer[2], lightColorBuffer[3]),
            )
        }

        lightValueBuffer[0] = light.intensity
        if (
            ImGui.drag(
                "Intensity##scene_inspector_light_intensity",
                lightValueBuffer,
                CameraPlaneDragSpeed,
                0f,
                0f,
                "%.2f",
                SliderFlag.AlwaysClamp,
            )
        ) {
            operations.setLightIntensity(entity.id, lightValueBuffer[0])
        }

        when (light.type) {
            LightType.Directional -> {
                if (drawLightDirectionEditor(light.direction)) {
                    operations.setLightDirection(entity.id, lightVectorBuffer.toVec3())
                }
                with(dsl) {
                    button("Align Direction to View##scene_inspector_light_align_direction") {
                        operations.alignLightDirectionToView(entity.id)
                    }
                }
            }

            LightType.Point -> ImGui.text("Point Light position is controlled by Transform.")
            else -> ImGui.text("Unsupported light type is hidden in MVP.")
        }
    }

    private fun drawLightTypeSelector(entity: Entity, light: LightComponent) {
        if (!ImGui.beginCombo("Type##scene_inspector_light_type", light.type.name)) return

        listOf(LightType.Directional, LightType.Point).forEach { type ->
            if (ImGui.selectable(type.name, light.type == type)) {
                operations.setLightType(entity.id, type)
            }
        }
        ImGui.endCombo()
    }

    private fun drawComponentActions(entity: Entity) {
        val hasTransform = entity.get<TransformComponent>() != null
        if (!hasTransform) {
            with(dsl) {
                button("Add TransformComponent##scene_inspector_components_add_transform") {
                    operations.addTransformComponent(entity.id)
                }
            }
            return
        }

        val transformRequired = entity.get<PerspectiveCameraComponent>() != null ||
            entity.get<LightComponent>() != null ||
            entity.get<ModelComponent>() != null ||
            entity.get<TerrainComponent>() != null
        ImGui.beginDisabled(transformRequired)
        with(dsl) {
            button("Remove TransformComponent##scene_inspector_components_remove_transform") {
                operations.removeTransformComponent(entity.id)
            }
        }
        ImGui.endDisabled()
        if (transformRequired) {
            ImGui.text("TransformComponent is required by camera, light, model, or terrain components.")
        }
    }

    private fun drawVec3Editor(label: String, value: Vec3): Boolean {
        transformBuffer[0] = value.x
        transformBuffer[1] = value.y
        transformBuffer[2] = value.z
        return ImGui.drag3(label, transformBuffer, DragSpeed, 0f, 0f, "%.3f")
    }

    private fun drawLightDirectionEditor(value: Vec3): Boolean {
        lightVectorBuffer[0] = value.x
        lightVectorBuffer[1] = value.y
        lightVectorBuffer[2] = value.z
        return ImGui.drag3("Direction##scene_inspector_light_direction", lightVectorBuffer, DragSpeed, 0f, 0f, "%.3f")
    }

    private fun syncNameBuffer(entity: Entity) {
        if (bufferedEntityId != entity.id) {
            bufferedEntityId = entity.id
            writeBuffer(nameBuffer, entity.name)
            return
        }

        if (!nameInputActive && readBuffer(nameBuffer) != entity.name) {
            writeBuffer(nameBuffer, entity.name)
        }
    }

    private fun FloatArray.toVec3(): Vec3 = Vec3(this[0], this[1], this[2])

    private val transformBuffer = FloatArray(3)
    private val cameraValueBuffer = FloatArray(1)
    private val ambientColorBuffer = FloatArray(4)
    private val ambientValueBuffer = FloatArray(1)
    private val lightColorBuffer = FloatArray(4)
    private val lightValueBuffer = FloatArray(1)
    private val lightVectorBuffer = FloatArray(3)

    companion object {
        private const val TAG = "SceneInspectorPanel"
        private const val NameInputBufferSize = 128
        private const val DragSpeed = 0.05f
        private const val CameraDragSpeed = 0.1f
        private const val CameraPlaneDragSpeed = 0.01f
        private const val MinCameraFovDegrees = 1f
        private const val MaxCameraFovDegrees = 160f
        private val TerrainTextureResolutionOptions = intArrayOf(512, 1024, 2048, 4096, 8192)
    }
}

/**
 * Minimal viewport status panel. Rendering still goes through the engine renderer.
 */
class SceneViewportPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val operations: SceneEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginSceneEditorPanel(SceneEditorPanelIds.Viewport, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            state.viewportFocused = false
            state.viewport.focused = false
            ImGui.end()
            return
        }

        ImGui.beginChild("scene_editor_viewport_body", ImVec2(0f, 0f), true)
        val viewportPosition = ImGui.windowPos
        val viewportSize = ImGui.windowSize
        state.viewportOrigin = Vec2(viewportPosition.x, viewportPosition.y)
        state.viewportSize = Vec2(viewportSize.x.coerceAtLeast(0f), viewportSize.y.coerceAtLeast(0f))
        state.viewportFocused = ImGui.isWindowHovered() || ImGui.isWindowFocused()
        state.viewport.origin = state.viewportOrigin
        state.viewport.size = state.viewportSize
        state.viewport.focused = state.viewportFocused
        ImGui.text("Scene view")
        ImGui.separator()
        ImGui.text("Entities: ${document.world.all().count { entity -> entity.get<EditorOnlyComponent>() == null }}")
        ImGui.text("Selection: ${selectedEntityName()}")
        ImGui.text("Camera: ${formatPosition(state.camera.position)}")
        ImGui.text("Speed: ${"%.2f".format(state.camera.speed)}${if (state.camera.navigating) " (navigating)" else ""}")
        ImGui.text("RMB look, WASD move, Q/E down/up, wheel speed, Shift faster.")
        ImGui.separator()
        ImGui.text("Guides")
        ImGui.checkbox("Grid##scene_viewport_show_grid", state::showGrid)
        ImGui.sameLine()
        ImGui.checkbox("Axes##scene_viewport_show_axes", state::showAxes)
        ImGui.sameLine()
        ImGui.checkbox("Bounding Box##scene_viewport_show_bounding_box", state::showSelectedBoundingBox)
        slider(
            "Grid extent##scene_viewport_grid_extent",
            state::gridHalfExtentCells,
            MinGridHalfExtentCells,
            MaxGridHalfExtentCells,
            "%d",
            SliderFlag.AlwaysClamp,
        )
        slider(
            "Cell size##scene_viewport_grid_cell_size",
            state::gridCellSize,
            MinGridCellSize,
            MaxGridCellSize,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        ImGui.endChild()
        ImGui.end()
    }

    companion object {
        private const val MinGridHalfExtentCells = 1
        private const val MaxGridHalfExtentCells = 256
        private const val MinGridCellSize = 0.05f
        private const val MaxGridCellSize = 16f
    }

    private fun selectedEntityName(): String {
        val entityId = state.selectedEntityId ?: return "none"
        return document.world.getEntity(entityId)?.name ?: "missing #$entityId"
    }
}

private fun formatPosition(position: Vec3): String =
    "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)

internal fun beginSceneEditorPanel(
    panelId: String,
    layoutConfig: ImGuiLayoutConfig,
    layoutTracker: ImGuiLayoutRuntimeTracker,
    eventLogger: ImGuiWindowEventLogger,
): Boolean {
    val layout = layoutConfig.panels.getValue(panelId)
    val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
    eventLogger.observe(panelId, layout.title)
    return expanded
}

internal fun readBuffer(buffer: ByteArray): String {
    val length = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, length, StandardCharsets.UTF_8)
}

private fun formatTextureResolution(resolution: Int): String =
    if (resolution >= 1024 && resolution % 1024 == 0) {
        "${resolution / 1024}k"
    } else {
        resolution.toString()
    }

internal fun writeBuffer(buffer: ByteArray, value: String) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val length = minOf(bytes.size, buffer.size - 1)
    bytes.copyInto(buffer, endIndex = length)
}

internal fun safeInputText(label: String, buffer: ByteArray): Boolean =
    try {
        ImGui.inputText(label, buffer)
    } catch (_: ArrayIndexOutOfBoundsException) {
        // imgui-core 1.89.7-1 can produce a negative copy length while committing text.
        // Keep the editor alive and preserve the last valid buffer contents.
        false
    }
