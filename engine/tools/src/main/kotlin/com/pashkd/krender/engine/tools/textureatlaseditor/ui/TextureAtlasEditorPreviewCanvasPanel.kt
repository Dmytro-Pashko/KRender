package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.AtlasRegionId
import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchDraft
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchSegment
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
import com.pashkd.krender.engine.tools.textureatlaseditor.layoutSampleText
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasNinePatchRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedFontDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPlan
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedRegionsForPage
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
import com.pashkd.krender.engine.ui.UiService
import com.pashkd.krender.engine.ui.UiTextureTint
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ColorEditFlag
import imgui.ImGui
import imgui.MouseButton
import imgui.SliderFlag
import imgui.WindowFlag
import imgui.api.colorEdit4
import imgui.api.slider
import imgui.or
import glm_.vec2.Vec2 as ImVec2
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private var activeNinePatchHandleId: NinePatchGuideHandleId? = null

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
            drawSharedPreviewOptionToggles(
                checkerId = "atlas_preview_checker",
                gridId = "atlas_preview_grid",
                boundsId = "atlas_preview_bounds",
                showBoundsToggle = true,
            )
            return
        }
        if (state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch) {
            drawSharedPreviewOptionToggles(
                checkerId = "np_editor_checker",
                gridId = "np_editor_grid",
                showBoundsToggle = false,
            )
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
            drawSharedPreviewOptionToggles(
                checkerId = "font_checker",
                gridId = "font_grid",
                showBoundsToggle = false,
            )
            drawFontTintEditor()
            return
        }
        drawSharedPreviewOptionToggles(
            checkerId = "texture_atlas_editor_checker",
            gridId = "texture_atlas_editor_grid",
            boundsId = "texture_atlas_editor_bounds",
            showBoundsToggle = true,
        )
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
                drawZoomControls("texture_atlas_editor")
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
                val plan = state.selectedPackingPlan()
                val selectedPage = state.selectedPackingPage()
                if (plan != null && plan.pages.size > 1) {
                    val currentPage = selectedPage?.name ?: plan.pages.first().name
                    ImGui.setNextItemWidth(300f)
                    if (ImGui.beginCombo("Atlas Page##atlas_preview_page", currentPage)) {
                        plan.pages.forEach { page ->
                            if (ImGui.selectable(page.name, currentPage == page.name)) {
                                operations.selectPackingPage(page.index)
                            }
                        }
                        ImGui.endCombo()
                    }
                }
                drawZoomControls("atlas_preview")
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
                    contentPaddingPixels = if (isNinePatchMode) NinePatchCanvasPaddingPixels else 0,
                )
            if (state.preview.showCheckerboard) {
                TextureAtlasEditorPreviewOverlays.drawCheckerboard(viewportLayout)
            }
            ImGui.cursorScreenPos = ImVec2(viewportLayout.imageX, viewportLayout.imageY)
            ui.drawTexturePreview(handle, viewportLayout.imageWidth, viewportLayout.imageHeight)

            if (isNinePatchMode) {
                if (state.preview.showGrid) {
                    TextureAtlasEditorPreviewOverlays.drawGrid(
                        viewportLayout,
                        spacingPixels = state.preview.gridSpacingPixels,
                        color = packImColor(state.preview.gridColor),
                    )
                }
                val draft = state.ninePatchEditor.draft
                if (draft != null && state.preview.showNinePatchGuides) {
                    val overlay = TextureAtlasEditorPreviewOverlays.buildNinePatchDraftOverlay(draft, viewportLayout)
                    TextureAtlasEditorPreviewOverlays.drawNinePatchDraftGuides(overlay, activeNinePatchHandleId)
                }
            } else {
                val regions = state.selectedRegionsForPage()
                val selectedRegion = regions.firstOrNull { region -> region.id == state.selectedRegionId }
                val hoveredRegion = regions.firstOrNull { region -> region.id == state.hoveredRegionId }
                if (state.preview.showGrid) {
                    TextureAtlasEditorPreviewOverlays.drawGrid(
                        viewportLayout,
                        spacingPixels = state.preview.gridSpacingPixels,
                        color = packImColor(state.preview.gridColor),
                    )
                }
                if (state.preview.showBounds && regions.isNotEmpty()) {
                    TextureAtlasEditorPreviewOverlays.drawRegionBounds(regions, viewportLayout, selectedRegion, hoveredRegion)
                    selectedRegion?.let { region -> TextureAtlasEditorPreviewOverlays.labelRegion(region, viewportLayout) }
                }
            }
            ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
            ImGui.invisibleButton("##texture_atlas_editor_preview_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
            if (isNinePatchMode) {
                handleNinePatchInteraction(viewportLayout, state.ninePatchEditor.draft)
            } else {
                handleTextureInteraction(viewportLayout, state.selectedRegionsForPage(), allowRegionSelection = true)
            }
        } else {
            clearCursorMetrics()
            wrappedTextLine(state.previewInfo.statusMessage)
        }
    }

    private fun drawPackedAtlasPreviewCanvas() {
        val page = state.selectedPackingPage()
        if (page == null) {
            clearCursorMetrics()
            hoveredPackingRegionId = null
            pendingSelectPackingRegionId = null
            wrappedTextLine("Pack Texture Atlas or select an atlas page to preview.")
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
            if (state.preview.showGrid) {
                TextureAtlasEditorPreviewOverlays.drawGrid(
                    viewportLayout,
                    spacingPixels = state.preview.gridSpacingPixels,
                    color = packImColor(state.preview.gridColor),
                )
            }
            ImGui.cursorScreenPos = ImVec2(viewportLayout.imageX, viewportLayout.imageY)
            ui.drawTexturePreview(handle, viewportLayout.imageWidth, viewportLayout.imageHeight)
            if (state.preview.showBounds) {
                TextureAtlasEditorPreviewOverlays.drawPackedRegionBounds(
                    regions = page.regions,
                    layout = viewportLayout,
                    selectedRegionId = state.packing.selectedRegionId,
                    hoveredRegionId = hoveredPackingRegionId,
                )
            }
            ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
            ImGui.invisibleButton("##texture_atlas_editor_packed_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
            handlePackedTextureInteraction(viewportLayout, page.regions)
            return
        }

        clearCursorMetrics()
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
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
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

    private fun handlePackedTextureInteraction(
        viewportLayout: TexturePreviewViewportLayout,
        regions: List<com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingRegion>,
    ) {
        val io = ImGui.io
        val hovered = ImGui.isItemHovered()
        if (!hovered) {
            clearCursorMetrics()
            hoveredPackingRegionId = null
            pendingSelectPackingRegionId = null
            clickDragDistance = 0f
            return
        }
        updateCursorMetrics(viewportLayout)
        if (io.mouseWheel != 0f) {
            operations.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            operations.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            clickDragDistance += abs(io.mouseDelta.x) + abs(io.mouseDelta.y)
            pendingSelectPackingRegionId = null
        }
        val hoveredRegion = TextureAtlasEditorPreviewOverlays.hitTestPackedRegion(regions, viewportLayout, io.mousePos.x, io.mousePos.y)
        hoveredPackingRegionId = hoveredRegion?.id
        if (hoveredRegion != null && cursorTextureX != null && cursorTextureY != null) {
            cursorRegionX = cursorTextureX!! - hoveredRegion.x
            cursorRegionY = cursorTextureY!! - hoveredRegion.y
        } else {
            cursorRegionX = null
            cursorRegionY = null
        }
        if (io.mouseClicked[0]) {
            pendingSelectPackingRegionId = hoveredRegion?.id
            clickDragDistance = 0f
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

    private fun handleNinePatchInteraction(
        viewportLayout: TexturePreviewViewportLayout,
        draft: NinePatchDraft?,
    ) {
        val io = ImGui.io
        val hovered = ImGui.isItemHovered()
        if (!hovered) {
            clearCursorMetrics()
            activeNinePatchHandleId = null
            return
        }
        updateCursorMetrics(viewportLayout)
        if (io.mouseWheel != 0f) {
            operations.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
        }
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            operations.panPreview(io.mouseDelta.x, io.mouseDelta.y)
            clickDragDistance += abs(io.mouseDelta.x) + abs(io.mouseDelta.y)
            activeNinePatchHandleId = null
            return
        }
        if (!state.preview.showNinePatchGuides || draft == null) {
            if (io.mouseReleased[0]) {
                activeNinePatchHandleId = null
            }
            return
        }
        val overlay = TextureAtlasEditorPreviewOverlays.buildNinePatchDraftOverlay(draft, viewportLayout)
        if (io.mouseClicked[0]) {
            activeNinePatchHandleId =
                TextureAtlasEditorPreviewOverlays.hitTestNinePatchGuideHandle(
                    overlay,
                    io.mousePos.x,
                    io.mousePos.y,
                )
        }
        val activeHandle = activeNinePatchHandleId
        if (activeHandle != null && io.mouseDown[0]) {
            updateNinePatchHandleDrag(activeHandle, draft, overlay, viewportLayout.effectiveZoom, io.mousePos.x, io.mousePos.y)
        }
        if (io.mouseReleased[0]) {
            activeNinePatchHandleId = null
        }
    }

    private fun drawFontPreviewCanvas() {
        clearCursorMetrics()
        val resource = state.selectedResource()
        if (resource !is FontAtlasResource) {
            wrappedTextLine("Select a font resource to preview glyphs.")
            return
        }
        state.hoveredRegionId = null
        val document = state.selectedFontDocument()
        if (document == null || !document.readable) {
            wrappedTextLine("Font descriptor is not readable or was not loaded.")
            document?.diagnostics?.take(5)?.forEach { diag -> wrappedTextLine("${diag.severity.name}: ${diag.message}") }
            return
        }

        val handle = state.previewInfo.texturePreviewHandle
        val sampleLayout = layoutSampleText(state.fontPreview.sampleText, document)
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
            if (state.preview.showGrid) {
                TextureAtlasEditorPreviewOverlays.drawGrid(
                    viewportLayout,
                    spacingPixels = state.preview.gridSpacingPixels,
                    color = packImColor(state.preview.gridColor),
                )
            }
            if (state.fontPreview.showSampleTextPreview) {
                TextureAtlasEditorPreviewOverlays.drawFontSampleText(
                    handle = handle,
                    layout = viewportLayout,
                    sampleLayout = sampleLayout,
                    tintColor = packImColor(state.fontPreview.tintColor),
                )
            } else {
                ImGui.cursorScreenPos = ImVec2(viewportLayout.imageX, viewportLayout.imageY)
                ui.drawTexturePreview(
                    handle,
                    viewportLayout.imageWidth,
                    viewportLayout.imageHeight,
                    UiTextureTint(
                        red = state.fontPreview.tintColor.red,
                        green = state.fontPreview.tintColor.green,
                        blue = state.fontPreview.tintColor.blue,
                        alpha = state.fontPreview.tintColor.alpha,
                    ),
                )
            }
            if (!state.fontPreview.showSampleTextPreview && state.fontPreview.showGlyphBounds) {
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
                updateCursorMetrics(viewportLayout)
                if (io.mouseWheel != 0f) {
                    operations.setPreviewZoom(state.preview.customZoom * (1f + io.mouseWheel * 0.1f))
                }
                if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
                    operations.panPreview(io.mouseDelta.x, io.mouseDelta.y)
                }
            } else {
                clearCursorMetrics()
            }
        } else {
            wrappedTextLine(state.previewInfo.statusMessage)
        }

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
        if (ImGui.run { MouseButton.Right.isDragging() } && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
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
                        else -> "Atlas File"
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
                wrappedTextLine(
                    if (state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch) {
                        "Wheel: zoom only. RMB drag: pan. LMB on guide handles: edit Nine-patch draft."
                    } else {
                        "Wheel: zoom only. RMB drag: pan. LMB on region: select. Double-click region: focus."
                    },
                )
                if (state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch) {
                    wrappedTextLine("Guide colors: orange = horizontal stretch, blue = vertical stretch, green = content padding. Drag the square handles to change the guide bounds.")
                }
            }
            TextureAtlasCanvasMode.FinalPackedAtlas -> {
                val page = state.selectedPackingPage()
                val plan = state.selectedPackingPlan()
                val zoomPercent =
                    when (state.preview.zoomMode) {
                        TexturePreviewZoomMode.Fit -> "Fit"
                        else -> "${(state.preview.viewport.zoom * 100f).toInt()}%"
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
                val hoveredText =
                    page?.regions?.firstOrNull { region -> region.id == hoveredPackingRegionId }?.displayName ?: "<none>"
                val selectedText = state.selectedPackingRegion()?.displayName ?: "<none>"
                textLine("Pages: ${plan?.pages?.size ?: 0} | Packed: ${plan?.packedRegionCount ?: 0} | Skipped: ${plan?.skippedCount ?: 0} | Zoom: $zoomPercent | Texture: ${state.previewInfo.textureWidth} x ${state.previewInfo.textureHeight}")
                textLine("Hovered: $hoveredText | Selected: $selectedText | Page: ${page?.name ?: "<none>"} | $cursorText | $regionCursorText")
                wrappedTextLine("Wheel: zoom only. RMB drag: pan. LMB on packed region: select. Save Texture Atlas writes files explicitly.")
            }
            TextureAtlasCanvasMode.FontPreview -> {
                val document = state.selectedFontDocument()
                val face = document?.info?.face ?: "<unknown>"
                val glyphCount = document?.glyphs?.size ?: 0
                val selectedGlyph = state.fontPreview.selectedGlyphId?.let { id -> document?.glyphs?.firstOrNull { it.id == id } }
                val selectedText = selectedGlyph?.let { g -> "id=${g.id} '${g.char ?: "?"}' [${g.width}x${g.height}]" } ?: "<none>"
                val cursorText =
                    if (cursorTextureX != null && cursorTextureY != null) {
                        "Cursor: ${cursorTextureX}, ${cursorTextureY}"
                    } else {
                        "Cursor: <outside>"
                    }
                val previewMode = if (state.fontPreview.showSampleTextPreview) "Sample Text" else "Full Font"
                textLine("Font: $face | Glyphs: $glyphCount | Page: ${state.fontPreview.selectedPageIndex} | Preview: $previewMode | Selected: $selectedText | $cursorText")
                wrappedTextLine("Wheel: zoom only. RMB drag: pan. Use Preview Tint to recolor the font preview and Inspector to switch between full font and sample text preview.")
            }
        }
    }

    private fun drawStatusSection() {
        drawStatusLine()
    }

    companion object {
        private const val ClickDragThreshold = 6f
        private const val NinePatchCanvasPaddingPixels = 100
        private val GridSpacingOptions = intArrayOf(4, 8, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512)
        private val PickerOnlyColorEditFlags = ColorEditFlag.NoInputs
    }

    private fun drawGridColorEditor(idSuffix: String) {
        val color = state.preview.gridColor
        if (
            colorEdit4(
                "Grid Color##$idSuffix",
                color.red,
                color.green,
                color.blue,
                color.alpha,
                PickerOnlyColorEditFlags,
            ) { r, g, b, a ->
                operations.setGridColor(r, g, b, a)
            }
        ) {
            Unit
        }
    }

    private fun drawGridSizeEditor(idSuffix: String) {
        ImGui.setNextItemWidth(140f)
        val currentValue = state.preview.gridSpacingPixels
        if (ImGui.beginCombo("Grid Size##$idSuffix", currentValue.toString())) {
            GridSpacingOptions.forEach { option ->
                if (ImGui.selectable(option.toString(), option == currentValue)) {
                    operations.setGridSpacingPixels(option)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawFontTintEditor() {
        val color = state.fontPreview.tintColor
        if (
            colorEdit4(
                "Preview Tint##font_preview_tint",
                color.red,
                color.green,
                color.blue,
                color.alpha,
                PickerOnlyColorEditFlags,
            ) { r, g, b, a ->
                operations.setFontPreviewTint(r, g, b, a)
            }
        ) {
            Unit
        }
    }

    private fun drawZoomControls(idPrefix: String) {
        ImGui.setNextItemWidth(180f)
        if (ImGui.beginCombo("Zoom Mode##${idPrefix}_zoom_mode", formatZoomMode(state.preview.zoomMode))) {
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
            if (slider("Custom##${idPrefix}_custom_zoom", state.preview::customZoom, 0.05f, 25f, "%.2f", SliderFlag.AlwaysClamp)) {
                operations.setPreviewZoom(state.preview.customZoom)
            }
        }
    }

    private fun drawSharedPreviewOptionToggles(
        checkerId: String,
        gridId: String,
        boundsId: String = "",
        showBoundsToggle: Boolean,
    ) {
        val checker = booleanArrayOf(state.preview.showCheckerboard)
        if (ImGui.checkbox("Checkerboard##$checkerId", checker)) {
            operations.setShowCheckerboard(checker[0])
        }
        ImGui.sameLine()
        val grid = booleanArrayOf(state.preview.showGrid)
        if (ImGui.checkbox("Grid##$gridId", grid)) {
            operations.setShowGrid(grid[0])
        }
        if (showBoundsToggle) {
            ImGui.sameLine()
            val bounds = booleanArrayOf(state.preview.showBounds)
            if (ImGui.checkbox("Bounds##$boundsId", bounds)) {
                operations.setShowBounds(bounds[0])
            }
        }
        if (state.preview.showGrid) {
            drawGridColorEditor(gridId)
            drawGridSizeEditor(gridId)
        }
    }

    private fun updateNinePatchHandleDrag(
        handleId: NinePatchGuideHandleId,
        draft: NinePatchDraft,
        overlay: NinePatchDraftOverlay,
        effectiveZoom: Float,
        mouseX: Float,
        mouseY: Float,
    ) {
        when (handleId.kind) {
            NinePatchGuideKind.StretchX ->
                updateHorizontalSegment(
                    current = draft.stretchX,
                    maxSize = draft.contentWidth,
                    mouseCoordinate = ((mouseX - overlay.contentMinX) / effectiveZoom.coerceAtLeast(0.0001f)).roundToInt(),
                    role = handleId.role,
                    onUpdate = operations::updateNinePatchStretchX,
                )
            NinePatchGuideKind.StretchY ->
                updateVerticalSegment(
                    current = draft.stretchY,
                    maxSize = draft.contentHeight,
                    mouseCoordinate = ((mouseY - overlay.contentMinY) / effectiveZoom.coerceAtLeast(0.0001f)).roundToInt(),
                    role = handleId.role,
                    onUpdate = operations::updateNinePatchStretchY,
                )
            NinePatchGuideKind.PaddingX -> {
                val segment = draft.paddingX ?: return
                updateHorizontalSegment(
                    current = segment,
                    maxSize = draft.contentWidth,
                    mouseCoordinate = ((mouseX - overlay.contentMinX) / effectiveZoom.coerceAtLeast(0.0001f)).roundToInt(),
                    role = handleId.role,
                    onUpdate = { start, length -> operations.updateNinePatchPaddingX(start, length) },
                )
            }
            NinePatchGuideKind.PaddingY -> {
                val segment = draft.paddingY ?: return
                updateVerticalSegment(
                    current = segment,
                    maxSize = draft.contentHeight,
                    mouseCoordinate = ((mouseY - overlay.contentMinY) / effectiveZoom.coerceAtLeast(0.0001f)).roundToInt(),
                    role = handleId.role,
                    onUpdate = { start, length -> operations.updateNinePatchPaddingY(start, length) },
                )
            }
        }
    }

    private fun updateHorizontalSegment(
        current: NinePatchSegment,
        maxSize: Int,
        mouseCoordinate: Int,
        role: NinePatchGuideHandleRole,
        onUpdate: (Int, Int) -> Unit,
    ) {
        val endExclusive = current.start + current.length
        when (role) {
            NinePatchGuideHandleRole.Start -> {
                val newStart = mouseCoordinate.coerceIn(0, endExclusive - 1)
                onUpdate(newStart, endExclusive - newStart)
            }
            NinePatchGuideHandleRole.End -> {
                val newEndExclusive = mouseCoordinate.coerceIn(current.start + 1, maxSize)
                onUpdate(current.start, newEndExclusive - current.start)
            }
        }
    }

    private fun updateVerticalSegment(
        current: NinePatchSegment,
        maxSize: Int,
        mouseCoordinate: Int,
        role: NinePatchGuideHandleRole,
        onUpdate: (Int, Int) -> Unit,
    ) {
        val endExclusive = current.start + current.length
        when (role) {
            NinePatchGuideHandleRole.Start -> {
                val newStart = mouseCoordinate.coerceIn(0, endExclusive - 1)
                onUpdate(newStart, endExclusive - newStart)
            }
            NinePatchGuideHandleRole.End -> {
                val newEndExclusive = mouseCoordinate.coerceIn(current.start + 1, maxSize)
                onUpdate(current.start, newEndExclusive - current.start)
            }
        }
    }
}

private fun formatCanvasMode(mode: TextureAtlasCanvasMode): String =
    when (mode) {
        TextureAtlasCanvasMode.TextureAtlas -> "Atlas File"
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
    contentPaddingPixels: Int = 0,
): TexturePreviewViewportLayout {
    val paddedWidth = textureWidth + contentPaddingPixels * 2
    val paddedHeight = textureHeight + contentPaddingPixels * 2
    val fitZoom =
        minOf(
            rect.width / paddedWidth.coerceAtLeast(1).toFloat(),
            rect.height / paddedHeight.coerceAtLeast(1).toFloat(),
        ).coerceAtLeast(0.05f)
    val effectiveZoom =
        when (previewState.zoomMode) {
            TexturePreviewZoomMode.Fit -> fitZoom
            TexturePreviewZoomMode.Percent50 -> 0.5f
            TexturePreviewZoomMode.Percent100 -> 1f
            TexturePreviewZoomMode.Percent200 -> 2f
            TexturePreviewZoomMode.Custom -> previewState.customZoom.coerceIn(0.05f, 25f)
        }
    val imageWidth = textureWidth * effectiveZoom
    val imageHeight = textureHeight * effectiveZoom
    val paddedImageWidth = paddedWidth * effectiveZoom
    val paddedImageHeight = paddedHeight * effectiveZoom
    val imageX = rect.x + (rect.width - paddedImageWidth) * 0.5f + previewState.viewport.panX + contentPaddingPixels * effectiveZoom
    val imageY = rect.y + (rect.height - paddedImageHeight) * 0.5f + previewState.viewport.panY + contentPaddingPixels * effectiveZoom
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
