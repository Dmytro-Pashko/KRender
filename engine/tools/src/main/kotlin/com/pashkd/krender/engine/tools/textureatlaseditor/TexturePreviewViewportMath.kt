package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewFocusResult
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewScreenRect
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewFocus
import com.pashkd.krender.engine.tools.common.texturepreview.hitTestTexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.textureRegionScreenRect
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout as computeSharedTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.formatZoomMode as formatSharedZoomMode
import com.pashkd.krender.engine.tools.common.texturepreview.screenToTexturePixelX as screenToTexturePreviewPixelX
import com.pashkd.krender.engine.tools.common.texturepreview.screenToTexturePixelY as screenToTexturePreviewPixelY

internal typealias TexturePreviewViewportLayout = com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewViewportLayout
internal typealias TextureRegionScreenRect = TexturePreviewScreenRect

internal data class TextureAtlasRegionMetrics(
    val areaPixels: Int? = null,
    val u0: Float? = null,
    val v0: Float? = null,
    val u1: Float? = null,
    val v1: Float? = null,
    val outsidePageBounds: Boolean = false,
)

internal fun computeTexturePreviewViewportLayout(
    rect: TextureAtlasEditorCanvasRect,
    textureWidth: Int,
    textureHeight: Int,
    previewState: TextureAtlasEditorPreviewState,
    contentPaddingPixels: Int = 0,
): TexturePreviewViewportLayout =
    computeSharedTexturePreviewViewportLayout(
        rect = rect,
        textureWidth = textureWidth,
        textureHeight = textureHeight,
        previewState =
            com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewState(
                zoomMode = previewState.zoomMode,
                surfaceMode = previewState.surfaceMode,
                customZoom = previewState.customZoom,
                customCanvasWidth = previewState.customCanvasWidth,
                customCanvasHeight = previewState.customCanvasHeight,
                viewport = previewState.viewport,
                showCheckerboard = previewState.showCheckerboard,
                showGrid = previewState.showGrid,
                gridSpacingPixels = previewState.gridSpacingPixels,
                gridColor = previewState.gridColor,
                showBounds = previewState.showBounds,
            ),
        contentPaddingPixels = contentPaddingPixels,
    )

internal fun atlasRegionScreenRect(
    region: TextureAtlasRegion,
    layout: TexturePreviewViewportLayout,
): TextureRegionScreenRect? {
    val previewRegion = region.asTexturePreviewRegion() ?: return null
    return textureRegionScreenRect(previewRegion, layout)
}

internal fun screenToTexturePixelX(
    screenX: Float,
    layout: TexturePreviewViewportLayout,
): Float = screenToTexturePreviewPixelX(screenX, layout)

internal fun screenToTexturePixelY(
    screenY: Float,
    layout: TexturePreviewViewportLayout,
): Float = screenToTexturePreviewPixelY(screenY, layout)

internal fun computeRegionMetrics(
    region: TextureAtlasRegion,
    textureWidth: Int,
    textureHeight: Int,
): TextureAtlasRegionMetrics {
    val xy = region.xy
    val size = region.size
    val area = size?.let { dimensions -> dimensions.first * dimensions.second }
    if (xy == null || size == null || textureWidth <= 0 || textureHeight <= 0) {
        return TextureAtlasRegionMetrics(areaPixels = area)
    }
    val right = xy.first + size.first
    val bottom = xy.second + size.second
    return TextureAtlasRegionMetrics(
        areaPixels = area,
        u0 = xy.first / textureWidth.toFloat(),
        v0 = xy.second / textureHeight.toFloat(),
        u1 = right / textureWidth.toFloat(),
        v1 = bottom / textureHeight.toFloat(),
        outsidePageBounds = xy.first < 0 || xy.second < 0 || right > textureWidth || bottom > textureHeight,
    )
}

internal fun hitTestAtlasRegion(
    regions: List<TextureAtlasRegion>,
    layout: TexturePreviewViewportLayout,
    mouseX: Float,
    mouseY: Float,
): TextureAtlasRegion? {
    val mappedRegions = regions.mapNotNull { region -> region.asTexturePreviewRegion()?.let { preview -> region to preview } }
    val hit =
        hitTestTexturePreviewRegion(
            regions = mappedRegions.map { (_, preview) -> preview },
            layout = layout,
            mouseX = mouseX,
            mouseY = mouseY,
        )
    return mappedRegions.firstOrNull { (_, preview) -> preview.id == hit?.id }?.first
}

internal fun formatZoomMode(mode: TexturePreviewZoomMode): String = formatSharedZoomMode(mode)

internal fun computeRegionFocus(
    rect: TextureAtlasEditorCanvasRect,
    textureWidth: Int,
    textureHeight: Int,
    previewState: TextureAtlasEditorPreviewState,
    region: TextureAtlasRegion,
    contentPaddingPixels: Int = 0,
): TexturePreviewFocusResult? {
    val previewRegion = region.asTexturePreviewRegion() ?: return null
    return computeTexturePreviewFocus(
        rect = rect,
        textureWidth = textureWidth,
        textureHeight = textureHeight,
        surfaceMode = previewState.surfaceMode,
        customSurfaceWidth = previewState.customCanvasWidth,
        customSurfaceHeight = previewState.customCanvasHeight,
        region = previewRegion,
        contentPaddingPixels = contentPaddingPixels,
    )
}

private fun TextureAtlasRegion.asTexturePreviewRegion(): TexturePreviewRegion<AtlasRegionId>? {
    val regionXy = xy
    val regionSize = size
    return if (regionXy != null && regionSize != null) {
        TexturePreviewRegion(
            id = id,
            label = id.regionName,
            x = regionXy.first,
            y = regionXy.second,
            width = regionSize.first,
            height = regionSize.second,
        )
    } else {
        null
    }
}
