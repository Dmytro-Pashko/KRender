package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewZoomMode

class EnvironmentSelectedResourcePreviewController(
    private val state: EnvironmentEditorState,
) {
    fun setZoomMode(mode: TexturePreviewZoomMode) {
        val preview = state.resourceInspectorState.selectedResourcePreviewState
        preview.zoomMode = mode
        when (mode) {
            TexturePreviewZoomMode.Fit -> fitPreview()
            TexturePreviewZoomMode.Percent50 -> setPreviewZoom(0.5f, updateMode = false)
            TexturePreviewZoomMode.Percent100 -> setPreviewZoom(1f, updateMode = false)
            TexturePreviewZoomMode.Percent200 -> setPreviewZoom(2f, updateMode = false)
            TexturePreviewZoomMode.Custom -> Unit
        }
    }

    fun setPreviewZoom(
        value: Float,
        updateMode: Boolean = true,
    ) {
        val preview = state.resourceInspectorState.selectedResourcePreviewState
        preview.customZoom = value.coerceIn(0.05f, 25f)
        preview.viewport.zoom = preview.customZoom
        if (updateMode) {
            preview.zoomMode = TexturePreviewZoomMode.Custom
        }
    }

    fun panPreview(
        deltaX: Float,
        deltaY: Float,
    ) {
        val viewport = state.resourceInspectorState.selectedResourcePreviewState.viewport
        viewport.panX += deltaX
        viewport.panY += deltaY
    }

    fun fitPreview() {
        val preview = state.resourceInspectorState.selectedResourcePreviewState
        preview.viewport.panX = 0f
        preview.viewport.panY = 0f
        preview.zoomMode = TexturePreviewZoomMode.Fit
    }

    fun resetPreviewCamera() {
        val preview = state.resourceInspectorState.selectedResourcePreviewState
        preview.viewport.panX = 0f
        preview.viewport.panY = 0f
        preview.viewport.zoom = 1f
        preview.customZoom = 1f
        preview.zoomMode = TexturePreviewZoomMode.Percent100
    }
}
