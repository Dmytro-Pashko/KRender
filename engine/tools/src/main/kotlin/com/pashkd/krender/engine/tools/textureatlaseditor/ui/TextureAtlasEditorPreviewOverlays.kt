package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.common.ninepatch.NinePatchPreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegionSelection
import com.pashkd.krender.engine.tools.textureatlaseditor.AtlasRegionId
import com.pashkd.krender.engine.tools.textureatlaseditor.BitmapFontGlyph
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchDraft
import com.pashkd.krender.engine.tools.textureatlaseditor.SampleTextLayout
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorCanvasRect
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasNinePatchStretchPreview
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TexturePreviewViewportLayout
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

internal object TextureAtlasEditorPreviewOverlays {
    fun drawCheckerboard(layout: TexturePreviewViewportLayout) {
        TexturePreviewOverlays.drawCheckerboard(layout)
    }

    fun drawGrid(
        layout: TexturePreviewViewportLayout,
        spacingPixels: Int = 32,
        color: Int = GridColor,
    ) {
        TexturePreviewOverlays.drawGrid(layout, spacingPixels, color)
    }

    fun drawRegionBounds(
        regions: List<TextureAtlasRegion>,
        layout: TexturePreviewViewportLayout,
        selectedRegion: TextureAtlasRegion?,
        hoveredRegion: TextureAtlasRegion?,
    ) {
        TexturePreviewOverlays.drawRegionBounds(
            regions = regions.mapNotNull(TextureAtlasRegion::toPreviewRegion),
            layout = layout,
            selection = TexturePreviewRegionSelection(selectedRegion?.id, hoveredRegion?.id),
        )
    }

    fun labelRegion(
        region: TextureAtlasRegion,
        layout: TexturePreviewViewportLayout,
    ) {
        val previewRegion = region.toPreviewRegion() ?: return
        TexturePreviewOverlays.labelRegion(previewRegion, layout)
    }

    fun drawNinePatchGuides(
        document: NinePatchDocument,
        layout: TexturePreviewViewportLayout,
    ) {
        NinePatchPreviewOverlays.drawSourceGuides(document, layout)
    }

    fun drawNinePatchDraftGuides(
        overlay: NinePatchDraftOverlay,
        highlightedHandle: NinePatchGuideHandleId? = null,
    ) {
        NinePatchPreviewOverlays.drawDraftGuides(overlay, highlightedHandle)
    }

    fun drawNinePatchStretchOverlays(
        preview: TextureAtlasNinePatchStretchPreview,
        layout: TexturePreviewViewportLayout,
        showSourceGuides: Boolean,
        showDestinationSlices: Boolean,
        showPaddingRect: Boolean,
    ) {
        NinePatchPreviewOverlays.drawStretchOverlays(preview, layout, showSourceGuides, showDestinationSlices, showPaddingRect)
    }

    fun buildNinePatchDraftOverlay(
        draft: NinePatchDraft,
        layout: TexturePreviewViewportLayout,
    ): NinePatchDraftOverlay = NinePatchPreviewOverlays.buildDraftOverlay(draft, layout)

