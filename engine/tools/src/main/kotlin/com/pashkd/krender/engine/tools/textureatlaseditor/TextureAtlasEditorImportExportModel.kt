package com.pashkd.krender.engine.tools.textureatlaseditor

data class TextureAtlasEditorFileWriteResult(
    val success: Boolean,
    val message: String,
    val writtenPaths: List<String> = emptyList(),
)

data class TextureAtlasEditorImportExportState(
    var importSourcePath: String = "",
    var targetPath: String = "",
    var importOverwrite: Boolean = false,
    var saveOverwrite: Boolean = false,
    var lastImportResult: TextureAtlasEditorFileWriteResult? = null,
    var lastExportResult: TextureAtlasEditorFileWriteResult? = null,
)
