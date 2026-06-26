package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.tools.common.bitmapfont.io.layoutSampleText as commonLayoutSampleText
import java.io.File

class BitmapFontParser {
    private val delegate = com.pashkd.krender.engine.tools.common.bitmapfont.io.BitmapFontParser()

    fun parse(fntFile: File): BitmapFontDocument = delegate.parse(fntFile)

    companion object {
        internal fun parseAttributes(text: String): Map<String, String> =
            com.pashkd.krender.engine.tools.common.bitmapfont.io.BitmapFontParser.parseAttributes(text)
    }
}

internal fun layoutSampleText(
    text: String,
    document: BitmapFontDocument,
): SampleTextLayout = commonLayoutSampleText(text, document)
