package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.List as GdxList
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.pashkd.krender.engine.tools.skin.EditableStyle
import com.pashkd.krender.engine.tools.skin.EditableStyleField
import com.pashkd.krender.engine.tools.skin.SkinEditSession
import com.pashkd.krender.engine.tools.skin.SkinEditorPreviewItem
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import java.lang.reflect.Field

class GdxSkinStyleEditApplier {
    fun apply(
        actor: Actor,
        item: SkinEditorPreviewItem,
        skin: Skin?,
        editSession: SkinEditSession,
        issues: MutableSet<PreviewBuildIssue>,
    ) {
        val styleName = item.styleName ?: defaultStyleNameFor(actor) ?: return
        val editableStyle =
            editSession.styles.values.firstOrNull { style ->
                !style.deleted && style.key.name == styleName && styleTypeMatches(actor, style.key.type)
            } ?: return
        val editedResourceNames =
            editSession.resources.values
                .filter { resource -> resource.modifiedFields.isNotEmpty() }
                .map { resource -> resource.key.name }
                .toSet()
        val resourceAffectedFields =
            editableStyle.fields.values
                .filter { field -> field.isReference && field.value in editedResourceNames }
                .map(EditableStyleField::name)
                .toSet()
        if (
            !editableStyle.createdInEditor &&
            !editableStyle.renamedInEditor &&
            editableStyle.modifiedFields.isEmpty() &&
            editableStyle.removedFields.isEmpty() &&
            resourceAffectedFields.isEmpty()
        ) {
            return
        }
        val styleClass = styleClassFor(editableStyle.key.type) ?: return
        val editedStyle = createStyleCopy(actor, editableStyle, styleClass, skin, issues) ?: return
        editableStyle.fields.values
            .filter { field ->
                editableStyle.createdInEditor ||
                    field.name in editableStyle.modifiedFields ||
                    field.name in resourceAffectedFields
            }.forEach { field ->
            applyField(editedStyle, field, skin, editSession, editableStyle, issues)
        }
        editableStyle.removedFields.forEach { fieldName ->
            clearField(editedStyle, fieldName, editableStyle, issues)
        }
        applyActorStyle(actor, editedStyle, editableStyle, issues)
    }

