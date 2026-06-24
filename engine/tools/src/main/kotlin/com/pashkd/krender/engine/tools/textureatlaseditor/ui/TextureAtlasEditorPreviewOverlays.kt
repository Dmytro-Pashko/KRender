package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.BitmapFontGlyph
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchDraft
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchSegment
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorCanvasRect
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureRegionScreenRect
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

internal object TextureAtlasEditorPreviewOverlays {
    fun drawCheckerboard(layout: TexturePreviewViewportLayout) {
        val drawList = ImGui.windowDrawList
        val tile = (16f * layout.effectiveZoom).coerceIn(8f, 32f)
        var row = 0
        var y = layout.imageY
        while (y < layout.imageY + layout.imageHeight) {
            var column = 0
            var x = layout.imageX
            while (x < layout.imageX + layout.imageWidth) {
                drawList.addRectFilled(
                    ImVec2(x, y),
                    ImVec2(minOf(x + tile, layout.imageX + layout.imageWidth), minOf(y + tile, layout.imageY + layout.imageHeight)),
                    if ((row + column) % 2 == 0) CheckerLight else CheckerDark,
                )
                x += tile
                column++
            }
            y += tile
            row++
        }
    }

    fun drawGrid(
        layout: TexturePreviewViewportLayout,
        spacingPixels: Int = 32,
        color: Int = GridColor,
    ) {
        val spacing = spacingPixels * layout.effectiveZoom
        if (spacing < 8f) return
        val drawList = ImGui.windowDrawList
        var x = layout.imageX
        while (x <= layout.imageX + layout.imageWidth) {
            drawList.addLine(ImVec2(x, layout.imageY), ImVec2(x, layout.imageY + layout.imageHeight), color, 1f)
            x += spacing
        }
        var y = layout.imageY
        while (y <= layout.imageY + layout.imageHeight) {
            drawList.addLine(ImVec2(layout.imageX, y), ImVec2(layout.imageX + layout.imageWidth, y), color, 1f)
            y += spacing
        }
    }

    fun drawRegionBounds(
        regions: List<TextureAtlasRegion>,
        layout: TexturePreviewViewportLayout,
        selectedRegion: TextureAtlasRegion?,
        hoveredRegion: TextureAtlasRegion?,
    ) {
        val drawList = ImGui.windowDrawList
        regions.forEach { region ->
            val rect = atlasRegionScreenRect(region, layout) ?: return@forEach
            val color =
                when {
                    selectedRegion?.id == region.id -> SelectedColor
                    hoveredRegion?.id == region.id -> HoverColor
                    else -> BoundsColor
                }
            drawList.addRect(ImVec2(rect.minX, rect.minY), ImVec2(rect.maxX, rect.maxY), color, 0f, thickness = 2f)
        }
    }

    fun labelRegion(
        region: TextureAtlasRegion,
        layout: TexturePreviewViewportLayout,
    ) {
        val rect = atlasRegionScreenRect(region, layout) ?: return
        val drawList = ImGui.windowDrawList
        drawList.addText(ImVec2(rect.minX + 4f, rect.minY + 4f), LabelColor, region.id.regionName)
    }

    fun drawNinePatchGuides(
        document: NinePatchDocument,
        layout: TexturePreviewViewportLayout,
    ) {
        val drawList = ImGui.windowDrawList
        val drawableMinX = layout.imageX + layout.effectiveZoom
        val drawableMinY = layout.imageY + layout.effectiveZoom
        val drawableMaxX = layout.imageX + (document.imageWidth - 1) * layout.effectiveZoom
        val drawableMaxY = layout.imageY + (document.imageHeight - 1) * layout.effectiveZoom
        drawList.addRect(
            ImVec2(drawableMinX, drawableMinY),
            ImVec2(drawableMaxX, drawableMaxY),
            NinePatchContentColor,
            0f,
            thickness = 2f,
        )

        document.stretchX.forEach { segment ->
            drawHorizontalGuide(segment, layout.imageY + layout.effectiveZoom * 0.5f, NinePatchStretchXColor, layout)
        }
        document.paddingX?.let { segment ->
            drawHorizontalGuide(segment, layout.imageY + (document.imageHeight - 0.5f) * layout.effectiveZoom, NinePatchPaddingColor, layout)
        }
        document.stretchY.forEach { segment ->
            drawVerticalGuide(segment, layout.imageX + layout.effectiveZoom * 0.5f, NinePatchStretchYColor, layout)
        }
        document.paddingY?.let { segment ->
            drawVerticalGuide(segment, layout.imageX + (document.imageWidth - 0.5f) * layout.effectiveZoom, NinePatchPaddingColor, layout)
        }
    }

