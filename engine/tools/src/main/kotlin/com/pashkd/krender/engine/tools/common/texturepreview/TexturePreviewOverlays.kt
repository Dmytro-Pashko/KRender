package com.pashkd.krender.engine.tools.common.texturepreview

import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

object TexturePreviewOverlays {
    fun drawCheckerboard(layout: TexturePreviewViewportLayout) {
        val drawList = ImGui.windowDrawList
        val tile = (16f * layout.effectiveZoom).coerceIn(8f, 32f)
        var row = 0
        var y = layout.surfaceY
        while (y < layout.surfaceY + layout.surfaceHeight) {
            var column = 0
            var x = layout.surfaceX
            while (x < layout.surfaceX + layout.surfaceWidth) {
                drawList.addRectFilled(
                    ImVec2(x, y),
                    ImVec2(minOf(x + tile, layout.surfaceX + layout.surfaceWidth), minOf(y + tile, layout.surfaceY + layout.surfaceHeight)),
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
        var x = layout.surfaceX
        while (x <= layout.surfaceX + layout.surfaceWidth) {
            drawList.addLine(ImVec2(x, layout.surfaceY), ImVec2(x, layout.surfaceY + layout.surfaceHeight), color, 1f)
            x += spacing
        }
        var y = layout.surfaceY
        while (y <= layout.surfaceY + layout.surfaceHeight) {
            drawList.addLine(ImVec2(layout.surfaceX, y), ImVec2(layout.surfaceX + layout.surfaceWidth, y), color, 1f)
            y += spacing
        }
    }

    fun <T> drawRegionBounds(
        regions: List<TexturePreviewRegion<T>>,
        layout: TexturePreviewViewportLayout,
        selection: TexturePreviewRegionSelection<T> = TexturePreviewRegionSelection(),
    ) {
        val drawList = ImGui.windowDrawList
        regions.forEach { region ->
            val rect = textureRegionScreenRect(region, layout)
            val strokeColor =
                when (region.id) {
                    selection.selectedId -> SelectedColor
                    selection.hoveredId -> HoverColor
                    else -> BoundsColor
                }
            val fillColor =
                when (region.id) {
                    selection.selectedId -> SelectedFillColor
                    selection.hoveredId -> HoverFillColor
                    else -> null
                }
            fillColor?.let { color ->
                drawList.addRectFilled(ImVec2(rect.minX, rect.minY), ImVec2(rect.maxX, rect.maxY), color)
            }
            drawList.addRect(ImVec2(rect.minX, rect.minY), ImVec2(rect.maxX, rect.maxY), strokeColor, 0f, thickness = 2f)
        }
    }

    fun labelRegion(
        region: TexturePreviewRegion<*>,
        layout: TexturePreviewViewportLayout,
    ) {
        val rect = textureRegionScreenRect(region, layout)
        ImGui.windowDrawList.addText(ImVec2(rect.minX + 4f, rect.minY + 4f), LabelColor, region.label)
    }

    private val CheckerLight = packColor(104, 104, 104, 255)
    private val CheckerDark = packColor(72, 72, 72, 255)
    private val GridColor = packColor(255, 255, 255, 48)
    private val BoundsColor = packColor(255, 214, 102, 180)
    private val HoverFillColor = packColor(64, 173, 255, 56)
    private val HoverColor = packColor(64, 173, 255, 220)
    private val SelectedFillColor = packColor(255, 92, 92, 56)
    private val SelectedColor = packColor(255, 92, 92, 255)
    private val LabelColor = packColor(255, 255, 255, 255)
}

fun packColor(
    r: Int,
    g: Int,
    b: Int,
    a: Int,
): Int = (a shl 24) or (b shl 16) or (g shl 8) or r
