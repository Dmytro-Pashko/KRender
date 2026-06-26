package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorController
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorPanelIds
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorState
import com.pashkd.krender.engine.tools.common.bitmapfont.io.layoutSampleText
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.FontPreviewOverlays
import com.pashkd.krender.engine.tools.common.canvas.CanvasOverlays
import com.pashkd.krender.engine.tools.common.canvas.CanvasRect
import com.pashkd.krender.engine.tools.common.canvas.computeCanvasViewportLayout
import com.pashkd.krender.engine.tools.common.canvas.packColor
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.MouseButton
import glm_.vec2.Vec2 as ImVec2

class FontPageCanvasPanel(
    private val state: BitmapFontEditorState,
    private val controller: BitmapFontEditorController,
    private val layout: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private var clickDragDistance = 0f

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

        drawCanvasOptions()

        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        state.canvasRect = CanvasRect(min.x, min.y, size.x.coerceAtLeast(1f), size.y.coerceAtLeast(1f))

        val document = state.document
        if (document == null || !document.readable) {
            ImGui.text("Open a .fnt file or generate a bitmap font to preview.")
            document?.diagnostics?.take(5)?.forEach { diag -> ImGui.text("${diag.severity.name}: ${diag.message}") }
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
                CanvasOverlays.drawGrid(
                    viewportLayout,
                    spacingPixels = state.preview.gridSpacingPixels,
                )
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
            if (ImGui.isItemHovered()) {
                val io = ImGui.io
                if (io.mouseWheel != 0f) {
                    controller.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
                }
                if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
                    controller.panPreview(io.mouseDelta.x, io.mouseDelta.y)
                    clickDragDistance += kotlin.math.abs(io.mouseDelta.x) + kotlin.math.abs(io.mouseDelta.y)
                }
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
                    if (io.mouseClicked[0] && clickDragDistance < 5f) {
                        controller.selectGlyph(hoveredGlyph?.id)
                    }
                    if (io.mouseClicked[0]) {
                        clickDragDistance = 0f
                    }
                } else {
                    state.glyphSelection.hoveredGlyphId = null
                }
            } else {
                state.glyphSelection.hoveredGlyphId = null
            }
        } else {
            if (handle == null && state.textureWidth > 0) {
                ImGui.text("Loading page texture...")
            } else {
                ImGui.text("No preview available.")
            }
        }

        if (sampleLayout.missingCodepoints.isNotEmpty()) {
            ImGui.text("Missing glyphs: ${sampleLayout.missingCodepoints.take(10).joinToString()}")
        }
        ImGui.end()
    }

    private fun drawCanvasOptions() {
        val showGlyphs = booleanArrayOf(state.showGlyphBounds)
        if (ImGui.checkbox("Show Glyph Bounds##bfe_show_glyphs", showGlyphs)) {
            controller.setShowGlyphBounds(showGlyphs[0])
        }
        ImGui.sameLine()
        val showChecker = booleanArrayOf(state.preview.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##bfe_checker", showChecker)) {
            state.preview.showCheckerboard = showChecker[0]
        }
        ImGui.sameLine()
        val showGrid = booleanArrayOf(state.preview.showGrid)
        if (ImGui.checkbox("Grid##bfe_grid", showGrid)) {
            state.preview.showGrid = showGrid[0]
        }

        ImGui.text("Sample Text:")
        ImGui.sameLine()
        val sampleBuf = ByteArray(256)
        val bytes = state.sampleText.toByteArray(Charsets.UTF_8)
        bytes.copyInto(sampleBuf, 0, 0, minOf(bytes.size, sampleBuf.size - 1))
        ImGui.setNextItemWidth(ImGui.contentRegionAvail.x - 200f)
        if (ImGui.inputText("##bfe_sample_text", sampleBuf)) {
            val nullIndex = sampleBuf.indexOf(0)
            val text = if (nullIndex >= 0) String(sampleBuf, 0, nullIndex, Charsets.UTF_8) else String(sampleBuf, Charsets.UTF_8)
            controller.setSampleText(text)
        }
        ImGui.sameLine()
        val sampleToggle = booleanArrayOf(state.showSampleTextPreview)
        if (ImGui.checkbox("Sample Preview##bfe_sample_toggle", sampleToggle)) {
            controller.setSampleTextPreviewEnabled(sampleToggle[0])
        }
    }
}
