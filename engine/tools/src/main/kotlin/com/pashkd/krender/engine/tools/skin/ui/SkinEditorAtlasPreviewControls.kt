package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewZoomMode
import com.pashkd.krender.engine.tools.skin.formatResourcePreviewZoom
import imgui.ImGui
import imgui.dsl

/** Draws atlas/texture resource preview controls without owning viewport math. */
internal object SkinEditorAtlasPreviewControls {
    fun draw(
        state: SkinEditorState,
        operations: SkinEditorOperations,
        selectedResource: SkinResourceInfo?,
    ) {
        val previewState = state.resourceVisualPreview
        if (selectedResource?.category == SkinResourceCategory.Font || selectedResource?.category == SkinResourceCategory.Color) {
            return
        }
        if (ImGui.beginCombo("Zoom##skin_editor_resource_preview_zoom", formatResourcePreviewZoom(previewState.zoomMode))) {
            SkinResourceVisualPreviewZoomMode.entries.forEach { zoomMode ->
                if (ImGui.selectable("${formatResourcePreviewZoom(zoomMode)}##skin_editor_resource_preview_zoom_$zoomMode", zoomMode == previewState.zoomMode)) {
                    operations.setResourcePreviewZoomMode(zoomMode)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        with(dsl) {
            button("Fit##skin_editor_resource_preview_zoom_fit") {
                operations.setResourcePreviewZoomMode(SkinResourceVisualPreviewZoomMode.Fit)
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("100%##skin_editor_resource_preview_zoom_100") {
                operations.setResourcePreviewZoomMode(SkinResourceVisualPreviewZoomMode.Percent100)
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset View##skin_editor_resource_preview_zoom_reset") {
                operations.resetResourcePreviewZoom()
            }
        }

        val showBounds = booleanArrayOf(previewState.showRegionBounds)
        if (ImGui.checkbox("Show region bounds##skin_editor_resource_preview_bounds", showBounds)) {
            operations.setShowResourceRegionBounds(showBounds[0])
        }
        if (selectedResource?.category == SkinResourceCategory.Atlas || selectedResource?.category == SkinResourceCategory.AtlasRegion) {
            val atlasVisuals = previewState.viewport.atlasVisuals
            val clickSelect = booleanArrayOf(previewState.viewport.clickSelectRegionEnabled)
            if (ImGui.checkbox("Click to select region##skin_editor_resource_preview_click_select", clickSelect)) {
                operations.setAtlasClickSelectionEnabled(clickSelect[0])
            }
            val checkerboard = booleanArrayOf(atlasVisuals.showCheckerboard)
            if (ImGui.checkbox("Checkerboard##skin_editor_resource_preview_checkerboard", checkerboard)) {
                operations.setAtlasCheckerboardEnabled(checkerboard[0])
            }
            val grid = booleanArrayOf(atlasVisuals.showGrid)
            if (ImGui.checkbox("Grid##skin_editor_resource_preview_grid", grid)) {
                operations.setAtlasGridEnabled(grid[0])
            }
            val gridLabel = "${atlasVisuals.gridSize}px"
            if (ImGui.beginCombo("Grid size##skin_editor_resource_preview_grid_size", gridLabel)) {
                listOf(8, 16, 32, 64, 128).forEach { gridSize ->
                    if (ImGui.selectable("${gridSize}px##skin_editor_resource_preview_grid_size_$gridSize", gridSize == atlasVisuals.gridSize)) {
                        operations.setAtlasGridSize(gridSize)
                    }
                }
                ImGui.endCombo()
            }
            val allBounds = booleanArrayOf(atlasVisuals.showAllRegionBounds)
            if (ImGui.checkbox("All region bounds##skin_editor_resource_preview_all_bounds", allBounds)) {
                operations.setAtlasAllRegionBoundsEnabled(allBounds[0])
            }
            val hoverHighlight = booleanArrayOf(atlasVisuals.showHoverHighlight)
            if (ImGui.checkbox("Hover highlight##skin_editor_resource_preview_hover", hoverHighlight)) {
                operations.setAtlasHoverHighlightEnabled(hoverHighlight[0])
            }
            ImGui.textWrapped("Ctrl + RMB drag: pan. Ctrl + mouse wheel: zoom. Click region: select.")
        } else {
            ImGui.textWrapped("Ctrl + RMB drag: pan. Ctrl + mouse wheel: zoom.")
        }
    }
}
