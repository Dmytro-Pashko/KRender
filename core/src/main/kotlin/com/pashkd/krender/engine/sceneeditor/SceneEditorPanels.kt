package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui
import imgui.dsl
import java.nio.charset.StandardCharsets

/**
 * Top-level scene editor action/status panel.
 */
class SceneEditorToolbarPanel(
    private val state: SceneEditorState,
    private val operations: SceneEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val saveAsPathBuffer = ByteArray(TextInputBufferSize)
    private val openPathBuffer = ByteArray(TextInputBufferSize)
    private var saveAsBufferSynced = false
    private var openBufferSynced = false

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SceneEditorPanelIds.Toolbar)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, SceneEditorPanelIds.Toolbar))
        eventLogger.observe(SceneEditorPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        with(dsl) {
            button("New Scene##scene_editor_new") {
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
                operations.requestPlayPlaceholder()
            }
        }

        ImGui.separator()
        ImGui.text("Scene: ${state.sceneName}")
        ImGui.sameLine()
        ImGui.text("Path: ${state.currentScenePath ?: "<memory>"}")
        ImGui.sameLine()
        ImGui.text("Dirty: ${if (state.hasUnsavedChanges) "yes" else "no"}")
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
        if (ImGui.inputText("##scene_editor_open_path", openPathBuffer)) {
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
        if (ImGui.inputText("##scene_editor_save_as_path", saveAsPathBuffer)) {
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
    private val eventLogger: ImGuiWindowEventLogger,
    private val logger: Logger,
) : UiPanel {
    private var lastFilteredEditorOnlyIds: Set<EntityId> = emptySet()
    private val renameBuffer = ByteArray(RenameInputBufferSize)
    private var renameRequested = false

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SceneEditorPanelIds.Hierarchy)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, SceneEditorPanelIds.Hierarchy))
        eventLogger.observe(SceneEditorPanelIds.Hierarchy, layout.title)
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
                val marker = if (entity.active) "" else " (inactive)"
                val label = "${entity.name}$marker##scene_entity_${entity.id}"
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
        ImGui.sameLine()
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
        ImGui.sameLine()
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
        ImGui.sameLine()
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
        ImGui.inputText("##scene_hierarchy_rename_name", renameBuffer)

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
 * Shows read-only details for the selected entity and its current components.
 */
class SceneInspectorPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
    private val logger: Logger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SceneEditorPanelIds.Inspector)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, SceneEditorPanelIds.Inspector))
        eventLogger.observe(SceneEditorPanelIds.Inspector, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val entity = state.selectedEntityId?.let(document.world::getEntity)
        if (entity?.get<EditorOnlyComponent>() != null) {
            logger.debug(TAG) { "Clearing editor-only selection entityId=${entity.id}" }
            state.selectedEntityId = null
            ImGui.text("No entity selected.")
            ImGui.end()
            return
        }
        if (entity == null) {
            ImGui.text("No entity selected.")
            ImGui.end()
            return
        }

        drawEntity(entity)
        ImGui.end()
    }

    private fun drawEntity(entity: Entity) {
        ImGui.text("Name: ${entity.name}")
        ImGui.text("Id: ${entity.id}")
        ImGui.text("Active: ${if (entity.active) "yes" else "no"}")

        entity.get<TransformComponent>()?.let { transform ->
            ImGui.separator()
            ImGui.text("Transform")
            drawVec3("Position", transform.position)
            drawVec3("Rotation", transform.eulerDegrees)
            drawVec3("Scale", transform.scale)
        }

        entity.get<PerspectiveCameraComponent>()?.let { camera ->
            ImGui.separator()
            ImGui.text("Camera")
            ImGui.text("FOV: ${"%.1f".format(camera.fieldOfViewDegrees)}")
            ImGui.text("Near/Far: ${"%.2f".format(camera.near)} / ${"%.1f".format(camera.far)}")
        }

        entity.get<LightComponent>()?.let { light ->
            ImGui.separator()
            ImGui.text("Light")
            ImGui.text("Type: ${light.type}")
            ImGui.text("Intensity: ${"%.2f".format(light.intensity)}")
        }

        ImGui.separator()
        ImGui.text("Components")
        entity.components.all().forEach { component ->
            ImGui.bulletText(component::class.simpleName ?: component::class.toString())
        }
    }

    private fun drawVec3(label: String, value: Vec3) {
        ImGui.text("$label: ${"%.2f".format(value.x)}, ${"%.2f".format(value.y)}, ${"%.2f".format(value.z)}")
    }

    companion object {
        private const val TAG = "SceneInspectorPanel"
    }
}

/**
 * Minimal viewport status panel. Rendering still goes through the engine renderer.
 */
class SceneViewportPanel(
    private val state: SceneEditorState,
    private val document: SceneEditorDocument,
    private val layoutConfig: ImGuiLayoutConfig,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SceneEditorPanelIds.Viewport)
        applyWindowDefaults(layout)
        val expanded = ImGui.begin(imguiWindowName(layout.title, SceneEditorPanelIds.Viewport))
        eventLogger.observe(SceneEditorPanelIds.Viewport, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.beginChild("scene_editor_viewport_body", Vec2(0f, 0f), true)
        ImGui.text("Scene view")
        ImGui.separator()
        ImGui.text("Entities: ${document.world.all().count { entity -> entity.get<EditorOnlyComponent>() == null }}")
        ImGui.text("Selected: ${state.selectedEntityId?.toString() ?: "none"}")
        ImGui.text("Camera and light placeholders are present in the runtime world.")
        ImGui.endChild()
        ImGui.end()
    }
}

private fun applyWindowDefaults(layout: ImGuiPanelLayout) {
    ImGui.setNextWindowPos(Vec2(layout.x, layout.y), Cond.FirstUseEver, Vec2())
    ImGui.setNextWindowSize(Vec2(layout.width, layout.height), Cond.FirstUseEver)
}

private fun imguiWindowName(title: String, id: String): String = "$title###$id"

private fun readBuffer(buffer: ByteArray): String {
    val length = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, length, StandardCharsets.UTF_8)
}

private fun writeBuffer(buffer: ByteArray, value: String) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val length = minOf(bytes.size, buffer.size - 1)
    bytes.copyInto(buffer, endIndex = length)
}
