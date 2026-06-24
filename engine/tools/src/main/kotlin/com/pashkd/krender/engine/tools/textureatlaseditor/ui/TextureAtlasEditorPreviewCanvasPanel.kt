package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorCanvasRect
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.AtlasRegionId
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.textureatlaseditor.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.textureatlaseditor.formatZoomMode
import com.pashkd.krender.engine.tools.textureatlaseditor.hitTestAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.screenToTexturePixelX
import com.pashkd.krender.engine.tools.textureatlaseditor.screenToTexturePixelY
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasNinePatchRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedNinePatchDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedRegionsForPage
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import com.pashkd.krender.engine.ui.editor.UiService
import imgui.ImGui
import imgui.ImGui.isDragging
import imgui.MouseButton
import imgui.SliderFlag
import imgui.WindowFlag
import imgui.api.slider
import imgui.or
import glm_.vec2.Vec2 as ImVec2

class TextureAtlasEditorPreviewCanvasPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
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
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Preview)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Preview, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Preview, layout.title)
        if (!expanded) {
            state.canvasRect = TextureAtlasEditorCanvasRect()
            ImGui.end()
            return
        }

        drawOptionRow()
        ImGui.separator()
        drawActionRow()
        ImGui.separator()
        drawStatusSection()
        ImGui.separator()

        ImGui.beginChild(
            "texture_atlas_editor_preview_canvas_body",
            ImVec2(0f, 0f),
            true,
            WindowFlag.NoScrollbar or WindowFlag.NoScrollWithMouse,
        )
        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        state.canvasRect = TextureAtlasEditorCanvasRect(min.x, min.y, size.x.coerceAtLeast(1f), size.y.coerceAtLeast(1f))

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
                TextureAtlasEditorPreviewOverlays.drawCheckerboard(viewportLayout)
            }
            ImGui.cursorScreenPos = ImVec2(viewportLayout.imageX, viewportLayout.imageY)
            ui.drawTexturePreview(handle, viewportLayout.imageWidth, viewportLayout.imageHeight)
            val regions = state.selectedRegionsForPage()
            val selectedRegion = regions.firstOrNull { region -> region.id == state.selectedRegionId }
            val hoveredRegion = regions.firstOrNull { region -> region.id == state.hoveredRegionId }
            if (state.preview.showGrid) {
                TextureAtlasEditorPreviewOverlays.drawGrid(viewportLayout)
            }
            if (state.preview.showBounds && regions.isNotEmpty()) {
                TextureAtlasEditorPreviewOverlays.drawRegionBounds(regions, viewportLayout, selectedRegion, hoveredRegion)
                selectedRegion?.let { region -> TextureAtlasEditorPreviewOverlays.labelRegion(region, viewportLayout) }
            }
            if (state.preview.showNinePatchGuides) {
                state.selectedNinePatchDocument()?.let { document ->
                    TextureAtlasEditorPreviewOverlays.drawNinePatchGuides(document, viewportLayout)
                }
            }
            ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
            ImGui.invisibleButton("##texture_atlas_editor_preview_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
            handleInteraction(viewportLayout, regions)
        } else {
            wrappedTextLine(state.previewInfo.statusMessage)
        }

        ImGui.endChild()
        ImGui.end()
    }

    private fun drawOptionRow() {
        val checker = booleanArrayOf(state.preview.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##texture_atlas_editor_checker", checker)) {
            operations.setShowCheckerboard(checker[0])
        }
        ImGui.sameLine()
        val grid = booleanArrayOf(state.preview.showGrid)
        if (ImGui.checkbox("Grid##texture_atlas_editor_grid", grid)) {
            operations.setShowGrid(grid[0])
        }
        ImGui.sameLine()
        val bounds = booleanArrayOf(state.preview.showBounds)
        if (ImGui.checkbox("Bounds##texture_atlas_editor_bounds", bounds)) {
            operations.setShowBounds(bounds[0])
        }
        ImGui.sameLine()
        val ninePatchGuides = booleanArrayOf(state.preview.showNinePatchGuides)
        if (ImGui.checkbox("Show Nine-patch Guides##texture_atlas_editor_nine_patch_guides", ninePatchGuides)) {
            operations.setShowNinePatchGuides(ninePatchGuides[0])
        }
    }

    private fun drawActionRow() {
        ImGui.setNextItemWidth(180f)
        if (ImGui.beginCombo("Zoom Mode##texture_atlas_editor_zoom_mode", formatZoomMode(state.preview.zoomMode))) {
            TexturePreviewZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), state.preview.zoomMode == mode)) {
                    operations.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        if (state.preview.zoomMode == TexturePreviewZoomMode.Custom) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(120f)
            if (slider("Custom##texture_atlas_editor_custom_zoom", state.preview::customZoom, 0.05f, 8f, "%.2f", SliderFlag.AlwaysClamp)) {
                operations.setPreviewZoom(state.preview.customZoom)
            }
        }
        if (ImGui.button("Fit##texture_atlas_editor_fit")) {
            operations.fitPreview()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset Camera##texture_atlas_editor_reset_camera")) {
            operations.resetPreviewCamera()
        }
        ImGui.sameLine()
        if (ImGui.button("Focus Selected Region##texture_atlas_editor_focus_region")) {
            operations.fitSelectedRegion()
        }
    }

    private fun handleInteraction(
        viewportLayout: com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewViewportLayout,
        regions: List<com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion>,
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
        if (MouseButton.Right.isDragging() &&
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

    private fun updateCursorMetrics(viewportLayout: com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewViewportLayout) {
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
                com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewZoomMode.Fit -> "Fit"
                else -> "${(state.preview.viewport.zoom * 100f).toInt()}%"
            }
        val ninePatchText =
            when {
                state.selectedNinePatchDocument() != null -> "Nine-patch"
                state.selectedAtlasNinePatchRegion() != null -> "Nine-patch Region"
                else -> "Texture"
            }
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
        textLine("Zoom: $zoomPercent | $ninePatchText: ${state.previewInfo.textureWidth} x ${state.previewInfo.textureHeight} | $cursorText | $regionCursorText")
        textLine("Hovered: $hoveredText | Selected: $selectedText")
        wrappedTextLine("Wheel: zoom only. RMB drag or Pan mode: pan. LMB on region: select.")
    }

    private fun drawStatusSection() {
        drawStatusLine()
    }

    companion object {
        private const val ClickDragThreshold = 6f
    }
}
