package com.pashkd.krender.engine.tools.skin

class FormsPreviewLayout : PreviewLayout {
    override val id: String = "forms"
    override val displayName: String = "Form"

    override fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem =
        factory.window(
            key = "forms_window",
            label = "Profile Settings",
            children =
                listOf(
                    factory.label("forms_title", context.text.labelText),
                    factory.label("forms_ukrainian", "Налаштування профілю та робочого простору"),
                    factory.textField("forms_username", context.text.textFieldPlaceholder),
                    factory.textField("forms_email", "name@example.com"),
                    factory.selectBox("forms_quality", listOf("Low", "High", "Ultra", "Автоматично")),
                    factory.checkBox("forms_notifications", "Enable notifications"),
                    factory.checkBox("forms_updates", "Автоматично перевіряти оновлення"),
                    factory.slider("forms_volume"),
                    factory.progressBar("forms_storage"),
                    factory.textButton("forms_save", context.text.buttonText),
                    factory.textButton("forms_cancel", "Cancel"),
                    factory.textButton("forms_reset", "Reset"),
                ),
        )
}

class TablesPreviewLayout : PreviewLayout {
    override val id: String = "tables"
    override val displayName: String = "Tables"

    override fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem {
        val rows =
            listOf(
                "Name                 Type          Size",
                "player_idle.glb      Model         2.4 MB",
                "terrain_grass.png    Texture       1.1 MB",
                "main_menu.krui       UI Scene      18 KB",
                "default.uiskin       Skin          42 KB",
                "рушій_логотип.png    Texture       640 KB",
                "ambient_forest.ogg   Audio         6.8 MB",
            )
        return factory.window(
            key = "tables_window",
            label = "Resource Table",
            children =
                listOf(
                    factory.label("tables_title", context.text.labelText),
                    factory.selectBox("tables_filter", listOf("All assets", "Models", "Textures", "UI")),
                    factory.scrollPane(
                        "tables_scroll",
                        factory.list("tables_list", rows),
                    ),
                    factory.textButton("tables_open", context.text.buttonText),
                    factory.textButton("tables_refresh", "Refresh"),
                ),
        )
    }
}

class DialogsPreviewLayout : PreviewLayout {
    override val id: String = "dialogs"
    override val displayName: String = "Dialog"

    override fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem =
        factory.window(
            key = "dialogs_window",
            label = "Unsaved Changes",
            children =
                listOf(
                    factory.label("dialogs_message", context.text.labelText),
                    factory.label("dialogs_ukrainian", "Зберегти зміни перед закриттям?"),
                    factory.textField("dialogs_name", context.text.textFieldPlaceholder),
                    factory.checkBox("dialogs_remember", "Remember my choice"),
                    factory.textButton("dialogs_save", context.text.buttonText),
                    factory.textButton("dialogs_discard", "Discard"),
                    factory.textButton("dialogs_cancel", "Cancel"),
                ),
        )
}

class StressPreviewLayout : PreviewLayout {
    override val id: String = "stress"
    override val displayName: String = "List"

    override fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem {
        val stressItems =
            listOf(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz",
                "0123456789 !@#$%^&*()_+-=[]{};':\",./<>?",
                "Українська абетка: А Б В Г Ґ Д Е Є Ж З И І Ї Й",
                "A deliberately long line intended to reveal clipping, wrapping, padding, and narrow-screen behavior.",
                "Disabled-like placeholder: [ unavailable action ]",
                "Mixed: KRender 2.0 / рушій / 1920x1080 / 100%",
            )
        return factory.window(
            key = "stress_window",
            label = "Widget Stress Preview",
            children =
                listOf(
                    factory.label("stress_long", context.text.labelText),
                    factory.label("stress_symbols", stressItems[1]),
                    factory.label("stress_ukrainian", stressItems[2]),
                    factory.textField("stress_field", context.text.textFieldPlaceholder),
                    factory.selectBox("stress_select", stressItems.take(4)),
                    factory.scrollPane("stress_scroll", factory.list("stress_list", stressItems)),
                    factory.checkBox("stress_checkbox_a", "Short option"),
                    factory.checkBox("stress_checkbox_b", "Надзвичайно довгий підпис параметра для перевірки компонування"),
                    factory.textButton("stress_button_a", context.text.buttonText),
                    factory.textButton("stress_button_b", "Додаткова дія"),
                    factory.slider("stress_slider"),
                    factory.progressBar("stress_progress"),
                ),
        )
    }
}

