package com.pashkd.krender.engine.tools.common.bitmapfont.preview

import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontGlyph
import com.pashkd.krender.engine.tools.common.bitmapfont.model.SampleTextLayout
import com.pashkd.krender.engine.tools.common.canvas.CanvasViewportLayout
import com.pashkd.krender.engine.tools.common.canvas.packColor
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

object FontPreviewOverlays {
    private val GlyphBoundsColor = packColor(255, 214, 102, 180)
    private val GlyphSelectedColor = packColor(255, 92, 92, 200)
    private val GlyphHoveredColor = packColor(64, 173, 255, 180)
    private val SelectedFillColor = packColor(255, 92, 92, 56)
    private val HoverFillColor = packColor(64, 173, 255, 56)
    private val LabelColor = packColor(255, 255, 255, 255)

    fun drawGlyphBounds(
        glyphs: List<BitmapFontGlyph>,
        layout: CanvasViewportLayout,
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

    fun hitTestGlyph(
        glyphs: List<BitmapFontGlyph>,
        layout: CanvasViewportLayout,
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

    fun drawSampleText(
        handle: TexturePreviewHandle,
        layout: CanvasViewportLayout,
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