    fun drawNinePatchDraftGuides(
        overlay: NinePatchDraftOverlay,
        highlightedHandle: NinePatchGuideHandleId? = null,
    ) {
        val drawList = ImGui.windowDrawList
        drawList.addRect(
            ImVec2(overlay.contentMinX, overlay.contentMinY),
            ImVec2(overlay.contentMaxX, overlay.contentMaxY),
            NinePatchContentColor,
            0f,
            thickness = 2f,
        )
        drawDraftGuide(overlay.stretchX, highlightedHandle)
        drawDraftGuide(overlay.stretchY, highlightedHandle)
        overlay.paddingX?.let { guide -> drawDraftGuide(guide, highlightedHandle) }
        overlay.paddingY?.let { guide -> drawDraftGuide(guide, highlightedHandle) }
    }

    fun buildNinePatchDraftOverlay(
        draft: NinePatchDraft,
        layout: TexturePreviewViewportLayout,
    ): NinePatchDraftOverlay {
        val contentMinX = layout.imageX
        val contentMinY = layout.imageY
        val contentMaxX = contentMinX + draft.contentWidth * layout.effectiveZoom
        val contentMaxY = contentMinY + draft.contentHeight * layout.effectiveZoom
        val lineOffset = (6f * layout.effectiveZoom).coerceIn(8f, 18f)
        val handleSize = (8f * layout.effectiveZoom).coerceIn(8f, 16f)
        return NinePatchDraftOverlay(
            contentMinX = contentMinX,
            contentMinY = contentMinY,
            contentMaxX = contentMaxX,
            contentMaxY = contentMaxY,
            stretchX =
                buildHorizontalDraftGuide(
                    kind = NinePatchGuideKind.StretchX,
                    segment = draft.stretchX,
                    contentMinX = contentMinX,
                    y = contentMinY - lineOffset,
                    layout = layout,
                    handleSize = handleSize,
                ),
            stretchY =
                buildVerticalDraftGuide(
                    kind = NinePatchGuideKind.StretchY,
                    segment = draft.stretchY,
                    contentMinY = contentMinY,
                    x = contentMinX - lineOffset,
                    layout = layout,
                    handleSize = handleSize,
                ),
            paddingX =
                draft.paddingX?.let { segment ->
                    buildHorizontalDraftGuide(
                        kind = NinePatchGuideKind.PaddingX,
                        segment = segment,
                        contentMinX = contentMinX,
                        y = contentMaxY + lineOffset,
                        layout = layout,
                        handleSize = handleSize,
                    )
                },
            paddingY =
                draft.paddingY?.let { segment ->
                    buildVerticalDraftGuide(
                        kind = NinePatchGuideKind.PaddingY,
                        segment = segment,
                        contentMinY = contentMinY,
                        x = contentMaxX + lineOffset,
                        layout = layout,
                        handleSize = handleSize,
                    )
                },
        )
    }

    fun hitTestNinePatchGuideHandle(
        overlay: NinePatchDraftOverlay,
        screenX: Float,
        screenY: Float,
    ): NinePatchGuideHandleId? =
        listOfNotNull(
            overlay.stretchX,
            overlay.stretchY,
            overlay.paddingX,
            overlay.paddingY,
        ).asReversed()
            .flatMap { guide -> listOf(guide.startHandle, guide.endHandle) }
            .firstOrNull { handle ->
                screenX >= handle.minX && screenX <= handle.maxX &&
                    screenY >= handle.minY && screenY <= handle.maxY
            }?.id

    private fun drawDraftGuide(
        guide: NinePatchGuideOverlay,
        highlightedHandle: NinePatchGuideHandleId?,
    ) {
        val drawList = ImGui.windowDrawList
        drawList.addLine(
            ImVec2(guide.lineMinX, guide.lineMinY),
            ImVec2(guide.lineMaxX, guide.lineMaxY),
            guide.color,
            3f,
        )
        drawDraftHandle(guide.startHandle, guide.color, highlightedHandle == guide.startHandle.id)
        drawDraftHandle(guide.endHandle, guide.color, highlightedHandle == guide.endHandle.id)
    }

