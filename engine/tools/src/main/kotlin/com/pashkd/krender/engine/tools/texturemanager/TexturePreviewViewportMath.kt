package com.pashkd.krender.engine.tools.texturemanager

internal data class TexturePreviewViewportLayout(
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

internal data class TextureRegionScreenRect(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

internal data class TextureAtlasRegionMetrics(
    val areaPixels: Int? = null,
    val u0: Float? = null,
    val v0: Float? = null,
    val u1: Float? = null,
    val v1: Float? = null,
    val outsidePageBounds: Boolean = false,
)

internal fun computeTexturePreviewViewportLayout(
    rect: TextureManagerCanvasRect,
    textureWidth: Int,
    textureHeight: Int,
    previewState: TextureManagerPreviewState,
): TexturePreviewViewportLayout {
    val fitZoom =
        minOf(
            rect.width / textureWidth.coerceAtLeast(1).toFloat(),
            rect.height / textureHeight.coerceAtLeast(1).toFloat(),
        ).coerceAtLeast(MinPreviewScale)
    val effectiveZoom =
        when (previewState.zoomMode) {
            TexturePreviewZoomMode.Fit -> fitZoom
            TexturePreviewZoomMode.Percent50 -> 0.5f
            TexturePreviewZoomMode.Percent100 -> 1f
            TexturePreviewZoomMode.Percent200 -> 2f
            TexturePreviewZoomMode.Custom -> previewState.customZoom.coerceAtLeast(MinPreviewScale)
        }
    val imageWidth = textureWidth * effectiveZoom
    val imageHeight = textureHeight * effectiveZoom
    val imageX = rect.x + (rect.width - imageWidth) * 0.5f + previewState.viewport.panX
    val imageY = rect.y + (rect.height - imageHeight) * 0.5f + previewState.viewport.panY
    return TexturePreviewViewportLayout(
        viewportX = rect.x,
        viewportY = rect.y,
        viewportWidth = rect.width,
        viewportHeight = rect.height,
        imageX = imageX,
        imageY = imageY,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        effectiveZoom = effectiveZoom,
    )
}

internal fun atlasRegionScreenRect(
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

internal fun screenToTexturePixelX(
    screenX: Float,
    layout: TexturePreviewViewportLayout,
): Float = (screenX - layout.imageX) / layout.effectiveZoom

internal fun screenToTexturePixelY(
    screenY: Float,
    layout: TexturePreviewViewportLayout,
): Float = (screenY - layout.imageY) / layout.effectiveZoom

internal fun computeRegionMetrics(
    region: TextureAtlasRegion,
    textureWidth: Int,
    textureHeight: Int,
): TextureAtlasRegionMetrics {
    val xy = region.xy
    val size = region.size
    val area = size?.let { dimensions -> dimensions.first * dimensions.second }
    if (xy == null || size == null || textureWidth <= 0 || textureHeight <= 0) {
        return TextureAtlasRegionMetrics(areaPixels = area)
    }
    val right = xy.first + size.first
    val bottom = xy.second + size.second
    return TextureAtlasRegionMetrics(
        areaPixels = area,
        u0 = xy.first / textureWidth.toFloat(),
        v0 = xy.second / textureHeight.toFloat(),
        u1 = right / textureWidth.toFloat(),
        v1 = bottom / textureHeight.toFloat(),
        outsidePageBounds = xy.first < 0 || xy.second < 0 || right > textureWidth || bottom > textureHeight,
    )
}

internal fun hitTestAtlasRegion(
    regions: List<TextureAtlasRegion>,
    layout: TexturePreviewViewportLayout,
    mouseX: Float,
    mouseY: Float,
): TextureAtlasRegion? {
    return regions
        .filter { region ->
            val rect = atlasRegionScreenRect(region, layout) ?: return@filter false
            mouseX >= rect.minX && mouseX <= rect.maxX && mouseY >= rect.minY && mouseY <= rect.maxY
        }.minByOrNull { region ->
            val size = region.size ?: (Int.MAX_VALUE to Int.MAX_VALUE)
            size.first * size.second
        }
}

internal fun formatRegionMetrics(metrics: TextureAtlasRegionMetrics): String {
    val area = metrics.areaPixels?.toString() ?: "<unknown>"
    val outside = if (metrics.outsidePageBounds) " outside bounds" else ""
    return "$area px$outside"
}

internal fun formatZoomMode(mode: TexturePreviewZoomMode): String =
    when (mode) {
        TexturePreviewZoomMode.Fit -> "Fit"
        TexturePreviewZoomMode.Percent50 -> "50%"
        TexturePreviewZoomMode.Percent100 -> "100%"
        TexturePreviewZoomMode.Percent200 -> "200%"
        TexturePreviewZoomMode.Custom -> "Custom"
    }

private const val MinPreviewScale = 0.05f
