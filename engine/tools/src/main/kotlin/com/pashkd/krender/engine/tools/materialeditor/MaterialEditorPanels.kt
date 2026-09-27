package com.pashkd.krender.engine.tools.materialeditor

import com.pashkd.krender.engine.tools.common.EditorTexturePreviewService
import com.pashkd.krender.engine.tools.common.TexturePreviewResult
import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import com.pashkd.krender.engine.ui.editor.drawImGuiLayoutLockButton
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorEdit4
import imgui.api.slider
import java.nio.charset.StandardCharsets

internal class MaterialEditorControlPanel(
    private val state: MaterialEditorState,
    private val controller: MaterialEditorController,
    private val layout: ImGuiLayoutConfig,
    private val tracker: ImGuiLayoutRuntimeTracker,
    private val events: ImGuiWindowEventLogger,
    private val saveLayout: () -> Unit,
    private val restoreLayout: () -> Unit,
    private val exit: () -> Unit,
) : UiPanel {
    override fun draw() {
        if (!begin(MaterialEditorPanelIds.Control, layout, tracker, events)) { ImGui.end(); return }
        if (ImGui.button("Save")) controller.save()
        ImGui.sameLine()
        if (ImGui.button("Reload")) {
            if (state.dirty) state.confirmReload = true else controller.reload()
        }
        ImGui.sameLine()
        if (ImGui.button("Save UI Layout")) saveLayout()
        ImGui.sameLine()
        if (ImGui.button("Restore UI Layout")) restoreLayout()
        ImGui.sameLine()
        drawImGuiLayoutLockButton(tracker, "material_editor")
        ImGui.sameLine()
        if (ImGui.button("Exit")) {
            if (state.dirty) state.confirmExit = true else exit()
        }
        ImGui.textUnformatted("File: ${state.path}")
        ImGui.textUnformatted("Unsaved changes: ${if (state.dirty) "yes" else "no"}")
        ImGui.textUnformatted("Status: ${state.status}")
        drawConfirmations()
        ImGui.end()
    }

    private fun drawConfirmations() {
        if (state.confirmReload) ImGui.openPopup("Discard changes and reload?##material_editor_reload")
        if (ImGui.beginPopupModal("Discard changes and reload?##material_editor_reload")) {
            ImGui.textUnformatted("Unsaved material changes will be lost.")
            if (ImGui.button("Discard and Reload")) {
                state.confirmReload = false
                controller.reload()
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
            ImGui.textUnformatted("Unsaved material changes will be lost.")
            if (ImGui.button("Discard and Exit")) exit()
            ImGui.sameLine()
            if (ImGui.button("Cancel##exit")) {
                state.confirmExit = false
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }
}

internal class MaterialsPanel(
    private val state: MaterialEditorState,
    private val controller: MaterialEditorController,
    private val layout: ImGuiLayoutConfig,
    private val tracker: ImGuiLayoutRuntimeTracker,
    private val events: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        if (!begin(MaterialEditorPanelIds.Materials, layout, tracker, events)) { ImGui.end(); return }
        if (ImGui.button("Add Material")) controller.add()
        ImGui.sameLine()
        ImGui.beginDisabled(state.materials.size <= 1)
        if (ImGui.button("Remove Material")) controller.removeSelected()
        ImGui.endDisabled()
        ImGui.separator()
        state.materials.forEachIndexed { index, material ->
            if (ImGui.selectable("${material.name} (${material.id})##material_$index", state.selectedIndex == index)) {
                state.selectedIndex = index
            }
        }
        ImGui.end()
    }
}

internal class MaterialPropertiesPanel(
    private val state: MaterialEditorState,
    private val previews: EditorTexturePreviewService,
    private val ui: UiService,
    private val layout: ImGuiLayoutConfig,
    private val tracker: ImGuiLayoutRuntimeTracker,
    private val events: ImGuiWindowEventLogger,
) : UiPanel {
    private val idBuffer = ByteArray(256)
    private val nameBuffer = ByteArray(256)
    private var bufferedMaterial: MaterialDraft? = null

    override fun draw() {
        if (!begin(MaterialEditorPanelIds.Properties, layout, tracker, events)) { ImGui.end(); return }
        val material = state.materials.getOrNull(state.selectedIndex)
        if (material == null) {
            ImGui.textUnformatted("No material selected")
        } else {
            drawSelected(material)
        }
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
        ImGui.separator()
        ImGui.textUnformatted("Texture Preview")
        ImGui.textWrapped(material.albedoTexture)
        drawPreview(material.albedoTexture)
    }

    private fun drawPreview(path: String) {
        state.previewError?.let { error ->
            ImGui.textWrapped("Preview unavailable: $error")
            return
        }
        if (state.previewLoading) {
            ImGui.textUnformatted("Loading texture preview...")
            return
        }
        when (val preview = previews.preview(path)) {
            is TexturePreviewResult.Unavailable -> ImGui.textWrapped("Preview unavailable: ${preview.reason}")
            is TexturePreviewResult.Available -> {
                val handle = preview.handle
                val width = handle.width.coerceAtLeast(1).toFloat()
                val height = handle.height.coerceAtLeast(1).toFloat()
                val scale = minOf(1f, minOf(ImGui.contentRegionAvail.x.coerceAtLeast(1f), 360f) / width, 360f / height)
                if (!ui.drawTexturePreview(handle, width * scale, height * scale)) {
                    ImGui.textWrapped("Preview unavailable: UI could not draw the texture")
                }
                ImGui.textUnformatted("Size: ${handle.width} x ${handle.height}")
            }
        }
    }

    private fun read(buffer: ByteArray): String =
        String(buffer, 0, buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size, StandardCharsets.UTF_8)

    private fun write(buffer: ByteArray, value: String) {
        buffer.fill(0)
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        bytes.copyInto(buffer, endIndex = minOf(bytes.size, buffer.size - 1))
    }
}

private fun begin(
    id: String,
    layout: ImGuiLayoutConfig,
    tracker: ImGuiLayoutRuntimeTracker,
    events: ImGuiWindowEventLogger,
): Boolean {
    val panel = layout.panels.getValue(id)
    val expanded = beginImGuiPanel(id, panel, tracker)
    events.observe(id, panel.title)
    return expanded
}
