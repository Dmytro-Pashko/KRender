package com.pashkd.krender.engine.tools.common.texturepreview

data class TexturePreviewCanvasRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isValid: Boolean get() = width > 0f && height > 0f
}
