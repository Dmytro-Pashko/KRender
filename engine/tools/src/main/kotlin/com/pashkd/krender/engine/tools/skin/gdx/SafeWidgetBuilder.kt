package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.List as GdxList
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.pashkd.krender.engine.tools.skin.PreviewWidgetKind
import com.pashkd.krender.engine.tools.skin.SkinEditSession
import com.pashkd.krender.engine.tools.skin.SkinEditorPreviewItem

data class PreviewBuildIssue(
    val message: String,
)

data class PreviewActorBuildResult(
    val actor: Actor,
    val issues: List<PreviewBuildIssue>,
)

class SafeWidgetBuilder : Disposable {
    private val fallbackSkin = createFallbackSkin()
    private val styleEditApplier = GdxSkinStyleEditApplier()
    private var currentEditSession = SkinEditSession()

    fun build(
        item: SkinEditorPreviewItem,
        loadedSkin: LoadedSkinHandle?,
        editSession: SkinEditSession,
    ): PreviewActorBuildResult {
        currentEditSession = editSession
        val issues = linkedSetOf<PreviewBuildIssue>()
        val actor = build(item, loadedSkin?.skin, editSession, issues)
        return PreviewActorBuildResult(actor = actor, issues = issues.toList())
    }

    override fun dispose() {
        fallbackSkin.dispose()
    }

    private fun build(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        editSession: SkinEditSession,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val actor =
            when (item.kind) {
                PreviewWidgetKind.Column -> buildColumn(item, primarySkin, editSession, issues)
                PreviewWidgetKind.Window -> buildWindow(item, primarySkin, editSession, issues)
                PreviewWidgetKind.Label -> buildLabel(item, primarySkin, issues)
                PreviewWidgetKind.TextButton -> buildTextButton(item, primarySkin, issues)
                PreviewWidgetKind.CheckBox -> buildCheckBox(item, primarySkin, issues)
                PreviewWidgetKind.TextField -> buildTextField(item, primarySkin, issues)
                PreviewWidgetKind.SelectBox -> buildSelectBox(item, primarySkin, issues)
                PreviewWidgetKind.List -> buildList(item, primarySkin, issues)
                PreviewWidgetKind.ScrollPane -> buildScrollPane(item, primarySkin, editSession, issues)
                PreviewWidgetKind.Slider -> buildSlider(item, primarySkin, issues)
                PreviewWidgetKind.ProgressBar -> buildProgressBar(item, primarySkin, issues)
            }
        styleEditApplier.apply(actor, item, primarySkin, editSession, issues)
        return actor
    }

    private fun buildColumn(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        editSession: SkinEditSession,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor =
        Table().apply {
            defaults().growX().pad(6f)
            item.children.forEach { child ->
                add(build(child, primarySkin, editSession, issues))
                row()
            }
            pack()
        }

    private fun buildWindow(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        editSession: SkinEditSession,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, Window.WindowStyle::class.java, "WindowStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor = if (styleName != null && skin.has(styleName, Window.WindowStyle::class.java)) Window(item.label, skin, styleName) else Window(item.label, skin)
        actor.isMovable = false
        actor.isResizable = false
        item.children.forEach { child ->
            actor.add(build(child, primarySkin, editSession, issues)).growX().pad(6f)
            actor.row()
        }
        actor.pack()
        return actor
    }

    private fun buildLabel(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, Label.LabelStyle::class.java, "LabelStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        return if (styleName != null && skin.has(styleName, Label.LabelStyle::class.java)) {
            Label(item.text.orEmpty(), skin, styleName)
        } else {
            Label(item.text.orEmpty(), skin)
        }
    }

