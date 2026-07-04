package com.pashkd.krender.engine.tools.common.texturepreview

data class TexturePreviewRegionSelection<T>(
    val selectedId: T? = null,
    val hoveredId: T? = null,
)
