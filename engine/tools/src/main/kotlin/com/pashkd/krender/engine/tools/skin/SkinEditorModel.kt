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
    val type: String,
    val source: String? = null,
)

data class SkinResourceIndex(
    val resources: List<ResourceReference> = emptyList(),
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
) {
    val key: StyleKey = StyleKey(type = type, name = name)
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
)

data class SkinEditorState(
    var currentInputPath: String? = null,
    var pendingPathInput: String = "",
    var loadResult: SkinLoadResult = SkinLoadResult(),
    var selectedStyleKey: StyleKey? = null,
    var selectedResourceName: String? = null,
    var selectedProblemIndex: Int? = null,
    var canvasRect: SkinEditorCanvasRect = SkinEditorCanvasRect(),
    var previewLayoutId: String = DefaultWidgetPreviewLayout.Id,
    var previewDirty: Boolean = true,
    var reloadRequested: Boolean = false,
    var statusMessage: String = "Skin Editor ready.",
    var previewInfo: SkinEditorPreviewStageInfo = SkinEditorPreviewStageInfo(),
)
