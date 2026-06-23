package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerCanvasRect
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerToolMode
import com.pashkd.krender.engine.tools.texturemanager.AtlasRegionId
import com.pashkd.krender.engine.tools.texturemanager.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.texturemanager.hitTestAtlasRegion
import com.pashkd.krender.engine.tools.texturemanager.screenToTexturePixelX
import com.pashkd.krender.engine.tools.texturemanager.screenToTexturePixelY
import com.pashkd.krender.engine.tools.texturemanager.selectedNinePatchDocument
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
    private var pendingSelectRegionId: AtlasRegionId? = null
    private var pendingSelectWasDoubleClick = false
    private var clickDragDistance = 0f
    private var cursorTextureX: Int? = null
    private var cursorTextureY: Int? = null
    private var cursorRegionX: Int? = null
    private var cursorRegionY: Int? = null

    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Preview)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Preview, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Preview, layout.title)
        if (!expanded) {
            state.canvasRect = TextureManagerCanvasRect()
            ImGui.end()
            return
        }

        drawStatusLine()
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
            if (state.preview.showNinePatchGuides) {
                state.selectedNinePatchDocument()?.let { document ->
                    TextureManagerPreviewOverlays.drawNinePatchGuides(document, viewportLayout)
                }
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
            cursorTextureX = null
            cursorTextureY = null
            cursorRegionX = null
            cursorRegionY = null
            resetPendingSelection()
            return
        }
        updateCursorMetrics(viewportLayout)
        if (io.mouseWheel != 0f) {
            operations.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if ((state.toolMode == TextureManagerToolMode.Pan || MouseButton.Right.isDragging()) &&
            (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)
        ) {
            operations.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            clickDragDistance += kotlin.math.abs(io.mouseDelta.x) + kotlin.math.abs(io.mouseDelta.y)
            pendingSelectWasDoubleClick = false
            pendingSelectRegionId = null
        }
        val hoveredRegion =
            if (regions.isNotEmpty()) {
                hitTestAtlasRegion(regions, viewportLayout, io.mousePos.x, io.mousePos.y)
            } else {
                null
            }
        operations.setHoveredRegion(hoveredRegion?.id)
        if (hoveredRegion?.xy != null && cursorTextureX != null && cursorTextureY != null) {
            cursorRegionX = cursorTextureX!! - hoveredRegion.xy.first
            cursorRegionY = cursorTextureY!! - hoveredRegion.xy.second
        } else {
            cursorRegionX = null
            cursorRegionY = null
        }
        if (io.mouseClicked[0]) {
            pendingSelectRegionId = hoveredRegion?.id
            pendingSelectWasDoubleClick = ImGui.run { MouseButton.Left.isDoubleClicked }
            clickDragDistance = 0f
        }
        if (pendingSelectRegionId != null && io.mouseReleased[0] && clickDragDistance < ClickDragThreshold) {
            operations.selectRegion(pendingSelectRegionId)
            if (pendingSelectWasDoubleClick) {
                operations.fitSelectedRegion()
            }
            resetPendingSelection()
        } else if (io.mouseReleased[0]) {
            resetPendingSelection()
        }
    }

    private fun updateCursorMetrics(viewportLayout: com.pashkd.krender.engine.tools.texturemanager.TexturePreviewViewportLayout) {
        val io = ImGui.io
        val textureX = screenToTexturePixelX(io.mousePos.x, viewportLayout)
        val textureY = screenToTexturePixelY(io.mousePos.y, viewportLayout)
        cursorTextureX =
            textureX
                .takeIf { x -> x >= 0f && x < state.previewInfo.textureWidth }
                ?.toInt()
        cursorTextureY =
            textureY
                .takeIf { y -> y >= 0f && y < state.previewInfo.textureHeight }
                ?.toInt()
    }

    private fun resetPendingSelection() {
        pendingSelectRegionId = null
        pendingSelectWasDoubleClick = false
        clickDragDistance = 0f
    }

    private fun drawStatusLine() {
        val zoomPercent =
            when (state.preview.zoomMode) {
                com.pashkd.krender.engine.tools.texturemanager.TexturePreviewZoomMode.Fit -> "Fit"
                else -> "${(state.preview.viewport.zoom * 100f).toInt()}%"
            }
        val ninePatchText = if (state.selectedNinePatchDocument() != null) "Nine-patch" else "Texture"
        val cursorText =
            if (cursorTextureX != null && cursorTextureY != null) {
                "Cursor: ${cursorTextureX}, ${cursorTextureY}"
            } else {
                "Cursor: <outside>"
            }
        val hoveredText = state.hoveredRegionId?.regionName ?: "<none>"
        val selectedText = state.selectedRegionId?.regionName ?: "<none>"
        val regionCursorText =
            if (cursorRegionX != null && cursorRegionY != null) {
                "Region: ${cursorRegionX}, ${cursorRegionY}"
            } else {
                "Region: <n/a>"
            }
        textLine("Zoom: $zoomPercent")
        ImGui.sameLine()
        textLine("$ninePatchText: ${state.previewInfo.textureWidth} x ${state.previewInfo.textureHeight}")
        ImGui.sameLine()
        textLine(cursorText)
        ImGui.sameLine()
        textLine(regionCursorText)
        textLine("Hovered: $hoveredText")
        ImGui.sameLine()
        textLine("Selected: $selectedText")
    }

    companion object {
        private const val ClickDragThreshold = 6f
    }
}
