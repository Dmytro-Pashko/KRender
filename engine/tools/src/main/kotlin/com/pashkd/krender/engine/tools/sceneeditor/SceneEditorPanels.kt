package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.scene.*
import com.pashkd.krender.engine.tools.common.EditorAssetPickerOption
import com.pashkd.krender.engine.ui.editor.*
import glm_.vec2.Vec2 as ImVec2
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorEdit4
import imgui.api.slider
import imgui.dsl
import java.nio.charset.StandardCharsets

class SceneEditorControlPanel(
    private val state: SceneEditorState,
    private val operations: SceneEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private var reloadRequested = false
    private var exitRequested = false

    override fun draw() {
        if (beginSceneEditorPanel(SceneEditorPanelIds.Control, layoutConfig, layoutTracker, eventLogger)) {
            panelButton("Save##scene_save") { operations.save() }
            ImGui.sameLine()
            panelButton("Reload##scene_reload") {
                if (state.hasUnsavedChanges) reloadRequested = true else operations.reload()
            }
            ImGui.sameLine()
            panelButton("Play##scene_play") { operations.playInNewWindow() }
            ImGui.sameLine()
            panelButton("Save UI Position##scene_save_ui") { operations.saveUiLayout() }
            ImGui.sameLine()
            panelButton("Restore UI##scene_restore_ui") { operations.restoreUiLayout() }
            ImGui.sameLine()
            drawImGuiLayoutLockButton(layoutTracker, "scene_editor")
            ImGui.sameLine()
            panelButton("Exit##scene_exit") {
                if (state.hasUnsavedChanges) exitRequested = true else operations.exit()
            }
            ImGui.separator()
            ImGui.text("Scene: ${state.sceneName}")
            ImGui.text("Path: ${state.currentScenePath ?: "<missing>"}")
        }
        ImGui.end()
        drawReloadDialog()
        drawExitDialog()
        drawErrorDialog()
    }

    private fun drawExitDialog() {
        if (!exitRequested) return
        ImGui.openPopup("Exit Scene Editor##scene_exit_confirm")
        if (!ImGui.beginPopupModal("Exit Scene Editor##scene_exit_confirm")) return
        ImGui.textWrapped("The scene has unsaved changes. Save them before exiting?")
        panelButton("Save##exit_save") {
            if (operations.save()) { exitRequested = false; operations.exit() }
        }
        ImGui.sameLine()
        panelButton("Discard##exit_discard") { exitRequested = false; operations.exit() }
        ImGui.sameLine()
        panelButton("Cancel##exit_cancel") { exitRequested = false }
        if (!exitRequested) ImGui.closeCurrentPopup()
        ImGui.endPopup()
    }

    private fun drawReloadDialog() {
        if (!reloadRequested) return
        ImGui.openPopup("Reload Scene##scene_reload_confirm")
        if (!ImGui.beginPopupModal("Reload Scene##scene_reload_confirm")) return
        ImGui.textWrapped("The scene has unsaved changes. Save them before reloading?")
        panelButton("Save##reload_save") {
            if (operations.save() && operations.reload()) reloadRequested = false
        }
        ImGui.sameLine()
        panelButton("Discard##reload_discard") {
            if (operations.reload()) reloadRequested = false
        }
        ImGui.sameLine()
        panelButton("Cancel##reload_cancel") { reloadRequested = false }
        if (!reloadRequested) ImGui.closeCurrentPopup()
        ImGui.endPopup()
    }

    private fun drawErrorDialog() {
        val message = state.openErrorMessage ?: state.saveErrorMessage ?: return
        ImGui.openPopup("Scene Editor Error##scene_error")
        if (!ImGui.beginPopupModal("Scene Editor Error##scene_error")) return
        ImGui.textWrapped(message)
        panelButton("Close##scene_error_close") {
            state.openErrorMessage = null
            state.saveErrorMessage = null
            ImGui.closeCurrentPopup()
        }
        ImGui.endPopup()
    }
}

class SceneHierarchyPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val operations: SceneEditorOperations,
    private val assets: SceneEditorAssetCatalog,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val types = listOf("Empty", "Model", "Camera", "Directional Light", "Point Light")
    private var selectedType = "Empty"
    private var selectedModelPath: String? = null

    override fun draw() {
        if (!beginSceneEditorPanel(SceneEditorPanelIds.Hierarchy, layoutConfig, layoutTracker, eventLogger)) { ImGui.end(); return }
        if (ImGui.beginCombo("Entity Type##hierarchy_type", selectedType)) {
            types.forEach { type ->
                if (ImGui.selectable(type, type == selectedType)) selectedType = type
            }
            ImGui.endCombo()
        }
        if (selectedType == "Model") {
            selectedModelPath = drawAssetCombo("Model Asset##hierarchy_model", selectedModelPath, assets.models())
        }
        ImGui.beginDisabled(selectedType == "Model" && selectedModelPath == null)
        panelButton("Add##hierarchy_add") { operations.addEntity(selectedType, selectedModelPath) }
        ImGui.endDisabled()
        val selected = state.selectedEntityId?.let(document.world::getEntity)
            ?.takeIf { it.get<EditorOnlyComponent>() == null }
        ImGui.beginDisabled(selected == null)
        panelButton("Duplicate##hierarchy_duplicate") { selected?.let { operations.duplicateEntity(it.id) } }
        ImGui.sameLine()
        panelButton("Delete##hierarchy_delete") { selected?.let { operations.deleteEntity(it.id) } }
        ImGui.endDisabled()
        ImGui.separator()
        val entities = document.world.all().filter { it.get<EditorOnlyComponent>() == null }
        ImGui.text("Entities: ${entities.size}")
        ImGui.beginChild("scene_hierarchy_entities", ImVec2(0f, 0f), true)
        entities.forEach { entity ->
            val activeCamera = if (document.descriptor?.settings?.activeCameraEntityId == entity.id) " [Active Camera]" else ""
            if (ImGui.selectable("${entity.name}$activeCamera##entity_${entity.id}", selected?.id == entity.id)) {
                state.selectedEntityId = entity.id
            }
        }
        ImGui.endChild()
        ImGui.end()
    }
}

class SceneInspectorPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val operations: SceneEditorOperations,
    private val assets: SceneEditorAssetCatalog,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val nameBuffer = ByteArray(128)
    private val number = FloatArray(1)
    private val bakedResolutions = listOf(256, 512, 1024, 2048, 4096, 8192)

    override fun draw() {
        if (!beginSceneEditorPanel(SceneEditorPanelIds.Inspector, layoutConfig, layoutTracker, eventLogger)) { ImGui.end(); return }
        val descriptor = document.descriptor
        if (descriptor == null) { ImGui.text("No scene loaded."); ImGui.end(); return }
        val settings = descriptor.settings
        if (!ImGui.isAnyItemActive) writeBuffer(nameBuffer, state.sceneName)
        if (safeInputText("Scene Name##scene_name", nameBuffer)) operations.setSceneName(readBuffer(nameBuffer))
        ImGui.separator()
        ImGui.text("Active Camera")
        val cameras = document.world.all().filter { it.get<com.pashkd.krender.engine.render3d.PerspectiveCameraComponent>() != null }
        val active = cameras.firstOrNull { it.id == settings.activeCameraEntityId }
        if (ImGui.beginCombo("##scene_camera", active?.name ?: "<none>")) {
            cameras.forEach { camera ->
                if (ImGui.selectable("${camera.name}##camera_${camera.id}", camera.id == active?.id)) operations.setActiveCamera(camera.id)
            }
            ImGui.endCombo()
        }
        ImGui.separator()
        ImGui.text("Environment")
        val environment = drawAssetCombo("##scene_environment", settings.environment.environmentAssetPath, assets.environments(), allowNone = true)
        if (environment != settings.environment.environmentAssetPath) operations.setEnvironmentAsset(environment)
        ImGui.separator()
        ImGui.text("Ambient Lighting")
        val color = settings.lighting.ambientColor
        if (colorEdit4("Color##scene_ambient_color", color.r, color.g, color.b, color.a) { r, g, b, a ->
                operations.setAmbientLightColor(Color(r, g, b, a))
            }) Unit
        number[0] = settings.lighting.ambientIntensity
        if (ImGui.drag("Intensity##scene_ambient_intensity", number, 0.01f, 0f, 0f, "%.2f", SliderFlag.AlwaysClamp)) {
            operations.setAmbientLightIntensity(number[0])
        }
        drawTerrain(settings.terrain)
        ImGui.separator()
        ImGui.text("Diagnostics")
        panelButton("Validate Scene##scene_validate") { operations.validateScene() }
        val report = state.validationReport
        ImGui.text("Errors: ${report.errors.size}; Warnings: ${report.warnings.size}")
        report.issues.take(6).forEach { ImGui.bulletText("${it.severity}: ${it.message}") }
        ImGui.end()
    }

    private fun drawTerrain(terrain: SceneTerrainSettingsDescriptor) {
        ImGui.separator()
        ImGui.text("Terrain")
        val path = drawAssetCombo("Terrain Asset##scene_terrain", terrain.terrainAssetPath, assets.terrains(), allowNone = true)
        if (path != terrain.terrainAssetPath) operations.setTerrain(terrain.copy(terrainAssetPath = path))
        if (terrain.terrainAssetPath == null) return
        val visible = booleanArrayOf(terrain.visible)
        if (ImGui.checkbox("Visible##scene_terrain_visible", visible)) operations.setTerrain(terrain.copy(visible = visible[0]))
        val previewModes = listOf("MaterialColor" to "Color", "MaterialTexture" to "Texture", "Wireframe" to "Wireframe")
        val currentPreview = if (state.terrainWireframe) "Wireframe" else if (terrain.previewMode == "MaterialTexture") "Texture" else "Color"
        if (ImGui.beginCombo("Preview##scene_terrain_preview", currentPreview)) {
            previewModes.forEach { (mode, label) ->
                if (ImGui.selectable(label, currentPreview == label)) {
                    if (mode == "Wireframe") state.terrainWireframe = true
                    else {
                        state.terrainWireframe = false
                        if (terrain.previewMode != mode) operations.setTerrain(terrain.copy(previewMode = mode))
                    }
                }
            }
            ImGui.endCombo()
        }
        ImGui.text("Texture Preview Resolution")
        drawResolutionButtons("scene_preview_resolution", listOf(256, 512, 1024, 4096, 8192), state.terrainPreviewResolution) {
            state.terrainPreviewResolution = it
        }
        ImGui.text("Baked Resolution")
        val availableResolutions = if (terrain.bakedTextureResolution in bakedResolutions) bakedResolutions
            else (bakedResolutions + terrain.bakedTextureResolution).sorted()
        drawResolutionButtons("scene_baked_resolution", availableResolutions, terrain.bakedTextureResolution) { resolution ->
            operations.setTerrain(terrain.copy(bakedTextureResolution = resolution))
        }
        val terrainPath = terrain.terrainAssetPath
        panelButton("Open in Terrain Editor##scene_terrain_open") { terrainPath?.let(operations::openTerrainInEditor) }
    }
}

