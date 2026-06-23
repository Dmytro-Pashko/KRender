package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.tools.skin.PreviewWidgetKind
import com.pashkd.krender.engine.tools.skin.SkinEditSession
import com.pashkd.krender.engine.tools.skin.SkinEditorPreviewItem
import com.pashkd.krender.engine.tools.skin.StyleKey
import com.badlogic.gdx.scenes.scene2d.ui.List as GdxList

data class PreviewBuildIssue(
    val message: String,
)

data class PreviewActorBuildResult(
    val actor: Actor,
    val issues: List<PreviewBuildIssue>,
    val selectedStyleActors: List<Actor> = emptyList(),
)

class SafeWidgetBuilder : Disposable {
    private val fallbackSkin = createFallbackSkin()
    private val styleEditApplier = GdxSkinStyleEditApplier()
    private var currentEditSession = SkinEditSession()
    private var currentSelectedStyleKey: StyleKey? = null
    private val selectedStyleActors = mutableListOf<Actor>()

    private class PreviewTreeNode(
        actor: Actor,
        value: String,
    ) : Tree.Node<PreviewTreeNode, String, Actor>(actor) {
        init {
            setValue(value)
        }
    }

    fun build(
        item: SkinEditorPreviewItem,
        loadedSkin: LoadedSkinHandle?,
        editSession: SkinEditSession,
        selectedStyleKey: StyleKey?,
    ): PreviewActorBuildResult {
        currentEditSession = editSession
        currentSelectedStyleKey = selectedStyleKey
        selectedStyleActors.clear()
        val issues = linkedSetOf<PreviewBuildIssue>()
        val actor = build(item, loadedSkin?.skin, editSession, issues)
        return PreviewActorBuildResult(
            actor = actor,
            issues = issues.toList(),
            selectedStyleActors = selectedStyleActors.toList(),
        )
    }

    override fun dispose() {
        fallbackSkin.dispose()
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
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
                PreviewWidgetKind.Button -> buildButton(item, primarySkin, issues)
                PreviewWidgetKind.TextButton -> buildTextButton(item, primarySkin, issues)
                PreviewWidgetKind.CheckBox -> buildCheckBox(item, primarySkin, issues)
                PreviewWidgetKind.TextField -> buildTextField(item, primarySkin, issues)
                PreviewWidgetKind.SelectBox -> buildSelectBox(item, primarySkin, issues)
                PreviewWidgetKind.List -> buildList(item, primarySkin, issues)
                PreviewWidgetKind.ScrollPane -> buildScrollPane(item, primarySkin, editSession, issues)
                PreviewWidgetKind.SplitPane -> buildSplitPane(item, primarySkin, editSession, issues)
                PreviewWidgetKind.Slider -> buildSlider(item, primarySkin, issues)
                PreviewWidgetKind.ProgressBar -> buildProgressBar(item, primarySkin, issues)
                PreviewWidgetKind.Tree -> buildTree(item, primarySkin, issues)
                PreviewWidgetKind.TextTooltip -> buildTextTooltip(item, primarySkin, issues)
            }
        actor.name = item.key
        styleEditApplier.apply(actor, item, primarySkin, editSession, issues)
        if (matchesSelectedStyle(item)) {
            selectedStyleActors += actor
        }
        return actor
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun matchesSelectedStyle(item: SkinEditorPreviewItem): Boolean {
        val selected = currentSelectedStyleKey ?: return false
        val itemType =
            when (item.kind) {
                PreviewWidgetKind.Window -> "WindowStyle"
                PreviewWidgetKind.Label -> "LabelStyle"
                PreviewWidgetKind.Button -> "ButtonStyle"
                PreviewWidgetKind.TextButton -> "TextButtonStyle"
                PreviewWidgetKind.CheckBox -> "CheckBoxStyle"
                PreviewWidgetKind.TextField -> selected.type.takeIf { it == "TextAreaStyle" } ?: "TextFieldStyle"
                PreviewWidgetKind.SelectBox -> "SelectBoxStyle"
                PreviewWidgetKind.List -> "ListStyle"
                PreviewWidgetKind.ScrollPane -> "ScrollPaneStyle"
                PreviewWidgetKind.SplitPane -> "SplitPaneStyle"
                PreviewWidgetKind.Slider -> "SliderStyle"
                PreviewWidgetKind.ProgressBar -> "ProgressBarStyle"
                PreviewWidgetKind.Tree -> "TreeStyle"
                PreviewWidgetKind.TextTooltip -> "TextTooltipStyle"
                PreviewWidgetKind.Column -> return false
            }
        val effectiveStyleName =
            item.styleName ?: when (item.kind) {
                PreviewWidgetKind.Slider,
                PreviewWidgetKind.ProgressBar,
                -> DefaultHorizontalSliderStyle

                else -> DefaultStyleName
            }
        return itemType == selected.type && effectiveStyleName == selected.name
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

    private fun buildButton(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, Button.ButtonStyle::class.java, "ButtonStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, Button.ButtonStyle::class.java)) {
                Button(skin, styleName)
            } else {
                Button(skin)
            }
        actor.add(Label(item.text.orEmpty(), skin)).pad(10f)
        actor.pack()
        return actor
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

    @Suppress("SpreadOperator")
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

    @Suppress("SpreadOperator")
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

    private fun buildSplitPane(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        editSession: SkinEditSession,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val firstChild = item.children.getOrNull(0)?.let { build(it, primarySkin, editSession, issues) } ?: missingActor("Missing SplitPane left child")
        val secondChild = item.children.getOrNull(1)?.let { build(it, primarySkin, editSession, issues) } ?: missingActor("Missing SplitPane right child")
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, SplitPane.SplitPaneStyle::class.java, "SplitPaneStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, SplitPane.SplitPaneStyle::class.java)) {
                SplitPane(firstChild, secondChild, false, skin, styleName)
            } else {
                SplitPane(firstChild, secondChild, false, skin)
            }
        actor.splitAmount = 0.35f
        actor.setSize(420f, 180f)
        actor.layout()
        return actor
    }

