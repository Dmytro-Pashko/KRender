package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.TexturePreviewHandle
import java.io.File

enum class TextureManagerAssetKind {
    Texture,
    Atlas,
    Directory,
    Unknown,
}

enum class TextureManagerToolMode {
    Inspect,
    SelectRegion,
    Pan,
    Zoom,
    MoveRegion,
    ResizeRegion,
    NinePatch,
    CreateRegion,
}

enum class TexturePreviewZoomMode {
    Fit,
    Percent50,
    Percent100,
    Percent200,
    Custom,
}

data class TextureManagerTextureInfo(
    val width: Int? = null,
    val height: Int? = null,
    val hasAlpha: Boolean? = null,
    val colorFormat: String? = null,
)

data class TextureManagerAssetDescriptor(
    val id: TextureAssetId,
    val path: String,
    val displayName: String,
    val kind: TextureManagerAssetKind,
    val extension: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val metadataPath: String? = null,
    val textureInfo: TextureManagerTextureInfo? = null,
    val registryMetadata: Map<String, String> = emptyMap(),
) {
    val fileName: String = File(path).name
}

data class TextureManagerProject(
    val inputPath: String? = null,
    val resolvedInputPath: String? = null,
    val rootDirectory: File? = null,
    val selectedTexturePath: String? = null,
    val selectedAtlasPath: String? = null,
    val discoveredTextureFiles: List<File> = emptyList(),
    val discoveredAtlasFiles: List<File> = emptyList(),
    val discoveredMetadataFiles: List<File> = emptyList(),
    val assets: List<TextureManagerAssetDescriptor> = emptyList(),
    val atlasDocuments: Map<String, TextureAtlasDocument> = emptyMap(),
)

data class TextureAtlasDocument(
    val file: File,
    val pages: List<TextureAtlasPage> = emptyList(),
    val regions: List<TextureAtlasRegion> = emptyList(),
    val diagnostics: List<TextureManagerDiagnostic> = emptyList(),
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

data class TextureManagerPreviewViewportState(
    var panX: Float = 0f,
    var panY: Float = 0f,
    var zoom: Float = 1f,
)

data class TextureManagerPreviewState(
    var zoomMode: TexturePreviewZoomMode = TexturePreviewZoomMode.Fit,
    var customZoom: Float = 1f,
    var viewport: TextureManagerPreviewViewportState = TextureManagerPreviewViewportState(),
    var showCheckerboard: Boolean = true,
    var showGrid: Boolean = false,
    var showBounds: Boolean = true,
)

data class TextureManagerCanvasRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isValid: Boolean get() = width > 1f && height > 1f
}

data class TextureManagerPreviewInfo(
    val resolvedTexturePath: String? = null,
    val atlasPageName: String? = null,
    val texturePreviewHandle: TexturePreviewHandle? = null,
    val textureWidth: Int = 0,
    val textureHeight: Int = 0,
    val statusMessage: String = "Select a texture or atlas page to preview.",
)

data class TextureManagerAssetBrowserState(
    var query: String = "",
)

data class TextureManagerAtlasBrowserState(
    var query: String = "",
)

data class TextureManagerDiagnosticsFilterState(
    var query: String = "",
    var showInfo: Boolean = true,
    var showWarnings: Boolean = true,
    var showErrors: Boolean = true,
)

data class TextureManagerState(
    var currentInputPath: String? = null,
    var pendingPathInput: String = "",
    var project: TextureManagerProject = TextureManagerProject(),
    var diagnostics: List<TextureManagerDiagnostic> = emptyList(),
    var selectedAssetId: TextureAssetId? = null,
    var selectedAtlasPageName: String? = null,
    var selectedRegionId: AtlasRegionId? = null,
    var hoveredRegionId: AtlasRegionId? = null,
    var toolMode: TextureManagerToolMode = TextureManagerToolMode.Inspect,
    var preview: TextureManagerPreviewState = TextureManagerPreviewState(),
    var previewInfo: TextureManagerPreviewInfo = TextureManagerPreviewInfo(),
    var canvasRect: TextureManagerCanvasRect = TextureManagerCanvasRect(),
    var assetBrowser: TextureManagerAssetBrowserState = TextureManagerAssetBrowserState(),
    var atlasBrowser: TextureManagerAtlasBrowserState = TextureManagerAtlasBrowserState(),
    var diagnosticsFilter: TextureManagerDiagnosticsFilterState = TextureManagerDiagnosticsFilterState(),
    var statusMessage: String = "Texture Manager ready.",
    var reloadRequested: Boolean = false,
)

internal fun TextureManagerState.clearPreviewSelection() {
    selectedAssetId = null
    selectedAtlasPageName = null
    selectedRegionId = null
    hoveredRegionId = null
    previewInfo = TextureManagerPreviewInfo()
}
