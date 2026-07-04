package com.pashkd.krender.engine.tools.common.texturepreview

data class TexturePreviewViewportLayout(
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

data class TexturePreviewScreenRect(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

data class TexturePreviewFocusResult(
    val zoom: Float,
    val panX: Float,
    val panY: Float,
)
