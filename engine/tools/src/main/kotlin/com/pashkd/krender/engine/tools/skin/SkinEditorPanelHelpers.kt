package com.pashkd.krender.engine.tools.skin

import imgui.ImGui
import java.io.File
import java.nio.charset.StandardCharsets

internal val PreviewScales = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
internal val FontPreviewScales = listOf(0.5f, 1f, 1.5f, 2f)
internal const val MaxInspectorAtlasRegions = 100
internal const val ResourceSearchWidth = 300f
internal const val MaxInlineResourcePreviewHeight = 320f
internal const val MinInlineResourcePreviewScale = 0.05f
internal const val FontPreviewTextHeight = 96f
internal const val ResourcePreviewViewportHeight = 360f
internal const val ResourcePreviewClickDragThreshold = 4f
internal const val MinResourcePreviewGridScreenSpacing = 8f

internal fun formatPreviewScale(scale: Float): String = "${(scale * 100f).toInt()}%"

internal fun formatResourcePreviewZoom(zoomMode: SkinResourceVisualPreviewZoomMode): String =
    when (zoomMode) {
        SkinResourceVisualPreviewZoomMode.Fit -> "Fit"
        SkinResourceVisualPreviewZoomMode.Percent50 -> "50%"
        SkinResourceVisualPreviewZoomMode.Percent100 -> "100%"
        SkinResourceVisualPreviewZoomMode.Percent200 -> "200%"
        SkinResourceVisualPreviewZoomMode.Custom -> "Custom"
    }

internal fun parseResourceColor(values: Map<String, String>): FloatArray? {
    val color = SkinColorValueParser.parse(values) ?: return null
    return floatArrayOf(color.r, color.g, color.b, color.a)
}

internal fun selectedResourceSummary(state: SkinEditorState): String? =
    state.selectedResourceKey?.let { key ->
        val resource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == key }
            ?: return@let "${key.category}.${key.name}"
        "${resource.category}.${resource.name}${if (resource.resolved) "" else " (missing)"}"
    }

internal fun drawSelectedResourcePreviewHint(state: SkinEditorState) {
    val selectedResourceKey = state.selectedResourceKey ?: return
    val resource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == selectedResourceKey } ?: return
    if (resource.category in AvailablePreviewCategories) {
        ImGui.textWrapped("Visual preview is available in the Resources preview section.")
    }
}

internal fun readBuffer(buffer: ByteArray): String {
    val end = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, end, StandardCharsets.UTF_8)
}

internal fun writeBuffer(
    buffer: ByteArray,
    value: String,
) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    bytes.copyInto(buffer, endIndex = bytes.size.coerceAtMost(buffer.size - 1))
}

internal fun safeTextWrapped(value: String) {
    ImGui.textWrapped(value.toAsciiSafeText())
}

internal fun String.toAsciiSafeText(): String =
    buildString(length) {
        for (character in this@toAsciiSafeText) {
            when {
                character == '\n' || character == '\r' || character == '\t' -> append(character)
                character.code in 32..126 -> append(character)
                else -> append("\\u").append(character.code.toString(16).uppercase().padStart(4, '0'))
            }
        }
    }

internal fun String.shortSource(): String {
    val normalized = substringBefore('#')
    val suffix = substringAfter('#', missingDelimiterValue = "")
    val fileName = File(normalized).name.ifBlank { normalized }
    return if (suffix.isBlank()) fileName else "$fileName#$suffix"
}

internal data class ResourcePreviewViewportLayout(
    val viewportX: Float,
    val viewportY: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val imageX: Float,
    val imageY: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val effectiveZoom: Float,
)

internal data class AtlasRegionHitInfo(
    val resource: SkinResourceInfo,
    val pageName: String?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val area: Int get() = width * height
}

