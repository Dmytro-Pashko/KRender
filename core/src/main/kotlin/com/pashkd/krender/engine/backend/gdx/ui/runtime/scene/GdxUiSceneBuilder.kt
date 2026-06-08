package com.pashkd.krender.engine.backend.gdx.ui.runtime.scene

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Scaling
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler
import com.pashkd.krender.engine.ui.scene.UiSceneAlign
import com.pashkd.krender.engine.ui.scene.UiSceneBindings
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneScaling
import com.pashkd.krender.engine.ui.scene.UiSceneSpacing

/**
 * Builds LibGDX Scene2D actors from shared `.krui` documents.
 *
 * This class belongs to the runtime backend, not the shared model or future editor
 * UI. It maps the KRender-native `.krui` widget subset to real Scene2D widgets and
 * deliberately keeps Scene2D as the layout engine. It does not implement custom
 * rendering, Skin editing, Asset Browser integration, arbitrary Actor
 * serialization, or UiComposerScene editor UI. `.krui` background values are
 * existing Skin drawable names only.
 */
class GdxUiSceneBuilder(
    private val logger: Logger,
) : Disposable {
    companion object {
        private const val TAG = "GdxUiSceneBuilder"
        private const val DefaultProgressStyle = "default-horizontal"
    }

    private val skinCache = mutableMapOf<String, Skin>()
    private val textureCache = mutableMapOf<String, Texture>()

    /**
     * Builds a Scene2D actor tree from a decoded `.krui` document.
     *
     * The [payload] provides simple string bindings for text placeholders, button
     * action placeholders, and progress values. For the MVP, the builder caches
     * Skin and Image textures by project-relative path; asset-service-backed
     * loading and disposal ownership should replace this later.
     */
    fun build(
        document: UiSceneDocument,
        payload: Map<String, String>,
        actionHandler: RuntimeUiActionHandler?,
    ): Actor {
        // TODO: Replace this path cache with AssetService-backed loading and explicit lifetime tracking.
        val skin = skin(document.skin)
        return buildNode(
            node = document.root,
            skin = skin,
            payload = payload,
            actionHandler = actionHandler,
            isRoot = true,
        )
    }

    /**
     * Disposes Skin and Texture resources loaded by this runtime builder.
     */
    override fun dispose() {
        textureCache.values.forEach(Texture::dispose)
        textureCache.clear()
        skinCache.values.forEach(Skin::dispose)
        skinCache.clear()
    }

    private fun buildNode(
        node: UiSceneNode,
        skin: Skin,
        payload: Map<String, String>,
        actionHandler: RuntimeUiActionHandler?,
        isRoot: Boolean,
    ): Actor {
        val actor = when (node.type) {
            UiSceneNodeType.Stack -> buildStack(node, skin, payload, actionHandler, isRoot)
            UiSceneNodeType.Table -> buildTable(node, skin, payload, actionHandler, isRoot)
            UiSceneNodeType.Container -> buildContainer(node, skin, payload, actionHandler, isRoot)
            UiSceneNodeType.Label -> buildLabel(node, skin, payload)
            UiSceneNodeType.TextButton -> buildTextButton(node, skin, payload, actionHandler)
            UiSceneNodeType.ProgressBar -> buildProgressBar(node, skin, payload)
            UiSceneNodeType.Image -> buildImage(node, payload)
            UiSceneNodeType.Space -> Actor()
        }
        actor.isVisible = node.visible
        applyActorSize(actor, node)
        return actor
    }

    private fun buildStack(
        node: UiSceneNode,
        skin: Skin,
        payload: Map<String, String>,
        actionHandler: RuntimeUiActionHandler?,
        isRoot: Boolean,
    ): Stack =
        Stack().apply {
            setFillParent(isRoot)
            node.children.forEach { child ->
                add(buildNode(child, skin, payload, actionHandler, isRoot = false))
            }
        }

    private fun buildTable(
        node: UiSceneNode,
        skin: Skin,
        payload: Map<String, String>,
        actionHandler: RuntimeUiActionHandler?,
        isRoot: Boolean,
    ): Table =
        Table().apply {
            setFillParent(isRoot)
            applyPadding(node.padding)
            applyBackground(node, skin)
            node.children.forEachIndexed { index, child ->
                val childActor = buildNode(child, skin, payload, actionHandler, isRoot = false)
                // MVP layout rule:
                // `.krui` Table currently adds every child as a new row. This intentionally
                // keeps the runtime builder simple while UI Composer is not implemented yet.
                // Horizontal rows/cell metadata can be added later as an explicit schema change.
                add(childActor)
                    .applyNodeSize(child)
                    .padBottom(if (index < node.children.lastIndex) node.spacing else 0f)
                row()
            }
        }

    private fun buildContainer(
        node: UiSceneNode,
        skin: Skin,
        payload: Map<String, String>,
        actionHandler: RuntimeUiActionHandler?,
        isRoot: Boolean,
    ): Container<Actor> {
        val content = when {
            node.children.isEmpty() -> Table()
            node.children.size == 1 -> buildNode(node.children.first(), skin, payload, actionHandler, isRoot = false)
            else -> {
                // MVP fallback:
                // Scene2D Container is a single-child widget. `.krui` is editor-authored,
                // so invalid/multi-child containers may happen while building early tools.
                // Instead of dropping children, we wrap them in a Stack so the document still
                // previews/renders. The validator should warn about suspicious structures;
                // future UI Composer should either prevent this or explicitly insert a Stack.
                logger.warn(TAG) {
                    "Container '${node.id}' has ${node.children.size} children; wrapping them in a Stack for `.krui` MVP."
                }
                buildStack(node.copy(type = UiSceneNodeType.Stack), skin, payload, actionHandler, isRoot = false)
            }
        }

        return Container<Actor>().apply {
            setFillParent(isRoot)
            setActor(content)
            node.align?.let { align(toGdxAlign(it)) }
            applyPadding(node.padding)
            applyBackground(node, skin)
        }
    }

    private fun buildLabel(
        node: UiSceneNode,
        skin: Skin,
        payload: Map<String, String>,
    ): Label {
        val text = UiSceneBindings.bindText(node.text ?: "", payload)
        return if (node.style.isNullOrBlank()) {
            Label(text, skin)
        } else {
            Label(text, skin, node.style)
        }.apply {
            // Only explicit-width labels wrap. Unbounded wrapped labels can collapse
            // to a tiny preferred width and render words or characters vertically.
            wrap = node.width != null
            node.align?.let { setAlignment(toGdxAlign(it)) }
        }
    }

    private fun buildTextButton(
        node: UiSceneNode,
        skin: Skin,
        payload: Map<String, String>,
        actionHandler: RuntimeUiActionHandler?,
    ): TextButton {
        val text = UiSceneBindings.bindText(node.text ?: "", payload)
        val button = if (node.style.isNullOrBlank()) {
            TextButton(text, skin)
        } else {
            TextButton(text, skin, node.style)
        }
        val action = node.action
            ?.let { UiSceneBindings.bindText(it, payload) }
            ?.takeIf(String::isNotBlank)
        if (action != null) {
            button.addListener(
                object : ClickListener() {
                    override fun clicked(
                        event: InputEvent?,
                        x: Float,
                        y: Float,
                    ) {
                        actionHandler?.onRuntimeUiAction(action)
                    }
                },
            )
        }
        return button
    }

    private fun buildProgressBar(
        node: UiSceneNode,
        skin: Skin,
        payload: Map<String, String>,
    ): ProgressBar {
        require(node.max > node.min) {
            "ProgressBar '${node.id}' must have max greater than min, but min=${node.min}, max=${node.max}."
        }
        require(node.step > 0f) {
            "ProgressBar '${node.id}' must have positive step, but step=${node.step}."
        }
        val styleName = node.style?.takeIf(String::isNotBlank) ?: DefaultProgressStyle
        val progressBar = ProgressBar(
            node.min,
            node.max,
            node.step,
            false,
            skin.get(styleName, ProgressBar.ProgressBarStyle::class.java),
        )
        val fallback = node.value ?: node.min
        progressBar.value = UiSceneBindings
            .boundFloat(node.valueBinding, payload, fallback)
            .coerceIn(node.min, node.max)
        return progressBar
    }

    private fun buildImage(
        node: UiSceneNode,
        payload: Map<String, String>,
    ): Image {
        val texturePath = node.texture
            ?.let { UiSceneBindings.bindText(it, payload) }
            ?.takeIf(String::isNotBlank)
        if (texturePath == null) {
            logger.warn(TAG) { "Image '${node.id}' has no texture path; using an empty Actor-sized Image." }
            return Image()
        }

        val texture = texture(texturePath)
        return Image(texture).apply {
            setScaling(toGdxScaling(node.scaling))
        }
    }

    private fun skin(path: String): Skin =
        skinCache.getOrPut(path) {
            Skin(Gdx.files.internal(path))
        }

    private fun texture(path: String): Texture =
        textureCache.getOrPut(path) {
            Texture(Gdx.files.internal(path))
        }

    private fun Table.applyPadding(spacing: UiSceneSpacing) {
        pad(spacing.top, spacing.left, spacing.bottom, spacing.right)
    }

    private fun Container<Actor>.applyPadding(spacing: UiSceneSpacing) {
        pad(spacing.top, spacing.left, spacing.bottom, spacing.right)
    }

    private fun Table.applyBackground(
        node: UiSceneNode,
        skin: Skin,
    ) {
        node.background?.takeIf(String::isNotBlank)?.let { drawableName ->
            setBackground(skin.getDrawable(drawableName))
        }
    }

    private fun Container<Actor>.applyBackground(
        node: UiSceneNode,
        skin: Skin,
    ) {
        node.background?.takeIf(String::isNotBlank)?.let { drawableName ->
            background(skin.getDrawable(drawableName))
        }
    }

    private fun Cell<Actor>.applyNodeSize(node: UiSceneNode): Cell<Actor> {
        node.width?.let(::width)
        node.height?.let(::height)
        return this
    }

    private fun applyActorSize(
        actor: Actor,
        node: UiSceneNode,
    ) {
        if (node.width != null || node.height != null) {
            actor.setSize(node.width ?: actor.width, node.height ?: actor.height)
        }
    }

    private fun toGdxAlign(align: UiSceneAlign): Int =
        when (align) {
            UiSceneAlign.TopLeft -> Align.topLeft
            UiSceneAlign.Top -> Align.top
            UiSceneAlign.TopRight -> Align.topRight
            UiSceneAlign.Left -> Align.left
            UiSceneAlign.Center -> Align.center
            UiSceneAlign.Right -> Align.right
            UiSceneAlign.BottomLeft -> Align.bottomLeft
            UiSceneAlign.Bottom -> Align.bottom
            UiSceneAlign.BottomRight -> Align.bottomRight
        }

    private fun toGdxScaling(scaling: UiSceneScaling): Scaling =
        when (scaling) {
            UiSceneScaling.Fit -> Scaling.fit
            UiSceneScaling.Fill -> Scaling.fill
            UiSceneScaling.Stretch -> Scaling.stretch
            UiSceneScaling.None -> Scaling.none
        }
}