    private fun drawDraftHandle(
        handle: NinePatchGuideHandleOverlay,
        color: Int,
        highlighted: Boolean,
    ) {
        val drawList = ImGui.windowDrawList
        val fillColor = if (highlighted) SelectedColor else color
        drawList.addRectFilled(ImVec2(handle.minX, handle.minY), ImVec2(handle.maxX, handle.maxY), fillColor, 2f)
        drawList.addRect(ImVec2(handle.minX, handle.minY), ImVec2(handle.maxX, handle.maxY), LabelColor, 2f, thickness = 1.5f)
    }

    private fun buildHorizontalDraftGuide(
        kind: NinePatchGuideKind,
        segment: NinePatchSegment,
        contentMinX: Float,
        y: Float,
        layout: TexturePreviewViewportLayout,
        handleSize: Float,
    ): NinePatchGuideOverlay {
        val startX = contentMinX + segment.start * layout.effectiveZoom
        val endX = contentMinX + (segment.start + segment.length) * layout.effectiveZoom
        return NinePatchGuideOverlay(
            kind = kind,
            orientation = NinePatchGuideOrientation.Horizontal,
            segment = segment,
            lineMinX = startX,
            lineMinY = y,
            lineMaxX = endX,
            lineMaxY = y,
            color = guideColor(kind),
            startHandle =
                NinePatchGuideHandleOverlay(
                    id = NinePatchGuideHandleId(kind, NinePatchGuideHandleRole.Start),
                    minX = startX - handleSize * 0.5f,
                    minY = y - handleSize * 0.5f,
                    maxX = startX + handleSize * 0.5f,
                    maxY = y + handleSize * 0.5f,
                ),
            endHandle =
                NinePatchGuideHandleOverlay(
                    id = NinePatchGuideHandleId(kind, NinePatchGuideHandleRole.End),
                    minX = endX - handleSize * 0.5f,
                    minY = y - handleSize * 0.5f,
                    maxX = endX + handleSize * 0.5f,
                    maxY = y + handleSize * 0.5f,
                ),
        )
    }

    private fun buildVerticalDraftGuide(
        kind: NinePatchGuideKind,
        segment: NinePatchSegment,
        contentMinY: Float,
        x: Float,
        layout: TexturePreviewViewportLayout,
        handleSize: Float,
    ): NinePatchGuideOverlay {
        val startY = contentMinY + segment.start * layout.effectiveZoom
        val endY = contentMinY + (segment.start + segment.length) * layout.effectiveZoom
        return NinePatchGuideOverlay(
            kind = kind,
            orientation = NinePatchGuideOrientation.Vertical,
            segment = segment,
            lineMinX = x,
            lineMinY = startY,
            lineMaxX = x,
            lineMaxY = endY,
            color = guideColor(kind),
            startHandle =
                NinePatchGuideHandleOverlay(
                    id = NinePatchGuideHandleId(kind, NinePatchGuideHandleRole.Start),
                    minX = x - handleSize * 0.5f,
                    minY = startY - handleSize * 0.5f,
                    maxX = x + handleSize * 0.5f,
                    maxY = startY + handleSize * 0.5f,
                ),
            endHandle =
                NinePatchGuideHandleOverlay(
                    id = NinePatchGuideHandleId(kind, NinePatchGuideHandleRole.End),
                    minX = x - handleSize * 0.5f,
                    minY = endY - handleSize * 0.5f,
                    maxX = x + handleSize * 0.5f,
                    maxY = endY + handleSize * 0.5f,
                ),
        )
    }

    private fun guideColor(kind: NinePatchGuideKind): Int =
        when (kind) {
            NinePatchGuideKind.StretchX -> NinePatchStretchXColor
            NinePatchGuideKind.StretchY -> NinePatchStretchYColor
            NinePatchGuideKind.PaddingX,
            NinePatchGuideKind.PaddingY,
            -> NinePatchPaddingColor
        }

    private fun drawHorizontalGuide(
        segment: NinePatchSegment,
        y: Float,
        color: Int,
        layout: TexturePreviewViewportLayout,
    ) {
        val startX = layout.imageX + (segment.start + 1f) * layout.effectiveZoom
        val endX = layout.imageX + (segment.endInclusive + 2f) * layout.effectiveZoom
        ImGui.windowDrawList.addLine(ImVec2(startX, y), ImVec2(endX, y), color, 3f)
    }