fun interface SceneComponentCustomEditor {
    fun draw(entity: Entity, component: Component, operations: SceneEditorOperations)
}

class SceneComponentFieldRenderer(
    private val assets: SceneEditorAssetCatalog,
    private val customEditors: Map<String, SceneComponentCustomEditor> = emptyMap(),
) {
    private val textBuffers = mutableMapOf<String, ByteArray>()
    private val floats = FloatArray(4)

    fun draw(entity: Entity, definition: SceneComponentDefinition, component: Component, operations: SceneEditorOperations) {
        val custom = customEditors[definition.type]
        if (custom != null) { custom.draw(entity, component, operations); return }
        val values = definition.encode(component)
        definition.fields.forEach { field ->
            val value = values[field.key].orEmpty()
            val id = "${entity.id}_${definition.type}_${field.key}"
            val next = drawField(field, id, value)
            if (next != null && next != value) operations.setComponentField(entity.id, definition.type, field.key, next)
        }
    }

    private fun drawField(field: SceneFieldDescriptor, id: String, value: String): String? = when (field.kind) {
        SceneFieldKind.Text -> {
            val buffer = textBuffers.getOrPut(id) { ByteArray(256) }
            if (!ImGui.isAnyItemActive) writeBuffer(buffer, value)
            if (safeInputText("${field.label}##$id", buffer)) readBuffer(buffer) else null
        }
        SceneFieldKind.ModelAsset -> drawAssetCombo("${field.label}##$id", value, assets.models())
        SceneFieldKind.Choice -> {
            var choice: String? = null
            if (ImGui.beginCombo("${field.label}##$id", value)) {
                field.choices.forEach { option -> if (ImGui.selectable(option, value == option)) choice = option }
                ImGui.endCombo()
            }
            choice
        }
        SceneFieldKind.Float -> {
            floats[0] = value.toFloatOrNull() ?: 0f
            if (ImGui.drag("${field.label}##$id", floats, 0.01f, 0f, 0f, "%.3f")) floats[0].toString() else null
        }
        SceneFieldKind.Vec3 -> {
            val values = value.split(',')
            (0..2).forEach { floats[it] = values.getOrNull(it)?.toFloatOrNull() ?: 0f }
            if (ImGui.drag3("${field.label}##$id", floats, 0.05f, 0f, 0f, "%.3f")) "${floats[0]},${floats[1]},${floats[2]}" else null
        }
        SceneFieldKind.Color -> {
            val values = value.split(',')
            (0..3).forEach { floats[it] = values.getOrNull(it)?.toFloatOrNull() ?: 1f }
            var next: String? = null
            colorEdit4("${field.label}##$id", floats[0], floats[1], floats[2], floats[3]) { r, g, b, a ->
                next = "$r,$g,$b,$a"
            }
            next
        }
    }
}

class EntityPropertiesPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val operations: SceneEditorOperations,
    private val assets: SceneEditorAssetCatalog,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val fields = SceneComponentFieldRenderer(assets)
    private val nameBuffer = ByteArray(128)
    private var bufferedId: Long? = null
    private var addType: String? = null
    private var modelPath: String? = null

    override fun draw() {
        if (!beginSceneEditorPanel(SceneEditorPanelIds.Properties, layoutConfig, layoutTracker, eventLogger)) { ImGui.end(); return }
        val entity = state.selectedEntityId?.let(document.world::getEntity)?.takeIf { it.get<EditorOnlyComponent>() == null }
        if (entity == null) { ImGui.text("Select an entity."); ImGui.end(); return }
        if (bufferedId != entity.id || !ImGui.isAnyItemActive) { writeBuffer(nameBuffer, entity.name); bufferedId = entity.id }
        if (safeInputText("Name##entity_name", nameBuffer)) operations.setEntityName(entity.id, readBuffer(nameBuffer))
        ImGui.text("Id: ${entity.id}")
        val active = booleanArrayOf(entity.active)
        if (ImGui.checkbox("Active##entity_active", active)) operations.setEntityActive(entity.id, active[0])
        drawParent(entity)
        ImGui.separator()
        entity.components.all().forEach { component ->
            val definition = SceneComponentRegistry.find(component) ?: return@forEach
            if (!definition.addable && definition.type != SceneComponentTypes.Transform) return@forEach
            ImGui.text(definition.type)
            if (definition.type != SceneComponentTypes.Transform) {
                ImGui.sameLine()
                panelButton("Remove##${entity.id}_${definition.type}") { operations.removeComponent(entity.id, definition.type) }
            }
            fields.draw(entity, definition, component, operations)
            if (definition.type == SceneComponentTypes.Camera) {
                panelButton("Camera To View##${entity.id}") { operations.cameraToView(entity.id) }
                ImGui.sameLine()
                panelButton("View To Camera##${entity.id}") { operations.viewToCamera(entity.id) }
            }
            ImGui.separator()
        }
        val available = SceneComponentRegistry.definitions.filter { it.addable && entity.get(it.componentClass) == null }
        if (available.isNotEmpty()) {
            val selected = available.firstOrNull { it.type == addType } ?: available.first().also { addType = it.type }
            if (ImGui.beginCombo("Add Component##entity_add_type", selected.type)) {
                available.forEach { option -> if (ImGui.selectable(option.type, option == selected)) addType = option.type }
                ImGui.endCombo()
            }
            if (selected.type == SceneComponentTypes.Model) modelPath = drawAssetCombo("Model Asset##entity_add_model", modelPath, assets.models())
            ImGui.beginDisabled(selected.type == SceneComponentTypes.Model && modelPath == null)
            panelButton("Add Component##entity_add") {
                operations.addComponent(entity.id, selected.type, if (selected.type == SceneComponentTypes.Model) mapOf("model" to modelPath.orEmpty()) else emptyMap())
            }
            ImGui.endDisabled()
        }
        document.descriptor?.entities?.firstOrNull { it.id == entity.id }?.components
            ?.filter { SceneComponentRegistry.find(it.type) == null }
            ?.forEach { component ->
                ImGui.textWrapped("Unknown component: ${component.type} (read only)")
                component.properties.forEach { (key, value) -> ImGui.bulletText("$key: $value") }
            }
        ImGui.end()
    }

    private fun drawParent(entity: Entity) {
        val parentId = entity.get<ParentComponent>()?.parentId
        val parent = parentId?.let(document.world::getEntity)
        if (ImGui.beginCombo("Parent##entity_parent", parent?.name ?: "<none>")) {
            if (ImGui.selectable("<none>##parent_none", parentId == null)) operations.setParent(entity.id, null)
            document.world.all().filter { it.id != entity.id && it.get<EditorOnlyComponent>() == null }.forEach { candidate ->
                if (ImGui.selectable("${candidate.name}##parent_${candidate.id}", candidate.id == parentId)) operations.setParent(entity.id, candidate.id)
            }
            ImGui.endCombo()
        }
    }
}

class SceneViewportPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        if (!beginSceneEditorPanel(SceneEditorPanelIds.Viewport, layoutConfig, layoutTracker, eventLogger)) {
            state.viewportFocused = false
            state.viewport.focused = false
            ImGui.end()
            return
        }
        ImGui.beginChild("scene_editor_viewport_body", ImVec2(0f, 0f), true)
        val position = ImGui.windowPos
        val size = ImGui.windowSize
        state.viewportOrigin = Vec2(position.x, position.y)
        state.viewportSize = Vec2(size.x.coerceAtLeast(0f), size.y.coerceAtLeast(0f))
        state.viewportFocused = ImGui.isWindowHovered() || ImGui.isWindowFocused()
        state.viewport.origin = state.viewportOrigin
        state.viewport.size = state.viewportSize
        state.viewport.focused = state.viewportFocused
        ImGui.text("Selection: ${state.selectedEntityId?.let(document.world::getEntity)?.name ?: "none"}")
        ImGui.text("RMB look, WASD move, Q/E down/up.")
        ImGui.text("Wheel speed, Shift faster.")
        ImGui.separator()
        ImGui.checkbox("Grid##scene_grid", state::showGrid)
        ImGui.sameLine()
        ImGui.checkbox("Axes##scene_axes", state::showAxes)
        ImGui.sameLine()
        ImGui.checkbox("Bounding Box##scene_bounds", state::showSelectedBoundingBox)
        slider("Grid extent##scene_grid_extent", state::gridHalfExtentCells, 1, 256, "%d", SliderFlag.AlwaysClamp)
        slider("Cell size##scene_cell_size", state::gridCellSize, 0.05f, 16f, "%.2f", SliderFlag.AlwaysClamp)
        ImGui.endChild()
        ImGui.end()
    }
}

private fun panelButton(label: String, action: () -> Unit) { with(dsl) { button(label) { action() } } }

private fun drawResolutionButtons(id: String, resolutions: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    resolutions.forEachIndexed { index, resolution ->
        if (index > 0) ImGui.sameLine()
        val isSelected = resolution == selected
        if (isSelected) ImGui.beginDisabled(true)
        val label = if (resolution >= 1024 && resolution % 1024 == 0) "${resolution / 1024}K" else "$resolution"
        if (ImGui.smallButton("${if (isSelected) "[$label]" else label}##${id}_$resolution") && !isSelected) onSelect(resolution)
        if (isSelected) ImGui.endDisabled()
    }
}

private fun drawAssetCombo(label: String, selectedPath: String?, options: List<EditorAssetPickerOption>, allowNone: Boolean = false): String? {
    var selected = selectedPath
    val preview = options.firstOrNull { it.path == selectedPath }?.displayName ?: selectedPath ?: "<none>"
    if (ImGui.beginCombo(label, preview)) {
        if (allowNone && ImGui.selectable("<none>##${label}_none", selectedPath == null)) selected = null
        options.forEach { option ->
            if (ImGui.selectable("${option.displayName}##${label}_${option.assetId}", selectedPath == option.path)) selected = option.path
        }
        ImGui.endCombo()
    }
    return selected
}

internal fun beginSceneEditorPanel(panelId: String, layoutConfig: ImGuiLayoutConfig, layoutTracker: ImGuiLayoutRuntimeTracker, eventLogger: ImGuiWindowEventLogger): Boolean {
    val layout = layoutConfig.panels.getValue(panelId)
    val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
    eventLogger.observe(panelId, layout.title)
    return expanded
}

internal fun readBuffer(buffer: ByteArray): String {
    val length = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, length, StandardCharsets.UTF_8)
}

internal fun writeBuffer(buffer: ByteArray, value: String) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    bytes.copyInto(buffer, endIndex = minOf(bytes.size, buffer.size - 1))
}

internal fun safeInputText(label: String, buffer: ByteArray): Boolean = try {
    ImGui.inputText(label, buffer)
} catch (_: ArrayIndexOutOfBoundsException) {
    false
}
