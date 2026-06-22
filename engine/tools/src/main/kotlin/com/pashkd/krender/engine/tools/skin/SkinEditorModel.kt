package com.pashkd.krender.engine.tools.skin

import java.io.File

data class SkinProject(
    val rootDirectory: File,
    val skinFile: File? = null,
    val descriptorCandidates: List<File> = emptyList(),
    val descriptorResolutionMessage: String? = null,
    val atlasFiles: List<File> = emptyList(),
    val textureFiles: List<File> = emptyList(),
    val fontFiles: List<File> = emptyList(),
)

data class ResourceReference(
    val name: String,
    val category: SkinResourceCategory? = null,
    val source: String? = null,
    val resolved: Boolean = false,
)

enum class SkinResourceCategory {
    Color,
    Font,
    Drawable,
    AtlasRegion,
    Texture,
    Atlas,
    Unknown,
}

data class SkinResourceKey(
    val category: SkinResourceCategory,
    val name: String,
)

data class SkinResourceInfo(
    val name: String,
    val category: SkinResourceCategory,
    val type: String,
    val source: String? = null,
    val referencedBy: List<String> = emptyList(),
    val resolved: Boolean = true,
    val details: Map<String, String> = emptyMap(),
) {
    val key: SkinResourceKey = SkinResourceKey(category = category, name = name)
}

data class SkinResourceIndex(
    val colors: List<SkinResourceInfo> = emptyList(),
    val fonts: List<SkinResourceInfo> = emptyList(),
    val drawables: List<SkinResourceInfo> = emptyList(),
    val atlasRegions: List<SkinResourceInfo> = emptyList(),
    val textures: List<SkinResourceInfo> = emptyList(),
    val atlasFiles: List<SkinResourceInfo> = emptyList(),
    val unknownReferences: List<SkinResourceInfo> = emptyList(),
) {
    val resources: List<SkinResourceInfo>
        get() = colors + fonts + drawables + atlasRegions + textures + atlasFiles + unknownReferences

    fun resourcesFor(category: SkinResourceCategory): List<SkinResourceInfo> =
        when (category) {
            SkinResourceCategory.Color -> colors
            SkinResourceCategory.Font -> fonts
            SkinResourceCategory.Drawable -> drawables
            SkinResourceCategory.AtlasRegion -> atlasRegions
            SkinResourceCategory.Texture -> textures
            SkinResourceCategory.Atlas -> atlasFiles
            SkinResourceCategory.Unknown -> unknownReferences
        }

    fun resolves(reference: ResourceReference): Boolean =
        when (reference.category) {
            SkinResourceCategory.Drawable ->
                sequenceOf(drawables, atlasRegions, textures)
                    .flatten()
                    .any { resource -> resource.name == reference.name && resource.resolved }

            null,
            SkinResourceCategory.Unknown,
            -> resources.any { resource -> resource.name == reference.name && resource.resolved }

            else -> resourcesFor(reference.category).any { resource -> resource.name == reference.name && resource.resolved }
        }
}

data class SkinResourceBrowserState(
    var query: String = "",
    var selectedCategory: SkinResourceCategory? = null,
    var showOnlyUnresolved: Boolean = false,
    var showOnlyReferenced: Boolean = false,
)

enum class SkinResourceVisualPreviewZoomMode {
    Fit,
    Percent50,
    Percent100,
    Percent200,
}

data class SkinResourceVisualPreviewState(
    var zoomMode: SkinResourceVisualPreviewZoomMode = SkinResourceVisualPreviewZoomMode.Fit,
    var showRegionBounds: Boolean = true,
    var showRegionLabels: Boolean = false,
    var selectedAtlasRegionName: String? = null,
)

data class SkinResourceVisualPreviewInfo(
    val statusMessage: String = "Select a texture, atlas, or atlas region.",
    val resolvedTexturePath: String? = null,
    val textureWidth: Int = 0,
    val textureHeight: Int = 0,
    val atlasPageName: String? = null,
    val selectedRegionName: String? = null,
)

data class StyleFieldInfo(
    val name: String,
    val valueType: String,
    val rawValue: String? = null,
    val reference: ResourceReference? = null,
)

data class StyleKey(
    val type: String,
    val name: String,
)

data class StyleInfo(
    val name: String,
    val type: String,
    val fields: List<StyleFieldInfo> = emptyList(),
    val resourceReferences: List<ResourceReference> = emptyList(),
    val rawFieldCount: Int = fields.size,
) {
    val key: StyleKey = StyleKey(type = type, name = name)
    val displayName: String = "$type.$name"
}

