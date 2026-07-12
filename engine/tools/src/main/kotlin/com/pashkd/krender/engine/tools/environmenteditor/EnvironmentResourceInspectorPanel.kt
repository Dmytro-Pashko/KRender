package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegionSelection
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewCursorInfo
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.formatZoomMode
import com.pashkd.krender.engine.tools.common.texturepreview.hitTestTexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.packColor
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

@Suppress("LongMethod", "CyclomaticComplexMethod")
class EnvironmentResourceInspectorPanel(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentResourcePreviewController,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private var clickDragDistance = 0f
    private var cursorText: String = "Cursor: <outside>"

    @Suppress("ReturnCount")
    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.CubemapPreview)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.CubemapPreview, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.CubemapPreview, layout.title)
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

        val model = controller.buildModel(environment)
        if (model.items.isEmpty()) {
            drawToolbar(model)
            ImGui.separator()
            ImGui.text("Preview unavailable for ${state.resourceInspectorState.selectedResourceMode.label()}.")
            model.diagnostics.forEach(ImGui::textWrapped)
            ImGui.end()
            return
        }
        drawToolbar(model)
        ImGui.separator()
        drawSelectionSummary(model)
        ImGui.separator()
        drawCanvas(model)
        ImGui.separator()
        drawSelectedItemDetails(model)
        ImGui.end()
    }

    private fun drawToolbar(model: EnvironmentResourcePreviewModel) {
        val inspectorState = state.resourceInspectorState
        ImGui.setNextItemWidth(220f)
        if (ImGui.beginCombo("Mode##env_resource_mode", inspectorState.selectedResourceMode.label())) {
            EnvironmentResourceMode.entries.filterNot { it == EnvironmentResourceMode.ImportedSkyboxSource }.forEach { mode ->
                if (ImGui.selectable(mode.label(), inspectorState.selectedResourceMode == mode)) {
                    controller.setMode(mode)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(160f)
        val previewState = inspectorState.resourcePreviewState
        if (ImGui.beginCombo("Zoom##env_resource_zoom", formatZoomMode(previewState.zoomMode))) {
            TexturePreviewZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), previewState.zoomMode == mode)) {
                    controller.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (previewState.zoomMode == TexturePreviewZoomMode.Custom) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(120f)
            if (slider("Custom##env_resource_custom_zoom", previewState::customZoom, 0.05f, 25f, "%.2f", SliderFlag.AlwaysClamp)) {
                controller.setPreviewZoom(previewState.customZoom)
            }
        }
        if (ImGui.button("Fit##env_resource_fit")) {
            controller.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset##env_resource_reset")) {
            controller.resetPreviewCamera()
        }
        ImGui.sameLine()
        val showGrid = booleanArrayOf(previewState.showGrid)
        if (ImGui.checkbox("Grid##env_resource_grid", showGrid)) {
            previewState.showGrid = showGrid[0]
        }
        ImGui.sameLine()
        val showChecker = booleanArrayOf(previewState.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##env_resource_checker", showChecker)) {
            previewState.showCheckerboard = showChecker[0]
        }
        ImGui.sameLine()
        val showBounds = booleanArrayOf(previewState.showBounds)
        if (ImGui.checkbox("Bounds##env_resource_bounds", showBounds)) {
            previewState.showBounds = showBounds[0]
        }
        if (previewState.showGrid) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(100f)
            slider("Grid##env_resource_grid_size", previewState::gridSpacingPixels, 4, 128, "%d", SliderFlag.AlwaysClamp)
        }
        if (model.diagnostics.isNotEmpty()) {
            ImGui.textWrapped(model.diagnostics.joinToString(" | "))
        }
    }

    private fun drawSelectionSummary(model: EnvironmentResourcePreviewModel) {
        val selected = model.selectedItem
        ImGui.text("Mode: ${model.mode.label()}")
        ImGui.text(cursorText)
        ImGui.textWrapped(model.statusMessage)
        selected?.let {
            ImGui.text(
                buildString {
                    append("Selected: ${it.label}")
                    it.mipLevel?.let { mip -> append(" | Mip: $mip") }
                    it.roughness?.let { roughness -> append(" | Roughness: ${"%.2f".format(roughness)}") }
                },
            )
        } ?: ImGui.text("Selected: <none>")
    }

    private fun drawCanvas(model: EnvironmentResourcePreviewModel) {
        ImGui.beginChild(
            "environment_resource_canvas",
            ImVec2(0f, 280f),
            true,
            WindowFlag.NoScrollbar or WindowFlag.NoScrollWithMouse,
        )
        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        val canvasRect =
            com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewCanvasRect(
                min.x,
                min.y,
                size.x.coerceAtLeast(1f),
                size.y.coerceAtLeast(1f),
            )
        val previewState = state.resourceInspectorState.resourcePreviewState
        val layout =
            computeTexturePreviewViewportLayout(
                rect = canvasRect,
                textureWidth = model.contentWidth.coerceAtLeast(1),
                textureHeight = model.contentHeight.coerceAtLeast(1),
                previewState = previewState,
            )
        if (previewState.showCheckerboard) {
            TexturePreviewOverlays.drawCheckerboard(layout)
        }
        if (model.drawItemsAsOverlayRegions) {
            model.canvasPreviewHandle?.let { handle ->
                ImGui.windowDrawList.addImage(
                    handle.id,
                    ImVec2(layout.imageX, layout.imageY),
                    ImVec2(layout.imageX + layout.imageWidth, layout.imageY + layout.imageHeight),
                    ImVec2(handle.u0, handle.v0),
                    ImVec2(handle.u1, handle.v1),
                )
            }
        }
        if (previewState.showGrid) {
            TexturePreviewOverlays.drawGrid(layout, spacingPixels = previewState.gridSpacingPixels, color = packPreviewColor(previewState.gridColor))
        }
        if (!model.drawItemsAsOverlayRegions) {
            model.items.forEach { item ->
                val rect =
                    com.pashkd.krender.engine.tools.common.texturepreview
                        .textureRegionScreenRect(item.region, layout)
                val handle = item.previewHandle
                if (handle != null) {
                    ImGui.windowDrawList.addImage(
                        handle.id,
                        ImVec2(rect.minX, rect.minY),
                        ImVec2(rect.maxX, rect.maxY),
                        ImVec2(handle.u0, handle.v0),
                        ImVec2(handle.u1, handle.v1),
                    )
                } else {
                    ImGui.windowDrawList.addRect(
                        ImVec2(rect.minX, rect.minY),
                        ImVec2(rect.maxX, rect.maxY),
                        0x99FFFFFF.toInt(),
                        0f,
                        thickness = 1.5f,
                    )
                    ImGui.windowDrawList.addText(ImVec2(rect.minX + 8f, rect.minY + 8f), 0xFFFFFFFF.toInt(), item.label)
                }
            }
        }
        if (previewState.showBounds) {
            TexturePreviewOverlays.drawRegionBounds(
                regions = model.items.map(EnvironmentResourceCanvasItem::region),
                layout = layout,
                selection =
                    TexturePreviewRegionSelection(
                        selectedId = state.resourceInspectorState.selectedRegionOrFace,
                        hoveredId = state.resourceInspectorState.hoveredRegionOrFace,
                    ),
            )
            model.selectedItem?.let { selected ->
                TexturePreviewOverlays.labelRegion(selected.region, layout)
            }
        }

        ImGui.cursorScreenPos = ImVec2(canvasRect.x, canvasRect.y)
        ImGui.invisibleButton("##environment_resource_canvas_hit", ImVec2(canvasRect.width, canvasRect.height))
        handleCanvasInteraction(model, layout)
        ImGui.endChild()
    }

    private fun drawSelectedItemDetails(model: EnvironmentResourcePreviewModel) {
        val selected = model.selectedItem
        if (selected == null) {
            ImGui.text("No selected resource.")
            return
        }
        ImGui.text("Selected Resource")
        ImGui.text("Kind: ${selected.resourceMode.label()}")
        ImGui.text("Face: ${selected.label}")
        selected.mipLevel?.let { ImGui.text("Mip Level: $it") }
        selected.roughness?.let { ImGui.text("Roughness: ${"%.3f".format(it)}") }
        ImGui.text("Manifest Path: ${selected.manifestPath ?: "<none>"}")
        ImGui.text("Resolved Path: ${selected.resolvedPath ?: "<none>"}")
        ImGui.text("Exists: ${if (selected.exists) "yes" else "no"}")
        ImGui.text("Resolution: ${selected.width ?: 0} x ${selected.height ?: 0}")
        ImGui.text("Format: ${selected.format ?: "<unknown>"}")
        if (selected.warnings.isNotEmpty()) {
            ImGui.text("Warnings")
            selected.warnings.forEach(ImGui::bulletText)
        }
    }

    private fun handleCanvasInteraction(
        model: EnvironmentResourcePreviewModel,
        layout: com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewViewportLayout,
    ) {
        if (!ImGui.isItemHovered()) {
            controller.setHoveredItem(null)
            cursorText = "Cursor: <outside>"
            return
        }
        val io = ImGui.io
        if (io.keyCtrl && io.mouseWheel != 0f) {
            controller.setPreviewZoom(state.resourceInspectorState.resourcePreviewState.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            controller.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            clickDragDistance += abs(io.mouseDelta.x) + abs(io.mouseDelta.y)
        }
        val hovered =
            hitTestTexturePreviewRegion(
                regions = model.items.map(EnvironmentResourceCanvasItem::region),
                layout = layout,
                mouseX = io.mousePos.x,
                mouseY = io.mousePos.y,
            )
        controller.setHoveredItem(hovered?.id)
        val cursor =
            computeTexturePreviewCursorInfo(
                screenX = io.mousePos.x,
                screenY = io.mousePos.y,
                layout = layout,
                textureWidth = model.contentWidth.coerceAtLeast(1),
                textureHeight = model.contentHeight.coerceAtLeast(1),
            )
        cursorText =
            if (cursor.isInside) {
                "Cursor: ${cursor.textureX}, ${cursor.textureY}"
            } else {
                "Cursor: <outside>"
            }
        if (io.mouseClicked[0] && clickDragDistance < ClickDragThreshold) {
            controller.setSelectedItem(hovered?.id)
        }
        if (io.mouseClicked[0]) {
            clickDragDistance = 0f
        }
    }

    private companion object {
        private const val ClickDragThreshold = 6f
    }

    private fun packPreviewColor(color: com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewColor): Int =
        packColor(
            (color.red * 255f).toInt().coerceIn(0, 255),
            (color.green * 255f).toInt().coerceIn(0, 255),
            (color.blue * 255f).toInt().coerceIn(0, 255),
            (color.alpha * 255f).toInt().coerceIn(0, 255),
        )
}
