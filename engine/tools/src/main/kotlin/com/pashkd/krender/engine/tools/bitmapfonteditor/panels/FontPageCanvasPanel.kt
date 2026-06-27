package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.io.layoutSampleText
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.FontPreviewOverlays
import com.pashkd.krender.engine.tools.common.canvas.CanvasOverlays
import com.pashkd.krender.engine.tools.common.canvas.CanvasRect
import com.pashkd.krender.engine.tools.common.canvas.CanvasZoomMode
import com.pashkd.krender.engine.tools.common.canvas.computeCanvasViewportLayout
import com.pashkd.krender.engine.tools.common.canvas.packColor
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
import kotlin.math.roundToInt
import glm_.vec2.Vec2 as ImVec2

class FontPageCanvasPanel(
    private val state: BitmapFontEditorState,
    private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val sampleBuf = ByteArray(256)
    private var sampleSynced = false
    private var clickDragDistance = 0f
    private var cursorTextureX: Int? = null
    private var cursorTextureY: Int? = null

    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    override fun draw() {
        val panelLayout = layout.panels.getValue(BitmapFontEditorPanelIds.Preview)
        val expanded = beginImGuiPanel(BitmapFontEditorPanelIds.Preview, panelLayout, layoutTracker)
        eventLogger.observe(BitmapFontEditorPanelIds.Preview, panelLayout.title)
        if (!expanded) {
            state.canvasRect = CanvasRect()
            ImGui.end()
            return
        }

        drawCanvasModeRow()
        ImGui.separator()
        drawOptionRow()
        ImGui.separator()
        drawActionRow()
        ImGui.separator()
        drawStatusSection()
        ImGui.separator()

        ImGui.beginChild(
            "bfe_preview_canvas_body",
            ImVec2(0f, 0f),
            true,
            WindowFlag.NoScrollbar or WindowFlag.NoScrollWithMouse,
        )
        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        state.canvasRect = CanvasRect(min.x, min.y, size.x.coerceAtLeast(1f), size.y.coerceAtLeast(1f))

        val document = state.document
        if (document == null || !document.readable) {
            clearCursorMetrics()
            textLine("Open a .fnt file or generate a bitmap font to preview.")
            document?.diagnostics?.take(5)?.forEach { diag -> textLine("${diag.severity.name}: ${diag.message}") }
            ImGui.endChild()
            ImGui.end()
            return
        }

        val handle = state.texturePreviewHandle
        val sampleLayout = layoutSampleText(state.sampleText, document)
        val previewWidth = if (state.showSampleTextPreview) sampleLayout.boundsWidth.coerceAtLeast(1) else state.textureWidth
        val previewHeight = if (state.showSampleTextPreview) sampleLayout.boundsHeight.coerceAtLeast(1) else state.textureHeight

        if (handle != null && previewWidth > 0 && previewHeight > 0) {
            val viewportLayout =
                computeCanvasViewportLayout(
                    rect = state.canvasRect,
                    contentWidth = previewWidth,
                    contentHeight = previewHeight,
                    previewState = state.preview,
                )
            if (state.preview.showCheckerboard) {
                CanvasOverlays.drawCheckerboard(viewportLayout)
            }
            if (state.showSampleTextPreview) {
                FontPreviewOverlays.drawSampleText(
                    handle = handle,
                    layout = viewportLayout,
                    sampleLayout = sampleLayout,
                    tintColor = packColor(255, 255, 255, 255),
                )
            } else {
                val drawList = ImGui.windowDrawList
                drawList.addImage(
                    handle.id,
                    ImVec2(viewportLayout.imageX, viewportLayout.imageY),
                    ImVec2(viewportLayout.imageX + viewportLayout.imageWidth, viewportLayout.imageY + viewportLayout.imageHeight),
                    ImVec2(handle.u0, handle.v0),
                    ImVec2(handle.u1, handle.v1),
                )
            }
            if (state.preview.showGrid) {
                CanvasOverlays.drawGrid(viewportLayout, spacingPixels = state.preview.gridSpacingPixels)
            }
            if (!state.showSampleTextPreview && state.showGlyphBounds) {
                val pageId = document.pages.getOrNull(state.selectedPageIndex)?.id ?: 0
                val pageGlyphs = document.glyphs.filter { it.page == pageId }
                FontPreviewOverlays.drawGlyphBounds(
                    glyphs = pageGlyphs,
                    layout = viewportLayout,
                    selectedGlyphId = state.glyphSelection.selectedGlyphId,
                    hoveredGlyphId = state.glyphSelection.hoveredGlyphId,
                )
            }

            ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
            ImGui.invisibleButton("##bfe_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
            handleCanvasInteraction(document, viewportLayout)
        } else {
            clearCursorMetrics()
            if (handle == null && state.textureWidth > 0) {
                textLine("Loading page texture...")
            } else {
                textLine("No preview available.")
            }
        }

        ImGui.endChild()
        ImGui.end()
    }

    private fun drawCanvasModeRow() {
        ImGui.setNextItemWidth(240f)
        val currentMode = if (state.showSampleTextPreview) SampleTextModeLabel else FontPageModeLabel
        if (ImGui.beginCombo("Canvas Mode##bfe_canvas_mode", currentMode)) {
            val sampleMode = state.showSampleTextPreview
            if (ImGui.selectable(FontPageModeLabel, !sampleMode)) {
                controller.setSampleTextPreviewEnabled(false)
            }
            if (ImGui.selectable(SampleTextModeLabel, sampleMode)) {
                controller.setSampleTextPreviewEnabled(true)
            }
            ImGui.endCombo()
        }
        tooltipOnHover("Switches the preview canvas between the full font page and sample text rendering.")
    }

    private fun drawOptionRow() {
        val showGlyphs = booleanArrayOf(state.showGlyphBounds)
        if (ImGui.checkbox("Highlight Selected Glyph##bfe_show_glyphs", showGlyphs)) {
            controller.setShowGlyphBounds(showGlyphs[0])
        }
        tooltipOnHover("Highlights the selected or hovered glyph and shows its bounds on the current page.")
        ImGui.sameLine()
        val showChecker = booleanArrayOf(state.preview.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##bfe_checker", showChecker)) {
            state.preview.showCheckerboard = showChecker[0]
        }
        tooltipOnHover("Shows a checkerboard background behind transparent font pixels.")
        ImGui.sameLine()
        val showGrid = booleanArrayOf(state.preview.showGrid)
        if (ImGui.checkbox("Grid##bfe_grid", showGrid)) {
            state.preview.showGrid = showGrid[0]
        }
        tooltipOnHover("Shows a pixel grid over the preview to inspect glyph alignment.")
        if (state.preview.showGrid) {
            ImGui.sameLine()
            drawGridSizeCombo()
        }
        if (!sampleSynced || readBuffer(sampleBuf) != state.sampleText) {
            writeBuffer(sampleBuf, state.sampleText)
            sampleSynced = true
        }
        textLine("Sample Text")
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
        if (ImGui.inputText("##bfe_sample_text", sampleBuf)) {
            controller.setSampleText(readBuffer(sampleBuf))
        }
        tooltipOnHover("Text used when Sample Preview is enabled.")
    }

    private fun drawActionRow() {
        val document = state.document
        if (document != null && document.pages.size > 1) {
            val currentPageName = document.pages.getOrNull(state.selectedPageIndex)?.file ?: "page 0"
            ImGui.setNextItemWidth(300f)
            if (ImGui.beginCombo("Font Page##bfe_action_page", currentPageName)) {
                document.pages.forEachIndexed { index, page ->
                    if (ImGui.selectable("${page.id}: ${page.file}", state.selectedPageIndex == index)) {
                        state.selectedPageIndex = index
                    }
                }
                ImGui.endCombo()
            }
            tooltipOnHover("Selects which bitmap-font page texture is shown in the preview.")
        }
        drawZoomControls()
        if (ImGui.button("Fit##bfe_fit")) {
            controller.fitPreview()
        }
        tooltipOnHover("Fits the current preview content inside the canvas.")
        ImGui.sameLine()
        if (ImGui.button("Reset Camera##bfe_reset_camera")) {
            controller.resetPreviewCamera()
        }
        tooltipOnHover("Resets preview pan and zoom to the default camera state.")
        ImGui.sameLine()
        if (ImGui.button("Focus Selected Char##bfe_focus_char")) {
            controller.focusSelectedGlyph()
        }
        tooltipOnHover("Centers and zooms the preview around the selected glyph.")
    }

    private fun drawStatusSection() {
        val zoomText =
            when (state.preview.zoomMode) {
                CanvasZoomMode.Fit -> "Fit"
                else -> "${(state.preview.viewport.zoom * 100f).roundToInt()}%"
            }
        val previewMode = if (state.showSampleTextPreview) "Sample Text" else "Full Font"
        val cursorText =
            if (cursorTextureX != null && cursorTextureY != null) {
                "$cursorTextureX, $cursorTextureY"
            } else {
                "<outside>"
            }
        val hoveredText = glyphLabel(state.glyphSelection.hoveredGlyphId)
        val selectedText = glyphLabel(state.glyphSelection.selectedGlyphId)
        textLine("Mode: $previewMode | Zoom: $zoomText | Page: ${state.selectedPageIndex} | Cursor: $cursorText")
        textLine("Hovered: $hoveredText | Selected: $selectedText | Wheel: zoom | RMB drag: pan")
        wrappedTextLine("Hints: LMB on glyph selects it and syncs the Glyph List. Use Fit, Reset Camera, and Focus Selected Char to navigate large pages.")
    }

    private fun handleCanvasInteraction(
        document: com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument,
        viewportLayout: com.pashkd.krender.engine.tools.common.canvas.CanvasViewportLayout,
    ) {
        if (!ImGui.isItemHovered()) {
            state.glyphSelection.hoveredGlyphId = null
            clearCursorMetrics()
            return
        }
        val io = ImGui.io
        if (io.mouseWheel != 0f) {
            controller.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            controller.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            clickDragDistance += abs(io.mouseDelta.x) + abs(io.mouseDelta.y)
        }

        cursorTextureX = ((io.mousePos.x - viewportLayout.imageX) / viewportLayout.effectiveZoom).roundToInt()
        cursorTextureY = ((io.mousePos.y - viewportLayout.imageY) / viewportLayout.effectiveZoom).roundToInt()

        if (!state.showSampleTextPreview && state.showGlyphBounds) {
            val pageId = document.pages.getOrNull(state.selectedPageIndex)?.id ?: 0
            val pageGlyphs = document.glyphs.filter { it.page == pageId }
            val hoveredGlyph =
                FontPreviewOverlays.hitTestGlyph(
                    glyphs = pageGlyphs,
                    layout = viewportLayout,
                    screenX = io.mousePos.x,
                    screenY = io.mousePos.y,
                )
            state.glyphSelection.hoveredGlyphId = hoveredGlyph?.id
            if (io.mouseClicked[0] && clickDragDistance < ClickDragThreshold) {
                controller.selectGlyph(hoveredGlyph?.id, revealInList = hoveredGlyph != null)
            }
            if (io.mouseClicked[0]) {
                clickDragDistance = 0f
            }
        } else {
            state.glyphSelection.hoveredGlyphId = null
        }
    }

    private fun drawGridSizeCombo() {
        ImGui.setNextItemWidth(120f)
        val currentValue = state.preview.gridSpacingPixels
        if (ImGui.beginCombo("Grid Size##bfe_grid_size", currentValue.toString())) {
            GridSpacingOptions.forEach { option ->
                if (ImGui.selectable(option.toString(), option == currentValue)) {
                    state.preview.gridSpacingPixels = option
                }
            }
            ImGui.endCombo()
        }
        tooltipOnHover("Sets the spacing of the preview grid in pixels.")
    }

    private fun drawZoomControls() {
        ImGui.setNextItemWidth(160f)
        if (ImGui.beginCombo("Zoom##bfe_zoom_mode", formatZoomMode(state.preview.zoomMode))) {
            CanvasZoomMode.entries.forEach { mode ->
                if (ImGui.selectable(formatZoomMode(mode), state.preview.zoomMode == mode)) {
                    controller.setZoomMode(mode)
                }
            }
            ImGui.endCombo()
        }
        tooltipOnHover("Switches between fit, preset, and custom zoom levels.")
        if (state.preview.zoomMode == CanvasZoomMode.Custom) {
            ImGui.sameLine()
            ImGui.setNextItemWidth(140f)
            if (slider("Custom##bfe_custom_zoom", state.preview::customZoom, 0.05f, 50f, "%.2f", SliderFlag.AlwaysClamp)) {
                controller.setPreviewZoom(state.preview.customZoom)
            }
            tooltipOnHover("Custom zoom ranges from 5% to 5000%.")
        }
    }

    private fun formatZoomMode(mode: CanvasZoomMode): String =
        when (mode) {
            CanvasZoomMode.Fit -> "Fit"
            CanvasZoomMode.Percent50 -> "50%"
            CanvasZoomMode.Percent100 -> "100%"
            CanvasZoomMode.Percent200 -> "200%"
            CanvasZoomMode.Custom -> "Custom"
        }

    private fun glyphLabel(glyphId: Int?): String {
        val glyph = state.document?.glyphs?.firstOrNull { it.id == glyphId } ?: return "<none>"
        return "id=${glyph.id} '${glyph.char ?: "?"}' [${glyph.width}x${glyph.height}]"
    }

    private fun clearCursorMetrics() {
        cursorTextureX = null
        cursorTextureY = null
    }

    companion object {
        private const val ClickDragThreshold = 6f
        private const val FontPageModeLabel = "Font Page"
        private const val SampleTextModeLabel = "Sample Text"
        private val GridSpacingOptions = intArrayOf(1, 2, 4, 8, 16, 32, 64)
    }
}
