package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewCanvasRect
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewScreenRect
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.common.texturepreview.hitTestTexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.textureRegionScreenRect

internal typealias ResourcePreviewViewportLayout = TexturePreviewViewportLayout
internal typealias AtlasRegionScreenRect = TexturePreviewScreenRect

internal data class AtlasRegionHitInfo(
    val resource: SkinResourceInfo,
    val pageName: String?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val area: Int get() = width * height
}

internal fun computeResourcePreviewViewportLayout(
    viewportX: Float,
    viewportY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    previewState: SkinResourceVisualPreviewState,
): ResourcePreviewViewportLayout =
    computeTexturePreviewViewportLayout(
        rect =
            TexturePreviewCanvasRect(
                x = viewportX,
                y = viewportY,
                width = viewportWidth,
                height = viewportHeight,
            ),
        textureWidth = imageWidth,
        textureHeight = imageHeight,
        previewState = previewState.toTexturePreviewState(),
    )

internal fun atlasRegionScreenRect(
    region: AtlasRegionHitInfo,
    layout: ResourcePreviewViewportLayout,
): AtlasRegionScreenRect = textureRegionScreenRect(region.toPreviewRegion(), layout)

internal fun hitTestAtlasRegion(
    regions: List<AtlasRegionHitInfo>,
    layout: ResourcePreviewViewportLayout,
    imageWidth: Int,
    imageHeight: Int,
    mouseX: Float,
    mouseY: Float,
): AtlasRegionHitInfo? {
    if (mouseX < layout.imageX || mouseX > layout.imageX + layout.imageWidth || mouseY < layout.imageY || mouseY > layout.imageY + layout.imageHeight) return null
    val hit = hitTestTexturePreviewRegion(regions.map(AtlasRegionHitInfo::toPreviewRegion), layout, mouseX, mouseY) ?: return null
    return regions.firstOrNull { region -> region.resource.key == hit.id }
        ?.takeIf { imageWidth > 0 && imageHeight > 0 }
}

@Suppress("ReturnCount")
internal fun parseAtlasRegionHitInfo(resource: SkinResourceInfo): AtlasRegionHitInfo? {
    val xy = resource.details["xy"]?.parseIntPair() ?: return null
    val size = resource.details["size"]?.parseIntPair() ?: return null
    return AtlasRegionHitInfo(
        resource = resource,
        pageName = resource.details["page"]?.takeIf(String::isNotBlank),
        x = xy.first,
        y = xy.second,
        width = size.first,
        height = size.second,
    )
}

internal fun AtlasRegionHitInfo.toPreviewRegion(): TexturePreviewRegion<SkinResourceKey> =
    TexturePreviewRegion(
        id = resource.key,
        label = resource.name,
        x = x,
        y = y,
        width = width,
        height = height,
    )

@Suppress("ReturnCount")
internal fun String.parseIntPair(): Pair<Int, Int>? {
    val parts = split(',').map(String::trim)
    if (parts.size < 2) return null
    val first = parts[0].toIntOrNull() ?: return null
    val second = parts[1].toIntOrNull() ?: return null
    return first to second
}