    private fun buildTree(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, Tree.TreeStyle::class.java, "TreeStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val actor =
            if (styleName != null && skin.has(styleName, Tree.TreeStyle::class.java)) {
                Tree<PreviewTreeNode, String>(skin, styleName)
            } else {
                Tree<PreviewTreeNode, String>(skin)
            }
        val nodes = linkedMapOf<String, PreviewTreeNode>()
        item.items.ifEmpty { listOf("Root", "Root/Child", "Root/Child/Leaf") }.forEach { path ->
            var currentPath = ""
            var parentNode: PreviewTreeNode? = null
            path.split('/').filter(String::isNotBlank).forEach { segment ->
                currentPath = if (currentPath.isBlank()) segment else "$currentPath/$segment"
                val node =
                    nodes.getOrPut(currentPath) {
                        PreviewTreeNode(Label(segment, skin), currentPath).also { created ->
                            if (parentNode == null) {
                                actor.add(created)
                            } else {
                                parentNode?.add(created)
                            }
                        }
                    }
                parentNode = node
            }
        }
        actor.expandAll()
        actor.pack()
        return actor
    }

    private fun buildTextTooltip(
        item: SkinEditorPreviewItem,
        primarySkin: Skin?,
        issues: MutableSet<PreviewBuildIssue>,
    ): Actor {
        val skin = skinFor(primarySkin, item.styleName, DefaultStyleName, TextTooltip.TextTooltipStyle::class.java, "TextTooltipStyle", issues)
        val styleName = item.styleName.takeIf { !it.isNullOrBlank() }
        val tooltip =
            if (styleName != null && skin.has(styleName, TextTooltip.TextTooltipStyle::class.java)) {
                TextTooltip(item.text.orEmpty(), skin, styleName)
            } else {
                TextTooltip(item.text.orEmpty(), skin)
            }
        tooltip.container.pack()
        return tooltip.container
    }

    @Suppress("CyclomaticComplexMethod")
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

    @Suppress("LongMethod", "CyclomaticComplexMethod")
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
            Button.ButtonStyle().apply {
                up = panelDrawable
                down = accentDrawable
                checked = accentDrawable
                over = accentDrawable
                disabled = outlineDrawable
            },
        )
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
                scrollStyle =
                    ScrollPane.ScrollPaneStyle().apply {
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
            "default",
            SplitPane.SplitPaneStyle().apply {
                handle = accentDrawable
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
        skin.add(
            "default",
            Tree.TreeStyle().apply {
                plus = accentDrawable
                minus = outlineDrawable
                selection = accentDrawable
                over = panelDrawable
            },
        )
        skin.add(
            "default",
            TextTooltip.TextTooltipStyle().apply {
                label = skin.get("default", Label.LabelStyle::class.java)
                background = panelDrawable
                wrapWidth = 260f
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
