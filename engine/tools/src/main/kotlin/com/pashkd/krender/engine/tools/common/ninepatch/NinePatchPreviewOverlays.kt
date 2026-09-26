package com.pashkd.krender.engine.tools.common.ninepatch

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.packColor
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

object NinePatchPreviewOverlays {
    fun drawSourceGuides(
        document: NinePatchDocument,
        layout: TexturePreviewViewportLayout,
    ) {
        val drawList = ImGui.windowDrawList
        val drawableMinX = layout.imageX + layout.effectiveZoom
        val drawableMinY = layout.imageY + layout.effectiveZoom
        val drawableMaxX = layout.imageX + (document.imageWidth - 1) * layout.effectiveZoom
        val drawableMaxY = layout.imageY + (document.imageHeight - 1) * layout.effectiveZoom
        drawList.addRect(ImVec2(drawableMinX, drawableMinY), ImVec2(drawableMaxX, drawableMaxY), ContentColor, 0f, thickness = 2f)
        document.stretchX.forEach { segment ->
            drawHorizontalSourceGuide(segment, layout.imageY + layout.effectiveZoom * 0.5f, StretchXColor, layout)
        }
        document.paddingX?.let { segment ->
            drawHorizontalSourceGuide(segment, layout.imageY + (document.imageHeight - 0.5f) * layout.effectiveZoom, PaddingColor, layout)
        }
        document.stretchY.forEach { segment ->
            drawVerticalSourceGuide(segment, layout.imageX + layout.effectiveZoom * 0.5f, StretchYColor, layout)
        }
        document.paddingY?.let { segment ->
            drawVerticalSourceGuide(segment, layout.imageX + (document.imageWidth - 0.5f) * layout.effectiveZoom, PaddingColor, layout)
        }
    }

    fun drawDraftGuides(
        overlay: NinePatchDraftOverlay,
        highlightedHandle: NinePatchGuideHandleId? = null,
    ) {
        val drawList = ImGui.windowDrawList
        drawList.addRect(ImVec2(overlay.contentMinX, overlay.contentMinY), ImVec2(overlay.contentMaxX, overlay.contentMaxY), ContentColor, 0f, thickness = 2f)
        drawDraftGuide(overlay.stretchX, highlightedHandle)
        drawDraftGuide(overlay.stretchY, highlightedHandle)
        overlay.paddingX?.let { guide -> drawDraftGuide(guide, highlightedHandle) }
        overlay.paddingY?.let { guide -> drawDraftGuide(guide, highlightedHandle) }
    }

    fun drawStretchOverlays(
        preview: NinePatchStretchPreview,
        layout: TexturePreviewViewportLayout,
        showSourceGuides: Boolean,
        showDestinationSlices: Boolean,
        showPaddingRect: Boolean,
    ) {
        val drawList = ImGui.windowDrawList
        drawList.addRect(ImVec2(layout.imageX, layout.imageY), ImVec2(layout.imageX + layout.imageWidth, layout.imageY + layout.imageHeight), ContentColor, 0f, thickness = 2f)
        if (showDestinationSlices) {
            preview.destinationVerticalCuts.forEach { cut ->
                val x = layout.imageX + cut * layout.effectiveZoom
                drawList.addLine(ImVec2(x, layout.imageY), ImVec2(x, layout.imageY + layout.imageHeight), DestinationSliceColor, 2f)
            }
            preview.destinationHorizontalCuts.forEach { cut ->
                val y = layout.imageY + cut * layout.effectiveZoom
                drawList.addLine(ImVec2(layout.imageX, y), ImVec2(layout.imageX + layout.imageWidth, y), DestinationSliceColor, 2f)
            }
        }
        if (showPaddingRect) {
            preview.paddingRect?.let { paddingRect -> drawPaddingRect(paddingRect, layout) }
        }
        if (showSourceGuides) {
            val left = layout.imageX + preview.fixedLeft * layout.effectiveZoom
            val right = layout.imageX + (preview.targetWidth - preview.fixedRight) * layout.effectiveZoom
            val top = layout.imageY + preview.fixedTop * layout.effectiveZoom
            val bottom = layout.imageY + (preview.targetHeight - preview.fixedBottom) * layout.effectiveZoom
            drawList.addLine(ImVec2(left, layout.imageY), ImVec2(left, layout.imageY + layout.imageHeight), StretchXColor, 1.5f)
            drawList.addLine(ImVec2(right, layout.imageY), ImVec2(right, layout.imageY + layout.imageHeight), StretchXColor, 1.5f)
            drawList.addLine(ImVec2(layout.imageX, top), ImVec2(layout.imageX + layout.imageWidth, top), StretchYColor, 1.5f)
            drawList.addLine(ImVec2(layout.imageX, bottom), ImVec2(layout.imageX + layout.imageWidth, bottom), StretchYColor, 1.5f)
        }
    }

