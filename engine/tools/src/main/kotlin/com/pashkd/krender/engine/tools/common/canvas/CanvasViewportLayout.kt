package com.pashkd.krender.engine.tools.common.canvas

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout

typealias CanvasViewportLayout = TexturePreviewViewportLayout

fun computeCanvasViewportLayout(
    rect: CanvasRect,
    contentWidth: Int,
    contentHeight: Int,
    previewState: CanvasPreviewState,
): CanvasViewportLayout =
    computeTexturePreviewViewportLayout(
        rect = rect,
        textureWidth = contentWidth,
        textureHeight = contentHeight,
        previewState = previewState,
    )
