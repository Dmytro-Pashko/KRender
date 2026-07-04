package com.pashkd.krender.engine.tools.common.texturepreview

data class TexturePreviewColor(
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
    val alpha: Float = 1f,
)

data class TexturePreviewState(
    var zoomMode: TexturePreviewZoomMode = TexturePreviewZoomMode.Fit,
    var surfaceMode: TexturePreviewSurfaceMode = TexturePreviewSurfaceMode.Actual,
    var customZoom: Float = 1f,
    var customCanvasWidth: Int = 512,
    var customCanvasHeight: Int = 512,
    var viewport: TexturePreviewViewportState = TexturePreviewViewportState(),
    var showCheckerboard: Boolean = true,
    var showGrid: Boolean = false,
    var gridSpacingPixels: Int = 32,
    var gridColor: TexturePreviewColor = TexturePreviewColor(red = 1f, green = 1f, blue = 1f, alpha = 0.19f),
    var showBounds: Boolean = true,
)
