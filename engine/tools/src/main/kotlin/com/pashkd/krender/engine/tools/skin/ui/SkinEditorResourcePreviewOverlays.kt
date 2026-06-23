package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.AtlasRegionHitInfo
import com.pashkd.krender.engine.tools.skin.AtlasRegionScreenRect
import com.pashkd.krender.engine.tools.skin.ResourcePreviewViewportLayout
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.atlasRegionScreenRect
import com.pashkd.krender.engine.tools.skin.clipRectToViewport
import com.pashkd.krender.engine.tools.skin.packImColor
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

/** Pure ImGui drawing helpers for atlas/texture preview overlays. */
internal object SkinEditorResourcePreviewOverlays {
    @Suppress("ReturnCount")
    fun drawTexturePreviewBackground(
        selectedResource: SkinResourceInfo?,
        layout: ResourcePreviewViewportLayout,
        showCheckerboard: Boolean,
    ) {
        if (!showCheckerboard) return
        if (selectedResource?.category !in setOf(SkinResourceCategory.Atlas, SkinResourceCategory.AtlasRegion, SkinResourceCategory.Texture)) return
        val imageRect = clipRectToViewport(imageScreenRect(layout), layout) ?: return
        val drawList = ImGui.windowDrawList
        val tileSize = (16f * layout.effectiveZoom).coerceIn(8f, 32f)
        var rowIndex = 0
        var y = imageRect.minY
        while (y < imageRect.maxY) {
            var columnIndex = 0
            var x = imageRect.minX
            while (x < imageRect.maxX) {
                val color = if ((rowIndex + columnIndex) % 2 == 0) CheckerboardLightColor else CheckerboardDarkColor
                drawList.addRectFilled(
                    ImVec2(x, y),
                    ImVec2(minOf(x + tileSize, imageRect.maxX), minOf(y + tileSize, imageRect.maxY)),
                    color,
                )
                x += tileSize
                columnIndex++
            }
            y += tileSize
            rowIndex++
        }
    }

    fun drawAtlasGrid(
        layout: ResourcePreviewViewportLayout,
        gridSize: Int,
        minScreenSpacing: Float,
    ) {
        val screenSpacing = gridSize.coerceAtLeast(1) * layout.effectiveZoom
        if (screenSpacing < minScreenSpacing) return
        val imageRect = clipRectToViewport(imageScreenRect(layout), layout) ?: return
        val drawList = ImGui.windowDrawList
        var x = imageRect.minX
        while (x <= imageRect.maxX) {
            drawList.addLine(ImVec2(x, imageRect.minY), ImVec2(x, imageRect.maxY), GridColor, 1f)
            x += screenSpacing
        }
        var y = imageRect.minY
        while (y <= imageRect.maxY) {
            drawList.addLine(ImVec2(imageRect.minX, y), ImVec2(imageRect.maxX, y), GridColor, 1f)
            y += screenSpacing
        }
    }

    fun drawAtlasRegionBounds(
        regions: List<AtlasRegionHitInfo>,
        layout: ResourcePreviewViewportLayout,
    ) {
        val drawList = ImGui.windowDrawList
        regions.forEach { region ->
            val screenRect = clipRectToViewport(atlasRegionScreenRect(region, layout), layout) ?: return@forEach
            drawList.addRect(
                ImVec2(screenRect.minX, screenRect.minY),
                ImVec2(screenRect.maxX, screenRect.maxY),
                AllRegionBoundsColor,
            )
        }
    }

    fun drawHoveredAtlasRegion(
        hoveredRegion: AtlasRegionHitInfo,
        layout: ResourcePreviewViewportLayout,
    ) {
        val drawList = ImGui.windowDrawList
        val screenRect = clipRectToViewport(atlasRegionScreenRect(hoveredRegion, layout), layout) ?: return
        drawList.addRectFilled(
            ImVec2(screenRect.minX, screenRect.minY),
            ImVec2(screenRect.maxX, screenRect.maxY),
            HoverFillColor,
        )
        drawList.addRect(
            ImVec2(screenRect.minX, screenRect.minY),
            ImVec2(screenRect.maxX, screenRect.maxY),
            HoverStrokeColor,
        )
        drawList.addRect(
            ImVec2(screenRect.minX + 1f, screenRect.minY + 1f),
            ImVec2(screenRect.maxX - 1f, screenRect.maxY - 1f),
            HoverStrokeColor,
        )
    }

    private fun imageScreenRect(layout: ResourcePreviewViewportLayout): AtlasRegionScreenRect =
        AtlasRegionScreenRect(
            minX = layout.imageX,
            minY = layout.imageY,
            maxX = layout.imageX + layout.imageWidth,
            maxY = layout.imageY + layout.imageHeight,
        )

    private val CheckerboardLightColor = packImColor(104, 104, 104, 255)
    private val CheckerboardDarkColor = packImColor(72, 72, 72, 255)
    private val GridColor = packImColor(255, 255, 255, 48)
    private val AllRegionBoundsColor = packImColor(255, 214, 102, 180)
    private val HoverFillColor = packImColor(64, 173, 255, 48)
    private val HoverStrokeColor = packImColor(64, 173, 255, 255)
}
