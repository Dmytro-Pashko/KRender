package com.pashkd.krender.engine.tools.common.texturepreview

import kotlin.math.roundToInt

data class TexturePreviewCursorInfo(
    val textureX: Int? = null,
    val textureY: Int? = null,
) {
    val isInside: Boolean get() = textureX != null && textureY != null
}

fun computeTexturePreviewCursorInfo(
    screenX: Float,
    screenY: Float,
    layout: TexturePreviewViewportLayout,
    textureWidth: Int,
    textureHeight: Int,
): TexturePreviewCursorInfo {
    val textureX = screenToTexturePixelX(screenX, layout).roundToInt()
    val textureY = screenToTexturePixelY(screenY, layout).roundToInt()
    if (textureX < 0 || textureY < 0 || textureX >= textureWidth || textureY >= textureHeight) {
        return TexturePreviewCursorInfo()
    }
    return TexturePreviewCursorInfo(textureX = textureX, textureY = textureY)
}
