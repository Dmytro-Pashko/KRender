package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.formatZoomMode
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.MouseButton
import imgui.SliderFlag
import imgui.WindowFlag
import imgui.api.slider
import imgui.or
import kotlin.math.abs
import glm_.vec2.Vec2 as ImVec2

class EnvironmentSelectedResourcePreviewPanel(
    private val state: EnvironmentEditorState,
    private val resourcePreviewController: EnvironmentResourcePreviewController,
    private val selectedPreviewController: EnvironmentSelectedResourcePreviewController,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private var clickDragDistance = 0f

    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.SelectedResourcePreview)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.SelectedResourcePreview, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.SelectedResourcePreview, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val environment = state.environment
        if (environment == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }
        val model = resourcePreviewController.buildModel(environment)
        val selected = model.selectedItem
        if (selected == null) {
            ImGui.text("Select a face or resource in Resource Inspector.")
            ImGui.end()
            return
        }
        drawToolbar()
        ImGui.separator()
        drawCanvas(selected)
        ImGui.separator()
        drawDetails(model, selected)
        ImGui.end()
    }

    private fun drawToolbar() {
        val previewState = state.resourceInspectorState.selectedResourcePreviewState
        ImGui.setNextItemWidth(160f)
        if (ImGui.beginCombo("Zoom##env_selected_preview_zoom", formatZoomMode(previewState.zoomMode))) {
            TexturePreviewZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), previewState.zoomMode == mode)) {
                    selectedPreviewController.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (previewState.zoomMode == TexturePreviewZoomMode.Custom) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(120f)
            if (slider("Custom##env_selected_preview_custom_zoom", previewState::customZoom, 0.05f, 25f, "%.2f", SliderFlag.AlwaysClamp)) {
                selectedPreviewController.setPreviewZoom(previewState.customZoom)
            }
        }
        if (ImGui.button("Fit##env_selected_preview_fit")) {
            selectedPreviewController.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset##env_selected_preview_reset")) {
            selectedPreviewController.resetPreviewCamera()
        }
        ImGui.sameLine()
        val showGrid = booleanArrayOf(previewState.showGrid)
        if (ImGui.checkbox("Grid##env_selected_preview_grid", showGrid)) {
            previewState.showGrid = showGrid[0]
        }
        ImGui.sameLine()
        val showChecker = booleanArrayOf(previewState.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##env_selected_preview_checker", showChecker)) {
            previewState.showCheckerboard = showChecker[0]
        }
        ImGui.sameLine()
        val showBounds = booleanArrayOf(previewState.showBounds)
        if (ImGui.checkbox("Bounds##env_selected_preview_bounds", showBounds)) {
            previewState.showBounds = showBounds[0]
        }
    }

    private fun drawCanvas(selected: EnvironmentResourceCanvasItem) {
        ImGui.beginChild(
            "environment_selected_resource_canvas",
            ImVec2(0f, 260f),
            true,
            WindowFlag.NoScrollbar or WindowFlag.NoScrollWithMouse,
        )
        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        val rect =
            com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewCanvasRect(
                x = min.x,
                y = min.y,
                width = size.x.coerceAtLeast(1f),
                height = size.y.coerceAtLeast(1f),
            )
        val previewState = state.resourceInspectorState.selectedResourcePreviewState
        val width = selected.sourceRegion?.width ?: selected.width ?: 1
        val height = selected.sourceRegion?.height ?: selected.height ?: 1
        val layout =
            computeTexturePreviewViewportLayout(
                rect = rect,
                textureWidth = width.coerceAtLeast(1),
                textureHeight = height.coerceAtLeast(1),
                previewState = previewState,
            )
        if (previewState.showCheckerboard) {
            TexturePreviewOverlays.drawCheckerboard(layout)
        }
        selected.previewHandle?.let { handle ->
            ImGui.windowDrawList.addImage(
                handle.id,
                ImVec2(layout.imageX, layout.imageY),
                ImVec2(layout.imageX + layout.imageWidth, layout.imageY + layout.imageHeight),
                ImVec2(selected.sourceRegion?.u0 ?: handle.u0, selected.sourceRegion?.v0 ?: handle.v0),
                ImVec2(selected.sourceRegion?.u1 ?: handle.u1, selected.sourceRegion?.v1 ?: handle.v1),
            )
        } ?: ImGui.text("Preview unavailable for selected resource.")
        if (previewState.showGrid) {
            TexturePreviewOverlays.drawGrid(layout, spacingPixels = previewState.gridSpacingPixels)
        }
        if (previewState.showBounds) {
            ImGui.windowDrawList.addRect(
                ImVec2(layout.imageX, layout.imageY),
                ImVec2(layout.imageX + layout.imageWidth, layout.imageY + layout.imageHeight),
                0xFFFFFFFF.toInt(),
                0f,
                thickness = 2f,
            )
        }
        ImGui.cursorScreenPos = ImVec2(rect.x, rect.y)
        ImGui.invisibleButton("##environment_selected_resource_canvas_hit", ImVec2(rect.width, rect.height))
        if (ImGui.isItemHovered()) {
            val io = ImGui.io
            if (io.mouseWheel != 0f) {
                selectedPreviewController.setPreviewZoom(previewState.customZoom * (1f + io.mouseWheel * 0.1f))
            }
            if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
                selectedPreviewController.panPreview(io.mouseDelta.x, io.mouseDelta.y)
                clickDragDistance += abs(io.mouseDelta.x) + abs(io.mouseDelta.y)
            }
            if (io.mouseClicked[0]) {
                clickDragDistance = 0f
            }
        }
        ImGui.endChild()
    }

    private fun drawDetails(
        model: EnvironmentResourcePreviewModel,
        selected: EnvironmentResourceCanvasItem,
    ) {
        ImGui.text("Resource Kind: ${selected.resourceMode.label()}")
        ImGui.text("Face: ${selected.label}")
        selected.mipLevel?.let { ImGui.text("Mip Level: $it") }
        selected.roughness?.let { ImGui.text("Roughness: ${"%.3f".format(it)}") }
        ImGui.text("Source Path: ${selected.manifestPath ?: "<none>"}")
        ImGui.text("Resolved Path: ${selected.resolvedPath ?: "<none>"}")
        selected.sourceRegion?.let { region ->
            ImGui.text("Region Rect: x=${region.x}, y=${region.y}, w=${region.width}, h=${region.height}")
            ImGui.text("UV Range: (${region.u0}, ${region.v0}) -> (${region.u1}, ${region.v1})")
        } ?: run {
            ImGui.text("Region Rect: <full face>")
            ImGui.text("UV Range: 0.0, 0.0 -> 1.0, 1.0")
        }
        ImGui.text("Texture Size: ${(selected.width ?: 0)} x ${(selected.height ?: 0)}")
        val warnings = EnvironmentResourceDiagnostics.buildSelectedPreviewWarnings(model)
        if (warnings.isNotEmpty()) {
            ImGui.text("Warnings")
            warnings.forEach(ImGui::bulletText)
        }
    }
}
