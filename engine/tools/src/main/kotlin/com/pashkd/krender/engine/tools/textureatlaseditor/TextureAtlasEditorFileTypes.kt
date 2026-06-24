package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File

internal val TextureAtlasEditorSupportedTextureExtensions = setOf("png", "jpg", "jpeg", "ktx", "webp")
internal val TextureAtlasEditorImportTextureExtensions = setOf("png", "bmp", "jpg", "jpeg", "ktx", "webp")

internal fun isSupportedTextureExtension(extension: String): Boolean = extension.lowercase() in TextureAtlasEditorSupportedTextureExtensions

internal fun isSupportedTextureFile(file: File): Boolean = file.isFile && isSupportedTextureExtension(file.extension)

internal fun isSupportedTextureImportExtension(extension: String): Boolean = extension.lowercase() in TextureAtlasEditorImportTextureExtensions

internal fun isSupportedTextureImportFile(file: File): Boolean = file.isFile && isSupportedTextureImportExtension(file.extension)