data class SkinStyleIndex(
    val styles: List<StyleInfo> = emptyList(),
)

enum class SkinProblemSeverity {
    Info,
    Warning,
    Error,
}

enum class SkinProblemCategory {
    Project,
    Loading,
    Atlas,
    Font,
    Drawable,
    Style,
    Resource,
    Preview,
}

data class SkinProblem(
    val severity: SkinProblemSeverity,
    val category: SkinProblemCategory,
    val message: String,
    val source: String? = null,
    val suggestedFix: String? = null,
)

data class SkinLoadResult(
    val project: SkinProject? = null,
    val resourceIndex: SkinResourceIndex = SkinResourceIndex(),
    val styleIndex: SkinStyleIndex = SkinStyleIndex(),
    val problems: List<SkinProblem> = emptyList(),
    val previewSkinAvailable: Boolean = false,
)

data class SkinEditorCanvasRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isValid: Boolean get() = width > 1f && height > 1f
}

data class SkinEditorPreviewItem(
    val key: String,
    val label: String,
    val kind: PreviewWidgetKind,
    val styleName: String? = null,
    val text: String? = null,
    val items: List<String> = emptyList(),
    val children: List<SkinEditorPreviewItem> = emptyList(),
)

enum class PreviewWidgetKind {
    Column,
    Window,
    Label,
    TextButton,
    CheckBox,
    TextField,
    SelectBox,
    List,
    ScrollPane,
    Slider,
    ProgressBar,
}

data class SkinEditorPreviewStageInfo(
    val actorCount: Int = 0,
    val rootActorClass: String? = null,
    val layoutId: String? = null,
    val logicalWidth: Int = 0,
    val logicalHeight: Int = 0,
    val scale: Float = 1f,
    val fallbackIssueCount: Int = 0,
)

data class SkinPreviewSettings(
    var screenPresetId: String = SkinPreviewScreenPresets.DesktopId,
    var scale: Float = 1f,
    var showBounds: Boolean = false,
    var showFallbackWarnings: Boolean = true,
    var selectedOnly: Boolean = false,
)

data class SkinPreviewScreenPreset(
    val id: String,
    val displayName: String,
    val width: Int,
    val height: Int,
)

object SkinPreviewScreenPresets {
    const val DesktopId = "desktop"

    val presets: List<SkinPreviewScreenPreset> =
        listOf(
            SkinPreviewScreenPreset(id = "compact", displayName = "Compact", width = 640, height = 480),
            SkinPreviewScreenPreset(id = DesktopId, displayName = "Desktop", width = 1280, height = 720),
            SkinPreviewScreenPreset(id = "wide", displayName = "Wide", width = 1600, height = 900),
            SkinPreviewScreenPreset(id = "mobile", displayName = "Mobile", width = 390, height = 844),
            SkinPreviewScreenPreset(id = "tablet", displayName = "Tablet", width = 1024, height = 768),
        )

    fun presetOrDefault(id: String?): SkinPreviewScreenPreset =
        presets.firstOrNull { preset -> preset.id == id } ?: presets.first { preset -> preset.id == DesktopId }
}

data class SkinEditorState(
    var currentInputPath: String? = null,
    var pendingPathInput: String = "",
    var loadResult: SkinLoadResult = SkinLoadResult(),
    var selectedStyleKey: StyleKey? = null,
    var selectedResourceKey: SkinResourceKey? = null,
    var selectedProblemIndex: Int? = null,
    var resourceBrowser: SkinResourceBrowserState = SkinResourceBrowserState(),
    var canvasRect: SkinEditorCanvasRect = SkinEditorCanvasRect(),
    var resourcePreviewCanvasRect: SkinEditorCanvasRect = SkinEditorCanvasRect(),
    var resourceVisualPreview: SkinResourceVisualPreviewState = SkinResourceVisualPreviewState(),
    var resourceVisualPreviewInfo: SkinResourceVisualPreviewInfo = SkinResourceVisualPreviewInfo(),
    var previewLayoutId: String = DefaultWidgetPreviewLayout.Id,
    var previewSettings: SkinPreviewSettings = SkinPreviewSettings(),
    var previewDirty: Boolean = true,
    var reloadRequested: Boolean = false,
    var statusMessage: String = "Skin Editor ready.",
    var previewInfo: SkinEditorPreviewStageInfo = SkinEditorPreviewStageInfo(),
)
