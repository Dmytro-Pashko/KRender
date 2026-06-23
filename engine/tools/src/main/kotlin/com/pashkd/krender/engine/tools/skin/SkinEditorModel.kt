package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.TexturePreviewHandle
import java.io.File

/**
 * Skin Editor state model layers.
 *
 * Loaded/indexed model:
 * [SkinLoadResult], [SkinStyleIndex], [SkinResourceIndex]
 *
 * In-memory edit model:
 * [SkinEditSession], [EditableStyle], [EditableResource], [SkinEditChange]
 *
 * Preview state:
 * [SkinPreviewSettings], [SkinResourceVisualPreviewState]
 *
 * ImGui panels are view/controllers only. GDX adapters are rendering-only.
 * Future JSON writing must stay independent from panels, preview adapters, UI
 * buffers, and [TexturePreviewHandle].
 */

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
    var showOnlyReferenced: Boolean = true,
)

data class SkinProblemFilterState(
    var query: String = "",
    var severity: SkinProblemSeverity? = null,
    var category: SkinProblemCategory? = null,
    var showInfo: Boolean = true,
    var showWarnings: Boolean = true,
    var showErrors: Boolean = true,
)

enum class SkinResourceVisualPreviewZoomMode {
    Fit,
    Percent50,
    Percent100,
    Percent200,
    Custom,
}

/**
 * Atlas-only overlay toggles used by the ImGui resource preview viewport.
 *
 * This state is inspection/UI-only and must not participate in persistence,
 * diff generation, or the future JSON writer pipeline.
 */
data class SkinAtlasPreviewVisualOptions(
    var showCheckerboard: Boolean = true,
    var showGrid: Boolean = false,
    var gridSize: Int = 32,
    var showAllRegionBounds: Boolean = false,
    var showHoverHighlight: Boolean = true,
)

/**
 * Interactive viewport state for texture/atlas previews shown in ImGui panels.
 *
 * Pan/zoom here applies only to the resource preview viewport. It is separate
 * from Scene2D preview camera state used by the Preview Canvas.
 */
data class SkinResourcePreviewViewportState(
    var panX: Float = 0f,
    var panY: Float = 0f,
    var zoom: Float = 1f,
    var clickSelectRegionEnabled: Boolean = true,
    var contentKey: String? = null,
    var atlasVisuals: SkinAtlasPreviewVisualOptions = SkinAtlasPreviewVisualOptions(),
)

data class SkinFontPreviewState(
    var sampleText: String = DefaultFontPreviewSampleText,
    var showCyrillicSample: Boolean = true,
    var showAsciiSample: Boolean = true,
    var fontScale: Float = 1f,
)

enum class SkinResourceVisualPreviewKind {
    None,
    Texture,
    Font,
    Color,
}

/**
 * Resource preview UI state for the Resources panel.
 *
 * This model tracks only preview selection, viewport interaction, and
 * lightweight toggles for inspection. It is not part of the loaded skin model
 * and must not be consumed by the writer.
 */
data class SkinResourceVisualPreviewState(
    var zoomMode: SkinResourceVisualPreviewZoomMode = SkinResourceVisualPreviewZoomMode.Fit,
    var showRegionBounds: Boolean = true,
    var selectedAtlasRegionName: String? = null,
    var viewport: SkinResourcePreviewViewportState = SkinResourcePreviewViewportState(),
    var fontPreview: SkinFontPreviewState = SkinFontPreviewState(),
)

/**
 * Opaque result of one resource preview refresh.
 *
 * [texturePreviewHandle] is an engine UI handle for ImGui rendering only. It
 * intentionally carries no ownership or LibGDX object for the editor model and
 * must never be used by persistence or JSON writer code.
 */
