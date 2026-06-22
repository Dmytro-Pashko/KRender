package com.pashkd.krender.engine.tools.skin

data class PreviewLayoutContext(
    val loadResult: SkinLoadResult,
    val selectedStyleKey: StyleKey? = null,
    val selectedResourceName: String? = null,
)

interface PreviewLayout {
    val id: String
    val displayName: String

    fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem
}

class PreviewLayoutRegistry(
    layouts: List<PreviewLayout> =
        listOf(
            DefaultWidgetPreviewLayout(),
            FormsPreviewLayout(),
            TablesPreviewLayout(),
            DialogsPreviewLayout(),
            StressPreviewLayout(),
            SelectedStylePreviewLayout(),
        ),
) {
    private val layoutsById = layouts.associateBy { it.id }
    val layouts: List<PreviewLayout> = layouts

    fun layoutOrDefault(id: String?): PreviewLayout = id?.let(layoutsById::get) ?: layouts.first()
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

    fun textButton(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(key = key, label = "TextButton", kind = PreviewWidgetKind.TextButton, styleName = styleName, text = text)

    fun checkBox(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(key = key, label = "CheckBox", kind = PreviewWidgetKind.CheckBox, styleName = styleName, text = text)

    fun textField(
        key: String,
        text: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(key = key, label = "TextField", kind = PreviewWidgetKind.TextField, styleName = styleName, text = text)

    fun selectBox(
        key: String,
        items: List<String>,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(key = key, label = "SelectBox", kind = PreviewWidgetKind.SelectBox, styleName = styleName, items = items)

    fun list(
        key: String,
        items: List<String>,
        styleName: String? = null,
    ): SkinEditorPreviewItem =
        SkinEditorPreviewItem(key = key, label = "List", kind = PreviewWidgetKind.List, styleName = styleName, items = items)

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

    fun slider(
        key: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "Slider", kind = PreviewWidgetKind.Slider, styleName = styleName)

    fun progressBar(
        key: String,
        styleName: String? = null,
    ): SkinEditorPreviewItem = SkinEditorPreviewItem(key = key, label = "ProgressBar", kind = PreviewWidgetKind.ProgressBar, styleName = styleName)
}

class DefaultWidgetPreviewLayout : PreviewLayout {
    override val id: String = Id
    override val displayName: String = "Default Widgets"

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
                    factory.label("header", "KRender Skin Preview"),
                    factory.label("ukrainian", "Український текст: Привіт, рушій!"),
                    factory.label("english", "The quick brown fox jumps over the lazy dog."),
                    factory.textButton("button", "Apply"),
                    factory.checkBox("checkbox", "Enable preview"),
                    factory.textField("text_field", "Search skin resources"),
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
