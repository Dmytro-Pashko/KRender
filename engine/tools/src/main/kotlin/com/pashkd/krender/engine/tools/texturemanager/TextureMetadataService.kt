package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.assets.TextureMetadataReader
import java.io.File

class TextureMetadataService {
    fun read(file: File): TextureManagerTextureInfo? {
        val metadata = TextureMetadataReader.read(file) ?: return null
        return TextureManagerTextureInfo(
            width = metadata.width,
            height = metadata.height,
            hasAlpha = metadata.hasAlpha,
            colorFormat = metadata.colorFormat,
        )
    }
}

