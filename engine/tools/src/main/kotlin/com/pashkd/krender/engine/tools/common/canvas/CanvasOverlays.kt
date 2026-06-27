package com.pashkd.krender.engine.tools.common.canvas

import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

object CanvasOverlays {
    private val CheckerLight = packColor(104, 104, 104, 255)
    private val CheckerDark = packColor(72, 72, 72, 255)
    private const val DefaultCheckerSize = 8

    fun drawCheckerboard(
        layout: CanvasViewportLayout,
        checkerSize: Int = DefaultCheckerSize,
    ) {
        val drawList = ImGui.windowDrawList
        val minX = layout.imageX.toInt()
        val minY = layout.imageY.toInt()
        val maxX = (layout.imageX + layout.imageWidth).toInt()
        val maxY = (layout.imageY + layout.imageHeight).toInt()
        var y = minY
        while (y < maxY) {
            var x = minX
            val rowIndex = (y - minY) / checkerSize
            while (x < maxX) {
                val colIndex = (x - minX) / checkerSize
                val color = if ((rowIndex + colIndex) % 2 == 0) CheckerLight else CheckerDark
                val cellMaxX = minOf(x + checkerSize, maxX).toFloat()
                val cellMaxY = minOf(y + checkerSize, maxY).toFloat()
                drawList.addRectFilled(ImVec2(x.toFloat(), y.toFloat()), ImVec2(cellMaxX, cellMaxY), color)
                x += checkerSize
            }
            y += checkerSize
        }
    }

    fun drawGrid(
        layout: CanvasViewportLayout,
        spacingPixels: Int = 32,
        color: Int = packColor(255, 255, 255, 48),
    ) {
        if (spacingPixels <= 0) return
        val drawList = ImGui.windowDrawList
        val step = spacingPixels * layout.effectiveZoom
        if (step < 2f) return
        val minX = layout.imageX
        val minY = layout.imageY
        val maxX = layout.imageX + layout.imageWidth
        val maxY = layout.imageY + layout.imageHeight
        var x = minX
        while (x <= maxX) {
            drawList.addLine(ImVec2(x, minY), ImVec2(x, maxY), color)
            x += step
        }
        var y = minY
        while (y <= maxY) {
            drawList.addLine(ImVec2(minX, y), ImVec2(maxX, y), color)
            y += step
        }
    }
}

fun packColor(
    r: Int,
    g: Int,
    b: Int,
    a: Int,
): Int = (a shl 24) or (b shl 16) or (g shl 8) or r
