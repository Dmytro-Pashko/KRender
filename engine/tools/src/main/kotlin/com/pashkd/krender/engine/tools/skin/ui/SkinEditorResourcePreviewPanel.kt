package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewKind
import com.pashkd.krender.engine.tools.skin.parseResourceColor
import com.pashkd.krender.engine.ui.UiService
import imgui.ImGui
import imgui.api.colorButton
import java.io.File

/** Delegates the selected resource preview to focused atlas/font/color helpers. */
internal class SkinEditorResourcePreviewPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val ui: UiService,
) {
    private val atlasViewport = SkinEditorAtlasPreviewViewport(state, operations)
    private val fontPreviewControls = SkinEditorFontPreviewControls(state, operations)

    fun draw(selectedResource: SkinResourceInfo?) {
        ImGui.separator()
        ImGui.textUnformatted("Resource Preview")
        SkinEditorAtlasPreviewControls.draw(state, operations, selectedResource)
        fontPreviewControls.drawIfNeeded(selectedResource)
        drawSelectedResourceSummary(selectedResource)
        drawInlinePreview(selectedResource)
        drawAtlasRegionPreviewDetails(selectedResource)
    }

    private fun drawSelectedResourceSummary(selectedResource: SkinResourceInfo?) {
        ImGui.separator()
        ImGui.textWrapped(state.resourceVisualPreviewInfo.statusMessage)
        state.resourceVisualPreviewInfo.resolvedTexturePath?.let { path ->
            ImGui.textWrapped("Texture: ${File(path).name}")
            ImGui.textUnformatted("Size: ${state.resourceVisualPreviewInfo.textureWidth} x ${state.resourceVisualPreviewInfo.textureHeight}")
        }
        state.resourceVisualPreviewInfo.resolvedFontPath?.let { path ->
            ImGui.textWrapped("Font file: ${File(path).name}")
        }
        state.resourceVisualPreviewInfo.fontPreviewSource?.let { source ->
            ImGui.textWrapped("Font source: $source")
        }
        state.resourceVisualPreviewInfo.colorValue?.let { value ->
            ImGui.textWrapped("Color: $value")
        }
        state.resourceVisualPreviewInfo.atlasPageName?.let { page ->
            ImGui.textWrapped("Atlas page: $page")
        }
        state.resourceVisualPreviewInfo.selectedRegionName?.let { regionName ->
            ImGui.textWrapped("Region overlay: $regionName")
        }
        val hoverRegionLabel =
            atlasViewport.hoveredAtlasRegion?.let { hovered ->
                "${hovered.resource.name}  xy=${hovered.x},${hovered.y}  size=${hovered.width} x ${hovered.height}"
            } ?: "<none>"
        ImGui.textWrapped("Hover region: $hoverRegionLabel")
        selectedResource?.let { resource ->
            if (resource.category == SkinResourceCategory.Atlas || resource.category == SkinResourceCategory.AtlasRegion) {
                drawAtlasRegionSelector(resource)
            }
            ImGui.textUnformatted("Selected resource: ${resource.category}.${resource.name}")
        } ?: ImGui.textUnformatted("Selected resource: <none>")
        ImGui.separator()
    }

    private fun drawInlinePreview(selectedResource: SkinResourceInfo?) {
        val info = state.resourceVisualPreviewInfo
        when (info.kind) {
            SkinResourceVisualPreviewKind.Texture -> atlasViewport.draw(selectedResource, info)
            SkinResourceVisualPreviewKind.Color -> drawColorPreview(selectedResource)
            SkinResourceVisualPreviewKind.Font -> drawFontPreview(info)
            SkinResourceVisualPreviewKind.None -> atlasViewport.clearHover()
        }
    }

    private fun drawColorPreview(selectedResource: SkinResourceInfo?) {
        atlasViewport.clearHover()
        val values = selectedResource?.let { resource -> state.editSession.resources[resource.key]?.values ?: resource.details } ?: return
        parseResourceColor(values)?.let { color ->
            colorButton("Color preview##skin_editor_resource_color_preview", color[0], color[1], color[2], color[3])
        }
    }

    private fun drawFontPreview(info: com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewInfo) {
        atlasViewport.clearHover()
        val handle = info.texturePreviewHandle
        if (handle == null) {
            ImGui.textWrapped("Font preview image unavailable.")
            return
        }
        val availableWidth = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
        val scale = minOf(1f, availableWidth / handle.width.coerceAtLeast(1).toFloat())
        if (!ui.drawTexturePreview(handle, handle.width * scale, handle.height * scale)) {
            ImGui.textWrapped("Font preview unavailable: UI backend rejected the texture handle.")
        }
    }

    private fun drawAtlasRegionSelector(resource: SkinResourceInfo) {
        val atlasSource = resource.source ?: return
        val pageName = atlasViewport.activeAtlasPage(resource)
        val regions =
            state.loadResult.resourceIndex.atlasRegions
                .filter { region -> region.source == atlasSource }
                .filter { region -> pageName == null || region.details["page"] == pageName }
                .map(SkinResourceInfo::name)
                .distinct()
                .sorted()
        if (regions.isEmpty()) return

        val selectedName = state.resourceVisualPreview.selectedAtlasRegionName?.takeIf { name -> name in regions }
        val comboLabel = selectedName ?: "None"
        if (ImGui.beginCombo("Region overlay##skin_editor_resource_preview_region", comboLabel)) {
            if (ImGui.selectable("None##skin_editor_resource_preview_region_none", selectedName == null)) {
                operations.selectAtlasRegionByName(null)
            }
            regions.forEach { regionName ->
                if (ImGui.selectable("$regionName##skin_editor_resource_preview_region_$regionName", regionName == selectedName)) {
                    operations.selectAtlasRegionByName(regionName, atlasSource, pageName)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawAtlasRegionPreviewDetails(selectedResource: SkinResourceInfo?) {
        val atlasRegionResource =
            when (selectedResource?.category) {
                SkinResourceCategory.AtlasRegion -> selectedResource
                SkinResourceCategory.Atlas -> {
                    val selectedRegionName = state.resourceVisualPreview.selectedAtlasRegionName
                    state.loadResult.resourceIndex.atlasRegions.firstOrNull { region ->
                        region.name == selectedRegionName &&
                            region.source == selectedResource.source &&
                            region.details["page"] == atlasViewport.activeAtlasPage(selectedResource)
                    }
                }
                else -> null
            } ?: return

        ImGui.separator()
        ImGui.textUnformatted("Atlas Region")
        ImGui.textWrapped("Name: ${atlasRegionResource.name}")
        listOf("atlas", "page", "xy", "size", "orig", "offset", "index").forEach { field ->
            atlasRegionResource.details[field]?.let { value ->
                ImGui.textWrapped("$field: $value")
            }
        }
    }
}
