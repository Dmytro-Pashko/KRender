package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.ui.scene.UiSceneBindings
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer

data class PreviewLayoutContext(
    val loadResult: SkinLoadResult,
    val selectedStyleKey: StyleKey? = null,
    val selectedResourceName: String? = null,
    val text: SkinPreviewTextSettings = SkinPreviewTextSettings(),
)

interface PreviewLayout {
    val id: String
    val displayName: String
    val issues: List<String>
        get() = emptyList()

    fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem
}

class PreviewLayoutRegistry(
    sceneFiles: SceneFileService? = null,
    serializer: UiSceneSerializer = UiSceneSerializer(),
    fallbackLayouts: List<PreviewLayout> = fallbackPreviewLayouts(),
) {
    private val fallbackById = fallbackLayouts.associateBy { it.id }
    private val layoutsById =
        if (sceneFiles == null) {
            fallbackById
        } else {
            DefaultAssetPreviewLayouts
                .map { spec -> AssetBackedPreviewLayout(spec, sceneFiles, serializer, fallbackById.getValue(spec.fallbackId)) }
                .associateBy { it.id }
        }
    val layouts: List<PreviewLayout> = layoutsById.values.toList()

    fun layoutOrDefault(id: String?): PreviewLayout = id?.let(layoutsById::get) ?: layouts.first()

    companion object {
        fun fallbackPreviewLayouts(): List<PreviewLayout> =
            listOf(
                DefaultWidgetPreviewLayout(),
                TablesPreviewLayout(),
                StressPreviewLayout(),
                FormsPreviewLayout(),
                DialogsPreviewLayout(),
                SelectedStylePreviewLayout(),
            )
    }
}

private data class PreviewAssetSpec(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val fallbackId: String,
)

private val DefaultAssetPreviewLayouts =
    listOf(
        PreviewAssetSpec("all", "All Widgets", "ui/scenes/skin-preview/all.krui", DefaultWidgetPreviewLayout.Id),
        PreviewAssetSpec("table", "Table", "ui/scenes/skin-preview/table.krui", TablesPreviewLayout.Id),
        PreviewAssetSpec("list", "List", "ui/scenes/skin-preview/list.krui", StressPreviewLayout.Id),
        PreviewAssetSpec("form", "Form", "ui/scenes/skin-preview/form.krui", FormsPreviewLayout.Id),
        PreviewAssetSpec("dialog", "Dialog", "ui/scenes/skin-preview/dialog.krui", DialogsPreviewLayout.Id),
        PreviewAssetSpec("selected-style", "Selected Style", "ui/scenes/skin-preview/selected-style.krui", SelectedStylePreviewLayout.Id),
    )

private class AssetBackedPreviewLayout(
    private val spec: PreviewAssetSpec,
    private val sceneFiles: SceneFileService,
    private val serializer: UiSceneSerializer,
    private val fallback: PreviewLayout,
) : PreviewLayout {
    override val id: String = spec.id
    override val displayName: String = spec.displayName
    private var cachedDocument: UiSceneDocument? = null
    private var loadIssue: String? = null
    override val issues: List<String>
        get() = loadIssue?.let(::listOf).orEmpty()

    override fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem {
        val document = documentOrNull()
        return if (document == null) {
            fallback.build(context, factory)
        } else {
            val payload = previewPayload(context)
            document.root.toPreviewItem(context, factory, payload)
                ?: factory.column(
                    key = "${id}_empty",
                    label = displayName,
                    children = listOf(factory.label("${id}_empty_label", "No preview widgets available for current selection.")),
                )
        }
    }

    private fun documentOrNull(): UiSceneDocument? {
        cachedDocument?.let { return it }
        return try {
            serializer.decode(sceneFiles.readText(spec.assetPath)).also { document ->
                cachedDocument = document
                loadIssue = null
            }
        } catch (error: Exception) {
            loadIssue = "Preview layout '${spec.assetPath}' is unavailable; using built-in fallback. ${error.message.orEmpty()}".trim()
            null
        }
    }

    private fun previewPayload(context: PreviewLayoutContext): Map<String, String> =
        mapOf(
            "labelText" to context.text.labelText,
            "buttonText" to context.text.buttonText,
            "textFieldPlaceholder" to context.text.textFieldPlaceholder,
            "selectedStyleType" to context.selectedStyleKey?.type.orEmpty(),
            "selectedStyleName" to context.selectedStyleKey?.name.orEmpty(),
            "selectedResourceName" to context.selectedResourceName.orEmpty(),
        )
}

