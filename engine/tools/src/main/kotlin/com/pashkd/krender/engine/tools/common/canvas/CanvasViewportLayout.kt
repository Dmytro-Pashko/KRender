package com.pashkd.krender.engine.tools.common.canvas

data class CanvasViewportLayout(
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

private const val MIN_PREVIEW_SCALE = 0.05f
private const val MAX_PREVIEW_SCALE = 25f

fun computeCanvasViewportLayout(
    rect: CanvasRect,
    contentWidth: Int,
    contentHeight: Int,
    previewState: CanvasPreviewState,
): CanvasViewportLayout {
    val viewportWidth = contentWidth.coerceAtLeast(1)
    val viewportHeight = contentHeight.coerceAtLeast(1)
    val fitZoom =
        minOf(
            rect.width / viewportWidth.toFloat(),
            rect.height / viewportHeight.toFloat(),
        ).coerceAtLeast(MIN_PREVIEW_SCALE)
    val effectiveZoom =
        when (previewState.zoomMode) {
            CanvasZoomMode.Fit -> fitZoom
            CanvasZoomMode.Percent50 -> 0.5f
            CanvasZoomMode.Percent100 -> 1f
            CanvasZoomMode.Percent200 -> 2f
            CanvasZoomMode.Custom -> previewState.customZoom.coerceIn(MIN_PREVIEW_SCALE, MAX_PREVIEW_SCALE)
        }
    val imageWidth = viewportWidth * effectiveZoom
    val imageHeight = viewportHeight * effectiveZoom
    val imageX = rect.x + (rect.width - imageWidth) * 0.5f + previewState.viewport.panX
    val imageY = rect.y + (rect.height - imageHeight) * 0.5f + previewState.viewport.panY
    return CanvasViewportLayout(
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
