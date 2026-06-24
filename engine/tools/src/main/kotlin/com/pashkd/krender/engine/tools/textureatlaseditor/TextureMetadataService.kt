package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.assets.TextureMetadataReader
import java.io.File

class TextureMetadataService {
    fun read(file: File): TextureAtlasEditorTextureInfo? {
        val metadata = TextureMetadataReader.read(file) ?: return null
        return TextureAtlasEditorTextureInfo(
            width = metadata.width,
            height = metadata.height,
            hasAlpha = metadata.hasAlpha,
            colorFormat = metadata.colorFormat,
        )
    }
}

