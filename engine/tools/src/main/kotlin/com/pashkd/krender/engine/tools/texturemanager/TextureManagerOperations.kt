package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.io.File

class TextureManagerOperations(
    private val state: TextureManagerState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun openPath(path: String) {
        val normalized = path.trim().replace('\\', '/').ifBlank { null }
        if (normalized != state.currentInputPath) {
            state.clearPreviewSelection()
            engine.logger.info(TAG) {
                "Texture Manager input path changed old='${state.currentInputPath ?: "<none>"}' new='${normalized ?: "<none>"}'; selection reset"
            }
        }
        state.currentInputPath = normalized
        state.pendingPathInput = normalized.orEmpty()
        state.reloadRequested = true
        engine.logger.info(TAG) { "Texture Manager openPath path='${normalized ?: "<none>"}'" }
    }

    fun reload() {
        state.reloadRequested = true
        state.statusMessage = "Reload requested."
    }

    fun selectAsset(assetId: TextureAssetId) {
        val asset = state.project.assets.firstOrNull { it.id == assetId } ?: return
        state.selectedAssetId = assetId
        state.hoveredRegionId = null
        when (asset.kind) {
            TextureManagerAssetKind.Texture -> {
                state.selectedAtlasPageName = null
                state.selectedRegionId = null
            }
            TextureManagerAssetKind.Atlas -> {
                val atlas = state.project.atlasDocuments[asset.path]
                state.selectedAtlasPageName = atlas?.pages?.firstOrNull()?.name
                state.selectedRegionId = atlas?.regions?.firstOrNull()?.id
            }
            else -> Unit
        }
        state.statusMessage = "Selected ${asset.kind.name.lowercase()} '${asset.displayName}'."
        engine.logger.info(TAG) { "Texture Manager selected asset id='${assetId.value}' kind=${asset.kind}" }
    }

    fun selectAtlasPage(pageName: String) {
        state.selectedAtlasPageName = pageName
        val atlas = selectedAtlasDocument()
        state.selectedRegionId =
            atlas?.regions
                ?.firstOrNull { region -> region.id.pageName == pageName }
                ?.id
        state.statusMessage = "Selected atlas page '$pageName'."
        engine.logger.info(TAG) { "Texture Manager selected atlas page='$pageName'" }
    }

    fun selectRegion(regionId: AtlasRegionId?) {
        state.selectedRegionId = regionId
        if (regionId != null) {
            state.selectedAtlasPageName = regionId.pageName
            state.statusMessage = "Selected region '${regionId.regionName}'."
            engine.logger.info(TAG) { "Texture Manager selected region='${regionId.regionName}' page='${regionId.pageName}'" }
        } else {
            state.statusMessage = "Region selection cleared."
        }
    }

    fun setHoveredRegion(regionId: AtlasRegionId?) {
        if (state.hoveredRegionId == regionId) return
        state.hoveredRegionId = regionId
    }

    fun setToolMode(mode: TextureManagerToolMode) {
        state.toolMode = mode
        state.statusMessage = "Tool mode set to ${mode.name}."
    }

    fun setZoomMode(mode: TexturePreviewZoomMode) {
        state.preview.zoomMode = mode
        when (mode) {
            TexturePreviewZoomMode.Fit -> fitPreview()
            TexturePreviewZoomMode.Percent50 -> setPreviewZoom(0.5f, updateMode = false)
            TexturePreviewZoomMode.Percent100 -> setPreviewZoom(1f, updateMode = false)
            TexturePreviewZoomMode.Percent200 -> setPreviewZoom(2f, updateMode = false)
            TexturePreviewZoomMode.Custom -> state.statusMessage = "Preview zoom set to Custom."
        }
    }

    fun setPreviewZoom(
        value: Float,
        updateMode: Boolean = true,
    ) {
        state.preview.customZoom = value.coerceIn(0.05f, 8f)
        state.preview.viewport.zoom = state.preview.customZoom
        if (updateMode) {
            state.preview.zoomMode = TexturePreviewZoomMode.Custom
        }
        state.statusMessage = "Preview zoom set to ${(state.preview.customZoom * 100f).toInt()}%."
    }

    fun panPreview(
        deltaX: Float,
        deltaY: Float,
    ) {
        state.preview.viewport.panX += deltaX
        state.preview.viewport.panY += deltaY
    }

    fun resetPreviewCamera() {
        state.preview.viewport.panX = 0f
        state.preview.viewport.panY = 0f
        state.preview.viewport.zoom = 1f
        state.preview.customZoom = 1f
        if (state.preview.zoomMode == TexturePreviewZoomMode.Custom) {
            state.preview.zoomMode = TexturePreviewZoomMode.Percent100
        }
        state.statusMessage = "Preview camera reset."
    }

    fun fitPreview() {
        state.preview.viewport.panX = 0f
        state.preview.viewport.panY = 0f
        state.preview.zoomMode = TexturePreviewZoomMode.Fit
        state.statusMessage = "Preview fit to canvas."
    }

    fun setShowCheckerboard(enabled: Boolean) {
        state.preview.showCheckerboard = enabled
        state.statusMessage = if (enabled) "Checkerboard enabled." else "Checkerboard hidden."
    }

    fun setShowGrid(enabled: Boolean) {
        state.preview.showGrid = enabled
        state.statusMessage = if (enabled) "Grid enabled." else "Grid hidden."
    }

    fun setShowBounds(enabled: Boolean) {
        state.preview.showBounds = enabled
        state.statusMessage = if (enabled) "Bounds enabled." else "Bounds hidden."
    }

    fun importTexturePlaceholder() = placeholder("Import Texture")

    fun saveMetadataPlaceholder() = placeholder("Save Metadata")

    fun packAtlasPlaceholder() = placeholder("Pack Atlas")

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(TextureManagerUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(TextureManagerUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }

    fun requestExit() {
        engine.requestExit()
    }

    private fun selectedAtlasDocument(): TextureAtlasDocument? {
        val asset = state.project.assets.firstOrNull { it.id == state.selectedAssetId } ?: return null
        if (asset.kind != TextureManagerAssetKind.Atlas) return null
        return state.project.atlasDocuments[asset.path]
    }

    private fun placeholder(action: String) {
        val target = state.currentInputPath ?: state.project.resolvedInputPath ?: "<none>"
        state.statusMessage = "$action is deferred in this MVP."
        engine.logger.info(TAG) { "Texture Manager placeholder action='$action' target='$target'" }
    }

    companion object {
        private const val TAG = "TextureManagerOps"
    }
}

internal fun TextureManagerState.selectedAsset(): TextureManagerAssetDescriptor? = project.assets.firstOrNull { it.id == selectedAssetId }

internal fun TextureManagerState.selectedAtlasDocument(): TextureAtlasDocument? = selectedAsset()?.takeIf { it.kind == TextureManagerAssetKind.Atlas }?.let { asset -> project.atlasDocuments[asset.path] }

internal fun TextureManagerState.selectedRegionsForPage(): List<TextureAtlasRegion> =
    selectedAtlasDocument()
        ?.regions
        ?.filter { region -> selectedAtlasPageName == null || region.id.pageName == selectedAtlasPageName }
        .orEmpty()

internal fun TextureManagerState.selectedPreviewTexturePath(): String? {
    val asset = selectedAsset()
    if (asset == null) {
        project.selectedTexturePath?.let { return it }
        val atlasPath = project.selectedAtlasPath ?: return null
        return resolveAtlasPreviewTexturePath(
            atlasPath = atlasPath,
            atlas = project.atlasDocuments[atlasPath],
            selectedPageName = selectedAtlasPageName,
        )
    }
    return when (asset.kind) {
        TextureManagerAssetKind.Texture -> asset.path
        TextureManagerAssetKind.Atlas -> resolveAtlasPreviewTexturePath(asset.path, project.atlasDocuments[asset.path], selectedAtlasPageName)
        else -> null
    }
}

internal fun resolveAtlasPreviewTexturePath(
    atlasPath: String,
    atlas: TextureAtlasDocument?,
    selectedPageName: String?,
): String? {
    val pageName = selectedPageName ?: atlas?.pages?.firstOrNull()?.name ?: return null
    val atlasFile = File(atlasPath)
    val pageFile = File(pageName)
    if (pageFile.isAbsolute) {
        return normalizePath(pageFile.path)
    }
    val parent = atlasFile.parentFile ?: return null
    return normalizePath(File(parent, pageName).path)
}
