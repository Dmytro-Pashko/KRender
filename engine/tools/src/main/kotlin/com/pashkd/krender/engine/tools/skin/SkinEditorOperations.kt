package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

class SkinEditorOperations(
    private val state: SkinEditorState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    private val editService = SkinEditService(state, engine.logger)
    private val saveService = SkinStyleSaveService(engine.logger, engine.sceneFiles)

    // Load and navigation commands.
    fun requestReload() {
        state.reloadRequested = true
        engine.logger.info(TAG) { "Skin Editor reload requested path='${state.currentInputPath ?: "<none>"}' dirty=${state.editSession.dirty} pendingChanges=${state.editSession.changes.size}" }
    }

    fun requestExit() {
        engine.logger.info(TAG) { "Skin Editor exit requested path='${state.currentInputPath ?: "<none>"}' dirty=${state.editSession.dirty} pendingChanges=${state.editSession.changes.size}" }
        engine.requestExit()
    }

    fun openPath(path: String) {
        val previousPath = state.currentInputPath
        state.currentInputPath = path.trim().replace('\\', '/').ifBlank { null }
        state.pendingPathInput = state.currentInputPath.orEmpty()
        state.reloadRequested = true
        engine.logger.info(TAG) {
            "Skin Editor openPath old='${previousPath ?: "<none>"}' new='${state.currentInputPath ?: "<none>"}' dirty=${state.editSession.dirty} pendingChanges=${state.editSession.changes.size}"
        }
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

    // In-memory edit commands.
    fun discardInMemoryEdits() {
        editService.discardEdits()
    }

    fun saveChanges() {
        if (state.loadResult.project?.skinFile == null) {
            state.statusMessage = "Failed to save style changes: no skin file loaded."
            return
        }
        if (!state.editSession.dirty || state.editSession.changes.isEmpty()) {
            state.statusMessage = "No draft style changes to save."
            return
        }
        val result = saveService.save(state.loadResult.project, state.loadResult, state.editSession)
        state.statusMessage = result.message
        if (result.success) {
            state.pendingStatusAfterReload = result.message
            state.reloadRequested = true
        }
        engine.logger.info(TAG) {
            "Skin Editor saveChanges result success=${result.success} file='${result.file?.path ?: "<none>"}' backup='${result.backupFile?.path ?: "<none>"}' savedChanges=${result.savedChangeCount} pendingChangesBeforeSave=${state.editSession.changes.size}"
        }
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

    // Scene2D preview commands.
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

    fun setPreviewCheckerboardEnabled(enabled: Boolean) {
        state.previewSettings.showCheckerboard = enabled
        state.statusMessage = if (enabled) "Preview checkerboard enabled." else "Preview checkerboard hidden."
    }

    fun setHighlightSelectedStyle(enabled: Boolean) {
        state.previewSettings.highlightSelectedStyle = enabled
        state.statusMessage = if (enabled) "Selected style highlight enabled." else "Selected style highlight hidden."
    }

    fun setCanvasInteractionEnabled(enabled: Boolean) {
        state.previewSettings.interaction.inputEnabled = enabled
        state.previewSettings.interaction.lastInputStatus =
            if (enabled) {
                "Widget interaction enabled. Keyboard input is deferred."
            } else {
                "Widget interaction disabled."
            }
        state.statusMessage =
            state.previewSettings.interaction.lastInputStatus
                .orEmpty()
    }

    fun queuePreviewPointerEvent(event: SkinPreviewPointerEvent) {
        state.pendingPreviewPointerEvents += event
    }

    fun clearPreviewInteractionFeedback() {
        state.previewSettings.interaction.hoveredActorPath = null
        state.previewSettings.interaction.focusedActorPath = null
        state.previewSettings.interaction.cursorCanvasX = null
        state.previewSettings.interaction.cursorCanvasY = null
        state.previewSettings.interaction.cursorStageX = null
        state.previewSettings.interaction.cursorStageY = null
    }

    fun updatePreviewInteractionFeedback(feedback: SkinPreviewInteractionFeedback) {
        state.previewSettings.interaction.hoveredActorPath = feedback.hoveredActorPath
        state.previewSettings.interaction.focusedActorPath = feedback.focusedActorPath
        state.previewSettings.interaction.cursorCanvasX = feedback.cursorCanvasX
        state.previewSettings.interaction.cursorCanvasY = feedback.cursorCanvasY
        state.previewSettings.interaction.cursorStageX = feedback.cursorStageX
        state.previewSettings.interaction.cursorStageY = feedback.cursorStageY
        feedback.lastInputStatus?.let { status ->
            state.previewSettings.interaction.lastInputStatus = status
        }
    }

    fun panPreviewCamera(
        deltaScreenX: Float,
        deltaScreenY: Float,
    ) {
        val canvasWidth = state.canvasRect.width.coerceAtLeast(1f)
        val canvasHeight = state.canvasRect.height.coerceAtLeast(1f)
        val preset = SkinPreviewScreenPresets.presetOrDefault(state.previewSettings.screenPresetId)
        val visibleWorldWidth = preset.width / state.previewSettings.scale / state.previewSettings.camera.zoom
        val visibleWorldHeight = preset.height / state.previewSettings.scale / state.previewSettings.camera.zoom
        state.previewSettings.camera.panX -= deltaScreenX * (visibleWorldWidth / canvasWidth)
        state.previewSettings.camera.panY += deltaScreenY * (visibleWorldHeight / canvasHeight)
    }

    fun setPreviewCameraZoom(value: Float) {
        state.previewSettings.camera.zoom = value.coerceIn(MinPreviewCameraZoom, MaxPreviewCameraZoom)
        state.statusMessage = "Preview camera zoom set to ${formatScale(state.previewSettings.camera.zoom)}."
    }

    fun resetPreviewCamera() {
        state.previewSettings.camera.panX = 0f
        state.previewSettings.camera.panY = 0f
        state.previewSettings.camera.zoom = 1f
        state.statusMessage = "Preview camera reset."
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

    // Resource preview commands.
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

    // Atlas preview overlay commands.
    fun setAtlasClickSelectionEnabled(enabled: Boolean) {
        state.resourceVisualPreview.viewport.clickSelectRegionEnabled = enabled
        state.statusMessage = if (enabled) "Atlas click selection enabled." else "Atlas click selection disabled."
    }

    fun setAtlasCheckerboardEnabled(enabled: Boolean) {
        state.resourceVisualPreview.viewport.atlasVisuals.showCheckerboard = enabled
        state.statusMessage = if (enabled) "Atlas transparency checkerboard enabled." else "Atlas transparency checkerboard hidden."
    }

    fun setAtlasGridEnabled(enabled: Boolean) {
        state.resourceVisualPreview.viewport.atlasVisuals.showGrid = enabled
        state.statusMessage = if (enabled) "Atlas preview grid enabled." else "Atlas preview grid hidden."
    }

    fun setAtlasGridSize(size: Int) {
        state.resourceVisualPreview.viewport.atlasVisuals.gridSize = size.coerceIn(4, 256)
        state.statusMessage = "Atlas preview grid size set to ${state.resourceVisualPreview.viewport.atlasVisuals.gridSize}px."
    }

    fun setAtlasAllRegionBoundsEnabled(enabled: Boolean) {
        state.resourceVisualPreview.viewport.atlasVisuals.showAllRegionBounds = enabled
        state.statusMessage = if (enabled) "Atlas region bounds overlay enabled." else "Atlas region bounds overlay hidden."
    }

    fun setAtlasHoverHighlightEnabled(enabled: Boolean) {
        state.resourceVisualPreview.viewport.atlasVisuals.showHoverHighlight = enabled
        state.statusMessage = if (enabled) "Atlas hover highlight enabled." else "Atlas hover highlight hidden."
    }

    fun selectAtlasRegionByName(
        regionName: String?,
        preferredSource: String? = null,
        preferredPage: String? = null,
    ) {
        state.resourceVisualPreview.selectedAtlasRegionName = regionName?.takeIf(String::isNotBlank)
        val selectedRegionName = state.resourceVisualPreview.selectedAtlasRegionName
        if (selectedRegionName != null) {
            state.loadResult.resourceIndex.atlasRegions
                .firstOrNull { region ->
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

    // Resource preview synchronization and font preview commands.
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

    private companion object {
        private const val MinResourcePreviewZoom = 0.1f
        private const val MaxResourcePreviewZoom = 8f
        private const val MinPreviewCameraZoom = 0.25f
        private const val MaxPreviewCameraZoom = 4f
        private const val TAG = "SkinEditorOps"
    }
}
