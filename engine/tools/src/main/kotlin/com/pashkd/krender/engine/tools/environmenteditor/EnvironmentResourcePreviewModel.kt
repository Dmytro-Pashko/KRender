package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion

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
