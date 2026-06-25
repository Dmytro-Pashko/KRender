package com.pashkd.krender.engine.tools.textureatlaseditor

internal data class TexturePreviewViewportLayout(
    val viewportX: Float,
    val viewportY: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val surfaceX: Float,
    val surfaceY: Float,
    val surfaceWidth: Float,
    val surfaceHeight: Float,
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

internal const val PreviewSurfacePaddingPixels = 100

internal fun computeTexturePreviewViewportLayout(
    rect: TextureAtlasEditorCanvasRect,
    textureWidth: Int,
    textureHeight: Int,
    previewState: TextureAtlasEditorPreviewState,
    contentPaddingPixels: Int = 0,
): TexturePreviewViewportLayout {
    val surfaceWidth =
        when (previewState.surfaceMode) {
            TexturePreviewSurfaceMode.Actual -> textureWidth
            TexturePreviewSurfaceMode.Padding -> textureWidth + PreviewSurfacePaddingPixels * 2
            TexturePreviewSurfaceMode.Custom -> previewState.customCanvasWidth.coerceAtLeast(1)
        }
    val surfaceHeight =
        when (previewState.surfaceMode) {
            TexturePreviewSurfaceMode.Actual -> textureHeight
            TexturePreviewSurfaceMode.Padding -> textureHeight + PreviewSurfacePaddingPixels * 2
            TexturePreviewSurfaceMode.Custom -> previewState.customCanvasHeight.coerceAtLeast(1)
        }
    val viewportWidth = maxOf(surfaceWidth, textureWidth + contentPaddingPixels * 2)
    val viewportHeight = maxOf(surfaceHeight, textureHeight + contentPaddingPixels * 2)
    val fitZoom =
        minOf(
            rect.width / viewportWidth.coerceAtLeast(1).toFloat(),
            rect.height / viewportHeight.coerceAtLeast(1).toFloat(),
        ).coerceAtLeast(MinPreviewScale)
    val surfaceBaseZoom = 1f
    val effectiveZoom =
        when (previewState.zoomMode) {
            TexturePreviewZoomMode.Fit -> fitZoom
            TexturePreviewZoomMode.Percent50 -> surfaceBaseZoom * 0.5f
            TexturePreviewZoomMode.Percent100 -> surfaceBaseZoom
            TexturePreviewZoomMode.Percent200 -> surfaceBaseZoom * 2f
            TexturePreviewZoomMode.Custom -> surfaceBaseZoom * previewState.customZoom.coerceIn(MinPreviewScale, 25f)
        }
    val imageWidth = textureWidth * effectiveZoom
    val imageHeight = textureHeight * effectiveZoom
    val viewportImageWidth = viewportWidth * effectiveZoom
    val viewportImageHeight = viewportHeight * effectiveZoom
    val imagePaddingX = ((viewportWidth - textureWidth) * 0.5f) * effectiveZoom
    val imagePaddingY = ((viewportHeight - textureHeight) * 0.5f) * effectiveZoom
    val imageX = rect.x + (rect.width - viewportImageWidth) * 0.5f + previewState.viewport.panX + imagePaddingX
    val imageY = rect.y + (rect.height - viewportImageHeight) * 0.5f + previewState.viewport.panY + imagePaddingY
    return TexturePreviewViewportLayout(
        viewportX = rect.x,
        viewportY = rect.y,
        viewportWidth = rect.width,
        viewportHeight = rect.height,
        surfaceX = rect.x + (rect.width - viewportImageWidth) * 0.5f + previewState.viewport.panX,
        surfaceY = rect.y + (rect.height - viewportImageHeight) * 0.5f + previewState.viewport.panY,
        surfaceWidth = viewportImageWidth,
        surfaceHeight = viewportImageHeight,
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

internal fun formatZoomMode(mode: TexturePreviewZoomMode): String =
    when (mode) {
        TexturePreviewZoomMode.Fit -> "Fit"
        TexturePreviewZoomMode.Percent50 -> "50%"
        TexturePreviewZoomMode.Percent100 -> "100%"
        TexturePreviewZoomMode.Percent200 -> "200%"
        TexturePreviewZoomMode.Custom -> "Custom"
    }

private const val MinPreviewScale = 0.05f
