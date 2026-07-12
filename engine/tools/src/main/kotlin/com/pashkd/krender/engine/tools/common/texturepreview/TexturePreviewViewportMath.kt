package com.pashkd.krender.engine.tools.common.texturepreview

private const val MIN_PREVIEW_SCALE = 0.05f
private const val MAX_PREVIEW_SCALE = 25f
private const val DEFAULT_SURFACE_PADDING_PIXELS = 100

fun computeTexturePreviewViewportLayout(
    rect: TexturePreviewCanvasRect,
    textureWidth: Int,
    textureHeight: Int,
    previewState: TexturePreviewState,
    contentPaddingPixels: Int = 0,
): TexturePreviewViewportLayout {
    val surfaceDimensions =
        computeTexturePreviewSurfaceDimensions(
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            surfaceMode = previewState.surfaceMode,
            customSurfaceWidth = previewState.customCanvasWidth,
            customSurfaceHeight = previewState.customCanvasHeight,
        )
    val viewportWidth = maxOf(surfaceDimensions.width, textureWidth + contentPaddingPixels * 2)
    val viewportHeight = maxOf(surfaceDimensions.height, textureHeight + contentPaddingPixels * 2)
    val fitZoom =
        minOf(
            rect.width / viewportWidth.coerceAtLeast(1).toFloat(),
            rect.height / viewportHeight.coerceAtLeast(1).toFloat(),
        ).coerceAtLeast(MIN_PREVIEW_SCALE)
    val effectiveZoom =
        when (previewState.zoomMode) {
            TexturePreviewZoomMode.Fit -> fitZoom
            TexturePreviewZoomMode.Percent50 -> 0.5f
            TexturePreviewZoomMode.Percent100 -> 1f
            TexturePreviewZoomMode.Percent200 -> 2f
            TexturePreviewZoomMode.Custom -> previewState.customZoom.coerceIn(MIN_PREVIEW_SCALE, MAX_PREVIEW_SCALE)
        }
    val imageWidth = textureWidth * effectiveZoom
    val imageHeight = textureHeight * effectiveZoom
    val viewportImageWidth = viewportWidth * effectiveZoom
    val viewportImageHeight = viewportHeight * effectiveZoom
    val imagePaddingX = ((viewportWidth - textureWidth) * 0.5f) * effectiveZoom
    val imagePaddingY = ((viewportHeight - textureHeight) * 0.5f) * effectiveZoom
    val surfaceX = rect.x + (rect.width - viewportImageWidth) * 0.5f + previewState.viewport.panX
    val surfaceY = rect.y + (rect.height - viewportImageHeight) * 0.5f + previewState.viewport.panY
    return TexturePreviewViewportLayout(
        viewportX = rect.x,
        viewportY = rect.y,
        viewportWidth = rect.width,
        viewportHeight = rect.height,
        surfaceX = surfaceX,
        surfaceY = surfaceY,
        surfaceWidth = viewportImageWidth,
        surfaceHeight = viewportImageHeight,
        imageX = surfaceX + imagePaddingX,
        imageY = surfaceY + imagePaddingY,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        effectiveZoom = effectiveZoom,
    )
}

