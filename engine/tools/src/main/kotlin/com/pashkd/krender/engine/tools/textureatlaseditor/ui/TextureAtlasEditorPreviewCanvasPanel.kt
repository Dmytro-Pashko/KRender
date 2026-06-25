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
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasNinePatchPreviewType
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasNinePatchStretchPreset
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewSurfaceMode
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewZoomMode
import com.pashkd.krender.engine.tools.textureatlaseditor.buildNinePatchStretchPreview
import com.pashkd.krender.engine.tools.textureatlaseditor.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.textureatlaseditor.formatZoomMode
import com.pashkd.krender.engine.tools.textureatlaseditor.hitTestAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.isShowingPackedAtlasPreview
import com.pashkd.krender.engine.tools.textureatlaseditor.layoutSampleText
import com.pashkd.krender.engine.tools.textureatlaseditor.screenToTexturePixelX
import com.pashkd.krender.engine.tools.textureatlaseditor.screenToTexturePixelY
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
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ColorEditFlag
import imgui.ImGui
import imgui.MouseButton
import imgui.SliderFlag
import imgui.WindowFlag
import imgui.api.colorEdit4
import imgui.api.slider
import imgui.or
import kotlin.math.abs
import kotlin.math.roundToInt
import glm_.vec2.Vec2 as ImVec2

class TextureAtlasEditorPreviewCanvasPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
    private val ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val fontSampleBuf = ByteArray(256)
    private val customCanvasWidthBuf = ByteArray(16)
    private val customCanvasHeightBuf = ByteArray(16)
    private val stretchTargetWidthBuf = ByteArray(16)
    private val stretchTargetHeightBuf = ByteArray(16)
    private var fontSampleSynced = false
    private var lastSyncedCustomCanvasWidth: Int? = null
    private var lastSyncedCustomCanvasHeight: Int? = null
    private var lastSyncedStretchTargetWidth: Int? = null
    private var lastSyncedStretchTargetHeight: Int? = null
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
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Preview, layout, layoutTracker)
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
            listOf(TextureAtlasCanvasMode.TextureAtlas, TextureAtlasCanvasMode.NinePatch, TextureAtlasCanvasMode.FontPreview).forEach { mode ->
                if (ImGui.selectable(formatCanvasMode(mode), currentMode == mode)) {
                    operations.setCanvasMode(mode)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawOptionRow() {
        if (state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch) {
            drawNinePatchPreviewTypeCombo()
            drawSharedPreviewOptionToggles(
                checkerId = "np_editor_checker",
                gridId = "np_editor_grid",
                showBoundsToggle = false,
            )
            if (state.preview.ninePatchStretch.previewType == TextureAtlasNinePatchPreviewType.Source) {
                ImGui.sameLine()
                val guides = booleanArrayOf(state.preview.showNinePatchGuides)
                if (ImGui.checkbox("Show Guides##np_editor_guides", guides)) {
                    operations.setShowNinePatchGuides(guides[0])
                }
                drawPreviewSurfaceModeCombo("np_surface")
                drawCustomCanvasSizeEditors("np_surface")
            } else {
                drawNinePatchStretchControls()
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
            drawPreviewSurfaceModeCombo("font_surface")
            drawCustomCanvasSizeEditors("font_surface")
            syncFontSampleBuffer()
            textLine("Sample Text")
            ImGui.setNextItemWidth(ImGui.contentRegionAvail.x)
            if (ImGui.inputText("##font_canvas_sample_text", fontSampleBuf)) {
                operations.setFontSampleText(readBuffer(fontSampleBuf))
            }
            val samplePreviewToggle = booleanArrayOf(state.fontPreview.showSampleTextPreview)
            if (ImGui.checkbox("Sample Text Preview##font_canvas_sample_preview_toggle", samplePreviewToggle)) {
                operations.setFontSampleTextPreviewEnabled(samplePreviewToggle[0])
            }
            return
        }
        drawSharedPreviewOptionToggles(
            checkerId = "texture_atlas_editor_checker",
            gridId = "texture_atlas_editor_grid",
            boundsId = "texture_atlas_editor_bounds",
            showBoundsToggle = true,
        )
        ImGui.sameLine()
        val canShowPacked = state.selectedPackingPlan()?.packedRegionCount?.let { it > 0 } == true
        if (!canShowPacked) ImGui.beginDisabled()
        val packedPreview = booleanArrayOf(state.preview.showPackedAtlasPreview)
        if (ImGui.checkbox("Preview Packed Atlas##texture_atlas_editor_packed_preview_toggle", packedPreview)) {
            operations.setShowPackedAtlasPreview(packedPreview[0])
        }
        if (!canShowPacked) ImGui.endDisabled()
        drawPreviewSurfaceModeCombo("atlas_surface")
        drawCustomCanvasSizeEditors("atlas_surface")
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
                if (state.isShowingPackedAtlasPreview()) {
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
                }
                drawZoomControls("texture_atlas_editor")
                if (ImGui.button("Fit##texture_atlas_editor_fit")) {
                    operations.fitPreview()
                }
                ImGui.sameLine()
                if (ImGui.button("Reset Camera##texture_atlas_editor_reset_camera")) {
                    operations.resetPreviewCamera()
                }
                ImGui.sameLine()
                if (ImGui.button(if (state.isShowingPackedAtlasPreview()) "Focus Region##texture_atlas_editor_focus_region" else "Focus Selected Region##texture_atlas_editor_focus_region")) {
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
        if (state.isShowingPackedAtlasPreview()) {
            drawPackedAtlasPreviewCanvas()
            return
        }
        val isNinePatchMode = state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch
        val selectedResource = state.selectedResource()
        if (isNinePatchMode && selectedResource !is NinePatchAtlasResource) {
            clearCursorMetrics()
            wrappedTextLine("Select a Nine-patch resource to edit guides.")
            return
        }

        val handle = state.previewInfo.texturePreviewHandle
        if (handle != null && state.previewInfo.textureWidth > 0 && state.previewInfo.textureHeight > 0) {
            if (isNinePatchMode && state.preview.ninePatchStretch.previewType == TextureAtlasNinePatchPreviewType.StretchTest) {
                drawNinePatchStretchCanvas(handle, state.ninePatchEditor.draft)
                return
            }
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

    private fun drawNinePatchStretchCanvas(
        handle: com.pashkd.krender.engine.api.TexturePreviewHandle,
        draft: NinePatchDraft?,
    ) {
        val preview = buildNinePatchStretchPreview(draft, state.preview.ninePatchStretch)
        if (preview == null) {
            clearCursorMetrics()
            wrappedTextLine("Select a readable NinePatch resource to run a stretch test.")
            return
        }
        val viewportLayout =
            computeTexturePreviewViewportLayout(
                rect = state.canvasRect,
                textureWidth = preview.targetWidth,
                textureHeight = preview.targetHeight,
                previewState = state.preview.copy(surfaceMode = TexturePreviewSurfaceMode.Actual),
            )
        if (state.preview.showCheckerboard) {
            TextureAtlasEditorPreviewOverlays.drawCheckerboard(viewportLayout)
        }
        preview.slices.forEach { slice ->
            ImGui.cursorScreenPos =
                ImVec2(
                    viewportLayout.imageX + slice.destinationX * viewportLayout.effectiveZoom,
                    viewportLayout.imageY + slice.destinationY * viewportLayout.effectiveZoom,
                )
            ui.drawTexturePreview(
                deriveSliceHandle(handle, slice.sourceX, slice.sourceY, slice.sourceWidth, slice.sourceHeight),
                slice.destinationWidth * viewportLayout.effectiveZoom,
                slice.destinationHeight * viewportLayout.effectiveZoom,
            )
        }
        if (state.preview.showGrid) {
            TextureAtlasEditorPreviewOverlays.drawGrid(
                viewportLayout,
                spacingPixels = state.preview.gridSpacingPixels,
                color = packImColor(state.preview.gridColor),
            )
        }
        TextureAtlasEditorPreviewOverlays.drawNinePatchStretchOverlays(
            preview = preview,
            layout = viewportLayout,
            showSourceGuides = state.preview.ninePatchStretch.showSourceGuides,
            showDestinationSlices = state.preview.ninePatchStretch.showDestinationSlices,
            showPaddingRect = state.preview.ninePatchStretch.showPaddingRect,
        )
        ImGui.cursorScreenPos = ImVec2(state.canvasRect.x, state.canvasRect.y)
        ImGui.invisibleButton("##texture_atlas_editor_ninepatch_stretch_canvas_hit", ImVec2(state.canvasRect.width, state.canvasRect.height))
        handleTextureInteraction(viewportLayout, emptyList(), allowRegionSelection = false)
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
            ImGui.cursorScreenPos = ImVec2(viewportLayout.imageX, viewportLayout.imageY)
            ui.drawTexturePreview(handle, viewportLayout.imageWidth, viewportLayout.imageHeight)
            if (state.preview.showGrid) {
                TextureAtlasEditorPreviewOverlays.drawGrid(
                    viewportLayout,
                    spacingPixels = state.preview.gridSpacingPixels,
                    color = packImColor(state.preview.gridColor),
                )
            }
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
        val previewWidth = if (state.fontPreview.showSampleTextPreview) sampleLayout.boundsWidth.coerceAtLeast(1) else state.previewInfo.textureWidth
        val previewHeight = if (state.fontPreview.showSampleTextPreview) sampleLayout.boundsHeight.coerceAtLeast(1) else state.previewInfo.textureHeight
        if (handle != null && previewWidth > 0 && previewHeight > 0) {
            val viewportLayout =
                computeTexturePreviewViewportLayout(
                    rect = state.canvasRect,
                    textureWidth = previewWidth,
                    textureHeight = previewHeight,
                    previewState = state.preview,
                )
            if (state.preview.showCheckerboard) {
                TextureAtlasEditorPreviewOverlays.drawCheckerboard(viewportLayout)
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
            if (state.preview.showGrid) {
                TextureAtlasEditorPreviewOverlays.drawGrid(
                    viewportLayout,
                    spacingPixels = state.preview.gridSpacingPixels,
                    color = packImColor(state.preview.gridColor),
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
                if (state.isShowingPackedAtlasPreview()) {
                    drawPackedAtlasStatusLine()
                    return
                }
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
                        "Cursor: $cursorTextureX, $cursorTextureY"
                    } else {
                        "Cursor: <outside>"
                    }
                val regionCursorText =
                    if (cursorRegionX != null && cursorRegionY != null) {
                        "Region: $cursorRegionX, $cursorRegionY"
                    } else {
                        "Region: <n/a>"
                    }
                val hoveredText = state.hoveredRegionId?.regionName ?: "<none>"
                val selectedText = state.selectedRegionId?.regionName ?: "<none>"
                textLine("Mode: $modeLabel | Zoom: $zoomPercent | Texture: ${state.previewInfo.textureWidth} x ${state.previewInfo.textureHeight} | $cursorText | $regionCursorText")
                textLine("Hovered: $hoveredText | Selected: $selectedText")
                wrappedTextLine(
                    if (state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch) {
                        if (state.preview.ninePatchStretch.previewType == TextureAtlasNinePatchPreviewType.StretchTest) {
                            "Wheel: zoom only. RMB drag: pan. Stretch Test is preview-only and does not write files."
                        } else {
                            "Wheel: zoom only. RMB drag: pan. LMB on guide handles: edit Nine-patch draft."
                        }
                    } else {
                        "Wheel: zoom only. RMB drag: pan. LMB on region: select. Double-click region: focus."
                    },
                )
                if (state.preview.canvasMode == TextureAtlasCanvasMode.NinePatch) {
                    if (state.preview.ninePatchStretch.previewType == TextureAtlasNinePatchPreviewType.StretchTest) {
                        val stretchPreview = buildNinePatchStretchPreview(state.ninePatchEditor.draft, state.preview.ninePatchStretch)
                        wrappedTextLine("Guide colors: orange = horizontal stretch, blue = vertical stretch, green = content padding, white = destination slice boundaries.")
                        stretchPreview?.warnings?.forEach { warning ->
                            wrappedTextLine("Warning: $warning")
                        }
                    } else {
                        wrappedTextLine("Guide colors: orange = horizontal stretch, blue = vertical stretch, green = content padding. Drag the square handles to change the guide bounds.")
                    }
                }
            }
            TextureAtlasCanvasMode.FinalPackedAtlas -> drawPackedAtlasStatusLine()
            TextureAtlasCanvasMode.FontPreview -> {
                val document = state.selectedFontDocument()
                val face = document?.info?.face ?: "<unknown>"
                val glyphCount = document?.glyphs?.size ?: 0
                val selectedGlyph = state.fontPreview.selectedGlyphId?.let { id -> document?.glyphs?.firstOrNull { it.id == id } }
                val selectedText = selectedGlyph?.let { g -> "id=${g.id} '${g.char ?: "?"}' [${g.width}x${g.height}]" } ?: "<none>"
                val cursorText =
                    if (cursorTextureX != null && cursorTextureY != null) {
                        "Cursor: $cursorTextureX, $cursorTextureY"
                    } else {
                        "Cursor: <outside>"
                    }
                val previewMode = if (state.fontPreview.showSampleTextPreview) "Sample Text" else "Full Font"
                textLine("Font: $face | Glyphs: $glyphCount | Page: ${state.fontPreview.selectedPageIndex} | Preview: $previewMode | Selected: $selectedText | $cursorText")
                wrappedTextLine("Wheel: zoom only. RMB drag: pan. Use Preview Tint to recolor the font preview and Preview panel controls to switch between full font and sample text preview.")
            }
        }
    }

    private fun drawStatusSection() {
        drawStatusLine()
    }

    private fun drawNinePatchPreviewTypeCombo() {
        ImGui.setNextItemWidth(220f)
        if (ImGui.beginCombo(
                "Preview Type##np_preview_type",
                state.preview.ninePatchStretch.previewType
                    .label(),
            )
        ) {
            TextureAtlasNinePatchPreviewType.entries.forEach { type ->
                if (ImGui.selectable(type.label(), state.preview.ninePatchStretch.previewType == type)) {
                    operations.setNinePatchPreviewType(type)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawNinePatchStretchControls() {
        ImGui.sameLine()
        ImGui.setNextItemWidth(140f)
        if (ImGui.beginCombo(
                "Preset##np_stretch_preset",
                state.preview.ninePatchStretch.preset
                    .label(),
            )
        ) {
            TextureAtlasNinePatchStretchPreset.entries.forEach { preset ->
                if (ImGui.selectable(preset.label(), state.preview.ninePatchStretch.preset == preset)) {
                    operations.setNinePatchStretchPreset(preset)
                }
            }
            ImGui.endCombo()
        }
        syncStretchTargetBuffers()
        textLine("Target")
        ImGui.sameLine()
        ImGui.setNextItemWidth(80f)
        if (ImGui.inputText("##np_stretch_target_width", stretchTargetWidthBuf)) {
            parseCustomCanvasDimension(stretchTargetWidthBuf)?.let(operations::setNinePatchStretchTargetWidth)
        }
        ImGui.sameLine()
        textLine("x")
        ImGui.sameLine()
        ImGui.setNextItemWidth(80f)
        if (ImGui.inputText("##np_stretch_target_height", stretchTargetHeightBuf)) {
            parseCustomCanvasDimension(stretchTargetHeightBuf)?.let(operations::setNinePatchStretchTargetHeight)
        }
        val showSourceGuides = booleanArrayOf(state.preview.ninePatchStretch.showSourceGuides)
        if (ImGui.checkbox("Show Source Guides##np_stretch_show_source_guides", showSourceGuides)) {
            operations.setShowNinePatchStretchSourceGuides(showSourceGuides[0])
        }
        ImGui.sameLine()
        val showDestinationSlices = booleanArrayOf(state.preview.ninePatchStretch.showDestinationSlices)
        if (ImGui.checkbox("Show Destination Slices##np_stretch_show_dest_slices", showDestinationSlices)) {
            operations.setShowNinePatchStretchDestinationSlices(showDestinationSlices[0])
        }
        ImGui.sameLine()
        val showPaddingRect = booleanArrayOf(state.preview.ninePatchStretch.showPaddingRect)
        if (ImGui.checkbox("Show Padding Rect##np_stretch_show_padding_rect", showPaddingRect)) {
            operations.setShowNinePatchStretchPaddingRect(showPaddingRect[0])
        }
    }

    private fun drawPackedAtlasStatusLine() {
        val page = state.selectedPackingPage()
        val plan = state.selectedPackingPlan()
        val zoomPercent =
            when (state.preview.zoomMode) {
                TexturePreviewZoomMode.Fit -> "Fit"
                else -> "${(state.preview.viewport.zoom * 100f).toInt()}%"
            }
        val cursorText =
            if (cursorTextureX != null && cursorTextureY != null) {
                "Cursor: $cursorTextureX, $cursorTextureY"
            } else {
                "Cursor: <outside>"
            }
        val regionCursorText =
            if (cursorRegionX != null && cursorRegionY != null) {
                "Region: $cursorRegionX, $cursorRegionY"
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

    companion object {
        private const val ClickDragThreshold = 6f
        private const val NinePatchCanvasPaddingPixels = 100
        private val GridSpacingOptions = intArrayOf(1, 2, 4, 8, 16, 32, 64)
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

    private fun syncFontSampleBuffer() {
        if (!fontSampleSynced || readBuffer(fontSampleBuf) != state.fontPreview.sampleText) {
            writeBuffer(fontSampleBuf, state.fontPreview.sampleText)
            fontSampleSynced = true
        }
    }

    private fun syncCustomCanvasSizeBuffers() {
        if (lastSyncedCustomCanvasWidth != state.preview.customCanvasWidth) {
            writeBuffer(customCanvasWidthBuf, state.preview.customCanvasWidth.toString())
            lastSyncedCustomCanvasWidth = state.preview.customCanvasWidth
        }
        if (lastSyncedCustomCanvasHeight != state.preview.customCanvasHeight) {
            writeBuffer(customCanvasHeightBuf, state.preview.customCanvasHeight.toString())
            lastSyncedCustomCanvasHeight = state.preview.customCanvasHeight
        }
    }

    private fun syncStretchTargetBuffers() {
        val preview = buildNinePatchStretchPreview(state.ninePatchEditor.draft, state.preview.ninePatchStretch)
        val targetWidth = preview?.targetWidth ?: state.preview.ninePatchStretch.targetWidth
        val targetHeight = preview?.targetHeight ?: state.preview.ninePatchStretch.targetHeight
        if (lastSyncedStretchTargetWidth != targetWidth) {
            writeBuffer(stretchTargetWidthBuf, targetWidth.toString())
            lastSyncedStretchTargetWidth = targetWidth
        }
        if (lastSyncedStretchTargetHeight != targetHeight) {
            writeBuffer(stretchTargetHeightBuf, targetHeight.toString())
            lastSyncedStretchTargetHeight = targetHeight
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
            if (slider("Custom##${idPrefix}_custom_zoom", state.preview::customZoom, 0.05f, 30f, "%.2f", SliderFlag.AlwaysClamp)) {
                operations.setPreviewZoom(state.preview.customZoom)
            }
        }
    }

    private fun drawPreviewSurfaceModeCombo(idPrefix: String) {
        ImGui.sameLine()
        ImGui.setNextItemWidth(140f)
        if (ImGui.beginCombo("Canvas##${idPrefix}_surface_mode", state.preview.surfaceMode.label())) {
            TexturePreviewSurfaceMode.entries.forEach { mode ->
                if (ImGui.selectable(mode.label(), state.preview.surfaceMode == mode)) {
                    operations.setPreviewSurfaceMode(mode)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawCustomCanvasSizeEditors(idPrefix: String) {
        if (state.preview.surfaceMode != TexturePreviewSurfaceMode.Custom) return
        syncCustomCanvasSizeBuffers()
        ImGui.sameLine()
        textLine("Size")
        ImGui.sameLine()
        ImGui.setNextItemWidth(80f)
        if (ImGui.inputText("##${idPrefix}_custom_canvas_width", customCanvasWidthBuf)) {
            parseCustomCanvasDimension(customCanvasWidthBuf)?.let(operations::setCustomCanvasWidth)
        }
        ImGui.sameLine()
        textLine("x")
        ImGui.sameLine()
        ImGui.setNextItemWidth(80f)
        if (ImGui.inputText("##${idPrefix}_custom_canvas_height", customCanvasHeightBuf)) {
            parseCustomCanvasDimension(customCanvasHeightBuf)?.let(operations::setCustomCanvasHeight)
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
            ImGui.sameLine()
            drawGridSizeEditor(gridId)
            ImGui.sameLine()
            drawGridColorEditor(gridId)
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

    private fun deriveSliceHandle(
        handle: com.pashkd.krender.engine.api.TexturePreviewHandle,
        sourceX: Int,
        sourceY: Int,
        sourceWidth: Int,
        sourceHeight: Int,
    ): com.pashkd.krender.engine.api.TexturePreviewHandle {
        val width = handle.width.coerceAtLeast(1).toFloat()
        val height = handle.height.coerceAtLeast(1).toFloat()
        val uSpan = handle.u1 - handle.u0
        val vSpan = handle.v1 - handle.v0
        val u0 = handle.u0 + (sourceX / width) * uSpan
        val v0 = handle.v0 + (sourceY / height) * vSpan
        val u1 = handle.u0 + ((sourceX + sourceWidth) / width) * uSpan
        val v1 = handle.v0 + ((sourceY + sourceHeight) / height) * vSpan
        return com.pashkd.krender.engine.api.TexturePreviewHandle(
            id = handle.id,
            width = sourceWidth,
            height = sourceHeight,
            u0 = u0,
            v0 = v0,
            u1 = u1,
            v1 = v1,
        )
    }
}

private fun formatCanvasMode(mode: TextureAtlasCanvasMode): String =
    when (mode) {
        TextureAtlasCanvasMode.TextureAtlas -> "Atlas File"
        TextureAtlasCanvasMode.NinePatch -> "NinePatch Editor"
        TextureAtlasCanvasMode.FontPreview -> "Font Preview"
        TextureAtlasCanvasMode.FinalPackedAtlas -> "Atlas Preview"
    }

private fun TextureAtlasNinePatchPreviewType.label(): String =
    when (this) {
        TextureAtlasNinePatchPreviewType.Source -> "Source"
        TextureAtlasNinePatchPreviewType.StretchTest -> "Stretch Test"
    }

private fun TextureAtlasNinePatchStretchPreset.label(): String =
    when (this) {
        TextureAtlasNinePatchStretchPreset.Actual -> "Actual"
        TextureAtlasNinePatchStretchPreset.Button -> "Button"
        TextureAtlasNinePatchStretchPreset.Panel -> "Panel"
        TextureAtlasNinePatchStretchPreset.Custom -> "Custom"
    }

private fun TexturePreviewSurfaceMode.label(): String =
    when (this) {
        TexturePreviewSurfaceMode.Actual -> "Actual"
        TexturePreviewSurfaceMode.Padding -> "Padding"
        TexturePreviewSurfaceMode.Custom -> "Custom"
    }

private fun parseCustomCanvasDimension(buffer: ByteArray): Int? = readBuffer(buffer).trim().toIntOrNull()
