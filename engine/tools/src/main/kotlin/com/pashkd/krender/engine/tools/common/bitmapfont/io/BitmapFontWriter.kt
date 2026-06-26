package com.pashkd.krender.engine.tools.common.bitmapfont.io

import com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDocument
import java.io.File

data class BitmapFontWriteResult(
    val success: Boolean,
    val message: String,
    val writtenPaths: List<String> = emptyList(),
)

class BitmapFontWriter {
    @Suppress("ReturnCount")
    fun write(
        targetFile: File,
        document: BitmapFontDocument,
        overwrite: Boolean = false,
        pageFileOverrides: Map<Int, String> = emptyMap(),
    ): BitmapFontWriteResult {
        if (!targetFile.name.endsWith(".fnt", ignoreCase = true)) {
            return failure("Font export target must end with '.fnt'.")
        }
        if (targetFile.exists() && !overwrite) {
            return failure("Font export target already exists and overwrite is disabled.")
        }

        return runCatching {
            targetFile.parentFile?.mkdirs()
            val content = buildFntText(document, pageFileOverrides)
            targetFile.writeText(content, Charsets.UTF_8)
            val normalizedTarget = normalizePath(targetFile.path)
            BitmapFontWriteResult(
                success = true,
                message = "Exported font descriptor to '$normalizedTarget'.",
                writtenPaths = listOf(normalizedTarget),
            )
        }.getOrElse { error ->
            failure("Font export failed: ${error.message ?: "unknown error"}.")
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun buildFntText(
        document: BitmapFontDocument,
        pageFileOverrides: Map<Int, String> = emptyMap(),
    ): String =
        buildString {
            val info = document.info
            if (info != null) {
                append("info")
                info.face?.let { append(" face=\"$it\"") }
                info.size?.let { append(" size=$it") }
                append(" bold=${if (info.bold) 1 else 0}")
                append(" italic=${if (info.italic) 1 else 0}")
                info.charset?.let { append(" charset=\"$it\"") }
                append(" unicode=${if (info.unicode) 1 else 0}")
                info.stretchH?.let { append(" stretchH=$it") }
                append(" smooth=${if (info.smooth) 1 else 0}")
                info.aa?.let { append(" aa=$it") }
                if (info.padding.isNotEmpty()) append(" padding=${info.padding.joinToString(",")}")
                if (info.spacing.isNotEmpty()) append(" spacing=${info.spacing.joinToString(",")}")
                appendLine()
            }

            val common = document.common
            if (common != null) {
                append("common")
                append(" lineHeight=${common.lineHeight}")
                append(" base=${common.base}")
                append(" scaleW=${common.scaleW}")
                append(" scaleH=${common.scaleH}")
                append(" pages=${common.pages}")
                append(" packed=${if (common.packed) 1 else 0}")
                appendLine()
            }

            document.pages.forEach { page ->
                val pageFile = pageFileOverrides[page.id] ?: page.file
                appendLine("page id=${page.id} file=\"$pageFile\"")
            }

            appendLine("chars count=${document.glyphs.size}")
            document.glyphs.forEach { glyph ->
                append("char id=${glyph.id}")
                append(" x=${glyph.x}")
                append(" y=${glyph.y}")
                append(" width=${glyph.width}")
                append(" height=${glyph.height}")
                append(" xoffset=${glyph.xOffset}")
                append(" yoffset=${glyph.yOffset}")
                append(" xadvance=${glyph.xAdvance}")
                append(" page=${glyph.page}")
                append(" chnl=${glyph.channel}")
                glyph.char?.let { append(" letter=\"$it\"") }
                appendLine()
            }

            appendLine("kernings count=${document.kernings.size}")
            document.kernings.forEach { kerning ->
                appendLine("kerning first=${kerning.first} second=${kerning.second} amount=${kerning.amount}")
            }
        }

    private fun failure(message: String): BitmapFontWriteResult = BitmapFontWriteResult(success = false, message = message)
}
