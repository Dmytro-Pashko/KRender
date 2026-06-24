package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.TexturePreviewHandle
import java.io.File

enum class TextureAtlasEditorAssetKind {
    Texture,
    Atlas,
    Directory,
    Unknown,
}

enum class TexturePreviewZoomMode {
    Fit,
    Percent50,
    Percent100,
    Percent200,
    Custom,
}

enum class TextureAtlasRegionSortMode {
    Name,
    Index,
    AreaAscending,
    AreaDescending,
}

enum class TextureAtlasCanvasMode {
    TextureAtlas,
    NinePatch,
    FontPreview,
    FinalPackedAtlas,
}

data class TextureAtlasEditorColor(
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
    val alpha: Float = 1f,
)

data class TextureAtlasEditorTextureInfo(
    val width: Int? = null,
    val height: Int? = null,
    val hasAlpha: Boolean? = null,
    val colorFormat: String? = null,
)

data class TextureAtlasEditorAssetDescriptor(
    val id: TextureAssetId,
    val path: String,
    val displayName: String,
    val kind: TextureAtlasEditorAssetKind,
    val extension: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val metadataPath: String? = null,
    val textureInfo: TextureAtlasEditorTextureInfo? = null,
    val registryMetadata: Map<String, String> = emptyMap(),
) {
    val fileName: String = File(path).name
}

data class TextureAtlasEditorProject(
    val inputPath: String? = null,
    val resolvedInputPath: String? = null,
    val rootDirectory: File? = null,
    val selectedTexturePath: String? = null,
    val selectedAtlasPath: String? = null,
    val discoveredTextureFiles: List<File> = emptyList(),
    val discoveredAtlasFiles: List<File> = emptyList(),
    val discoveredMetadataFiles: List<File> = emptyList(),
    val discoveredFontFiles: List<File> = emptyList(),
    val assets: List<TextureAtlasEditorAssetDescriptor> = emptyList(),
    val atlasDocuments: Map<String, TextureAtlasDocument> = emptyMap(),
    val ninePatchDocuments: Map<String, NinePatchDocument> = emptyMap(),
    val fontDocuments: Map<String, BitmapFontDocument> = emptyMap(),
)

data class TextureAtlasDocument(
    val file: File,
    val pages: List<TextureAtlasPage> = emptyList(),
    val regions: List<TextureAtlasRegion> = emptyList(),
    val diagnostics: List<TextureAtlasEditorDiagnostic> = emptyList(),
    val readable: Boolean = true,
)

data class TextureAtlasPage(
    val name: String,
    val details: Map<String, String> = emptyMap(),
)

data class TextureAtlasRegion(
    val id: AtlasRegionId,
    val rotate: String? = null,
    val xy: Pair<Int, Int>? = null,
    val size: Pair<Int, Int>? = null,
    val orig: Pair<Int, Int>? = null,
    val offset: Pair<Int, Int>? = null,
    val split: List<Int> = emptyList(),
    val pad: List<Int> = emptyList(),
    val index: Int? = null,
    val details: Map<String, String> = emptyMap(),
)

data class TextureAtlasEditorPreviewViewportState(
    var panX: Float = 0f,
    var panY: Float = 0f,
    var zoom: Float = 1f,
)

data class TextureAtlasEditorPreviewState(
    var canvasMode: TextureAtlasCanvasMode = TextureAtlasCanvasMode.TextureAtlas,
    var zoomMode: TexturePreviewZoomMode = TexturePreviewZoomMode.Fit,
    var customZoom: Float = 1f,
    var viewport: TextureAtlasEditorPreviewViewportState = TextureAtlasEditorPreviewViewportState(),
    var showCheckerboard: Boolean = true,
    var showGrid: Boolean = false,
    var gridColor: TextureAtlasEditorColor = TextureAtlasEditorColor(red = 1f, green = 1f, blue = 1f, alpha = 0.19f),
    var showBounds: Boolean = true,
    var showNinePatchGuides: Boolean = true,
)

data class TextureAtlasEditorCanvasRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isValid: Boolean get() = width > 1f && height > 1f
}

data class TextureAtlasEditorPreviewSlice(
    val sourceX: Int,
    val sourceY: Int,
    val width: Int,
    val height: Int,
)

data class TextureAtlasEditorPreviewInfo(
    val resolvedTexturePath: String? = null,
    val atlasPageName: String? = null,
    val texturePreviewHandle: TexturePreviewHandle? = null,
    val textureWidth: Int = 0,
    val textureHeight: Int = 0,
    val statusMessage: String = "Select a texture or atlas page to preview.",
)

data class TextureAtlasEditorAssetBrowserState(
    var query: String = "",
)

data class TextureAtlasEditorAtlasBrowserState(
    var query: String = "",
    var sortMode: TextureAtlasRegionSortMode = TextureAtlasRegionSortMode.Name,
)

data class TextureAtlasEditorDiagnosticsFilterState(
    var query: String = "",
    var showInfo: Boolean = true,
    var showWarnings: Boolean = true,
    var showErrors: Boolean = true,
)

data class TextureAtlasEditorState(
    var currentInputPath: String? = null,
    var pendingPathInput: String = "",
    var project: TextureAtlasEditorProject = TextureAtlasEditorProject(),
    var diagnostics: List<TextureAtlasEditorDiagnostic> = emptyList(),
    var selectedAssetId: TextureAssetId? = null,
    var selectedAtlasPageName: String? = null,
    var selectedRegionId: AtlasRegionId? = null,
    var hoveredRegionId: AtlasRegionId? = null,
    var resources: TextureAtlasResourceState = TextureAtlasResourceState(),
    var preview: TextureAtlasEditorPreviewState = TextureAtlasEditorPreviewState(),
    var previewInfo: TextureAtlasEditorPreviewInfo = TextureAtlasEditorPreviewInfo(),
    var canvasRect: TextureAtlasEditorCanvasRect = TextureAtlasEditorCanvasRect(),
    var assetBrowser: TextureAtlasEditorAssetBrowserState = TextureAtlasEditorAssetBrowserState(),
    var atlasBrowser: TextureAtlasEditorAtlasBrowserState = TextureAtlasEditorAtlasBrowserState(),
    var packing: TextureAtlasPackingState = TextureAtlasPackingState(),
    var importExport: TextureAtlasEditorImportExportState = TextureAtlasEditorImportExportState(),
    var diagnosticsFilter: TextureAtlasEditorDiagnosticsFilterState = TextureAtlasEditorDiagnosticsFilterState(),
    var ninePatchEditor: NinePatchEditorState = NinePatchEditorState(),
    var fontPreview: FontPreviewState = FontPreviewState(),
    var statusMessage: String = "Texture Atlas Editor ready.",
    var reloadRequested: Boolean = false,
)

internal fun TextureAtlasEditorState.clearPreviewSelection() {
    selectedAssetId = null
    selectedAtlasPageName = null
    selectedRegionId = null
    hoveredRegionId = null
    resources.selectedResourceId = null
    previewInfo = TextureAtlasEditorPreviewInfo()
}
