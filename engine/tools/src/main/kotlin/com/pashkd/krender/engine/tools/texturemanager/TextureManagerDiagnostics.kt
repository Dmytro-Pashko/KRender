package com.pashkd.krender.engine.tools.texturemanager

enum class TextureManagerDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

enum class TextureManagerDiagnosticCategory {
    Input,
    FileSystem,
    Texture,
    Atlas,
    Metadata,
    Preview,
}

data class TextureManagerDiagnostic(
    val severity: TextureManagerDiagnosticSeverity,
    val category: TextureManagerDiagnosticCategory,
    val message: String,
    val source: String? = null,
)