@Suppress("LongParameterList")
fun computeTexturePreviewFocus(
    rect: TexturePreviewCanvasRect,
    textureWidth: Int,
    textureHeight: Int,
    surfaceMode: TexturePreviewSurfaceMode,
    customSurfaceWidth: Int,
    customSurfaceHeight: Int,
    region: TexturePreviewRegion<*>,
    contentPaddingPixels: Int = 0,
    focusPaddingFactor: Float = 0.9f,
): TexturePreviewFocusResult {
    val surfaceDimensions =
        computeTexturePreviewSurfaceDimensions(
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            surfaceMode = surfaceMode,
            customSurfaceWidth = customSurfaceWidth,
            customSurfaceHeight = customSurfaceHeight,
        )
    val viewportWidth = maxOf(surfaceDimensions.width, textureWidth + contentPaddingPixels * 2)
    val viewportHeight = maxOf(surfaceDimensions.height, textureHeight + contentPaddingPixels * 2)
    val zoom =
        minOf(
            rect.width / region.width.coerceAtLeast(1).toFloat(),
            rect.height / region.height.coerceAtLeast(1).toFloat(),
        ).times(focusPaddingFactor)
            .coerceIn(MIN_PREVIEW_SCALE, MAX_PREVIEW_SCALE)
    val imagePaddingX = ((viewportWidth - textureWidth) * 0.5f) * zoom
    val imagePaddingY = ((viewportHeight - textureHeight) * 0.5f) * zoom
    val baseSurfaceX = rect.x + (rect.width - viewportWidth * zoom) * 0.5f
    val baseSurfaceY = rect.y + (rect.height - viewportHeight * zoom) * 0.5f
    val regionCenterX = region.x + region.width * 0.5f
    val regionCenterY = region.y + region.height * 0.5f
    val desiredCenterX = rect.x + rect.width * 0.5f
    val desiredCenterY = rect.y + rect.height * 0.5f
    val panX = desiredCenterX - (baseSurfaceX + imagePaddingX + regionCenterX * zoom)
    val panY = desiredCenterY - (baseSurfaceY + imagePaddingY + regionCenterY * zoom)
    return TexturePreviewFocusResult(zoom = zoom, panX = panX, panY = panY)
}

fun textureRegionScreenRect(
    region: TexturePreviewRegion<*>,
    layout: TexturePreviewViewportLayout,
): TexturePreviewScreenRect {
    val minX = layout.imageX + region.x * layout.effectiveZoom
    val minY = layout.imageY + region.y * layout.effectiveZoom
    return TexturePreviewScreenRect(
        minX = minX,
        minY = minY,
        maxX = minX + region.width * layout.effectiveZoom,
        maxY = minY + region.height * layout.effectiveZoom,
    )
}

fun screenToTexturePixelX(
    screenX: Float,
    layout: TexturePreviewViewportLayout,
): Float = (screenX - layout.imageX) / layout.effectiveZoom

fun screenToTexturePixelY(
    screenY: Float,
    layout: TexturePreviewViewportLayout,
): Float = (screenY - layout.imageY) / layout.effectiveZoom

fun <T> hitTestTexturePreviewRegion(
    regions: List<TexturePreviewRegion<T>>,
    layout: TexturePreviewViewportLayout,
    mouseX: Float,
    mouseY: Float,
): TexturePreviewRegion<T>? =
    regions
        .filter { region ->
            val rect = textureRegionScreenRect(region, layout)
            mouseX >= rect.minX && mouseX <= rect.maxX && mouseY >= rect.minY && mouseY <= rect.maxY
        }.minByOrNull { region ->
            region.width * region.height
        }

private data class TexturePreviewSurfaceDimensions(
    val width: Int,
    val height: Int,
)

private fun computeTexturePreviewSurfaceDimensions(
    textureWidth: Int,
    textureHeight: Int,
    surfaceMode: TexturePreviewSurfaceMode,
    customSurfaceWidth: Int,
    customSurfaceHeight: Int,
): TexturePreviewSurfaceDimensions =
    TexturePreviewSurfaceDimensions(
        width =
            when (surfaceMode) {
                TexturePreviewSurfaceMode.Actual -> textureWidth
                TexturePreviewSurfaceMode.Padding -> textureWidth + DEFAULT_SURFACE_PADDING_PIXELS * 2
                TexturePreviewSurfaceMode.Custom -> customSurfaceWidth.coerceAtLeast(1)
            },
        height =
            when (surfaceMode) {
                TexturePreviewSurfaceMode.Actual -> textureHeight
                TexturePreviewSurfaceMode.Padding -> textureHeight + DEFAULT_SURFACE_PADDING_PIXELS * 2
                TexturePreviewSurfaceMode.Custom -> customSurfaceHeight.coerceAtLeast(1)
            },
    )