    private fun buildTextButton(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, TextButton.TextButtonStyle::class.java, "TextButtonStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        return if (styleName != null && skin.has(styleName, TextButton.TextButtonStyle::class.java)) {
            TextButton(item.text.orEmpty(), skin, styleName)
        } else {
            TextButton(item.text.orEmpty(), skin)
        }
    }

    private fun buildCheckBox(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, CheckBox.CheckBoxStyle::class.java, "CheckBoxStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        return if (styleName != null && skin.has(styleName, CheckBox.CheckBoxStyle::class.java)) {
            CheckBox(item.text.orEmpty(), skin, styleName)
        } else {
            CheckBox(item.text.orEmpty(), skin)
        }
    }

    private fun buildTextField(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, TextField.TextFieldStyle::class.java, "TextFieldStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        return if (styleName != null && skin.has(styleName, TextField.TextFieldStyle::class.java)) {
            TextField(item.text.orEmpty(), skin, styleName)
        } else {
            TextField(item.text.orEmpty(), skin)
        }
    }

    private fun buildSelectBox(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, SelectBox.SelectBoxStyle::class.java, "SelectBoxStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, SelectBox.SelectBoxStyle::class.java)) {
                SelectBox<String>(skin, styleName)
            } else {
                SelectBox<String>(skin)
            }
        actor.setItems(Array.with(*item.items.toTypedArray()))
        actor.selectedIndex = 0
        return actor
    }

    private fun buildList(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, GdxList.ListStyle::class.java, "ListStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, GdxList.ListStyle::class.java)) {
                GdxList<String>(skin, styleName)
            } else {
                GdxList<String>(skin)
            }
        actor.setItems(Array.with(*item.items.toTypedArray()))
        return actor
    }

    private fun buildScrollPane(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        editSession: SkinEditSession,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val child = item.children.firstOrNull()
        val content = child?.let { build(it, primarySkin, editSession, issues) } ?: missingActor("Missing ScrollPane child")
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, ScrollPane.ScrollPaneStyle::class.java, "ScrollPaneStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, ScrollPane.ScrollPaneStyle::class.java)) {
                ScrollPane(content, skin, styleName)
            } else {
                ScrollPane(content, skin)
            }
        actor.setFadeScrollBars(false)
        actor.setScrollingDisabled(true, false)
        actor.setForceScroll(false, true)
        actor.height = 160f
        return actor
    }

    private fun buildSlider(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin =
            skinFor(
                primarySkin,
                requestedStyleName = item.styleName,
                defaultStyleName = DefaultHorizontalSliderStyle,
                type = Slider.SliderStyle::class.java,
                typeName = "SliderStyle",
                issues = issues,
            )
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, Slider.SliderStyle::class.java)) {
                Slider(0f, 100f, 1f, false, skin, styleName)
            } else if (skin.has(DefaultHorizontalSliderStyle, Slider.SliderStyle::class.java)) {
                Slider(0f, 100f, 1f, false, skin, DefaultHorizontalSliderStyle)
            } else {
                Slider(0f, 100f, 1f, false, skin)
            }
        actor.value = 55f
        return actor
    }

    private fun buildProgressBar(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin =
            skinFor(
                primarySkin,
                requestedStyleName = item.styleName,
                defaultStyleName = DefaultHorizontalProgressBarStyle,
                type = ProgressBar.ProgressBarStyle::class.java,
                typeName = "ProgressBarStyle",
                issues = issues,
            )
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, ProgressBar.ProgressBarStyle::class.java)) {
                ProgressBar(0f, 100f, 1f, false, skin, styleName)
            } else if (skin.has(DefaultHorizontalProgressBarStyle, ProgressBar.ProgressBarStyle::class.java)) {
                ProgressBar(0f, 100f, 1f, false, skin, DefaultHorizontalProgressBarStyle)
            } else {
                ProgressBar(0f, 100f, 1f, false, skin)
            }
        actor.value = 72f
        return actor
    }

    private fun <T> skinFor(
        primarySkin: Skin?,
        requestedStyleName: String?,
        defaultStyleName: String,
        type: Class<T>,
        typeName: String,
        issues: MutableSet<PreviewBuildIssue>,
    ): Skin =
        when {
            primarySkin == null -> fallbackSkin
            requestedStyleName != null && primarySkin.has(requestedStyleName, type) -> primarySkin
            requestedStyleName == null && primarySkin.has(defaultStyleName, type) -> primarySkin
            requestedStyleName != null &&
                currentEditSession.styles.values.any { style ->
                    !style.deleted &&
                        style.key.name == requestedStyleName &&
                        (style.key.type == typeName || style.key.type == "TextAreaStyle" && typeName == "TextFieldStyle")
                } ->
                if (primarySkin.has(defaultStyleName, type)) primarySkin else fallbackSkin

            else -> {
                val missingStyleName = requestedStyleName ?: defaultStyleName
                issues += PreviewBuildIssue("Missing $typeName '$missingStyleName'; rendered with fallback skin.")
                fallbackSkin
            }
        }

    private fun missingActor(message: String): Actor =
        Table().apply {
            background = fallbackSkin.getDrawable("skin_editor_panel")
            add(Label(message, fallbackSkin)).pad(8f)
            pack()
        }

    private fun createFallbackSkin(): Skin {
        val skin = Skin()
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        skin.add("skin_editor_white", texture)

        val panelDrawable = drawable(texture, 0.14f, 0.15f, 0.18f, 1f)
        val accentDrawable = drawable(texture, 0.26f, 0.48f, 0.82f, 1f)
        val outlineDrawable = drawable(texture, 0.62f, 0.66f, 0.78f, 1f)
        val knobDrawable = drawable(texture, 0.92f, 0.94f, 0.98f, 1f)
        val bitmapFont = BitmapFont()

        skin.add("default-font", bitmapFont)
        skin.add("skin_editor_panel", panelDrawable)
        skin.add("skin_editor_accent", accentDrawable)

        skin.add("default", Label.LabelStyle(bitmapFont, Color.WHITE))
        skin.add(
            "default",
            TextButton.TextButtonStyle().apply {
                up = panelDrawable
                down = accentDrawable
                checked = accentDrawable
                over = accentDrawable
                font = bitmapFont
            },
        )
        skin.add(
            "default",
            CheckBox.CheckBoxStyle().apply {
                checkboxOff = outlineDrawable
                checkboxOn = accentDrawable
                font = bitmapFont
                fontColor = Color.WHITE
            },
        )
        skin.add(
            "default",
            TextField.TextFieldStyle().apply {
                background = panelDrawable
                cursor = accentDrawable
                selection = accentDrawable
                font = bitmapFont
                fontColor = Color.WHITE
                focusedFontColor = Color.WHITE
            },
        )
        skin.add(
            "default",
            GdxList.ListStyle().apply {
                background = panelDrawable
                selection = accentDrawable
                font = bitmapFont
                fontColorSelected = Color.WHITE
                fontColorUnselected = Color(0.87f, 0.89f, 0.93f, 1f)
            },
        )
        skin.add(
            "default",
            SelectBox.SelectBoxStyle().apply {
                background = panelDrawable
                backgroundOpen = panelDrawable
                backgroundOver = accentDrawable
                font = bitmapFont
                fontColor = Color.WHITE
                scrollStyle = ScrollPane.ScrollPaneStyle().apply {
                    background = panelDrawable
                    vScroll = outlineDrawable
                    vScrollKnob = accentDrawable
                }
                listStyle = skin.get("default", GdxList.ListStyle::class.java)
            },
        )
        skin.add(
            "default",
            ScrollPane.ScrollPaneStyle().apply {
                background = panelDrawable
                vScroll = outlineDrawable
                vScrollKnob = accentDrawable
                hScroll = outlineDrawable
                hScrollKnob = accentDrawable
            },
        )
        skin.add(
            "default-horizontal",
            Slider.SliderStyle().apply {
                background = outlineDrawable
                knob = knobDrawable
            },
        )
        skin.add(
            "default-horizontal",
            ProgressBar.ProgressBarStyle().apply {
                background = outlineDrawable
                knobBefore = accentDrawable
                knob = knobDrawable
            },
        )
        skin.add(
            "default",
            Window.WindowStyle().apply {
                background = panelDrawable
                titleFont = bitmapFont
                titleFontColor = Color.WHITE
            },
        )
        return skin
    }

    private fun drawable(
        texture: Texture,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ): Drawable = TextureRegionDrawable(TextureRegion(texture)).tint(Color(r, g, b, a))

    private companion object {
        private const val DefaultStyleName = "default"
        private const val DefaultHorizontalSliderStyle = "default-horizontal"
        private const val DefaultHorizontalProgressBarStyle = "default-horizontal"
    }
}
