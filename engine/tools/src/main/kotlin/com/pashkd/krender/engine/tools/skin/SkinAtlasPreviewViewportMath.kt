package com.pashkd.krender.engine.tools.skin

/**
 * Screen-space layout of one resource preview viewport.
 *
 * Atlas preview UI overlays and hit-testing use this computed mapping from
 * image-space pixels into the current ImGui child viewport.
 */
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

/**
 * Parsed atlas region bounds using atlas metadata.
 *
 * Atlas `xy` is treated as top-left image-space coordinates, matching the
 * indexed metadata currently produced by the Skin Editor loader.
 */
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

/** Screen-space rectangle derived from atlas image-space coordinates. */
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

/**
 * Converts screen Y into atlas image-space Y using a top-left image origin.
 *
 * This convention must stay aligned with [parseAtlasRegionHitInfo] and every
 * overlay/hit-test helper that works with atlas `xy` metadata.
 */
internal fun screenToImageYTopLeft(
    screenY: Float,
    layout: ResourcePreviewViewportLayout,
): Float = (screenY - layout.imageY) / layout.effectiveZoom

@Suppress("ReturnCount")
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

internal fun hitTestAtlasRegion(
    regions: List<AtlasRegionHitInfo>,
    layout: ResourcePreviewViewportLayout,
    imageWidth: Int,
    imageHeight: Int,
    mouseX: Float,
    mouseY: Float,
): AtlasRegionHitInfo? {
    val imageX = screenToImageX(mouseX, layout)
    val imageY = screenToImageYTopLeft(mouseY, layout)
    @Suppress("ComplexCondition")
    if (imageX < 0f || imageY < 0f || imageX > imageWidth || imageY > imageHeight) return null
    return regions
        .filter { region ->
            imageX >= region.x &&
                imageX <= region.x + region.width &&
                imageY >= region.y &&
                imageY <= region.y + region.height
        }.minWithOrNull(compareBy<AtlasRegionHitInfo>({ it.area }, { it.resource.name }))
}

@Suppress("ReturnCount")
internal fun String.parseIntPair(): Pair<Int, Int>? {
    val parts = split(',').map(String::trim)
    if (parts.size < 2) return null
    val first = parts[0].toIntOrNull() ?: return null
    val second = parts[1].toIntOrNull() ?: return null
    return first to second
}
