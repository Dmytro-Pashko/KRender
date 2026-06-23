package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

class SkinEditorOperations(
    private val state: SkinEditorState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    private val editService = SkinEditService(state)

    fun requestReload() {
        state.reloadRequested = true
    }

    fun requestExit() {
        engine.requestExit()
    }

    fun openPath(path: String) {
        state.currentInputPath = path.trim().replace('\\', '/').ifBlank { null }
        state.pendingPathInput = state.currentInputPath.orEmpty()
        state.reloadRequested = true
    }

    fun selectStyle(styleKey: StyleKey) {
        state.selectedStyleKey = styleKey
        state.selectedProblemIndex = null
        state.previewDirty = true
    }

    fun selectResource(resource: SkinResourceInfo) {
        state.selectedResourceKey = resource.key
        state.selectedProblemIndex = null
        state.resourceVisualPreview.selectedAtlasRegionName =
            if (resource.category == SkinResourceCategory.AtlasRegion) {
                resource.name
            } else {
                null
            }
    }

    fun selectProblem(
        index: Int,
        problem: SkinProblem,
    ) {
        state.selectedProblemIndex = index
        state.selectedStyleKey = problem.styleKey
        state.selectedResourceKey = problem.resourceKey
        state.resourceVisualPreview.selectedAtlasRegionName =
            problem.resourceKey
                ?.takeIf { key -> key.category == SkinResourceCategory.AtlasRegion }
                ?.name
    }

    fun discardInMemoryEdits() {
        editService.discardEdits()
    }

    fun updateStyleField(
        styleKey: StyleKey,
        fieldName: String,
        value: String,
    ) {
        editService.updateStyleField(styleKey, fieldName, value)
    }

    fun resetStyleField(
        styleKey: StyleKey,
        fieldName: String,
    ) {
        editService.resetStyleField(styleKey, fieldName)
    }

    fun addStyleField(
        styleKey: StyleKey,
        knownField: KnownStyleField,
    ) {
        editService.addStyleField(styleKey, knownField)
    }

    fun removeStyleField(
        styleKey: StyleKey,
        fieldName: String,
    ) {
        editService.removeStyleField(styleKey, fieldName)
    }

    fun duplicateStyle(
        sourceKey: StyleKey,
        newName: String,
    ): Boolean = editService.duplicateStyle(sourceKey, newName)

    fun renameStyle(
        sourceKey: StyleKey,
        newName: String,
    ): Boolean = editService.renameStyle(sourceKey, newName)

    fun createStyle(
        type: String,
        name: String,
    ): Boolean = editService.createStyle(type, name)

    fun deleteStyle(styleKey: StyleKey) {
        editService.deleteStyle(styleKey)
    }

    fun updateColorResource(
        resourceKey: SkinResourceKey,
        fieldName: String,
        value: String,
    ) {
        editService.updateColorResource(resourceKey, fieldName, value)
    }

    fun selectEditChange(change: SkinEditChange) {
        editService.selectChange(change)
    }

    fun selectLayout(layoutId: String) {
        state.previewLayoutId = layoutId
        updatePreviewStatus("Preview layout set to '$layoutId'.")
    }

    fun selectScreenPreset(presetId: String) {
        val preset = SkinPreviewScreenPresets.presetOrDefault(presetId)
        state.previewSettings.screenPresetId = preset.id
        updatePreviewStatus("Preview screen set to ${preset.displayName} (${preset.width} x ${preset.height}).")
    }

    fun setPreviewScale(scale: Float) {
        state.previewSettings.scale = scale.coerceIn(0.5f, 1.5f)
        updatePreviewStatus("Preview scale set to ${formatScale(state.previewSettings.scale)}.")
    }

    fun setShowBounds(showBounds: Boolean) {
        state.previewSettings.showBounds = showBounds
        updatePreviewStatus(if (showBounds) "Preview bounds enabled." else "Preview bounds hidden.")
    }

    fun setShowFallbackWarnings(showWarnings: Boolean) {
        state.previewSettings.showFallbackWarnings = showWarnings
        updatePreviewStatus(if (showWarnings) "Fallback warnings shown in Problems." else "Fallback warnings hidden in Problems.")
    }

    fun setPreviewLabelText(value: String) {
        state.previewSettings.text.labelText = value
        updatePreviewStatus("Preview label text updated.")
    }

    fun setPreviewButtonText(value: String) {
        state.previewSettings.text.buttonText = value
        updatePreviewStatus("Preview button text updated.")
    }

    fun setPreviewTextFieldPlaceholder(value: String) {
        state.previewSettings.text.textFieldPlaceholder = value
        updatePreviewStatus("Preview text field placeholder updated.")
    }

    fun setResourcePreviewZoomMode(zoomMode: SkinResourceVisualPreviewZoomMode) {
        state.resourceVisualPreview.zoomMode = zoomMode
        when (zoomMode) {
            SkinResourceVisualPreviewZoomMode.Fit -> resetResourcePreviewViewport()
            SkinResourceVisualPreviewZoomMode.Percent50 -> setResourcePreviewViewportZoom(0.5f, updateMode = false)
            SkinResourceVisualPreviewZoomMode.Percent100 -> setResourcePreviewViewportZoom(1f, updateMode = false)
            SkinResourceVisualPreviewZoomMode.Percent200 -> setResourcePreviewViewportZoom(2f, updateMode = false)
            SkinResourceVisualPreviewZoomMode.Custom -> state.statusMessage = "Resource preview zoom set to Custom."
        }
        state.resourceVisualPreview.zoomMode = zoomMode
    }

    fun resetResourcePreviewZoom() {
        resetResourcePreviewViewport()
    }

    fun setShowResourceRegionBounds(showBounds: Boolean) {
        state.resourceVisualPreview.showRegionBounds = showBounds
        state.statusMessage = if (showBounds) "Resource preview bounds enabled." else "Resource preview bounds hidden."
    }

    fun setShowResourceRegionLabels(showLabels: Boolean) {
        state.resourceVisualPreview.showRegionLabels = showLabels
        state.statusMessage = if (showLabels) "Resource preview labels enabled." else "Resource preview labels hidden."
    }

    fun selectAtlasRegionPreview(
        regionName: String?,
        preferredSource: String? = null,
        preferredPage: String? = null,
    ) {
        selectAtlasRegionByName(regionName, preferredSource, preferredPage)
    }

    fun setResourcePreviewViewportZoom(
        value: Float,
        updateMode: Boolean = true,
    ) {
        state.resourceVisualPreview.viewport.zoom = value.coerceIn(MinResourcePreviewZoom, MaxResourcePreviewZoom)
        if (updateMode) {
            state.resourceVisualPreview.zoomMode = SkinResourceVisualPreviewZoomMode.Custom
        }
        state.statusMessage = "Resource preview zoom set to ${formatScale(state.resourceVisualPreview.viewport.zoom)}."
    }

    fun panResourcePreviewViewport(
        deltaX: Float,
        deltaY: Float,
    ) {
        state.resourceVisualPreview.viewport.panX += deltaX
        state.resourceVisualPreview.viewport.panY += deltaY
    }

    fun resetResourcePreviewViewport() {
        state.resourceVisualPreview.viewport.panX = 0f
        state.resourceVisualPreview.viewport.panY = 0f
        state.resourceVisualPreview.viewport.zoom = 1f
        state.resourceVisualPreview.zoomMode = SkinResourceVisualPreviewZoomMode.Fit
        state.statusMessage = "Resource preview view reset to Fit."
    }

    fun setAtlasClickSelectionEnabled(enabled: Boolean) {
        state.resourceVisualPreview.viewport.clickSelectRegionEnabled = enabled
        state.statusMessage = if (enabled) "Atlas click selection enabled." else "Atlas click selection disabled."
    }

    fun selectAtlasRegionByName(
        regionName: String?,
        preferredSource: String? = null,
        preferredPage: String? = null,
    ) {
        state.resourceVisualPreview.selectedAtlasRegionName = regionName?.takeIf(String::isNotBlank)
        val selectedRegionName = state.resourceVisualPreview.selectedAtlasRegionName
        if (selectedRegionName != null) {
            state.loadResult.resourceIndex.atlasRegions.firstOrNull { region ->
                region.name == selectedRegionName &&
                    (preferredSource == null || region.source == preferredSource) &&
                    (preferredPage == null || region.details["page"] == preferredPage)
            }?.let { region ->
                state.selectedResourceKey = region.key
            }
        }
        state.statusMessage =
            selectedRegionName?.let { name ->
                "Atlas preview region set to '$name'."
            } ?: "Atlas preview region cleared."
    }

    fun syncResourcePreviewViewportContent(contentKey: String?) {
        val viewport = state.resourceVisualPreview.viewport
        if (viewport.contentKey == contentKey) return
        viewport.contentKey = contentKey
        viewport.panX = 0f
        viewport.panY = 0f
        viewport.zoom = 1f
        state.resourceVisualPreview.zoomMode = SkinResourceVisualPreviewZoomMode.Fit
    }

    fun setFontPreviewScale(scale: Float) {
        state.resourceVisualPreview.fontPreview.fontScale = scale.coerceIn(0.5f, 2f)
        state.statusMessage = "Font preview scale set to ${formatScale(state.resourceVisualPreview.fontPreview.fontScale)}."
    }

    fun setFontPreviewSampleText(sampleText: String) {
        state.resourceVisualPreview.fontPreview.sampleText = sampleText
    }

    fun setShowCyrillicFontSample(show: Boolean) {
        state.resourceVisualPreview.fontPreview.showCyrillicSample = show
        state.statusMessage = if (show) "Cyrillic font sample enabled." else "Cyrillic font sample hidden."
    }

    fun setShowAsciiFontSample(show: Boolean) {
        state.resourceVisualPreview.fontPreview.showAsciiSample = show
        state.statusMessage = if (show) "ASCII font sample enabled." else "ASCII font sample hidden."
    }

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(SkinEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(SkinEditorUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }

    private fun updatePreviewStatus(message: String) {
        state.previewDirty = true
        state.statusMessage = message
    }

    private fun formatScale(scale: Float): String = "${(scale * 100f).toInt()}%"

    private fun formatResourceZoom(zoomMode: SkinResourceVisualPreviewZoomMode): String =
        when (zoomMode) {
            SkinResourceVisualPreviewZoomMode.Fit -> "Fit"
            SkinResourceVisualPreviewZoomMode.Percent50 -> "50%"
            SkinResourceVisualPreviewZoomMode.Percent100 -> "100%"
            SkinResourceVisualPreviewZoomMode.Percent200 -> "200%"
            SkinResourceVisualPreviewZoomMode.Custom -> "Custom"
        }

    private companion object {
        private const val MinResourcePreviewZoom = 0.1f
        private const val MaxResourcePreviewZoom = 8f
    }
}
