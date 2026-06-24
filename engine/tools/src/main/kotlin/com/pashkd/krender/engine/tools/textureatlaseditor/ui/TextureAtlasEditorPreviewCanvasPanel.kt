package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.AtlasRegionId
import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasCanvasMode
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorCanvasRect
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPreviewState
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureRegionScreenRect
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.textureatlaseditor.isNinePatchTexturePath
import com.pashkd.krender.engine.tools.textureatlaseditor.layoutSampleText
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasNinePatchRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedFontDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedNinePatchDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPlan
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedRegionsForPage
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
import com.pashkd.krender.engine.ui.UiService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
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
    private var hoveredPackingRegionId: String? = null
    private var pendingSelectPackingRegionId: String? = null

    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Preview)
        val expanded = beginPanel(TextureAtlasEditorPanelIds.Preview, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Preview, layout.title)
        if (!expanded) {
            state.canvasRect = TextureAtlasEditorCanvasRect()
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
            "texture_atlas_editor_preview_canvas_body",
            ImVec2(0f, 0f),
            true,
            WindowFlag.NoScrollbar or WindowFlag.NoScrollWithMouse,
        )
        val min = ImGui.cursorScreenPos
        val size = ImGui.contentRegionAvail
        state.canvasRect = TextureAtlasEditorCanvasRect(min.x, min.y, size.x.coerceAtLeast(1f), size.y.coerceAtLeast(1f))

        when (state.preview.canvasMode) {
            TextureAtlasCanvasMode.TextureAtlas,
            TextureAtlasCanvasMode.NinePatch,
            -> drawTexturePreviewCanvas()
            TextureAtlasCanvasMode.FontPreview -> drawFontPreviewCanvas()
            TextureAtlasCanvasMode.FinalPackedAtlas -> drawPackedAtlasPreviewCanvas()
        }

        ImGui.endChild()
        ImGui.end()
    }

    private fun drawCanvasModeRow() {
        val currentMode = state.preview.canvasMode
        ImGui.setNextItemWidth(300f)
        if (ImGui.beginCombo("Mode##texture_atlas_editor_canvas_mode", formatCanvasMode(currentMode))) {
            TextureAtlasCanvasMode.entries.forEach { mode ->
                if (ImGui.selectable(formatCanvasMode(mode), currentMode == mode)) {
                    operations.setCanvasMode(mode)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawOptionRow() {
        if (state.preview.canvasMode == TextureAtlasCanvasMode.FinalPackedAtlas) {
            val checker = booleanArrayOf(state.preview.showCheckerboard)
            if (ImGui.checkbox("Checkerboard##atlas_preview_checker", checker)) {
                operations.setShowCheckerboard(checker[0])
            }
            ImGui.sameLine()
            val bounds = booleanArrayOf(state.preview.showBounds)
            if (ImGui.checkbox("Bounds##atlas_preview_bounds", bounds)) {
                operations.setShowBounds(bounds[0])
            }
            return
        }
        if (state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch) {
            val checker = booleanArrayOf(state.preview.showCheckerboard)
            if (ImGui.checkbox("Checkerboard##np_editor_checker", checker)) {
                operations.setShowCheckerboard(checker[0])
            }
            ImGui.sameLine()
            val guides = booleanArrayOf(state.preview.showNinePatchGuides)
            if (ImGui.checkbox("Show Guides##np_editor_guides", guides)) {
                operations.setShowNinePatchGuides(guides[0])
            }
            return
        }
        if (state.preview.canvasMode == TextureAtlasCanvasMode.FontPreview) {
            val showGlyphs = booleanArrayOf(state.fontPreview.showGlyphBounds)
            if (ImGui.checkbox("Show Glyph Bounds##font_show_glyphs", showGlyphs)) {
                state.fontPreview.showGlyphBounds = showGlyphs[0]
            }
            ImGui.sameLine()
            val checker = booleanArrayOf(state.preview.showCheckerboard)
            if (ImGui.checkbox("Checkerboard##font_checker", checker)) {
                operations.setShowCheckerboard(checker[0])
            }
            return
        }
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
        when (state.preview.canvasMode) {
            TextureAtlasCanvasMode.NinePatch -> {
                if (ImGui.button("Fit##np_fit")) {
                    operations.fitPreview()
                }
                ImGui.sameLine()
                if (ImGui.button("Reset Camera##np_reset_camera")) {
                    operations.resetPreviewCamera()
                }
            }
            TextureAtlasCanvasMode.TextureAtlas -> {
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
            TextureAtlasCanvasMode.FinalPackedAtlas -> {
                val atlas = state.selectedAtlasDocument()
                if (atlas != null && atlas.pages.size > 1) {
                    val currentPage = state.selectedAtlasPageName ?: atlas.pages.first().name
                    ImGui.setNextItemWidth(300f)
                    if (ImGui.beginCombo("Atlas Page##atlas_preview_page", currentPage)) {
                        atlas.pages.forEach { page ->
                            if (ImGui.selectable(page.name, currentPage == page.name)) {
                                operations.selectAtlasPage(page.name)
                            }
                        }
                        ImGui.endCombo()
                    }
                }
                if (ImGui.button("Fit##atlas_preview_fit")) {
                    operations.fitPreview()
                }
                ImGui.sameLine()
                if (ImGui.button("Reset Camera##atlas_preview_reset")) {
                    operations.resetPreviewCamera()
                }
                ImGui.sameLine()
                if (ImGui.button("Focus Region##atlas_preview_focus")) {
                    operations.fitSelectedRegion()
                }
            }
            TextureAtlasCanvasMode.FontPreview -> {
                val document = state.selectedFontDocument()
                if (document != null && document.pages.size > 1) {
                    val currentPageName = document.pages.getOrNull(state.fontPreview.selectedPageIndex)?.file ?: "page 0"
                    ImGui.setNextItemWidth(300f)
                    if (ImGui.beginCombo("Font Page##font_action_page", currentPageName)) {
                        document.pages.forEachIndexed { index, page ->
                            if (ImGui.selectable("${page.id}: ${page.file}", state.fontPreview.selectedPageIndex == index)) {
                                operations.setFontPreviewPage(index)
                            }
                        }
                        ImGui.endCombo()
                    }
                }
                if (ImGui.button("Fit##font_fit")) {
                    operations.fitPreview()
                }
                ImGui.sameLine()
                if (ImGui.button("Reset Camera##font_reset_camera")) {
                    operations.resetPreviewCamera()
                }
            }
        }
    }

    private fun drawTexturePreviewCanvas() {
        val isNinePatchMode = state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch
        val selectedResource = state.selectedResource()
        if (isNinePatchMode && selectedResource !is NinePatchAtlasResource) {
            clearCursorMetrics()
            wrappedTextLine("Select a Nine-patch resource to edit guides.")
            return
        }

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

            if (isNinePatchMode) {
                val draft = state.ninePatchEditor.draft
                if (draft != null) {
                    val isNinePng = isNinePatchTexturePath(draft.sourcePath)
                    TextureAtlasEditorPreviewOverlays.drawNinePatchDraftGuides(draft, viewportLayout, isNinePng)
                }
            } else {
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
            }
            ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
            ImGui.invisibleButton("##texture_atlas_editor_preview_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
            val interactionRegions = if (isNinePatchMode) emptyList() else state.selectedRegionsForPage()
            handleTextureInteraction(viewportLayout, interactionRegions, allowRegionSelection = !isNinePatchMode)
        } else {
            clearCursorMetrics()
            wrappedTextLine(state.previewInfo.statusMessage)
        }
    }

    private fun drawPackedAtlasPreviewCanvas() {
        clearCursorMetrics()

        val handle = state.previewInfo.texturePreviewHandle
        if (handle != null && state.previewInfo.textureWidth > 0 && state.previewInfo.textureHeight > 0) {
            val viewportLayout = computeTexturePreviewViewportLayout(
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
            if (state.preview.showBounds && regions.isNotEmpty()) {
                TextureAtlasEditorPreviewOverlays.drawRegionBounds(regions, viewportLayout, selectedRegion, hoveredRegion)
                selectedRegion?.let { region -> TextureAtlasEditorPreviewOverlays.labelRegion(region, viewportLayout) }
            }
            ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
            ImGui.invisibleButton("##texture_atlas_editor_atlas_preview_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
            handleTextureInteraction(viewportLayout, regions, allowRegionSelection = true)
            return
        }

        val page = state.selectedPackingPage()
        if (page == null) {
            hoveredPackingRegionId = null
            pendingSelectPackingRegionId = null
            wrappedTextLine("Pack Texture Atlas or select an atlas page to preview.")
            return
        }

        ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
        ImGui.invisibleButton("##texture_atlas_editor_packed_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
        val layout =
            TextureAtlasEditorPreviewOverlays.drawPackedAtlasPage(
                page = page,
                canvasRect = state.canvasRect,
                selectedRegionId = state.packing.selectedRegionId,
                hoveredRegionId = hoveredPackingRegionId,
            )
        val io = ImGui.io
        if (!ImGui.isItemHovered()) {
            hoveredPackingRegionId = null
            pendingSelectPackingRegionId = null
            return
        }
        val hoveredRegion = TextureAtlasEditorPreviewOverlays.hitTestPackedRegion(layout, io.mousePos.x, io.mousePos.y)
        hoveredPackingRegionId = hoveredRegion?.id
        if (io.mouseClicked[0]) {
            pendingSelectPackingRegionId = hoveredRegion?.id
            clickDragDistance = 0f
        }
        if (MouseButton.Right.isDragging() && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            clickDragDistance += kotlin.math.abs(io.mouseDelta.x) + kotlin.math.abs(io.mouseDelta.y)
            pendingSelectPackingRegionId = null
        }
        if (pendingSelectPackingRegionId != null && io.mouseReleased[0] && clickDragDistance < ClickDragThreshold) {
            operations.selectPackingRegion(pendingSelectPackingRegionId)
            pendingSelectPackingRegionId = null
            clickDragDistance = 0f
        } else if (io.mouseReleased[0]) {
            pendingSelectPackingRegionId = null
            clickDragDistance = 0f
        }
    }

    private fun drawFontPreviewCanvas() {
        clearCursorMetrics()
        val resource = state.selectedResource()
        if (resource !is FontAtlasResource) {
            wrappedTextLine("Select a font resource to preview glyphs.")
            return
        }
        val document = state.selectedFontDocument()
        if (document == null || !document.readable) {
            wrappedTextLine("Font descriptor is not readable or was not loaded.")
            document?.diagnostics?.take(5)?.forEach { diag -> wrappedTextLine("${diag.severity.name}: ${diag.message}") }
            return
        }

        val handle = state.previewInfo.texturePreviewHandle
        if (handle != null && state.previewInfo.textureWidth > 0 && state.previewInfo.textureHeight > 0) {
            val viewportLayout = computeTexturePreviewViewportLayout(
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
            if (state.fontPreview.showGlyphBounds) {
                val pageIndex = state.fontPreview.selectedPageIndex
                val pageGlyphs = document.glyphs.filter { it.page == (document.pages.getOrNull(pageIndex)?.id ?: 0) }
                TextureAtlasEditorPreviewOverlays.drawFontGlyphBounds(
                    glyphs = pageGlyphs,
                    layout = viewportLayout,
                    selectedGlyphId = state.fontPreview.selectedGlyphId,
                    hoveredGlyphId = state.fontPreview.hoveredGlyphId,
                )
            }
            ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
            ImGui.invisibleButton("##texture_atlas_editor_font_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
            if (ImGui.isItemHovered()) {
                val io = ImGui.io
                if (io.mouseWheel != 0f) {
                    operations.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
                }
                if (MouseButton.Right.isDragging() && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
                    operations.panPreview(io.mouseDelta.x, io.mouseDelta.y)
                }
            }
        } else {
            wrappedTextLine(state.previewInfo.statusMessage)
        }

        val sampleLayout = layoutSampleText(state.fontPreview.sampleText, document)
        if (sampleLayout.missingCodepoints.isNotEmpty()) {
            wrappedTextLine("Missing glyphs for codepoints: ${sampleLayout.missingCodepoints.take(10).joinToString()}")
        }
        wrappedTextLine("Sample text layout: ${sampleLayout.totalWidth}px wide, ${sampleLayout.lineHeight}px line height, ${sampleLayout.glyphPlacements.size} glyphs placed.")
    }

    private fun handleTextureInteraction(
        viewportLayout: TexturePreviewViewportLayout,
        regions: List<TextureAtlasRegion>,
        allowRegionSelection: Boolean,
    ) {
        val io = ImGui.io
        val hovered = ImGui.isItemHovered()
        if (!hovered) {
            operations.setHoveredRegion(null)
            clearCursorMetrics()
            resetPendingSelection()
            return
        }
        updateCursorMetrics(viewportLayout)
        if (io.mouseWheel != 0f) {
            operations.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if (MouseButton.Right.isDragging() && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            operations.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            clickDragDistance += kotlin.math.abs(io.mouseDelta.x) + kotlin.math.abs(io.mouseDelta.y)
            pendingSelectWasDoubleClick = false
            pendingSelectRegionId = null
        }
        if (!allowRegionSelection || regions.isEmpty()) {
            operations.setHoveredRegion(null)
            cursorRegionX = null
            cursorRegionY = null
            resetPendingSelection()
            return
        }
        val hoveredRegion = hitTestAtlasRegion(regions, viewportLayout, io.mousePos.x, io.mousePos.y)
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

    private fun updateCursorMetrics(viewportLayout: TexturePreviewViewportLayout) {
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

    private fun clearCursorMetrics() {
        cursorTextureX = null
        cursorTextureY = null
        cursorRegionX = null
        cursorRegionY = null
    }

    private fun resetPendingSelection() {
        pendingSelectRegionId = null
        pendingSelectWasDoubleClick = false
        clickDragDistance = 0f
    }

    private fun drawStatusLine() {
        when (state.preview.canvasMode) {
            TextureAtlasCanvasMode.TextureAtlas,
            TextureAtlasCanvasMode.NinePatch,
            -> {
                val zoomPercent =
                    when (state.preview.zoomMode) {
                        TexturePreviewZoomMode.Fit -> "Fit"
                        else -> "${(state.preview.viewport.zoom * 100f).toInt()}%"
                    }
                val modeLabel =
                    when {
                        state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch -> "Nine-patch"
                        state.selectedAtlasNinePatchRegion() != null -> "Atlas Region"
                        else -> "Texture Atlas"
                    }
                val cursorText =
                    if (cursorTextureX != null && cursorTextureY != null) {
                        "Cursor: ${cursorTextureX}, ${cursorTextureY}"
                    } else {
                        "Cursor: <outside>"
                    }
                val regionCursorText =
                    if (cursorRegionX != null && cursorRegionY != null) {
                        "Region: ${cursorRegionX}, ${cursorRegionY}"
                    } else {
                        "Region: <n/a>"
                    }
                val hoveredText = state.hoveredRegionId?.regionName ?: "<none>"
                val selectedText = state.selectedRegionId?.regionName ?: "<none>"
                textLine("Mode: $modeLabel | Zoom: $zoomPercent | Texture: ${state.previewInfo.textureWidth} x ${state.previewInfo.textureHeight} | $cursorText | $regionCursorText")
                textLine("Hovered: $hoveredText | Selected: $selectedText")
                wrappedTextLine("Wheel: zoom only. RMB drag: pan. LMB on region: select. Double-click region: focus.")
            }
            TextureAtlasCanvasMode.FinalPackedAtlas -> {
                val page = state.selectedPackingPage()
                val plan = state.selectedPackingPlan()
                val hoveredText =
                    page?.regions
                        ?.firstOrNull { region -> region.id == hoveredPackingRegionId }
                        ?.displayName ?: "<none>"
                val selectedText = state.selectedPackingRegion()?.displayName ?: "<none>"
                textLine("Pages: ${plan?.pages?.size ?: 0} | Packed: ${plan?.packedRegionCount ?: 0} | Skipped: ${plan?.skippedCount ?: 0}")
                textLine("Hovered: $hoveredText | Selected: $selectedText | Page: ${page?.name ?: "<none>"}")
                wrappedTextLine("Pack Texture Atlas keeps results in memory until Save Texture Atlas writes files explicitly.")
            }
            TextureAtlasCanvasMode.FontPreview -> {
                val document = state.selectedFontDocument()
                val face = document?.info?.face ?: "<unknown>"
                val glyphCount = document?.glyphs?.size ?: 0
                val selectedGlyph = state.fontPreview.selectedGlyphId?.let { id -> document?.glyphs?.firstOrNull { it.id == id } }
                val selectedText = selectedGlyph?.let { g -> "id=${g.id} '${g.char ?: "?"}' [${g.width}x${g.height}]" } ?: "<none>"
                textLine("Font: $face | Glyphs: $glyphCount | Page: ${state.fontPreview.selectedPageIndex} | Selected: $selectedText")
                wrappedTextLine("Wheel: zoom. RMB drag: pan. Select glyphs in Inspector panel.")
            }
        }
    }

    private fun drawStatusSection() {
        drawStatusLine()
    }

    companion object {
        private const val ClickDragThreshold = 6f
    }
}

private fun formatCanvasMode(mode: TextureAtlasCanvasMode): String =
    when (mode) {
        TextureAtlasCanvasMode.TextureAtlas -> "Texture Atlas File"
        TextureAtlasCanvasMode.NinePatch -> "NinePatch Editor"
        TextureAtlasCanvasMode.FontPreview -> "Font Editor"
        TextureAtlasCanvasMode.FinalPackedAtlas -> "Atlas Preview"
    }

private fun beginPanel(
    panelId: String,
    layout: ImGuiPanelLayout,
    tracker: ImGuiLayoutRuntimeTracker,
): Boolean {
    tracker.consumeRestoreLayout(panelId)?.let { restored ->
        ImGui.setNextWindowPos(ImVec2(restored.x, restored.y))
        ImGui.setNextWindowSize(ImVec2(restored.width, restored.height))
    } ?: run {
        ImGui.setNextWindowPos(ImVec2(layout.x, layout.y), imgui.Cond.FirstUseEver)
        ImGui.setNextWindowSize(ImVec2(layout.width, layout.height), imgui.Cond.FirstUseEver)
    }
    val expanded = ImGui.begin("${layout.title}###$panelId")
    tracker.capture(panelId)
    return expanded
}

private fun computeTexturePreviewViewportLayout(
    rect: TextureAtlasEditorCanvasRect,
    textureWidth: Int,
    textureHeight: Int,
    previewState: TextureAtlasEditorPreviewState,
): TexturePreviewViewportLayout {
    val fitZoom =
        minOf(
            rect.width / textureWidth.coerceAtLeast(1).toFloat(),
            rect.height / textureHeight.coerceAtLeast(1).toFloat(),
        ).coerceAtLeast(0.05f)
    val effectiveZoom =
        when (previewState.zoomMode) {
            TexturePreviewZoomMode.Fit -> fitZoom
            TexturePreviewZoomMode.Percent50 -> 0.5f
            TexturePreviewZoomMode.Percent100 -> 1f
            TexturePreviewZoomMode.Percent200 -> 2f
            TexturePreviewZoomMode.Custom -> previewState.customZoom.coerceAtLeast(0.05f)
        }
    val imageWidth = textureWidth * effectiveZoom
    val imageHeight = textureHeight * effectiveZoom
    val imageX = rect.x + (rect.width - imageWidth) * 0.5f + previewState.viewport.panX
    val imageY = rect.y + (rect.height - imageHeight) * 0.5f + previewState.viewport.panY
    return TexturePreviewViewportLayout(
        viewportX = rect.x,
        viewportY = rect.y,
        viewportWidth = rect.width,
        viewportHeight = rect.height,
        imageX = imageX,
        imageY = imageY,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        effectiveZoom = effectiveZoom,
    )
}

private fun formatZoomMode(mode: TexturePreviewZoomMode): String =
    when (mode) {
        TexturePreviewZoomMode.Fit -> "Fit"
        TexturePreviewZoomMode.Percent50 -> "50%"
        TexturePreviewZoomMode.Percent100 -> "100%"
        TexturePreviewZoomMode.Percent200 -> "200%"
        TexturePreviewZoomMode.Custom -> "Custom"
    }

private fun atlasRegionScreenRect(
    region: TextureAtlasRegion,
    layout: TexturePreviewViewportLayout,
): TextureRegionScreenRect? {
    val xy = region.xy ?: return null
    val size = region.size ?: return null
    val minX = layout.imageX + xy.first * layout.effectiveZoom
    val minY = layout.imageY + xy.second * layout.effectiveZoom
    return TextureRegionScreenRect(
        minX = minX,
        minY = minY,
        maxX = minX + size.first * layout.effectiveZoom,
        maxY = minY + size.second * layout.effectiveZoom,
    )
}

private fun hitTestAtlasRegion(
    regions: List<TextureAtlasRegion>,
    layout: TexturePreviewViewportLayout,
    mouseX: Float,
    mouseY: Float,
): TextureAtlasRegion? =
    regions
        .filter { region ->
            val rect = atlasRegionScreenRect(region, layout) ?: return@filter false
            mouseX >= rect.minX && mouseX <= rect.maxX && mouseY >= rect.minY && mouseY <= rect.maxY
        }.minByOrNull { region ->
            val size = region.size ?: (Int.MAX_VALUE to Int.MAX_VALUE)
            size.first * size.second
        }

private fun screenToTexturePixelX(
    screenX: Float,
    layout: TexturePreviewViewportLayout,
): Float = (screenX - layout.imageX) / layout.effectiveZoom

private fun screenToTexturePixelY(
    screenY: Float,
    layout: TexturePreviewViewportLayout,
): Float = (screenY - layout.imageY) / layout.effectiveZoom
