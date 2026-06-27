package com.pashkd.krender.engine.tools.common.canvas

enum class CanvasZoomMode {
    Fit,
    Percent50,
    Percent100,
    Percent200,
    Custom,
}

data class CanvasViewportState(
    var panX: Float = 0f,
    var panY: Float = 0f,
    var zoom: Float = 1f,
)

data class CanvasRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isValid: Boolean get() = width > 0f && height > 0f
}

data class CanvasPreviewState(
    var zoomMode: CanvasZoomMode = CanvasZoomMode.Fit,
    var customZoom: Float = 1f,
    var viewport: CanvasViewportState = CanvasViewportState(),
    var showCheckerboard: Boolean = true,
    var showGrid: Boolean = false,
    var gridSpacingPixels: Int = 32,
)
