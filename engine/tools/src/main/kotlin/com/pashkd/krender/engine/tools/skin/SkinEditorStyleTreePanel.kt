package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class SkinEditorStyleTreePanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val createNameBuffer = ByteArray(128)
    private var createType = SkinStyleTemplates.types.first()

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.StyleTree)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.StyleTree, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.StyleTree, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawCreateStyle()
        ImGui.separator()
        ImGui.textUnformatted("Styles")
        val styles = state.editSession.activeStyles()
        if (styles.isEmpty()) {
            ImGui.textUnformatted("No styles indexed.")
        } else {
            styles.groupBy { it.key.type }.forEach { (type, typeStyles) ->
                if (ImGui.treeNode("$type (${typeStyles.size})##skin_editor_style_type_$type")) {
                    typeStyles.forEach { style ->
                        val stateLabel =
                            when {
                                style.createdInEditor -> " [new]"
                                style.renamedInEditor -> " [renamed]"
                                else -> ""
                            }
                        if (ImGui.selectable("${style.key.name}$stateLabel##skin_editor_style_${style.key.type}_${style.key.name}", state.selectedStyleKey == style.key)) {
                            operations.selectStyle(style.key)
                        }
                    }
                    ImGui.treePop()
                }
            }
        }
        ImGui.end()
    }

    private fun drawCreateStyle() {
        ImGui.textUnformatted("Create Style")
        if (ImGui.beginCombo("Type##skin_editor_create_style_type", createType)) {
            SkinStyleTemplates.types.forEach { type ->
                if (ImGui.selectable(type, type == createType)) createType = type
            }
            ImGui.endCombo()
        }
        ImGui.textUnformatted("Name:")
        ImGui.setNextItemWidth(-1f)
        ImGui.inputText("##skin_editor_create_style_name", createNameBuffer)
        if (ImGui.button("Create##skin_editor_create_style_apply") &&
            operations.createStyle(createType, readBuffer(createNameBuffer))
        ) {
            createNameBuffer.fill(0)
        }
    }
}