    private fun drawVerticalGuide(
        segment: NinePatchSegment,
        x: Float,
        color: Int,
        layout: TexturePreviewViewportLayout,
    ) {
        val startY = layout.imageY + (segment.start + 1f) * layout.effectiveZoom
        val endY = layout.imageY + (segment.endInclusive + 2f) * layout.effectiveZoom
        ImGui.windowDrawList.addLine(ImVec2(x, startY), ImVec2(x, endY), color, 3f)
    }

    fun drawPackedAtlasPage(
        page: TextureAtlasPackingPage,
        canvasRect: TextureAtlasEditorCanvasRect,
        selectedRegionId: String?,
        hoveredRegionId: String?,
    ): PackedAtlasPreviewLayout {
        val drawList = ImGui.windowDrawList
        val scale =
            minOf(
                (canvasRect.width - 16f) / page.width.toFloat(),
                (canvasRect.height - 16f) / page.height.toFloat(),
            ).coerceAtLeast(0.05f)
        val pageWidth = page.width * scale
        val pageHeight = page.height * scale
        val pageX = canvasRect.x + (canvasRect.width - pageWidth) * 0.5f
        val pageY = canvasRect.y + (canvasRect.height - pageHeight) * 0.5f
        drawList.addRect(ImVec2(pageX, pageY), ImVec2(pageX + pageWidth, pageY + pageHeight), PackedPageColor, 0f, thickness = 2f)
        val regionRects =
            page.regions.associateWith { region ->
                val minX = pageX + region.x * scale
                val minY = pageY + region.y * scale
                val maxX = minX + region.width * scale
                val maxY = minY + region.height * scale
                val color =
                    when (region.id) {
                        selectedRegionId -> PackedSelectedRegionColor
                        hoveredRegionId -> PackedHoverRegionColor
                        else -> PackedRegionColor
                    }
                drawList.addRectFilled(ImVec2(minX, minY), ImVec2(maxX, maxY), color)
                drawList.addRect(ImVec2(minX, minY), ImVec2(maxX, maxY), PackedRegionOutlineColor, 0f, thickness = 1.5f)
                PackedRegionScreenRect(region = region, minX = minX, minY = minY, maxX = maxX, maxY = maxY)
            }
        return PackedAtlasPreviewLayout(
            page = page,
            pageX = pageX,
            pageY = pageY,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            scale = scale,
            regionRects = regionRects.values.toList(),
        )
    }

    fun hitTestPackedRegion(
        layout: PackedAtlasPreviewLayout,
        screenX: Float,
        screenY: Float,
    ): TextureAtlasPackingRegion? =
        layout.regionRects.firstOrNull { rect ->
            screenX >= rect.minX && screenX <= rect.maxX && screenY >= rect.minY && screenY <= rect.maxY
        }?.region

    fun drawPackedRegionBounds(
        regions: List<TextureAtlasPackingRegion>,
        layout: TexturePreviewViewportLayout,
        selectedRegionId: String?,
        hoveredRegionId: String?,
    ) {
        val drawList = ImGui.windowDrawList
        regions.forEach { region ->
            val rect = packedRegionScreenRect(region, layout)
            val fillColor =
                when (region.id) {
                    selectedRegionId -> PackedSelectedRegionColor
                    hoveredRegionId -> PackedHoverRegionColor
                    else -> PackedRegionColor
                }
            drawList.addRectFilled(ImVec2(rect.minX, rect.minY), ImVec2(rect.maxX, rect.maxY), fillColor)
            drawList.addRect(ImVec2(rect.minX, rect.minY), ImVec2(rect.maxX, rect.maxY), PackedRegionOutlineColor, 0f, thickness = 1.5f)
        }
    }

    fun hitTestPackedRegion(
        regions: List<TextureAtlasPackingRegion>,
        layout: TexturePreviewViewportLayout,
        screenX: Float,
        screenY: Float,
    ): TextureAtlasPackingRegion? =
        regions.firstOrNull { region ->
            val rect = packedRegionScreenRect(region, layout)
            screenX >= rect.minX && screenX <= rect.maxX && screenY >= rect.minY && screenY <= rect.maxY
        }