private fun UiSceneNode.toPreviewItem(
    context: PreviewLayoutContext,
    factory: WidgetPreviewFactory,
    payload: Map<String, String>,
): SkinEditorPreviewItem? {
    val boundStyle = style?.let { UiSceneBindings.bindText(it, payload) }?.takeIf(String::isNotBlank)
    if (!supportsSelectedStylePlaceholder(context.selectedStyleKey, style)) return null
    val boundText = text?.let { UiSceneBindings.bindText(it, payload) }
    val boundItems = items.map { item -> UiSceneBindings.bindText(item, payload) }
    val childItems = children.mapNotNull { child -> child.toPreviewItem(context, factory, payload) }
    return when (type) {
        UiSceneNodeType.Stack,
        UiSceneNodeType.Table,
        UiSceneNodeType.Container,
        -> factory.column(id, boundText ?: id, childItems)

        UiSceneNodeType.Window -> factory.window(id, boundText ?: id, childItems, boundStyle)
        UiSceneNodeType.Label -> factory.label(id, boundText.orEmpty(), boundStyle)
        UiSceneNodeType.Button -> factory.button(id, boundText.orEmpty(), boundStyle)
        UiSceneNodeType.TextButton -> factory.textButton(id, boundText.orEmpty(), boundStyle)
        UiSceneNodeType.CheckBox -> factory.checkBox(id, boundText.orEmpty(), boundStyle)
        UiSceneNodeType.TextField -> factory.textField(id, boundText.orEmpty(), boundStyle)
        UiSceneNodeType.SelectBox -> factory.selectBox(id, boundItems, boundStyle)
        UiSceneNodeType.List -> factory.list(id, boundItems, boundStyle)
        UiSceneNodeType.ScrollPane -> childItems.firstOrNull()?.let { child -> factory.scrollPane(id, child, boundStyle) }
        UiSceneNodeType.SplitPane ->
            factory.splitPane(
                id,
                childItems.getOrNull(0) ?: factory.label("${id}_left_missing", "Left"),
                childItems.getOrNull(1) ?: factory.label("${id}_right_missing", "Right"),
                boundStyle,
            )
        UiSceneNodeType.Slider -> factory.slider(id, boundStyle)
        UiSceneNodeType.ProgressBar -> factory.progressBar(id, boundStyle)
        UiSceneNodeType.Tree -> factory.tree(id, boundItems, boundStyle)
        UiSceneNodeType.TextTooltip -> factory.textTooltip(id, boundText.orEmpty(), boundStyle)
        UiSceneNodeType.Image,
        UiSceneNodeType.Space,
        -> null
    }
}

private fun UiSceneNode.supportsSelectedStylePlaceholder(
    selectedStyleKey: StyleKey?,
    rawStyle: String?,
): Boolean {
    if (rawStyle?.trim() != "{selectedStyleName}") return true
    val selected = selectedStyleKey ?: return false
    return when (type) {
        UiSceneNodeType.Window -> selected.type == "WindowStyle"
        UiSceneNodeType.Label -> selected.type == "LabelStyle"
        UiSceneNodeType.Button -> selected.type == "ButtonStyle"
        UiSceneNodeType.TextButton -> selected.type == "TextButtonStyle"
        UiSceneNodeType.CheckBox -> selected.type == "CheckBoxStyle"
        UiSceneNodeType.TextField -> selected.type == "TextFieldStyle" || selected.type == "TextAreaStyle"
        UiSceneNodeType.SelectBox -> selected.type == "SelectBoxStyle"
        UiSceneNodeType.List -> selected.type == "ListStyle"
        UiSceneNodeType.ScrollPane -> selected.type == "ScrollPaneStyle"
        UiSceneNodeType.SplitPane -> selected.type == "SplitPaneStyle"
        UiSceneNodeType.Slider -> selected.type == "SliderStyle"
        UiSceneNodeType.ProgressBar -> selected.type == "ProgressBarStyle"
        UiSceneNodeType.Tree -> selected.type == "TreeStyle"
        UiSceneNodeType.TextTooltip -> selected.type == "TextTooltipStyle"
        else -> true
    }
}

