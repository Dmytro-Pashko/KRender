package com.pashkd.krender.engine.tools.common.texturepreview

fun formatZoomMode(mode: TexturePreviewZoomMode): String =
    when (mode) {
        TexturePreviewZoomMode.Fit -> "Fit"
        TexturePreviewZoomMode.Percent50 -> "50%"
        TexturePreviewZoomMode.Percent100 -> "100%"
        TexturePreviewZoomMode.Percent200 -> "200%"
        TexturePreviewZoomMode.Custom -> "Custom"
    }

fun formatSurfaceMode(mode: TexturePreviewSurfaceMode): String =
    when (mode) {
        TexturePreviewSurfaceMode.Actual -> "Actual"
        TexturePreviewSurfaceMode.Padding -> "Padding"
        TexturePreviewSurfaceMode.Custom -> "Custom"
    }