data class SkinResourceVisualPreviewInfo(
    val statusMessage: String = "Select a texture, atlas, atlas region, font, or color.",
    val kind: SkinResourceVisualPreviewKind = SkinResourceVisualPreviewKind.None,
    val resolvedTexturePath: String? = null,
    /**
     * Engine-level opaque UI texture reference used by inline ImGui previews.
     *
     * This is safe in neutral tool state because it carries no LibGDX object or
     * ownership; creation and disposal remain inside the GDX preview adapter.
     */
    val texturePreviewHandle: TexturePreviewHandle? = null,
    val textureWidth: Int = 0,
    val textureHeight: Int = 0,
    val atlasPageName: String? = null,
    val selectedRegionName: String? = null,
    val resolvedFontPath: String? = null,
    val fontPreviewSource: String? = null,
    val colorValue: String? = null,
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
    Color,
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
    val styleKey: StyleKey? = null,
    val resourceKey: SkinResourceKey? = null,
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

/**
 * Preview-only widget categories used to build temporary Scene2D preview
 * layouts. These kinds are not part of skin serialization or the writer model.
 */
enum class PreviewWidgetKind {
    Column,
    Window,
    Label,
    Button,
    TextButton,
    CheckBox,
    TextField,
    SelectBox,
    List,
    ScrollPane,
    SplitPane,
    Slider,
    ProgressBar,
    Tree,
    TextTooltip,
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

/** Camera transform for the Scene2D Preview Canvas only. */
data class SkinPreviewCameraState(
    var panX: Float = 0f,
    var panY: Float = 0f,
    var zoom: Float = 1f,
)

data class SkinPreviewInteractionState(
    var inputEnabled: Boolean = true,
    var hoveredActorPath: String? = null,
    var focusedActorPath: String? = null,
    var lastInputStatus: String? = null,
    var cursorCanvasX: Float? = null,
    var cursorCanvasY: Float? = null,
    var cursorStageX: Float? = null,
    var cursorStageY: Float? = null,
)

enum class SkinPreviewPointerEventType {
    Move,
    Down,
    Drag,
    Up,
    Scroll,
}

enum class SkinPreviewPointerButton {
    Left,
    Right,
    Middle,
}

data class SkinPreviewPointerEvent(
    val type: SkinPreviewPointerEventType,
    val screenX: Float,
    val screenY: Float,
    val button: SkinPreviewPointerButton,
    val pointer: Int = 0,
    val scrollAmountY: Float = 0f,
)

data class SkinPreviewInteractionFeedback(
    val hoveredActorPath: String? = null,
    val focusedActorPath: String? = null,
    val lastInputStatus: String? = null,
    val cursorCanvasX: Float? = null,
    val cursorCanvasY: Float? = null,
    val cursorStageX: Float? = null,
    val cursorStageY: Float? = null,
)

/**
 * Scene2D Preview Canvas UI state.
 *
 * This is separate from resource preview viewport state and exists only to
 * control preview rendering, diagnostics visibility, and text samples.
 */
data class SkinPreviewSettings(
    var screenPresetId: String = SkinPreviewScreenPresets.DefaultId,
    var scale: Float = 1f,
    var showCheckerboard: Boolean = true,
    var showBounds: Boolean = false,
    var highlightSelectedStyle: Boolean = true,
    var camera: SkinPreviewCameraState = SkinPreviewCameraState(),
    var interaction: SkinPreviewInteractionState = SkinPreviewInteractionState(),
    var showFallbackWarnings: Boolean = true,
    var text: SkinPreviewTextSettings = SkinPreviewTextSettings(),
)

/** Preview-only sample text configuration for Scene2D widget layouts. */
data class SkinPreviewTextSettings(
    var labelText: String = "KRender Label Preview",
    var buttonText: String = "KRender Button",
    var textFieldPlaceholder: String = "Placeholder text",
)

data class SkinPreviewScreenPreset(
    val id: String,
    val displayName: String,
    val width: Int,
    val height: Int,
)

object SkinPreviewScreenPresets {
    const val DefaultId = "hd"

    val presets: List<SkinPreviewScreenPreset> =
        listOf(
            SkinPreviewScreenPreset(id = "desktop_large", displayName = "1980 x 1020", width = 1980, height = 1020),
            SkinPreviewScreenPreset(id = "tablet", displayName = "1024 x 768", width = 1024, height = 768),
            SkinPreviewScreenPreset(id = DefaultId, displayName = "1280 x 720", width = 1280, height = 720),
        )

    fun presetOrDefault(id: String?): SkinPreviewScreenPreset =
        presets.firstOrNull { preset -> preset.id == id } ?: presets.first { preset -> preset.id == DefaultId }
}

data class SkinEditorState(
    var currentInputPath: String? = null,
    var pendingPathInput: String = "",
    var loadResult: SkinLoadResult = SkinLoadResult(),
    var selectedStyleKey: StyleKey? = null,
    var selectedResourceKey: SkinResourceKey? = null,
    var selectedProblemIndex: Int? = null,
    var editSession: SkinEditSession = SkinEditSession(),
    var selectedEditFieldName: String? = null,
    var resourceBrowser: SkinResourceBrowserState = SkinResourceBrowserState(),
    var problemFilters: SkinProblemFilterState = SkinProblemFilterState(),
    var canvasRect: SkinEditorCanvasRect = SkinEditorCanvasRect(),
    var resourceVisualPreview: SkinResourceVisualPreviewState = SkinResourceVisualPreviewState(),
    var resourceVisualPreviewInfo: SkinResourceVisualPreviewInfo = SkinResourceVisualPreviewInfo(),
    var previewLayoutId: String = DefaultWidgetPreviewLayout.Id,
    var previewSettings: SkinPreviewSettings = SkinPreviewSettings(),
    var previewDirty: Boolean = true,
    var reloadRequested: Boolean = false,
    var pendingStatusAfterReload: String? = null,
    var statusMessage: String = "Skin Editor ready.",
    var previewInfo: SkinEditorPreviewStageInfo = SkinEditorPreviewStageInfo(),
    val pendingPreviewPointerEvents: MutableList<SkinPreviewPointerEvent> = mutableListOf(),
)

const val DefaultFontPreviewSampleText =
    "KRender Font Preview\n" +
        "The quick brown fox jumps over the lazy dog.\n" +
        "0123456789 !@#$%^&*()"