    private val CheckerLight = packImColor(104, 104, 104, 255)
    private val CheckerDark = packImColor(72, 72, 72, 255)
    private val GridColor = packImColor(255, 255, 255, 48)
    private val BoundsColor = packImColor(255, 214, 102, 180)
    private val HoverColor = packImColor(64, 173, 255, 220)
    private val SelectedColor = packImColor(255, 92, 92, 255)
    private val LabelColor = packImColor(255, 255, 255, 255)
    private val NinePatchContentColor = packImColor(255, 255, 255, 180)
    private val NinePatchStretchXColor = packImColor(255, 184, 77, 255)
    private val NinePatchStretchYColor = packImColor(77, 184, 255, 255)
    private val NinePatchPaddingColor = packImColor(111, 230, 153, 255)
    private val PackedPageColor = packImColor(255, 255, 255, 220)
    private val PackedRegionColor = packImColor(77, 184, 255, 120)
    private val PackedHoverRegionColor = packImColor(111, 230, 153, 180)
    private val PackedSelectedRegionColor = packImColor(255, 184, 77, 180)
    private val PackedRegionOutlineColor = packImColor(255, 255, 255, 200)
    private val GlyphBoundsColor = packImColor(200, 200, 200, 80)
    private val GlyphSelectedColor = packImColor(255, 92, 92, 200)
    private val GlyphHoveredColor = packImColor(64, 173, 255, 180)

    fun drawFontGlyphBounds(
        glyphs: List<BitmapFontGlyph>,
        layout: TexturePreviewViewportLayout,
        selectedGlyphId: Int?,
        hoveredGlyphId: Int?,
    ) {
        val drawList = ImGui.windowDrawList
        glyphs.forEach { glyph ->
            if (glyph.width <= 0 || glyph.height <= 0) return@forEach
            val minX = layout.imageX + glyph.x * layout.effectiveZoom
            val minY = layout.imageY + glyph.y * layout.effectiveZoom
            val maxX = minX + glyph.width * layout.effectiveZoom
            val maxY = minY + glyph.height * layout.effectiveZoom
            val color = when (glyph.id) {
                selectedGlyphId -> GlyphSelectedColor
                hoveredGlyphId -> GlyphHoveredColor
                else -> GlyphBoundsColor
            }
            drawList.addRect(ImVec2(minX, minY), ImVec2(maxX, maxY), color, 0f, thickness = 1f)
        }
    }
}

private fun packedRegionScreenRect(
    region: TextureAtlasPackingRegion,
    layout: TexturePreviewViewportLayout,
): PackedRegionScreenRect {
    val minX = layout.imageX + region.x * layout.effectiveZoom
    val minY = layout.imageY + region.y * layout.effectiveZoom
    return PackedRegionScreenRect(
        region = region,
        minX = minX,
        minY = minY,
        maxX = minX + region.width * layout.effectiveZoom,
        maxY = minY + region.height * layout.effectiveZoom,
    )
}

internal data class PackedAtlasPreviewLayout(
    val page: TextureAtlasPackingPage,
    val pageX: Float,
    val pageY: Float,
    val pageWidth: Float,
    val pageHeight: Float,
    val scale: Float,
    val regionRects: List<PackedRegionScreenRect>,
)

internal data class PackedRegionScreenRect(
    val region: TextureAtlasPackingRegion,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

internal data class NinePatchDraftOverlay(
    val contentMinX: Float,
    val contentMinY: Float,
    val contentMaxX: Float,
    val contentMaxY: Float,
    val stretchX: NinePatchGuideOverlay,
    val stretchY: NinePatchGuideOverlay,
    val paddingX: NinePatchGuideOverlay? = null,
    val paddingY: NinePatchGuideOverlay? = null,
)

internal data class NinePatchGuideOverlay(
    val kind: NinePatchGuideKind,
    val orientation: NinePatchGuideOrientation,
    val segment: NinePatchSegment,
    val lineMinX: Float,
    val lineMinY: Float,
    val lineMaxX: Float,
    val lineMaxY: Float,
    val color: Int,
    val startHandle: NinePatchGuideHandleOverlay,
    val endHandle: NinePatchGuideHandleOverlay,
)

internal data class NinePatchGuideHandleOverlay(
    val id: NinePatchGuideHandleId,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

internal data class NinePatchGuideHandleId(
    val kind: NinePatchGuideKind,
    val role: NinePatchGuideHandleRole,
)

internal enum class NinePatchGuideKind {
    StretchX,
    StretchY,
    PaddingX,
    PaddingY,
}

internal enum class NinePatchGuideHandleRole {
    Start,
    End,
}

internal enum class NinePatchGuideOrientation {
    Horizontal,
    Vertical,
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
