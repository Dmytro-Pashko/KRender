package com.pashkd.krender.engine.tools.textureatlaseditor.ui

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
    ) {
        val spacing = spacingPixels * layout.effectiveZoom
        if (spacing < 8f) return
        val drawList = ImGui.windowDrawList
        var x = layout.imageX
        while (x <= layout.imageX + layout.imageWidth) {
            drawList.addLine(ImVec2(x, layout.imageY), ImVec2(x, layout.imageY + layout.imageHeight), GridColor, 1f)
            x += spacing
        }
        var y = layout.imageY
        while (y <= layout.imageY + layout.imageHeight) {
            drawList.addLine(ImVec2(layout.imageX, y), ImVec2(layout.imageX + layout.imageWidth, y), GridColor, 1f)
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
