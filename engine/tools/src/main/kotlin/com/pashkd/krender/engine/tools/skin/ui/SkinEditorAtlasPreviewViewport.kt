package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.AtlasRegionHitInfo
import com.pashkd.krender.engine.tools.skin.MinResourcePreviewGridScreenSpacing
import com.pashkd.krender.engine.tools.skin.ResourcePreviewClickDragThreshold
import com.pashkd.krender.engine.tools.skin.ResourcePreviewViewportHeight
import com.pashkd.krender.engine.tools.skin.ResourcePreviewViewportLayout
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewInfo
import com.pashkd.krender.engine.tools.skin.computeResourcePreviewViewportLayout
import com.pashkd.krender.engine.tools.skin.hitTestAtlasRegion
import com.pashkd.krender.engine.tools.skin.parseAtlasRegionHitInfo
import imgui.ImGui
import kotlin.math.hypot
import glm_.vec2.Vec2 as ImVec2

/** Owns atlas viewport interaction, hit-testing, and overlay composition. */
internal class SkinEditorAtlasPreviewViewport(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
) {
    private var previewClickPending = false
    private var previewClickDragDistance = 0f
    var hoveredAtlasRegion: AtlasRegionHitInfo? = null
        private set

    fun draw(
        selectedResource: SkinResourceInfo?,
        info: SkinResourceVisualPreviewInfo,
    ) {
        val handle = info.texturePreviewHandle
        if (handle == null) {
            ImGui.textWrapped("Image preview unavailable.")
            clearHover()
            return
        }
        val viewportWidth = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
        val viewportHeight = minOf(ResourcePreviewViewportHeight, ImGui.contentRegionAvail.y.coerceAtLeast(160f))
        ImGui.beginChild("skin_editor_resource_preview_viewport", ImVec2(viewportWidth, viewportHeight), true)
        val viewportMin = ImGui.cursorScreenPos
        val viewportContent = ImGui.contentRegionAvail
        operations.syncResourcePreviewViewportContent(buildPreviewContentKey(info))
        val viewportLayout =
            computeResourcePreviewViewportLayout(
                viewportX = viewportMin.x,
                viewportY = viewportMin.y,
                viewportWidth = viewportContent.x.coerceAtLeast(1f),
                viewportHeight = viewportContent.y.coerceAtLeast(1f),
                imageWidth = handle.width,
                imageHeight = handle.height,
                previewState = state.resourceVisualPreview,
            )
        val atlasVisuals = state.resourceVisualPreview.viewport.atlasVisuals
        SkinEditorResourcePreviewOverlays.drawTexturePreviewBackground(selectedResource, viewportLayout, atlasVisuals.showCheckerboard)
        ImGui.windowDrawList.addImage(
            handle.id,
            ImVec2(viewportLayout.imageX, viewportLayout.imageY),
            ImVec2(viewportLayout.imageX + viewportLayout.imageWidth, viewportLayout.imageY + viewportLayout.imageHeight),
            ImVec2(handle.u0, handle.v0),
            ImVec2(handle.u1, handle.v1),
        )
        val activeRegions = activeAtlasRegions(selectedResource)
        val hoveredRegion = hoveredAtlasRegion(selectedResource, info, viewportLayout, activeRegions)
        if (selectedResource?.category == SkinResourceCategory.Atlas || selectedResource?.category == SkinResourceCategory.AtlasRegion) {
            if (atlasVisuals.showGrid) {
                SkinEditorResourcePreviewOverlays.drawAtlasGrid(viewportLayout, atlasVisuals.gridSize, MinResourcePreviewGridScreenSpacing)
            }
            if (atlasVisuals.showAllRegionBounds) {
                SkinEditorResourcePreviewOverlays.drawAtlasRegionBounds(activeRegions, viewportLayout)
            }
            if (atlasVisuals.showHoverHighlight) {
                hoveredRegion?.let { SkinEditorResourcePreviewOverlays.drawHoveredAtlasRegion(it, viewportLayout) }
            }
        }
        handleTexturePreviewInteraction(selectedResource, viewportLayout, hoveredRegion)
        ImGui.endChild()
    }

    fun clearHover() {
        hoveredAtlasRegion = null
    }

    fun activeAtlasPage(selectedResource: SkinResourceInfo): String? =
        when (selectedResource.category) {
            SkinResourceCategory.AtlasRegion -> selectedResource.details["page"]?.takeIf(String::isNotBlank)
            SkinResourceCategory.Atlas -> state.resourceVisualPreviewInfo.atlasPageName?.takeIf(String::isNotBlank)
            else -> null
        }

    @Suppress("UnusedParameter", "CyclomaticComplexMethod")
    private fun handleTexturePreviewInteraction(
        selectedResource: SkinResourceInfo?,
        viewportLayout: ResourcePreviewViewportLayout,
        hoveredRegion: AtlasRegionHitInfo?,
    ) {
        val io = ImGui.io
        val hovered = ImGui.isWindowHovered()
        hoveredAtlasRegion = hoveredRegion?.takeIf { hovered }

        if (hovered && io.mouseClicked[0]) {
            previewClickPending = true
            previewClickDragDistance = 0f
        }
        if (previewClickPending && io.mouseDown[0]) {
            previewClickDragDistance += hypot(io.mouseDelta.x, io.mouseDelta.y)
        }
        @Suppress("ComplexCondition")
        if (hovered && io.keyCtrl && io.mouseDown[1] && (io.mouseDelta.x != 0f || io.mouseDelta.y != 0f)) {
            operations.panResourcePreviewViewport(io.mouseDelta.x, io.mouseDelta.y)
            previewClickPending = false
        }
        if (hovered && io.keyCtrl && io.mouseWheel != 0f) {
            val nextZoom = viewportLayout.effectiveZoom * (1f + io.mouseWheel * 0.1f)
            operations.setResourcePreviewViewportZoom(nextZoom)
            previewClickPending = false
        }
        if (previewClickPending && io.mouseReleased[0]) {
            val clickAllowed =
                hovered &&
                    !io.keyCtrl &&
                    previewClickDragDistance <= ResourcePreviewClickDragThreshold &&
                    state.resourceVisualPreview.viewport.clickSelectRegionEnabled
            if (clickAllowed) {
                hoveredRegion?.let { hit ->
                    operations.selectResource(hit.resource)
                    operations.selectAtlasRegionByName(hit.resource.name, hit.resource.source, hit.pageName)
                    state.statusMessage = "Selected atlas region '${hit.resource.name}' from preview."
                }
            }
            previewClickPending = false
            previewClickDragDistance = 0f
        }
    }

    @Suppress("ReturnCount")
    private fun hoveredAtlasRegion(
        selectedResource: SkinResourceInfo?,
        info: SkinResourceVisualPreviewInfo,
        viewportLayout: ResourcePreviewViewportLayout,
        activeRegions: List<AtlasRegionHitInfo>,
    ): AtlasRegionHitInfo? {
        if (!ImGui.isWindowHovered()) return null
        if (selectedResource?.category != SkinResourceCategory.Atlas && selectedResource?.category != SkinResourceCategory.AtlasRegion) return null
        return hitTestAtlasRegion(activeRegions, viewportLayout, info.textureWidth, info.textureHeight, ImGui.io.mousePos.x, ImGui.io.mousePos.y)
    }

    @Suppress("ReturnCount")
    private fun activeAtlasRegions(selectedResource: SkinResourceInfo?): List<AtlasRegionHitInfo> {
        selectedResource ?: return emptyList()
        val atlasSource = selectedResource.source ?: return emptyList()
        val pageName = activeAtlasPage(selectedResource)
        return state.loadResult.resourceIndex.atlasRegions
            .filter { region -> region.source == atlasSource }
            .filter { region -> pageName == null || region.details["page"] == pageName }
            .mapNotNull(::parseAtlasRegionHitInfo)
    }

    private fun buildPreviewContentKey(info: SkinResourceVisualPreviewInfo): String? = info.resolvedTexturePath?.let { texturePath -> "${info.kind}:$texturePath:${info.atlasPageName.orEmpty()}" }
}
