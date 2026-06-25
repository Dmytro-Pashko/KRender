package com.pashkd.krender.engine.tools.textureatlaseditor

enum class TextureAtlasEditorDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

enum class TextureAtlasEditorDiagnosticCategory {
    Input,
    FileSystem,
    Texture,
    Atlas,
    Metadata,
    Preview,
    Font,
}

data class TextureAtlasEditorDiagnostic(
    val severity: TextureAtlasEditorDiagnosticSeverity,
    val category: TextureAtlasEditorDiagnosticCategory,
    val message: String,
    val source: String? = null,
)
