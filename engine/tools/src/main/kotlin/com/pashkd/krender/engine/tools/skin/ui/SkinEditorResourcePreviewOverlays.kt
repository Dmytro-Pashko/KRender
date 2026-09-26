package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegionSelection
import com.pashkd.krender.engine.tools.skin.AtlasRegionHitInfo
import com.pashkd.krender.engine.tools.skin.ResourcePreviewViewportLayout
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.toPreviewRegion

/** Thin Skin facade over the shared texture preview overlay renderer. */
internal object SkinEditorResourcePreviewOverlays {
    fun drawTexturePreviewBackground(
        selectedResource: SkinResourceInfo?,
        layout: ResourcePreviewViewportLayout,
        showCheckerboard: Boolean,
    ) {
        if (!showCheckerboard) return
        if (selectedResource?.category !in setOf(SkinResourceCategory.Atlas, SkinResourceCategory.AtlasRegion, SkinResourceCategory.Texture)) return
        TexturePreviewOverlays.drawCheckerboard(layout)
    }

    fun drawAtlasGrid(
        layout: ResourcePreviewViewportLayout,
        gridSize: Int,
        minScreenSpacing: Float,
    ) {
        if (gridSize.coerceAtLeast(1) * layout.effectiveZoom < minScreenSpacing) return
        TexturePreviewOverlays.drawGrid(layout, spacingPixels = gridSize)
    }

    fun drawAtlasRegionBounds(
        regions: List<AtlasRegionHitInfo>,
        layout: ResourcePreviewViewportLayout,
        hoveredRegion: AtlasRegionHitInfo? = null,
        selectedRegion: AtlasRegionHitInfo? = null,
    ) {
        TexturePreviewOverlays.drawRegionBounds(
            regions = regions.map(AtlasRegionHitInfo::toPreviewRegion),
            layout = layout,
            selection = TexturePreviewRegionSelection(selectedRegion?.resource?.key, hoveredRegion?.resource?.key),
        )
    }

    fun drawHoveredAtlasRegion(
        hoveredRegion: AtlasRegionHitInfo,
        layout: ResourcePreviewViewportLayout,
    ) {
        drawAtlasRegionBounds(listOf(hoveredRegion), layout, hoveredRegion = hoveredRegion)
    }
}
