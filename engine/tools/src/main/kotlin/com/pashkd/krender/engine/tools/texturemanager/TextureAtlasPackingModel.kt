package com.pashkd.krender.engine.tools.texturemanager

data class TextureAtlasPackingSettings(
    var maxPageWidth: Int = 2048,
    var maxPageHeight: Int = 2048,
    var padding: Int = 2,
    var extrude: Int = 0,
    var allowRotation: Boolean = false,
    var trimWhitespace: Boolean = false,
    var includeNinePatch: Boolean = false,
)

data class TextureAtlasPackingInput(
    val sourcePath: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val isNinePatch: Boolean = false,
)

data class TextureAtlasPackingRegion(
    val sourcePath: String,
    val displayName: String,
    val pageIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val rotated: Boolean = false,
    val padding: Int = 0,
)

data class TextureAtlasPackingPage(
    val index: Int,
    val width: Int,
    val height: Int,
    val regions: List<TextureAtlasPackingRegion> = emptyList(),
) {
    val name: String = "page_${index + 1}"
}

data class TextureAtlasPackingDiagnostic(
    val severity: TextureManagerDiagnosticSeverity,
    val message: String,
    val sourcePath: String? = null,
)

data class TextureAtlasPackingPlan(
    val settings: TextureAtlasPackingSettings,
    val inputCount: Int,
    val packedRegionCount: Int,
    val skippedCount: Int,
    val pages: List<TextureAtlasPackingPage> = emptyList(),
)

data class TextureAtlasPackingResult(
    val plan: TextureAtlasPackingPlan? = null,
    val diagnostics: List<TextureAtlasPackingDiagnostic> = emptyList(),
)

data class TextureAtlasPackingState(
    var settings: TextureAtlasPackingSettings = TextureAtlasPackingSettings(),
    var includedTexturePaths: Set<String> = emptySet(),
    var lastResult: TextureAtlasPackingResult = TextureAtlasPackingResult(),
    var selectedPageIndex: Int = 0,
    var selectedRegionSourcePath: String? = null,
)