class WidgetPreviewFactory {
    fun column(
        key: String,
        label: String,
        children: List<SkinEditorPreviewItem>,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = label, kind = PreviewWidgetKind.Column, children = children)

    fun window(
        key: String,
        label: String,
        children: List<SkinEditorPreviewItem>,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(
            key = key,
            label = label,
            kind = PreviewWidgetKind.Window,
            styleName = styleName,
            children = children,
        )

    fun label(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "Label", kind = PreviewWidgetKind.Label, styleName = styleName, text = text)

    fun button(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "Button", kind = PreviewWidgetKind.Button, styleName = styleName, text = text)

    fun textButton(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "TextButton", kind = PreviewWidgetKind.TextButton, styleName = styleName, text = text)

    fun checkBox(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "CheckBox", kind = PreviewWidgetKind.CheckBox, styleName = styleName, text = text)

    fun textField(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "TextField", kind = PreviewWidgetKind.TextField, styleName = styleName, text = text)

    fun selectBox(
        key: String,
        items: List<String>,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "SelectBox", kind = PreviewWidgetKind.SelectBox, styleName = styleName, items = items)

    fun list(
        key: String,
        items: List<String>,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "List", kind = PreviewWidgetKind.List, styleName = styleName, items = items)

    fun scrollPane(
        key: String,
        child: SkinEditorPreviewItem,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(
            key = key,
            label = "ScrollPane",
            kind = PreviewWidgetKind.ScrollPane,
            styleName = styleName,
            children = listOf(child),
        )

    fun splitPane(
        key: String,
        firstChild: SkinEditorPreviewItem,
        secondChild: SkinEditorPreviewItem,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(
            key = key,
            label = "SplitPane",
            kind = PreviewWidgetKind.SplitPane,
            styleName = styleName,
            children = listOf(firstChild, secondChild),
        )

    fun slider(
        key: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "Slider", kind = PreviewWidgetKind.Slider, styleName = styleName)

    fun progressBar(
        key: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "ProgressBar", kind = PreviewWidgetKind.ProgressBar, styleName = styleName)

    fun tree(
        key: String,
        items: List<String>,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "Tree", kind = PreviewWidgetKind.Tree, styleName = styleName, items = items)

    fun textTooltip(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "TextTooltip", kind = PreviewWidgetKind.TextTooltip, styleName = styleName, text = text)
}

class DefaultWidgetPreviewLayout : PreviewLayout {
    override val id: String = Id
    override val displayName: String = "All Widgets"

    override fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem {
        val listItems =
            listOf(
                "KRender Skin Preview",
                "Український текст: Привіт, рушій!",
                "The quick brown fox jumps over the lazy dog.",
            )
        return factory.window(
            key = "default_window",
            label = "KRender Skin Preview",
            children =
                listOf(
                    factory.label("header", context.text.labelText),
                    factory.label("ukrainian", "Український текст: Привіт, рушій!"),
                    factory.label("english", "The quick brown fox jumps over the lazy dog."),
                    factory.textButton("button", context.text.buttonText),
                    factory.checkBox("checkbox", "Enable preview"),
                    factory.textField("text_field", context.text.textFieldPlaceholder),
                    factory.selectBox("select_box", items = listOf("Primary", "Secondary", "Danger")),
                    factory.scrollPane(
                        "scroll_pane",
                        factory.list("list", items = listItems),
                    ),
                    factory.slider("slider"),
                    factory.progressBar("progress"),
                ),
        )
    }

    companion object {
        const val Id = "default_widgets"
    }
}
