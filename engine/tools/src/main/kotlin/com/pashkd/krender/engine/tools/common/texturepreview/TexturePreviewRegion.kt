package com.pashkd.krender.engine.tools.common.texturepreview

data class TexturePreviewRegion<T>(
    val id: T,
    val label: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
