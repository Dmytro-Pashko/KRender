package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

class SkinEditorOperations(
    private val state: SkinEditorState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun requestReload() {
        state.reloadRequested = true
    }

    fun openPath(path: String) {
        state.currentInputPath = path.trim().replace('\\', '/').ifBlank { null }
        state.pendingPathInput = state.currentInputPath.orEmpty()
        state.reloadRequested = true
    }

    fun selectStyle(styleKey: StyleKey) {
        state.selectedStyleKey = styleKey
        state.selectedResourceKey = null
        state.selectedProblemIndex = null
        state.previewDirty = true
    }

    fun selectResource(resource: SkinResourceInfo) {
        state.selectedResourceKey = resource.key
        state.selectedStyleKey = null
        state.selectedProblemIndex = null
        state.resourceVisualPreview.selectedAtlasRegionName =
            if (resource.category == SkinResourceCategory.AtlasRegion) {
                resource.name
            } else {
                null
            }
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

    fun resetPreviewScale() {
        state.previewSettings.scale = 1f
        updatePreviewStatus("Preview scale reset to 100%.")
    }

    fun resetPreviewScreenPreset() {
        val preset = SkinPreviewScreenPresets.presetOrDefault(SkinPreviewScreenPresets.DesktopId)
        state.previewSettings.screenPresetId = preset.id
        updatePreviewStatus("Preview screen reset to ${preset.displayName}.")
    }

    fun resetPreviewLayout() {
        state.previewLayoutId = DefaultWidgetPreviewLayout.Id
        updatePreviewStatus("Preview layout reset to '${DefaultWidgetPreviewLayout.Id}'.")
    }

    fun resetPreviewSettings() {
        state.previewSettings = SkinPreviewSettings()
        state.previewLayoutId = DefaultWidgetPreviewLayout.Id
        updatePreviewStatus("Preview settings reset.")
    }

    fun previewSelectedStyle() {
        val selectedStyle = state.selectedStyleKey
        if (selectedStyle == null) {
            state.statusMessage = "Select a style before using selected-style preview."
            return
        }
        state.previewLayoutId = SelectedStylePreviewLayout.Id
        updatePreviewStatus("Previewing selected style '${selectedStyle.type}.${selectedStyle.name}'.")
    }

    fun setResourcePreviewZoomMode(zoomMode: SkinResourceVisualPreviewZoomMode) {
        state.resourceVisualPreview.zoomMode = zoomMode
        state.statusMessage = "Resource preview zoom set to ${formatResourceZoom(zoomMode)}."
    }

    fun resetResourcePreviewZoom() {
        state.resourceVisualPreview.zoomMode = SkinResourceVisualPreviewZoomMode.Fit
        state.statusMessage = "Resource preview zoom reset to Fit."
    }

    fun setShowResourceRegionBounds(showBounds: Boolean) {
        state.resourceVisualPreview.showRegionBounds = showBounds
        state.statusMessage = if (showBounds) "Resource preview bounds enabled." else "Resource preview bounds hidden."
    }

    fun setShowResourceRegionLabels(showLabels: Boolean) {
        state.resourceVisualPreview.showRegionLabels = showLabels
        state.statusMessage = if (showLabels) "Resource preview labels enabled." else "Resource preview labels hidden."
    }

    fun selectAtlasRegionPreview(regionName: String?) {
        state.resourceVisualPreview.selectedAtlasRegionName = regionName?.takeIf(String::isNotBlank)
        state.statusMessage =
            state.resourceVisualPreview.selectedAtlasRegionName?.let { name ->
                "Atlas preview region set to '$name'."
            } ?: "Atlas preview region cleared."
    }

    fun setFontPreviewScale(scale: Float) {
        state.resourceVisualPreview.fontPreview.fontScale = scale.coerceIn(0.5f, 2f)
        state.statusMessage = "Font preview scale set to ${formatScale(state.resourceVisualPreview.fontPreview.fontScale)}."
    }

    fun setFontPreviewSampleText(sampleText: String) {
        state.resourceVisualPreview.fontPreview.sampleText = sampleText
    }

    fun setShowUkrainianFontSample(show: Boolean) {
        state.resourceVisualPreview.fontPreview.showUkrainianSample = show
        state.statusMessage = if (show) "Ukrainian font sample enabled." else "Ukrainian font sample hidden."
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
        }
}