    fun hitTestNinePatchGuideHandle(
        overlay: NinePatchDraftOverlay,
        screenX: Float,
        screenY: Float,
    ): NinePatchGuideHandleId? = NinePatchPreviewOverlays.hitTestGuideHandle(overlay, screenX, screenY)

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
        layout.regionRects
            .firstOrNull { rect ->
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

    private val GridColor = packImColor(255, 255, 255, 48)
    private val BoundsColor = packImColor(255, 214, 102, 180)
    private val HoverFillColor = packImColor(64, 173, 255, 56)
    private val SelectedFillColor = packImColor(255, 92, 92, 56)
    private val LabelColor = packImColor(255, 255, 255, 255)
    private val PackedPageColor = packImColor(255, 255, 255, 220)
    private val PackedRegionColor = packImColor(77, 184, 255, 120)
    private val PackedHoverRegionColor = packImColor(111, 230, 153, 180)
    private val PackedSelectedRegionColor = packImColor(255, 184, 77, 180)
    private val PackedRegionOutlineColor = packImColor(255, 255, 255, 200)
    private val GlyphBoundsColor = BoundsColor
    private val GlyphSelectedColor = packImColor(255, 92, 92, 200)
    private val GlyphHoveredColor = packImColor(64, 173, 255, 180)

    fun drawFontGlyphBounds(
        glyphs: List<BitmapFontGlyph>,
        layout: TexturePreviewViewportLayout,
        selectedGlyphId: Int?,
        hoveredGlyphId: Int?,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ) {
        val drawList = ImGui.windowDrawList
        glyphs.forEach { glyph ->
            if (glyph.width <= 0 || glyph.height <= 0) return@forEach
            val minX = layout.imageX + (glyph.x - offsetX) * layout.effectiveZoom
            val minY = layout.imageY + (glyph.y - offsetY) * layout.effectiveZoom
            val maxX = minX + glyph.width * layout.effectiveZoom
            val maxY = minY + glyph.height * layout.effectiveZoom
            val strokeColor =
                when (glyph.id) {
                    selectedGlyphId -> GlyphSelectedColor
                    hoveredGlyphId -> GlyphHoveredColor
                    else -> GlyphBoundsColor
                }
            val fillColor =
                when (glyph.id) {
                    selectedGlyphId -> SelectedFillColor
                    hoveredGlyphId -> HoverFillColor
                    else -> null
                }
            fillColor?.let { color ->
                drawList.addRectFilled(ImVec2(minX, minY), ImVec2(maxX, maxY), color)
            }
            drawList.addRect(ImVec2(minX, minY), ImVec2(maxX, maxY), strokeColor, 0f, thickness = 2f)
        }
    }

    fun hitTestFontGlyph(
        glyphs: List<BitmapFontGlyph>,
        layout: TexturePreviewViewportLayout,
        screenX: Float,
        screenY: Float,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ): BitmapFontGlyph? =
        glyphs.firstOrNull { glyph ->
            if (glyph.width <= 0 || glyph.height <= 0) return@firstOrNull false
            val minX = layout.imageX + (glyph.x - offsetX) * layout.effectiveZoom
            val minY = layout.imageY + (glyph.y - offsetY) * layout.effectiveZoom
            val maxX = minX + glyph.width * layout.effectiveZoom
            val maxY = minY + glyph.height * layout.effectiveZoom
            screenX >= minX && screenX <= maxX && screenY >= minY && screenY <= maxY
        }

    fun drawFontSampleText(
        handle: TexturePreviewHandle,
        layout: TexturePreviewViewportLayout,
        sampleLayout: SampleTextLayout,
        tintColor: Int = LabelColor,
    ) {
        if (sampleLayout.glyphPlacements.isEmpty() || handle.width <= 0 || handle.height <= 0) return
        val drawList = ImGui.windowDrawList
        val originX = layout.imageX - sampleLayout.boundsMinX * layout.effectiveZoom
        val originY = layout.imageY - sampleLayout.boundsMinY * layout.effectiveZoom
        val uSpan = handle.u1 - handle.u0
        val vSpan = handle.v1 - handle.v0
        sampleLayout.glyphPlacements.forEach { placement ->
            val glyph = placement.glyph
            if (glyph.width <= 0 || glyph.height <= 0) return@forEach
            val minX = originX + placement.x * layout.effectiveZoom
            val minY = originY + placement.y * layout.effectiveZoom
            val maxX = minX + glyph.width * layout.effectiveZoom
            val maxY = minY + glyph.height * layout.effectiveZoom
            val u0 = handle.u0 + (glyph.x.toFloat() / handle.width.toFloat()) * uSpan
            val v0 = handle.v0 + (glyph.y.toFloat() / handle.height.toFloat()) * vSpan
            val u1 = handle.u0 + ((glyph.x + glyph.width).toFloat() / handle.width.toFloat()) * uSpan
            val v1 = handle.v0 + ((glyph.y + glyph.height).toFloat() / handle.height.toFloat()) * vSpan
            drawList.addImage(
                handle.id,
                ImVec2(minX, minY),
                ImVec2(maxX, maxY),
                ImVec2(u0, v0),
                ImVec2(u1, v1),
                tintColor,
            )
        }
    }
}

private fun TextureAtlasRegion.toPreviewRegion(): TexturePreviewRegion<AtlasRegionId>? {
    val regionXy = xy
    val regionSize = size
    return if (regionXy != null && regionSize != null) {
        TexturePreviewRegion(
            id = id,
            label = id.regionName,
            x = regionXy.first,
            y = regionXy.second,
            width = regionSize.first,
            height = regionSize.second,
        )
    } else {
        null
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

internal typealias NinePatchDraftOverlay = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchDraftOverlay
internal typealias NinePatchGuideOverlay = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchGuideOverlay
internal typealias NinePatchGuideHandleOverlay = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchGuideHandleOverlay
internal typealias NinePatchGuideHandleId = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchGuideHandleId
internal typealias NinePatchGuideKind = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchGuideKind
internal typealias NinePatchGuideHandleRole = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchGuideHandleRole
internal typealias NinePatchGuideOrientation = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchGuideOrientation
