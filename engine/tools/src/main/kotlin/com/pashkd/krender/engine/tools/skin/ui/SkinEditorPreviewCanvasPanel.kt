package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.PreviewScales
import com.pashkd.krender.engine.tools.skin.SkinEditorCanvasRect
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinPreviewPointerButton
import com.pashkd.krender.engine.tools.skin.SkinPreviewPointerEvent
import com.pashkd.krender.engine.tools.skin.SkinPreviewPointerEventType
import com.pashkd.krender.engine.tools.skin.SkinPreviewScreenPresets
import com.pashkd.krender.engine.tools.skin.formatPreviewScale
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import kotlin.math.hypot
import glm_.vec2.Vec2 as ImVec2

class SkinEditorPreviewCanvasPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private var previewClickPending = false
    private var previewClickDragDistance = 0f
    private var lastPointerScreenX = Float.NaN
    private var lastPointerScreenY = Float.NaN

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.PreviewCanvas)
        ImGui.setNextWindowBgAlpha(0f)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.PreviewCanvas, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.PreviewCanvas, layout.title)
        if (!expanded) {
            state.canvasRect = SkinEditorCanvasRect()
            ImGui.end()
            return
        }

        val selectedPreset = SkinPreviewScreenPresets.presetOrDefault(state.previewSettings.screenPresetId)
        ImGui.textUnformatted("Resolution:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(180f)
        if (ImGui.beginCombo("##skin_editor_canvas_resolution", selectedPreset.displayName)) {
            SkinPreviewScreenPresets.presets.forEach { preset ->
                if (ImGui.selectable("${preset.displayName}##skin_editor_canvas_resolution_${preset.id}", preset.id == selectedPreset.id)) {
                    operations.selectScreenPreset(preset.id)
                }
            }
            ImGui.endCombo()
        }
        val selectedScale = PreviewScales.minBy { scale -> kotlin.math.abs(scale - state.previewSettings.scale) }
        ImGui.textUnformatted("Scale:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(120f)
        if (ImGui.beginCombo("##skin_editor_canvas_scale", formatPreviewScale(selectedScale))) {
            PreviewScales.forEach { scale ->
                if (ImGui.selectable("${formatPreviewScale(scale)}##skin_editor_canvas_scale_$scale", scale == selectedScale)) {
                    operations.setPreviewScale(scale)
                }
            }
            ImGui.endCombo()
        }
        val showBounds = booleanArrayOf(state.previewSettings.showBounds)
        ImGui.textUnformatted("Show Bounding Box:")
        ImGui.sameLine()
        if (ImGui.checkbox("##skin_editor_canvas_bounds", showBounds)) operations.setShowBounds(showBounds[0])
        val showCheckerboard = booleanArrayOf(state.previewSettings.showCheckerboard)
        ImGui.textUnformatted("Show Checkerboard:")
        ImGui.sameLine()
        if (ImGui.checkbox("##skin_editor_canvas_checkerboard", showCheckerboard)) {
            operations.setPreviewCheckerboardEnabled(showCheckerboard[0])
        }
        val highlightSelectedStyle = booleanArrayOf(state.previewSettings.highlightSelectedStyle)
        ImGui.textUnformatted("Highlight selected style:")
        ImGui.sameLine()
        if (ImGui.checkbox("##skin_editor_canvas_highlight_style", highlightSelectedStyle)) {
            operations.setHighlightSelectedStyle(highlightSelectedStyle[0])
        }
        val interactionEnabled = booleanArrayOf(state.previewSettings.interaction.inputEnabled)
        ImGui.textUnformatted("Interact with widgets:")
        ImGui.sameLine()
        if (ImGui.checkbox("##skin_editor_canvas_interact", interactionEnabled)) {
            operations.setCanvasInteractionEnabled(interactionEnabled[0])
        }
        ImGui.textUnformatted("Canvas: ${state.canvasRect.width.toInt()} x ${state.canvasRect.height.toInt()}")
        ImGui.textWrapped(
            if (state.previewSettings.interaction.inputEnabled) {
                "LMB: interact with widgets. Ctrl + RMB drag: pan. Ctrl + mouse wheel: zoom."
            } else {
                "LMB interaction disabled. Ctrl + RMB drag: pan. Ctrl + mouse wheel: zoom."
            },
        )
        ImGui.textUnformatted("Hover actor: ${state.previewSettings.interaction.hoveredActorPath ?: "<none>"}")
        ImGui.textUnformatted("Focus actor: ${state.previewSettings.interaction.focusedActorPath ?: "<none>"}")
        ImGui.textUnformatted(
            "Cursor: " +
                (
                    state.previewSettings.interaction.cursorCanvasX?.let { x ->
                        val y = state.previewSettings.interaction.cursorCanvasY ?: 0f
                        val stageX = state.previewSettings.interaction.cursorStageX ?: 0f
                        val stageY = state.previewSettings.interaction.cursorStageY ?: 0f
                        "canvas (${x.toInt()}, ${y.toInt()}) | stage (${stageX.toInt()}, ${stageY.toInt()})"
                    } ?: "<none>"
                ),
        )
        state.previewSettings.interaction.lastInputStatus
            ?.let(ImGui::textWrapped)
        ImGui.separator()

        val min = ImGui.cursorScreenPos
        val available = ImGui.contentRegionAvail
        state.canvasRect =
            SkinEditorCanvasRect(
                x = min.x,
                y = min.y,
                width = available.x.coerceAtLeast(1f),
                height = available.y.coerceAtLeast(1f),
            )
        ImGui.invisibleButton("##skin_editor_preview_canvas", ImVec2(state.canvasRect.width, state.canvasRect.height))
        handleCanvasInteraction()
        ImGui.end()
    }

    private fun handleCanvasInteraction() {
        val io = ImGui.io
        val hovered = ImGui.isItemHovered()
        val mouseScreenX = io.mousePos.x
        val mouseScreenY = io.mousePos.y
        val pointerMoved = mouseScreenX != lastPointerScreenX || mouseScreenY != lastPointerScreenY
        lastPointerScreenX = mouseScreenX
        lastPointerScreenY = mouseScreenY

        if (hovered && io.mouseClicked[0]) {
            previewClickPending = true
            previewClickDragDistance = 0f
        }
        if (previewClickPending && io.mouseDown[0]) {
            previewClickDragDistance += hypot(io.mouseDelta.x, io.mouseDelta.y)
        }
        if (hovered && io.keyCtrl && io.mouseDown[1] && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            operations.panPreviewCamera(io.mouseDelta.x, io.mouseDelta.y)
            previewClickPending = false
        }
        if (hovered && io.keyCtrl && io.mouseWheel != 0f) {
            val nextZoom = state.previewSettings.camera.zoom * (1f + io.mouseWheel * 0.1f)
            operations.setPreviewCameraZoom(nextZoom)
            previewClickPending = false
        }
        if (hovered && state.previewSettings.interaction.inputEnabled && !io.keyCtrl) {
            if (pointerMoved) {
                operations.queuePreviewPointerEvent(
                    SkinPreviewPointerEvent(
                        type = SkinPreviewPointerEventType.Move,
                        screenX = mouseScreenX,
                        screenY = mouseScreenY,
                        button = SkinPreviewPointerButton.Left,
                    ),
                )
            }
            if (io.mouseClicked[0]) {
                operations.queuePreviewPointerEvent(
                    SkinPreviewPointerEvent(
                        type = SkinPreviewPointerEventType.Down,
                        screenX = mouseScreenX,
                        screenY = mouseScreenY,
                        button = SkinPreviewPointerButton.Left,
                    ),
                )
            }
            if (io.mouseDown[0] && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
                operations.queuePreviewPointerEvent(
                    SkinPreviewPointerEvent(
                        type = SkinPreviewPointerEventType.Drag,
                        screenX = mouseScreenX,
                        screenY = mouseScreenY,
                        button = SkinPreviewPointerButton.Left,
                    ),
                )
            }
            if (io.mouseReleased[0]) {
                operations.queuePreviewPointerEvent(
                    SkinPreviewPointerEvent(
                        type = SkinPreviewPointerEventType.Up,
                        screenX = mouseScreenX,
                        screenY = mouseScreenY,
                        button = SkinPreviewPointerButton.Left,
                    ),
                )
            }
            if (io.mouseWheel != 0f) {
                operations.queuePreviewPointerEvent(
                    SkinPreviewPointerEvent(
                        type = SkinPreviewPointerEventType.Scroll,
                        screenX = mouseScreenX,
                        screenY = mouseScreenY,
                        button = SkinPreviewPointerButton.Left,
                        scrollAmountY = io.mouseWheel,
                    ),
                )
            }
        } else if (!hovered && !io.mouseDown[0] && !io.mouseDown[1]) {
            operations.clearPreviewInteractionFeedback()
        }
        if (previewClickPending && io.mouseReleased[0]) {
            previewClickPending = false
            previewClickDragDistance = 0f
        }
    }
}
