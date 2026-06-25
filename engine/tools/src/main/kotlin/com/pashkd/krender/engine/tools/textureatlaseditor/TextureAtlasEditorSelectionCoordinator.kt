package com.pashkd.krender.engine.tools.textureatlaseditor

/**
 * Keeps the editor's parallel selection models in agreement.
 *
 * Atlas region, draft resource, and packed preview selection are rendered in
 * separate panels, but users expect them to track the same logical item. This
 * coordinator centralizes that glue so feature operations can stay focused on
 * their own state changes.
 */
internal class TextureAtlasEditorSelectionCoordinator(
    private val state: TextureAtlasEditorState,
) {
    fun syncSelectedResourceFromRegion(regionId: AtlasRegionId?) {
        if (regionId == null) {
            if (state.selectedResource()?.atlasRegionIdOrNull() != null) {
                state.resources.selectedResourceId = null
            }
            return
        }
        state.resources.selectedResourceId =
            state.resources.items
                .firstOrNull { resource -> resource.atlasRegionIdOrNull() == regionId }
                ?.id
                ?: state.resources.selectedResourceId
    }

    fun syncSelectedRegionFromResource() {
        val resource = state.selectedResource()
        state.selectedRegionId = resource?.atlasRegionIdOrNull()
        state.selectedAtlasPageName = resource?.atlasRegionIdOrNull()?.pageName ?: state.selectedAtlasPageName
        syncSelectedPackingFromCurrentSelection()
    }

    fun syncSelectedPackingFromCurrentSelection() {
        val plan =
            state.selectedPackingPlan() ?: run {
                state.packing.selectedRegionId = null
                state.packing.selectedPageIndex = 0
                return
            }
        val resource = state.selectedResource()
        val selectedRegion =
            when {
                resource != null ->
                    plan.pages
                        .asSequence()
                        .flatMap { page -> page.regions.asSequence() }
                        .firstOrNull { packed ->
                            packed.sourcePath == resource.sourcePathOrNull() && packed.regionName == resource.name
                        }
                state.selectedRegionId != null ->
                    plan.pages
                        .asSequence()
                        .flatMap { page -> page.regions.asSequence() }
                        .firstOrNull { packed -> packed.regionName == state.selectedRegionId?.regionName }
                else -> null
            }
        if (selectedRegion != null) {
            state.packing.selectedPageIndex = selectedRegion.pageIndex
            state.packing.selectedRegionId = selectedRegion.id
        } else if (plan.pages.none { page -> page.regions.any { region -> region.id == state.packing.selectedRegionId } }) {
            state.packing.selectedPageIndex = 0
            state.packing.selectedRegionId =
                plan.pages
                    .firstOrNull()
                    ?.regions
                    ?.firstOrNull()
                    ?.id
        }
    }
}
