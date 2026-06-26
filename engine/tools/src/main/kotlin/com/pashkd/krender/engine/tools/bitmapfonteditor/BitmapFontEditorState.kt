package com.pashkd.krender.engine.tools.bitmapfonteditor

import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnostic
import com.pashkd.krender.engine.tools.common.bitmapfont.preview.GlyphSelectionState
import com.pashkd.krender.engine.tools.common.canvas.CanvasPreviewState
import com.pashkd.krender.engine.tools.common.canvas.CanvasRect

class BitmapFontEditorState(
    var inputPath: String? = null,
) {
    var document: BitmapFontDocument? = null
    var dirty: Boolean = false
    var statusMessage: String = "Bitmap Font Editor ready."
    var diagnostics: List<BitmapFontDiagnostic> = emptyList()

    var canvasRect: CanvasRect = CanvasRect()
    var preview: CanvasPreviewState = CanvasPreviewState()
    var glyphSelection: GlyphSelectionState = GlyphSelectionState()
    var sampleText: String = "The quick brown fox jumps over the lazy dog 0123456789"
    var showSampleTextPreview: Boolean = false
    var showGlyphBounds: Boolean = true
    var selectedPageIndex: Int = 0

    var texturePreviewHandle: TexturePreviewHandle? = null
    var textureWidth: Int = 0
    var textureHeight: Int = 0

    var metadata: BitmapFontEditorMetadata? = null
    var metadataPath: String? = null

    var generatedPageRgba: ByteArray? = null
    var generatedPageWidth: Int = 0
    var generatedPageHeight: Int = 0
}
