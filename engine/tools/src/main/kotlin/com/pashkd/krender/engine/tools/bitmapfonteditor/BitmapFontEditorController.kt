package com.pashkd.krender.engine.tools.bitmapfonteditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.tools.bitmapfonteditor.workflow.GenerateBitmapFontWorkflow
import com.pashkd.krender.engine.tools.bitmapfonteditor.workflow.SaveBitmapFontWorkflow

class BitmapFontEditorController(
    val state: BitmapFontEditorState,
    val engine: EngineContext,
) {
    private val generateWorkflow = GenerateBitmapFontWorkflow(state, engine)
    private val saveWorkflow = SaveBitmapFontWorkflow(state, engine)

    fun generate() {
        generateWorkflow.generate()
    }

    fun save() {
        saveWorkflow.save()
    }
    fun selectGlyph(glyphId: Int?) {
        state.glyphSelection.selectedGlyphId = glyphId
    }

    fun setGlyphFilter(filter: String) {
        state.glyphSelection.glyphFilter = filter
    }

    fun setSampleText(text: String) {
        state.sampleText = text
    }

    fun setSampleTextPreviewEnabled(enabled: Boolean) {
        state.showSampleTextPreview = enabled
    }

    fun setShowGlyphBounds(show: Boolean) {
        state.showGlyphBounds = show
    }

    fun setPreviewZoom(zoom: Float) {
        state.preview.customZoom = zoom.coerceIn(0.05f, 25f)
        state.preview.zoomMode = com.pashkd.krender.engine.tools.common.canvas.CanvasZoomMode.Custom
        state.preview.viewport.zoom = state.preview.customZoom
    }

    fun panPreview(
        dx: Float,
        dy: Float,
    ) {
        state.preview.viewport.panX += dx
        state.preview.viewport.panY += dy
    }

    companion object {
        internal const val TAG = "BitmapFontEditorCtrl"
    }
}
