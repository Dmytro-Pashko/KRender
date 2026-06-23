package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.PreviewLayoutRegistry
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.drawSelectedResourcePreviewHint
import com.pashkd.krender.engine.tools.skin.formatPreviewScale
import com.pashkd.krender.engine.tools.skin.readBuffer
import com.pashkd.krender.engine.tools.skin.selectedResourceSummary
import com.pashkd.krender.engine.tools.skin.writeBuffer
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class SkinEditorPreviewControlsPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val previewLayouts: PreviewLayoutRegistry,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val labelTextBuffer = ByteArray(256)
    private val buttonTextBuffer = ByteArray(256)
    private val textFieldBuffer = ByteArray(256)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.PreviewControls)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.PreviewControls, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.PreviewControls, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (ImGui.beginCombo("Layout##skin_editor_preview_layout", previewLayouts.layoutOrDefault(state.previewLayoutId).displayName)) {
            previewLayouts.layouts.forEach { layoutOption ->
                if (ImGui.selectable("${layoutOption.displayName}##skin_editor_preview_layout_${layoutOption.id}", layoutOption.id == state.previewLayoutId)) {
                    operations.selectLayout(layoutOption.id)
                }
            }
            ImGui.endCombo()
        }
        drawPreviewTextSettings()
        val showFallbackWarnings = booleanArrayOf(state.previewSettings.showFallbackWarnings)
        if (ImGui.checkbox("Show fallback warnings##skin_editor_preview_warnings", showFallbackWarnings)) {
            operations.setShowFallbackWarnings(showFallbackWarnings[0])
        }
        val selectedStyle = state.selectedStyleKey
        ImGui.separator()
        ImGui.textUnformatted("Layout: ${previewLayouts.layoutOrDefault(state.previewInfo.layoutId ?: state.previewLayoutId).displayName} (${state.previewInfo.layoutId ?: state.previewLayoutId})")
        ImGui.textUnformatted("Logical screen: ${state.previewInfo.logicalWidth} x ${state.previewInfo.logicalHeight}")
        ImGui.textUnformatted("Scale: ${formatPreviewScale(state.previewInfo.scale)}")
        ImGui.textUnformatted("Root actor: ${state.previewInfo.rootActorClass ?: "<none>"}")
        ImGui.textUnformatted("Actor count: ${state.previewInfo.actorCount}")
        ImGui.textUnformatted("Fallback issues: ${state.previewInfo.fallbackIssueCount}")
        if (!state.previewSettings.showFallbackWarnings && state.previewInfo.fallbackIssueCount > 0) {
            ImGui.textWrapped("Fallback warnings hidden. Preview may use fallback widgets.")
        }
        ImGui.textUnformatted("Selected style: ${selectedStyle?.let { "${it.type}.${it.name}" } ?: "<none>"}")
        ImGui.textUnformatted("Selected resource: ${selectedResourceSummary(state) ?: "<none>"}")
        drawSelectedResourcePreviewHint(state)
        ImGui.end()
    }

    private fun drawPreviewTextSettings() {
        val text = state.previewSettings.text
        syncPreviewBuffer(labelTextBuffer, text.labelText)
        if (ImGui.inputText("Label text##skin_editor_preview_label_text", labelTextBuffer)) {
            operations.setPreviewLabelText(readBuffer(labelTextBuffer))
        }
        syncPreviewBuffer(buttonTextBuffer, text.buttonText)
        if (ImGui.inputText("Button text##skin_editor_preview_button_text", buttonTextBuffer)) {
            operations.setPreviewButtonText(readBuffer(buttonTextBuffer))
        }
        syncPreviewBuffer(textFieldBuffer, text.textFieldPlaceholder)
        if (ImGui.inputText("TextField text##skin_editor_preview_field_text", textFieldBuffer)) {
            operations.setPreviewTextFieldPlaceholder(readBuffer(textFieldBuffer))
        }
    }

    private fun syncPreviewBuffer(
        buffer: ByteArray,
        value: String,
    ) {
        if (readBuffer(buffer) != value) {
            writeBuffer(buffer, value)
        }
    }
}
