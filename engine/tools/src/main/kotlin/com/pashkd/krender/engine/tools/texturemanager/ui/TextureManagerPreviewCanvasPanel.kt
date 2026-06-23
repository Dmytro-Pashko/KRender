package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerCanvasRect
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerToolMode
import com.pashkd.krender.engine.tools.texturemanager.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.texturemanager.formatZoomMode
import com.pashkd.krender.engine.tools.texturemanager.hitTestAtlasRegion
import com.pashkd.krender.engine.tools.texturemanager.selectedRegionsForPage
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import com.pashkd.krender.engine.ui.editor.UiService
import imgui.ImGui
import imgui.ImGui.isDragging
import imgui.MouseButton
import glm_.vec2.Vec2 as ImVec2

class TextureManagerPreviewCanvasPanel(
    private val state: TextureManagerState,
    private val operations: TextureManagerOperations,
    private val ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Preview)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Preview, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Preview, layout.title)
        if (!expanded) {
            state.canvasRect = TextureManagerCanvasRect()
            ImGui.end()
            return
        }

        textLine("Zoom: ${formatZoomMode(state.preview.zoomMode)}")
        ImGui.sameLine()
        textLine("Texture: ${state.previewInfo.textureWidth} x ${state.previewInfo.textureHeight}")
        ImGui.sameLine()
        val cursorText =
            state.hoveredRegionId?.let { hovered ->
                "Hovered: ${hovered.regionName}"
            } ?: "Hovered: <none>"
        textLine(cursorText)
        ImGui.separator()

        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        state.canvasRect = TextureManagerCanvasRect(min.x, min.y, size.x.coerceAtLeast(1f), size.y.coerceAtLeast(1f))
        ImGui.beginChild("texture_manager_preview_canvas_body", ImVec2(0f, 0f), true)
        ImGui.invisibleButton("##texture_manager_preview_canvas_hit", ImVec2(state.canvasRect.width - 8f, state.canvasRect.height - 8f))

        val handle = state.previewInfo.texturePreviewHandle
        if (handle != null && state.previewInfo.textureWidth > 0 && state.previewInfo.textureHeight > 0) {
            val viewportLayout =
                computeTexturePreviewViewportLayout(
                    rect = state.canvasRect,
                    textureWidth = state.previewInfo.textureWidth,
                    textureHeight = state.previewInfo.textureHeight,
                    previewState = state.preview,
                )
            if (state.preview.showCheckerboard) {
                TextureManagerPreviewOverlays.drawCheckerboard(viewportLayout)
            }
            val screen = ImGui.cursorScreenPos
            ImGui.cursorScreenPos = ImVec2(viewportLayout.imageX, viewportLayout.imageY)
            ui.drawTexturePreview(handle, viewportLayout.imageWidth, viewportLayout.imageHeight)
            ImGui.cursorScreenPos = screen
            val regions = state.selectedRegionsForPage()
            val selectedRegion = regions.firstOrNull { region -> region.id == state.selectedRegionId }
            val hoveredRegion = regions.firstOrNull { region -> region.id == state.hoveredRegionId }
            if (state.preview.showGrid) {
                TextureManagerPreviewOverlays.drawGrid(viewportLayout)
            }
            if (state.preview.showBounds && regions.isNotEmpty()) {
                TextureManagerPreviewOverlays.drawRegionBounds(regions, viewportLayout, selectedRegion, hoveredRegion)
                selectedRegion?.let { region -> TextureManagerPreviewOverlays.labelRegion(region, viewportLayout) }
            }
            handleInteraction(viewportLayout, regions)
        } else {
            ImGui.textWrapped(state.previewInfo.statusMessage)
        }

        ImGui.endChild()
        ImGui.end()
    }

    private fun handleInteraction(
        viewportLayout: com.pashkd.krender.engine.tools.texturemanager.TexturePreviewViewportLayout,
        regions: List<com.pashkd.krender.engine.tools.texturemanager.TextureAtlasRegion>,
    ) {
        val io = ImGui.io
        val hovered = ImGui.isItemHovered()
        if (!hovered) {
            operations.setHoveredRegion(null)
            return
        }
        if (io.mouseWheel != 0f) {
            operations.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if ((state.toolMode == TextureManagerToolMode.Pan || MouseButton.Right.isDragging()) &&
            (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)
        ) {
            operations.panPreview(io.mouseDelta.x, io.mouseDelta.y)
        }
        val hoveredRegion =
            if (regions.isNotEmpty()) {
                hitTestAtlasRegion(regions, viewportLayout, io.mousePos.x, io.mousePos.y)
            } else {
                null
            }
        operations.setHoveredRegion(hoveredRegion?.id)
        if (io.mouseClicked[0] && hoveredRegion != null) {
            operations.selectRegion(hoveredRegion.id)
        }
    }
}
