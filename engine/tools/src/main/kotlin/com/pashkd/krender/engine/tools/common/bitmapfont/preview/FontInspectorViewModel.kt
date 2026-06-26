@file:Suppress("MatchingDeclarationName")

package com.pashkd.krender.engine.tools.common.bitmapfont.preview

import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument
import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontGlyph

data class FontInspectorData(
    val face: String? = null,
    val size: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val lineHeight: Int = 0,
    val base: Int = 0,
    val scaleW: Int = 0,
    val scaleH: Int = 0,
    val pageCount: Int = 0,
    val glyphCount: Int = 0,
    val kerningCount: Int = 0,
    val diagnosticCount: Int = 0,
)

fun buildFontInspectorData(document: BitmapFontDocument): FontInspectorData =
    FontInspectorData(
        face = document.info?.face,
        size = document.info?.size,
        bold = document.info?.bold ?: false,
        italic = document.info?.italic ?: false,
        lineHeight = document.common?.lineHeight ?: 0,
        base = document.common?.base ?: 0,
        scaleW = document.common?.scaleW ?: 0,
        scaleH = document.common?.scaleH ?: 0,
        pageCount = document.pages.size,
        glyphCount = document.glyphs.size,
        kerningCount = document.kernings.size,
        diagnosticCount = document.diagnostics.size,
    )

fun glyphDisplayLabel(glyph: BitmapFontGlyph): String {
    val charDisplay = glyph.char?.takeIf { it.isNotBlank() } ?: ""
    return if (charDisplay.isNotEmpty()) {
        "'$charDisplay' (${glyph.id})"
    } else {
        "U+${glyph.id.toString(16).uppercase().padStart(4, '0')} (${glyph.id})"
    }
}