class SelectedStylePreviewLayout : PreviewLayout {
    override val id: String = Id
    override val displayName: String = "Selected Style"

    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    override fun build(
        context: PreviewLayoutContext,
        factory: WidgetPreviewFactory,
    ): SkinEditorPreviewItem {
        val selected =
            context.selectedStyleKey
                ?: return factory.column(
                    key = "selected_style_empty",
                    label = "Selected Style",
                    children = listOf(factory.label("selected_style_empty_label", "Select a style in the Styles panel.")),
                )
        val styleName = selected.name
        val children =
            when (selected.type) {
                "LabelStyle" ->
                    listOf(
                        factory.label("selected_label_short", context.text.labelText, styleName),
                        factory.label("selected_label_ukrainian", "Український текст: Привіт, рушій!", styleName),
                        factory.label("selected_label_long", "The quick brown fox jumps over the lazy dog.", styleName),
                    )

                "TextButtonStyle" ->
                    listOf(
                        factory.textButton("selected_button_primary", context.text.buttonText, styleName),
                        factory.textButton("selected_button_ukrainian", "Підтвердити", styleName),
                        factory.textButton("selected_button_long", "A Button With A Deliberately Long Caption", styleName),
                    )

                "ButtonStyle" ->
                    listOf(
                        factory.button("selected_button_style_primary", "ButtonStyle Preview", styleName),
                        factory.button("selected_button_style_secondary", "Українська кнопка", styleName),
                    )

                "CheckBoxStyle" ->
                    listOf(
                        factory.checkBox("selected_checkbox_a", "Enabled option", styleName),
                        factory.checkBox("selected_checkbox_b", "Український параметр", styleName),
                    )

                "TextFieldStyle",
                "TextAreaStyle",
                ->
                    listOf(
                        factory.textField("selected_field_a", context.text.textFieldPlaceholder, styleName),
                        factory.textField("selected_field_b", "Введіть текст українською", styleName),
                    )

                "SelectBoxStyle" ->
                    listOf(factory.selectBox("selected_select", listOf("First", "Second", "Третій"), styleName))

                "ListStyle" ->
                    listOf(factory.list("selected_list", listOf("Alpha", "Beta", "Український пункт"), styleName))

                "ScrollPaneStyle" ->
                    listOf(
                        factory.scrollPane(
                            "selected_scroll",
                            factory.list("selected_scroll_list", listOf("One", "Two", "Three", "Чотири")),
                            styleName,
                        ),
                    )

                "SplitPaneStyle" ->
                    listOf(
                        factory.splitPane(
                            "selected_split_pane",
                            factory.label("selected_split_left", "Navigation", null),
                            factory.column(
                                "selected_split_right",
                                "Content",
                                listOf(
                                    factory.label("selected_split_text", "Split pane preview"),
                                    factory.label("selected_split_text_uk", "Попередній перегляд роздільника"),
                                ),
                            ),
                            styleName,
                        ),
                    )

                "SliderStyle" -> listOf(factory.slider("selected_slider", styleName))
                "ProgressBarStyle" -> listOf(factory.progressBar("selected_progress", styleName))
                "TreeStyle" ->
                    listOf(
                        factory.tree(
                            "selected_tree",
                            listOf(
                                "UI",
                                "UI/Buttons",
                                "UI/Buttons/Primary",
                                "UI/Dialogs",
                                "Scenes",
                            ),
                            styleName,
                        ),
                    )
                "TextTooltipStyle" ->
                    listOf(
                        factory.textTooltip("selected_tooltip", "Tooltip preview: KRender skin hint", styleName),
                        factory.label("selected_tooltip_note", "Tooltip container preview is shown in-place."),
                    )
                "WindowStyle" ->
                    return factory.window(
                        key = "selected_window",
                        label = "Selected Window Style",
                        styleName = styleName,
                        children =
                            listOf(
                                factory.label("selected_window_text", "Window content preview"),
                                factory.textButton("selected_window_action", "Close"),
                            ),
                    )

                else ->
                    listOf(
                        factory.label(
                            "selected_style_unsupported",
                            "Preview for ${selected.type}.${selected.name} is not available yet.",
                        ),
                    )
            }
        return factory.column(
            key = "selected_style_content",
            label = "Selected Style",
            children =
                listOf(factory.label("selected_style_title", "${selected.type}.${selected.name}")) +
                    children,
        )
    }

    companion object {
        const val Id = "selected_style"
    }
}