internal data class AtlasRegionScreenRect(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

internal fun computeFitZoom(
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
): Float =
    minOf(
        viewportWidth / imageWidth.coerceAtLeast(1).toFloat(),
        viewportHeight / imageHeight.coerceAtLeast(1).toFloat(),
    ).coerceAtLeast(MinInlineResourcePreviewScale)

internal fun computeResourcePreviewViewportLayout(
    viewportX: Float,
    viewportY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    previewState: SkinResourceVisualPreviewState,
): ResourcePreviewViewportLayout {
    val fitZoom = computeFitZoom(viewportWidth, viewportHeight, imageWidth, imageHeight)
    val effectiveZoom =
        when (previewState.zoomMode) {
            SkinResourceVisualPreviewZoomMode.Fit -> fitZoom
            else -> previewState.viewport.zoom.coerceAtLeast(MinInlineResourcePreviewScale)
        }
    val renderedWidth = imageWidth * effectiveZoom
    val renderedHeight = imageHeight * effectiveZoom
    val baseX = viewportX + (viewportWidth - renderedWidth) * 0.5f
    val baseY = viewportY + (viewportHeight - renderedHeight) * 0.5f
    return ResourcePreviewViewportLayout(
        viewportX = viewportX,
        viewportY = viewportY,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        imageX = baseX + previewState.viewport.panX,
        imageY = baseY + previewState.viewport.panY,
        imageWidth = renderedWidth,
        imageHeight = renderedHeight,
        effectiveZoom = effectiveZoom,
    )
}

internal fun screenToImageX(
    screenX: Float,
    layout: ResourcePreviewViewportLayout,
): Float = (screenX - layout.imageX) / layout.effectiveZoom

internal fun screenToImageYTopLeft(
    screenY: Float,
    layout: ResourcePreviewViewportLayout,
): Float = (screenY - layout.imageY) / layout.effectiveZoom

internal fun parseAtlasRegionHitInfo(resource: SkinResourceInfo): AtlasRegionHitInfo? {
    val xy = resource.details["xy"]?.parseIntPair() ?: return null
    val size = resource.details["size"]?.parseIntPair() ?: return null
    return AtlasRegionHitInfo(
        resource = resource,
        pageName = resource.details["page"]?.takeIf(String::isNotBlank),
        x = xy.first,
        y = xy.second,
        width = size.first,
        height = size.second,
    )
}

internal fun atlasRegionScreenRect(
    region: AtlasRegionHitInfo,
    layout: ResourcePreviewViewportLayout,
): AtlasRegionScreenRect {
    val minX = layout.imageX + region.x * layout.effectiveZoom
    val minY = layout.imageY + region.y * layout.effectiveZoom
    return AtlasRegionScreenRect(
        minX = minX,
        minY = minY,
        maxX = minX + region.width * layout.effectiveZoom,
        maxY = minY + region.height * layout.effectiveZoom,
    )
}

internal fun clipRectToViewport(
    rect: AtlasRegionScreenRect,
    layout: ResourcePreviewViewportLayout,
): AtlasRegionScreenRect? {
    val minX = maxOf(rect.minX, layout.viewportX)
    val minY = maxOf(rect.minY, layout.viewportY)
    val maxX = minOf(rect.maxX, layout.viewportX + layout.viewportWidth)
    val maxY = minOf(rect.maxY, layout.viewportY + layout.viewportHeight)
    if (maxX <= minX || maxY <= minY) return null
    return AtlasRegionScreenRect(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
}

internal fun packImColor(
    red: Int,
    green: Int,
    blue: Int,
    alpha: Int = 255,
): Int =
    (alpha.coerceIn(0, 255) shl 24) or
        (blue.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        red.coerceIn(0, 255)

internal fun String.parseIntPair(): Pair<Int, Int>? {
    val parts = split(',').map(String::trim)
    if (parts.size < 2) return null
    val first = parts[0].toIntOrNull() ?: return null
    val second = parts[1].toIntOrNull() ?: return null
    return first to second
}

private val AvailablePreviewCategories =
    setOf(
        SkinResourceCategory.Atlas,
        SkinResourceCategory.AtlasRegion,
        SkinResourceCategory.Texture,
        SkinResourceCategory.Font,
        SkinResourceCategory.Color,
    )