    fun buildDraftOverlay(
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
            stretchX = buildHorizontalDraftGuide(NinePatchGuideKind.StretchX, draft.stretchX, contentMinX, contentMinY - lineOffset, layout, handleSize),
            stretchY = buildVerticalDraftGuide(NinePatchGuideKind.StretchY, draft.stretchY, contentMinY, contentMinX - lineOffset, layout, handleSize),
            paddingX = draft.paddingX?.let { segment -> buildHorizontalDraftGuide(NinePatchGuideKind.PaddingX, segment, contentMinX, contentMaxY + lineOffset, layout, handleSize) },
            paddingY = draft.paddingY?.let { segment -> buildVerticalDraftGuide(NinePatchGuideKind.PaddingY, segment, contentMinY, contentMaxX + lineOffset, layout, handleSize) },
        )
    }

    fun hitTestGuideHandle(
        overlay: NinePatchDraftOverlay,
        screenX: Float,
        screenY: Float,
    ): NinePatchGuideHandleId? =
        listOfNotNull(overlay.stretchX, overlay.stretchY, overlay.paddingX, overlay.paddingY)
            .asReversed()
            .flatMap { guide -> listOf(guide.startHandle, guide.endHandle) }
            .firstOrNull { handle -> screenX >= handle.minX && screenX <= handle.maxX && screenY >= handle.minY && screenY <= handle.maxY }
            ?.id

    private fun drawDraftGuide(
        guide: NinePatchGuideOverlay,
        highlightedHandle: NinePatchGuideHandleId?,
    ) {
        val drawList = ImGui.windowDrawList
        drawList.addLine(ImVec2(guide.lineMinX, guide.lineMinY), ImVec2(guide.lineMaxX, guide.lineMaxY), guide.color, 3f)
        drawDraftHandle(guide.startHandle, guide.color, highlightedHandle == guide.startHandle.id)
        drawDraftHandle(guide.endHandle, guide.color, highlightedHandle == guide.endHandle.id)
    }

    private fun drawDraftHandle(
        handle: NinePatchGuideHandleOverlay,
        color: Int,
        highlighted: Boolean,
    ) {
        val fillColor = if (highlighted) SelectedColor else color
        ImGui.windowDrawList.addRectFilled(ImVec2(handle.minX, handle.minY), ImVec2(handle.maxX, handle.maxY), fillColor, 2f)
        ImGui.windowDrawList.addRect(ImVec2(handle.minX, handle.minY), ImVec2(handle.maxX, handle.maxY), LabelColor, 2f, thickness = 1.5f)
    }

    private fun drawPaddingRect(
        rect: NinePatchStretchRect,
        layout: TexturePreviewViewportLayout,
    ) {
        val minX = layout.imageX + rect.x * layout.effectiveZoom
        val minY = layout.imageY + rect.y * layout.effectiveZoom
        val maxX = minX + rect.width * layout.effectiveZoom
        val maxY = minY + rect.height * layout.effectiveZoom
        ImGui.windowDrawList.addRectFilled(ImVec2(minX, minY), ImVec2(maxX, maxY), PaddingFillColor)
        ImGui.windowDrawList.addRect(ImVec2(minX, minY), ImVec2(maxX, maxY), PaddingColor, 0f, thickness = 2f)
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
        return NinePatchGuideOverlay(kind, NinePatchGuideOrientation.Horizontal, segment, startX, y, endX, y, guideColor(kind), buildHandle(kind, NinePatchGuideHandleRole.Start, startX, y, handleSize), buildHandle(kind, NinePatchGuideHandleRole.End, endX, y, handleSize))
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
        return NinePatchGuideOverlay(kind, NinePatchGuideOrientation.Vertical, segment, x, startY, x, endY, guideColor(kind), buildHandle(kind, NinePatchGuideHandleRole.Start, x, startY, handleSize), buildHandle(kind, NinePatchGuideHandleRole.End, x, endY, handleSize))
    }

    private fun buildHandle(
        kind: NinePatchGuideKind,
        role: NinePatchGuideHandleRole,
        centerX: Float,
        centerY: Float,
        size: Float,
    ): NinePatchGuideHandleOverlay =
        NinePatchGuideHandleOverlay(
            id = NinePatchGuideHandleId(kind, role),
            minX = centerX - size * 0.5f,
            minY = centerY - size * 0.5f,
            maxX = centerX + size * 0.5f,
            maxY = centerY + size * 0.5f,
        )

    private fun guideColor(kind: NinePatchGuideKind): Int =
        when (kind) {
            NinePatchGuideKind.StretchX -> StretchXColor
            NinePatchGuideKind.StretchY -> StretchYColor
            NinePatchGuideKind.PaddingX,
            NinePatchGuideKind.PaddingY,
            -> PaddingColor
        }

    private fun drawHorizontalSourceGuide(
        segment: NinePatchSegment,
        y: Float,
        color: Int,
        layout: TexturePreviewViewportLayout,
    ) {
        val startX = layout.imageX + (segment.start + 1f) * layout.effectiveZoom
        val endX = layout.imageX + (segment.endInclusive + 2f) * layout.effectiveZoom
        ImGui.windowDrawList.addLine(ImVec2(startX, y), ImVec2(endX, y), color, 3f)
    }

    private fun drawVerticalSourceGuide(
        segment: NinePatchSegment,
        x: Float,
        color: Int,
        layout: TexturePreviewViewportLayout,
    ) {
        val startY = layout.imageY + (segment.start + 1f) * layout.effectiveZoom
        val endY = layout.imageY + (segment.endInclusive + 2f) * layout.effectiveZoom
        ImGui.windowDrawList.addLine(ImVec2(x, startY), ImVec2(x, endY), color, 3f)
    }

    private val ContentColor = packColor(255, 255, 255, 180)
    private val StretchXColor = packColor(255, 184, 77, 255)
    private val StretchYColor = packColor(77, 184, 255, 255)
    private val PaddingColor = packColor(111, 230, 153, 255)
    private val PaddingFillColor = packColor(111, 230, 153, 48)
    private val DestinationSliceColor = packColor(255, 255, 255, 120)
    private val SelectedColor = packColor(255, 92, 92, 255)
    private val LabelColor = packColor(255, 255, 255, 255)
}

data class NinePatchDraftOverlay(
    val contentMinX: Float,
    val contentMinY: Float,
    val contentMaxX: Float,
    val contentMaxY: Float,
    val stretchX: NinePatchGuideOverlay,
    val stretchY: NinePatchGuideOverlay,
    val paddingX: NinePatchGuideOverlay? = null,
    val paddingY: NinePatchGuideOverlay? = null,
)

data class NinePatchGuideOverlay(
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

data class NinePatchGuideHandleOverlay(
    val id: NinePatchGuideHandleId,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

data class NinePatchGuideHandleId(
    val kind: NinePatchGuideKind,
    val role: NinePatchGuideHandleRole,
)

enum class NinePatchGuideKind {
    StretchX,
    StretchY,
    PaddingX,
    PaddingY,
}

enum class NinePatchGuideHandleRole {
    Start,
    End,
}

enum class NinePatchGuideOrientation {
    Horizontal,
    Vertical,
}
