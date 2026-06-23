package com.pashkd.krender.engine.tools.texturemanager

data class TextureManagerFileWriteResult(
    val success: Boolean,
    val message: String,
    val writtenPaths: List<String> = emptyList(),
)

data class TextureManagerImportExportState(
    var importSourcePath: String = "",
    var importTargetDirectory: String = "textures",
    var importOverwrite: Boolean = false,
    var exportDirectory: String = "atlases",
    var exportBaseName: String = "packed",
    var exportOverwrite: Boolean = false,
    var lastImportResult: TextureManagerFileWriteResult? = null,
    var lastExportResult: TextureManagerFileWriteResult? = null,
)