    private fun createStyleCopy(
        actor: Actor,
        editableStyle: EditableStyle,
        styleClass: Class<*>,
        skin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Any? {
        val sourceName = editableStyle.sourceKey?.name ?: editableStyle.key.name
        val baseStyle =
            skin
                ?.takeIf { candidate -> candidate.has(sourceName, styleClass) }
                ?.get(sourceName, styleClass)
                ?: currentActorStyle(actor)?.takeIf(styleClass::isInstance)
        return runCatching {
            when {
                baseStyle != null ->
                    runCatching { styleClass.getConstructor(styleClass).newInstance(baseStyle) }
                        .getOrElse {
                            styleClass.getConstructor().newInstance().also { copy ->
                                styleClass.fields.forEach { field -> field.set(copy, field.get(baseStyle)) }
                            }
                        }

                else -> styleClass.getConstructor().newInstance()
            }
        }.getOrElse { error ->
            issues += PreviewBuildIssue("Could not create edited ${editableStyle.key.type} '${editableStyle.key.name}': ${error.message}")
            null
        }
    }

    private fun applyField(
        style: Any,
        editableField: EditableStyleField,
        skin: Skin?,
        editSession: SkinEditSession,
        editableStyle: EditableStyle,
        issues: MutableSet<PreviewBuildIssue>,
    ) {
        val targetField =
            style.javaClass.fields.firstOrNull { field -> field.name.equals(editableField.name, ignoreCase = true) }
                ?: run {
                    issues += PreviewBuildIssue(
                        "Edited field '${editableField.name}' is not supported by ${editableStyle.key.type}.",
                    )
                    return
                }
        if (editableField.value.isBlank()) {
            issues += PreviewBuildIssue(
                "Edited field '${editableStyle.key.type}.${editableStyle.key.name}.${editableField.name}' is empty.",
            )
            return
        }
        val value =
            resolveFieldValue(
                targetField = targetField,
                editableField = editableField,
                skin = skin,
                editSession = editSession,
            )
        if (value == UnresolvedValue) {
            issues += PreviewBuildIssue(
                "Could not resolve edited value '${editableField.value}' for ${editableStyle.key.type}.${editableStyle.key.name}.${editableField.name}.",
            )
            return
        }
        runCatching { targetField.set(style, value) }
            .onFailure { error ->
                issues += PreviewBuildIssue(
                    "Could not apply ${editableStyle.key.type}.${editableStyle.key.name}.${editableField.name}: ${error.message}",
                )
            }
    }

    private fun clearField(
        style: Any,
        fieldName: String,
        editableStyle: EditableStyle,
        issues: MutableSet<PreviewBuildIssue>,
    ) {
        val targetField =
            style.javaClass.fields.firstOrNull { field -> field.name.equals(fieldName, ignoreCase = true) }
                ?: return
        if (targetField.type.isPrimitive) {
            issues += PreviewBuildIssue("Removed primitive field '$fieldName' cannot be represented in preview.")
            return
        }
        runCatching { targetField.set(style, null) }
            .onFailure { error ->
                issues += PreviewBuildIssue(
                    "Could not remove ${editableStyle.key.type}.${editableStyle.key.name}.$fieldName: ${error.message}",
                )
            }
    }

    private fun resolveFieldValue(
        targetField: Field,
        editableField: EditableStyleField,
        skin: Skin?,
        editSession: SkinEditSession,
    ): Any? {
        val value = editableField.value
        return when {
            editableField.referenceCategory == SkinResourceCategory.Font ->
                skin?.takeIf { it.has(value, BitmapFont::class.java) }?.getFont(value) ?: UnresolvedValue

            editableField.referenceCategory == SkinResourceCategory.Color ->
                editedColor(value, editSession)
                    ?: skin?.takeIf { it.has(value, Color::class.java) }?.getColor(value)
                    ?: UnresolvedValue

            editableField.referenceCategory == SkinResourceCategory.Drawable ->
                runCatching { skin?.getDrawable(value) }.getOrNull() ?: UnresolvedValue

            editableField.referenceCategory == SkinResourceCategory.Texture ->
                runCatching { skin?.getDrawable(value) }.getOrNull() ?: UnresolvedValue

            targetField.type == String::class.java -> value
            targetField.type == java.lang.Boolean.TYPE || targetField.type == java.lang.Boolean::class.java ->
                value.toBooleanStrictOrNull() ?: UnresolvedValue

            targetField.type == java.lang.Integer.TYPE || targetField.type == java.lang.Integer::class.java ->
                value.toIntOrNull() ?: UnresolvedValue

            targetField.type == java.lang.Float.TYPE || targetField.type == java.lang.Float::class.java ->
                value.toFloatOrNull() ?: UnresolvedValue

            targetField.type == java.lang.Double.TYPE || targetField.type == java.lang.Double::class.java ->
                value.toDoubleOrNull() ?: UnresolvedValue

            Drawable::class.java.isAssignableFrom(targetField.type) ->
                runCatching { skin?.getDrawable(value) }.getOrNull() ?: UnresolvedValue

            BitmapFont::class.java.isAssignableFrom(targetField.type) ->
                skin?.takeIf { it.has(value, BitmapFont::class.java) }?.getFont(value) ?: UnresolvedValue

            Color::class.java.isAssignableFrom(targetField.type) ->
                editedColor(value, editSession)
                    ?: skin?.takeIf { it.has(value, Color::class.java) }?.getColor(value)
                    ?: UnresolvedValue

            targetField.type == ScrollPane.ScrollPaneStyle::class.java ->
                skin?.takeIf { it.has(value, ScrollPane.ScrollPaneStyle::class.java) }?.get(value, ScrollPane.ScrollPaneStyle::class.java)
                    ?: UnresolvedValue

            targetField.type == GdxList.ListStyle::class.java ->
                skin?.takeIf { it.has(value, GdxList.ListStyle::class.java) }?.get(value, GdxList.ListStyle::class.java)
                    ?: UnresolvedValue

            else -> UnresolvedValue
        }
    }

    private fun editedColor(
        name: String,
        editSession: SkinEditSession,
    ): Color? {
        val resource =
            editSession.resources.values.firstOrNull { editable ->
                !editable.deleted &&
                    editable.key.category == SkinResourceCategory.Color &&
                    editable.key.name == name
            } ?: return null
        val hex = resource.values["value"] ?: resource.values["hex"]
        if (hex != null) {
            return parseHexColor(hex)
        }
        val red = resource.values["r"]?.toFloatOrNull() ?: return null
        val green = resource.values["g"]?.toFloatOrNull() ?: return null
        val blue = resource.values["b"]?.toFloatOrNull() ?: return null
        val alpha = resource.values["a"]?.toFloatOrNull() ?: 1f
        if (listOf(red, green, blue, alpha).any { channel -> channel !in 0f..1f }) return null
        return Color(red, green, blue, alpha)
    }

    private fun parseHexColor(value: String): Color? {
        val normalized = value.removePrefix("#")
        if (normalized.length !in setOf(6, 8) || normalized.any { character -> !character.isDigit() && character.lowercaseChar() !in 'a'..'f' }) {
            return null
        }
        return runCatching { Color.valueOf(if (normalized.length == 6) normalized + "ff" else normalized) }.getOrNull()
    }

    private fun applyActorStyle(
        actor: Actor,
        style: Any,
        editableStyle: EditableStyle,
        issues: MutableSet<PreviewBuildIssue>,
    ) {
        val applied =
            when {
                actor is TextButton && style is TextButton.TextButtonStyle -> actor.setStyle(style).let { true }
                actor is CheckBox && style is CheckBox.CheckBoxStyle -> actor.setStyle(style).let { true }
                actor is Button && style is Button.ButtonStyle -> actor.setStyle(style).let { true }
                actor is Label && style is Label.LabelStyle -> actor.setStyle(style).let { true }
                actor is TextField && style is TextField.TextFieldStyle -> actor.setStyle(style).let { true }
                actor is SelectBox<*> && style is SelectBox.SelectBoxStyle -> actor.setStyle(style).let { true }
                actor is GdxList<*> && style is GdxList.ListStyle -> actor.setStyle(style).let { true }
                actor is ScrollPane && style is ScrollPane.ScrollPaneStyle -> actor.setStyle(style).let { true }
                actor is Slider && style is Slider.SliderStyle -> actor.setStyle(style).let { true }
                actor is ProgressBar && style is ProgressBar.ProgressBarStyle -> actor.setStyle(style).let { true }
                actor is Window && style is Window.WindowStyle -> actor.setStyle(style).let { true }
                else -> false
            }
        if (!applied) {
            issues += PreviewBuildIssue("Edited ${editableStyle.key.type} '${editableStyle.key.name}' is not supported by this preview widget.")
        }
    }

    private fun currentActorStyle(actor: Actor): Any? =
        when (actor) {
            is TextButton -> actor.style
            is CheckBox -> actor.style
            is Button -> actor.style
            is Label -> actor.style
            is TextField -> actor.style
            is SelectBox<*> -> actor.style
            is GdxList<*> -> actor.style
            is ScrollPane -> actor.style
            is Slider -> actor.style
            is ProgressBar -> actor.style
            is Window -> actor.style
            else -> null
        }

    private fun styleTypeMatches(
        actor: Actor,
        styleType: String,
    ): Boolean =
        when (actor) {
            is TextButton -> styleType == "TextButtonStyle"
            is CheckBox -> styleType == "CheckBoxStyle"
            is Button -> styleType == "ButtonStyle"
            is Label -> styleType == "LabelStyle"
            is TextField -> styleType == "TextFieldStyle" || styleType == "TextAreaStyle"
            is SelectBox<*> -> styleType == "SelectBoxStyle"
            is GdxList<*> -> styleType == "ListStyle"
            is ScrollPane -> styleType == "ScrollPaneStyle"
            is Slider -> styleType == "SliderStyle"
            is ProgressBar -> styleType == "ProgressBarStyle"
            is Window -> styleType == "WindowStyle"
            else -> false
        }

    private fun defaultStyleNameFor(actor: Actor): String? =
        when (actor) {
            is Slider,
            is ProgressBar,
            -> "default-horizontal"

            is TextButton,
            is CheckBox,
            is Button,
            is Label,
            is TextField,
            is SelectBox<*>,
            is GdxList<*>,
            is ScrollPane,
            is Window,
            -> "default"

            else -> null
        }

    private fun styleClassFor(type: String): Class<*>? =
        when (type) {
            "LabelStyle" -> Label.LabelStyle::class.java
            "TextButtonStyle" -> TextButton.TextButtonStyle::class.java
            "ButtonStyle" -> Button.ButtonStyle::class.java
            "CheckBoxStyle" -> CheckBox.CheckBoxStyle::class.java
            "TextFieldStyle",
            "TextAreaStyle",
            -> TextField.TextFieldStyle::class.java
            "ListStyle" -> GdxList.ListStyle::class.java
            "SelectBoxStyle" -> SelectBox.SelectBoxStyle::class.java
            "ScrollPaneStyle" -> ScrollPane.ScrollPaneStyle::class.java
            "SliderStyle" -> Slider.SliderStyle::class.java
            "ProgressBarStyle" -> ProgressBar.ProgressBarStyle::class.java
            "WindowStyle" -> Window.WindowStyle::class.java
            else -> null
        }

    private companion object {
        private val UnresolvedValue = Any()
    }
}
