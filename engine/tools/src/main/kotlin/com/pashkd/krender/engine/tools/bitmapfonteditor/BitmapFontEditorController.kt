package com.pashkd.krender.engine.tools.bitmapfonteditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.assets.importing.FileDialogFilter
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.tools.bitmapfonteditor.workflow.GenerateBitmapFontWorkflow
import com.pashkd.krender.engine.tools.bitmapfonteditor.workflow.OpenBitmapFontWorkflow
import com.pashkd.krender.engine.tools.bitmapfonteditor.workflow.SaveBitmapFontWorkflow
import com.pashkd.krender.engine.tools.common.canvas.CanvasZoomMode
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.io.File

class BitmapFontEditorController(
    val state: BitmapFontEditorState,
    val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val fileDialogService: FileDialogService,
) {
    private val generateWorkflow = GenerateBitmapFontWorkflow(state, engine)
    private val saveWorkflow = SaveBitmapFontWorkflow(state, engine)
    private val openWorkflow = OpenBitmapFontWorkflow(state, engine)

    fun generate() {
        generateWorkflow.generate()
    }

    fun preview() {
        generateWorkflow.preview()
    }

    fun save() {
        saveWorkflow.save()
    }

    fun reload() {
        if (state.dirty) {
            state.statusMessage = "Unsaved font changes are still in progress. Save the font before reloading."
            engine.logger.warn(TAG) { "Bitmap Font Editor blocked reload because unsaved changes are present" }
            return
        }
        val path = state.metadataPath ?: state.inputPath
        if (path.isNullOrBlank()) {
            state.statusMessage = "Nothing to reload."
            return
        }
        openWorkflow.openFromPath(path)
    }

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(BitmapFontEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(BitmapFontEditorUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }

    fun requestExit() {
        if (state.dirty) {
            state.statusMessage = "Unsaved font changes are still in progress. Save the font before exiting."
            engine.logger.warn(TAG) { "Bitmap Font Editor blocked exit because unsaved changes are present" }
            return
        }
        engine.requestExit()
    }

    fun browseSourceFont() {
        val selected =
            fileDialogService.openFile(
                listOf(
                    FileDialogFilter("Fonts", listOf("ttf", "otf")),
                    FileDialogFilter("TrueType Font", listOf("ttf")),
                    FileDialogFilter("OpenType Font", listOf("otf")),
                ),
            ) ?: return
        val current = state.metadata ?: BitmapFontEditorMetadata()
        state.metadata = current.copy(sourceFont = normalizePath(selected))
        state.dirty = true
        state.statusMessage = "Selected source font '${state.metadata?.sourceFont}'."
    }

    fun selectGlyph(
        glyphId: Int?,
        revealInList: Boolean = false,
    ) {
        state.glyphSelection.selectedGlyphId = glyphId
        if (revealInList && glyphId != null) {
            state.pendingScrollToSelectedGlyph = true
        }
    }

    fun setGlyphFilter(filter: String) {
        state.glyphSelection.glyphFilter = filter
    }

    fun setSampleText(text: String) {
        state.sampleText = text
    }

    fun setSampleTextPreviewEnabled(enabled: Boolean) {
        state.showSampleTextPreview = enabled
        state.statusMessage = if (enabled) "Preview mode set to Sample Text." else "Preview mode set to Font Page."
        engine.logger.info(TAG) { "Bitmap Font Editor canvas mode changed samplePreview=$enabled" }
    }

    fun setShowGlyphBounds(show: Boolean) {
        state.showGlyphBounds = show
    }

    fun setPreviewZoom(zoom: Float) {
        state.preview.customZoom = zoom.coerceIn(MinPreviewZoom, MaxPreviewZoom)
        state.preview.zoomMode = CanvasZoomMode.Custom
        state.preview.viewport.zoom = state.preview.customZoom
    }

    fun setZoomMode(mode: CanvasZoomMode) {
        state.preview.zoomMode = mode
        when (mode) {
            CanvasZoomMode.Fit -> fitPreview()
            CanvasZoomMode.Percent50 -> applyFixedZoom(0.5f, mode)
            CanvasZoomMode.Percent100 -> applyFixedZoom(1f, mode)
            CanvasZoomMode.Percent200 -> applyFixedZoom(2f, mode)
            CanvasZoomMode.Custom -> state.statusMessage = "Preview zoom set to Custom."
        }
    }

    fun panPreview(
        dx: Float,
        dy: Float,
    ) {
        state.preview.viewport.panX += dx
        state.preview.viewport.panY += dy
    }

    fun fitPreview() {
        state.preview.viewport.panX = 0f
        state.preview.viewport.panY = 0f
        state.preview.zoomMode = CanvasZoomMode.Fit
        state.statusMessage = "Preview fit to canvas."
        engine.logger.info(TAG) {
            "Bitmap Font Editor camera fit canvas=${state.canvasRect.width}x${state.canvasRect.height} texture=${state.textureWidth}x${state.textureHeight}"
        }
    }

    fun resetPreviewCamera() {
        state.preview.viewport.panX = 0f
        state.preview.viewport.panY = 0f
        state.preview.viewport.zoom = 1f
        state.preview.customZoom = 1f
        state.preview.zoomMode = CanvasZoomMode.Percent100
        state.statusMessage = "Preview camera reset."
        engine.logger.info(TAG) { "Bitmap Font Editor camera reset pan=0,0 zoom=1.0 mode=${state.preview.zoomMode}" }
    }

    fun focusSelectedGlyph() {
        if (state.showSampleTextPreview) {
            state.statusMessage = "Disable sample preview before focusing a glyph."
            return
        }
        val glyphId = state.glyphSelection.selectedGlyphId
        val document = state.document
        val pageId = document?.pages?.getOrNull(state.selectedPageIndex)?.id ?: 0
        val glyph = document?.glyphs?.firstOrNull { it.id == glyphId && it.page == pageId }
        if (glyph == null || glyph.width <= 0 || glyph.height <= 0) {
            state.statusMessage = "Select a glyph with valid bounds to focus it."
            return
        }
        val canvas = state.canvasRect
        if (!canvas.isValid || state.textureWidth <= 0 || state.textureHeight <= 0) {
            state.statusMessage = "Preview must be visible before focusing a glyph."
            return
        }
        val zoom =
            minOf(
                canvas.width / glyph.width.toFloat(),
                canvas.height / glyph.height.toFloat(),
            ).times(0.9f).coerceIn(MinPreviewZoom, MaxPreviewZoom)
        val imageWidth = state.textureWidth * zoom
        val imageHeight = state.textureHeight * zoom
        val baseImageX = canvas.x + (canvas.width - imageWidth) * 0.5f
        val baseImageY = canvas.y + (canvas.height - imageHeight) * 0.5f
        val glyphCenterX = glyph.x + glyph.width * 0.5f
        val glyphCenterY = glyph.y + glyph.height * 0.5f
        val desiredCenterX = canvas.x + canvas.width * 0.5f
        val desiredCenterY = canvas.y + canvas.height * 0.5f
        state.preview.customZoom = zoom
        state.preview.viewport.zoom = zoom
        state.preview.zoomMode = CanvasZoomMode.Custom
        state.preview.viewport.panX = desiredCenterX - (baseImageX + glyphCenterX * zoom)
        state.preview.viewport.panY = desiredCenterY - (baseImageY + glyphCenterY * zoom)
        state.statusMessage = "Focused glyph '${glyph.id}'."
        engine.logger.info(TAG) {
            "Bitmap Font Editor camera focus glyph=${glyph.id} page=$pageId zoom=$zoom pan=${state.preview.viewport.panX},${state.preview.viewport.panY}"
        }
    }

    private fun normalizePath(path: String): String {
        val assetRoot = engine.assetRegistry.baseDir().canonicalFile.path.replace('\\', '/')
        val selected = File(path).canonicalFile.path.replace('\\', '/')
        return if (selected.startsWith(assetRoot)) {
            selected.removePrefix(assetRoot).removePrefix("/")
        } else {
            selected
        }
    }

    private fun applyFixedZoom(
        zoom: Float,
        mode: CanvasZoomMode,
    ) {
        state.preview.customZoom = zoom.coerceIn(MinPreviewZoom, MaxPreviewZoom)
        state.preview.viewport.zoom = state.preview.customZoom
        state.preview.zoomMode = mode
        state.statusMessage = "Preview zoom set to ${(state.preview.customZoom * 100f).toInt()}%."
    }

    companion object {
        internal const val TAG = "BitmapFontEditorCtrl"
        private const val MinPreviewZoom = 0.05f
        private const val MaxPreviewZoom = 50f
    }
}
