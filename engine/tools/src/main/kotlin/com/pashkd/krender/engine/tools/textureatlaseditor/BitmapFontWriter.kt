package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File

class BitmapFontWriter {
    fun write(
        assetRoot: File,
        targetPath: String,
        document: BitmapFontDocument,
        overwrite: Boolean,
        pageFileOverrides: Map<Int, String> = emptyMap(),
        glyphPositionOverrides: Map<Int, GlyphPositionOverride> = emptyMap(),
    ): TextureAtlasEditorFileWriteResult {
        val targetFile = TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, targetPath)
            ?: return failure("Font export target path must stay inside the asset root.")
        if (!targetFile.name.endsWith(".fnt", ignoreCase = true)) {
            return failure("Font export target must end with '.fnt'.")
        }
        if (targetFile.exists() && !overwrite) {
            return failure("Font export target already exists and overwrite is disabled.")
        }

        return runCatching {
            targetFile.parentFile?.mkdirs()
            val content = buildFntText(document, pageFileOverrides, glyphPositionOverrides)
            targetFile.writeText(content, Charsets.UTF_8)
            val normalizedTarget = normalizePath(targetFile.path)
            TextureAtlasEditorFileWriteResult(
                success = true,
                message = "Exported font descriptor to '$normalizedTarget'.",
                writtenPaths = listOf(normalizedTarget),
            )
        }.getOrElse { error ->
            failure("Font export failed: ${error.message ?: "unknown error"}.")
        }
    }

    private fun buildFntText(
        document: BitmapFontDocument,
        pageFileOverrides: Map<Int, String>,
        glyphPositionOverrides: Map<Int, GlyphPositionOverride>,
    ): String = buildString {
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
            val override = glyphPositionOverrides[glyph.id]
            val x = override?.x ?: glyph.x
            val y = override?.y ?: glyph.y
            val width = override?.width ?: glyph.width
            val height = override?.height ?: glyph.height
            val page = override?.page ?: glyph.page
            append("char id=${glyph.id}")
            append(" x=$x")
            append(" y=$y")
            append(" width=$width")
            append(" height=$height")
            append(" xoffset=${glyph.xOffset}")
            append(" yoffset=${glyph.yOffset}")
            append(" xadvance=${glyph.xAdvance}")
            append(" page=$page")
            append(" chnl=${glyph.channel}")
            glyph.char?.let { append(" letter=\"$it\"") }
            appendLine()
        }

        if (document.kernings.isNotEmpty()) {
            appendLine("kernings count=${document.kernings.size}")
            document.kernings.forEach { kerning ->
                appendLine("kerning first=${kerning.first} second=${kerning.second} amount=${kerning.amount}")
            }
        }
    }

    private fun failure(message: String): TextureAtlasEditorFileWriteResult =
        TextureAtlasEditorFileWriteResult(success = false, message = message)
}

data class GlyphPositionOverride(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val page: Int,
)
