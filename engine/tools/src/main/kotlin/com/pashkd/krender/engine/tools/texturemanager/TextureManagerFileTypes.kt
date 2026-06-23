package com.pashkd.krender.engine.tools.texturemanager

import java.io.File

internal val TextureManagerSupportedTextureExtensions = setOf("png", "jpg", "jpeg", "ktx", "webp")

internal fun isSupportedTextureExtension(extension: String): Boolean = extension.lowercase() in TextureManagerSupportedTextureExtensions

internal fun isSupportedTextureFile(file: File): Boolean = file.isFile && isSupportedTextureExtension(file.extension)
