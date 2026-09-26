package com.pashkd.krender.engine.tools.materialeditor

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorEdit4
import imgui.api.slider
import java.nio.charset.StandardCharsets

class MaterialEditorScene(private val path: String) : Scene("material_editor") {
    override val config: SceneConfig = SceneConfigPresets.EditorTool

    override fun show() {
        val state = MaterialEditorState(path)
        val controller = MaterialEditorController(state, engine)
        controller.reload()
        val layout = ImGuiLayoutConfigLoader("ui/material_editor_layout.json", Defaults,).load(engine.logger, engine.sceneFiles)
        val tracker = ImGuiLayoutRuntimeTracker(layout)
        val events = ImGuiWindowEventLogger(engine.logger, "MaterialEditorUi")
        world.systems.add(UiSystem(engine.ui).also { ui ->
            ui.addPanel(MaterialEditorPanel(state, controller, layout, tracker, events) { engine.requestExit() })
            ui.addPanel(LogsPanel(engine.logs, layout, events, layoutTracker = tracker))
        })
    }

    companion object {
        private val Defaults = ImGuiLayoutConfig(mapOf(
            "materialEditor" to ImGuiPanelLayout("Material Editor", 16f, 16f, 780f, 700f),
            "runtimeLogs" to ImGuiPanelLayout("Runtime Logs", 810f, 16f, 460f, 700f),
        ))
    }
}

private class MaterialEditorPanel(
    private val state: MaterialEditorState,
    private val controller: MaterialEditorController,
    private val layout: ImGuiLayoutConfig,
    private val tracker: ImGuiLayoutRuntimeTracker,
    private val events: ImGuiWindowEventLogger,
    private val exit: () -> Unit,
) : UiPanel {
    private val idBuffer = ByteArray(256)
    private val nameBuffer = ByteArray(256)
    private var bufferedMaterial: MaterialDraft? = null

    override fun draw() {
        val panel = layout.panels.getValue("materialEditor")
        val expanded = beginImGuiPanel("materialEditor", panel, tracker)
        events.observe("materialEditor", panel.title)
        if (!expanded) { ImGui.end(); return }
        if (ImGui.button("Save")) controller.save()
        ImGui.sameLine()
        if (ImGui.button("Reload")) {
            if (state.dirty) state.confirmReload = true else controller.reload()
        }
        ImGui.sameLine()
        if (ImGui.button("Exit")) {
            if (state.dirty) state.confirmExit = true else exit()
        }
        ImGui.textUnformatted("File: ${state.path}")
        ImGui.textUnformatted("Status: ${state.status}")
        ImGui.separator()
        ImGui.textUnformatted("Materials")
        state.materials.forEachIndexed { index, material ->
            if (ImGui.selectable("${material.name} (${material.id})##material_$index", state.selectedIndex == index)) {
                state.selectedIndex = index
            }
        }
        if (ImGui.button("Add Material")) controller.add()
        ImGui.sameLine()
        if (ImGui.button("Remove Material")) controller.removeSelected()
        ImGui.separator()
        state.materials.getOrNull(state.selectedIndex)?.let(::drawSelected)
        drawConfirmations()
        ImGui.end()
    }

    private fun drawSelected(material: MaterialDraft) {
        if (bufferedMaterial !== material) {
            bufferedMaterial = material
            write(idBuffer, material.id)
            write(nameBuffer, material.name)
        }
        if (ImGui.inputText("ID", idBuffer)) {
            material.id = read(idBuffer)
            state.dirty = true
        }
        if (ImGui.inputText("Name", nameBuffer)) {
            material.name = read(nameBuffer)
            state.dirty = true
        }
        if (ImGui.beginCombo("Texture", material.albedoTexture)) {
            state.texturePaths.forEach { texture ->
                if (ImGui.selectable(texture, texture == material.albedoTexture)) {
                    material.albedoTexture = texture
                    state.dirty = true
                }
            }
            ImGui.endCombo()
        }
        val color = material.fallbackColor
        if (colorEdit4("Fallback color", color.r, color.g, color.b, color.a) { r, g, b, a ->
                material.fallbackColor = TerrainLayerColorDescriptor(r, g, b, a)
            }) state.dirty = true
        if (slider("Default tiling", material::defaultTiling, 0.1f, 128f, "%.2f", SliderFlag.AlwaysClamp)) {
            state.dirty = true
        }
    }

    private fun drawConfirmations() {
        if (state.confirmReload) ImGui.openPopup("Discard changes and reload?##material_editor_reload")
        if (ImGui.beginPopupModal("Discard changes and reload?##material_editor_reload")) {
            if (ImGui.button("Discard and Reload")) {
                state.confirmReload = false
                controller.reload()
                bufferedMaterial = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel##reload")) {
                state.confirmReload = false
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
        if (state.confirmExit) ImGui.openPopup("Discard changes and exit?##material_editor_exit")
        if (ImGui.beginPopupModal("Discard changes and exit?##material_editor_exit")) {
            if (ImGui.button("Discard and Exit")) exit()
            ImGui.sameLine()
            if (ImGui.button("Cancel##exit")) {
                state.confirmExit = false
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun read(buffer: ByteArray): String = String(buffer, 0, buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size, StandardCharsets.UTF_8)

    private fun write(buffer: ByteArray, value: String) {
        buffer.fill(0)
        value.toByteArray(StandardCharsets.UTF_8).copyInto(buffer, endIndex = minOf(value.toByteArray(StandardCharsets.UTF_8).size, buffer.size - 1))
    }
}
