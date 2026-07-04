package com.pashkd.krender.engine.tools.common.canvas

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.packColor

object CanvasOverlays {
    fun drawCheckerboard(
        layout: CanvasViewportLayout,
        checkerSize: Int = 8,
    ) {
        if (checkerSize < 1) return
        TexturePreviewOverlays.drawCheckerboard(layout)
    }

    fun drawGrid(
        layout: CanvasViewportLayout,
        spacingPixels: Int = 32,
        color: Int = packColor(255, 255, 255, 48),
    ) {
        TexturePreviewOverlays.drawGrid(layout, spacingPixels = spacingPixels, color = color)
    }
}

fun packColor(
    r: Int,
    g: Int,
    b: Int,
    a: Int,
): Int = com.pashkd.krender.engine.tools.common.texturepreview.packColor(r, g, b, a)
