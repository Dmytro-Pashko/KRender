package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion

data class EnvironmentResourceSourceRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
)

data class EnvironmentResourceCanvasItem(
    val id: String,
    val label: String,
    val resourceMode: EnvironmentResourceMode,
    val region: TexturePreviewRegion<String>,
    val manifestPath: String? = null,
    val resolvedPath: String? = null,
    val previewHandle: TexturePreviewHandle? = null,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null,
    val exists: Boolean = false,
    val sourceRegion: EnvironmentResourceSourceRegion? = null,
    val mipLevel: Int? = null,
    val roughness: Float? = null,
    val warnings: List<String> = emptyList(),
)

data class EnvironmentResourcePreviewModel(
    val mode: EnvironmentResourceMode,
    val contentWidth: Int,
    val contentHeight: Int,
    val items: List<EnvironmentResourceCanvasItem>,
    val diagnostics: List<String> = emptyList(),
    val selectedItem: EnvironmentResourceCanvasItem? = null,
    val hoveredItem: EnvironmentResourceCanvasItem? = null,
    val statusMessage: String = "",
)
