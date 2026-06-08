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
 * serialization, or UiComposerScene editor UI.
 */
class GdxUiSceneBuilder(
    private val logger: Logger,
) : Disposable {
    companion object {
        private const val TAG = "GdxUiSceneBuilder"
        private const val DefaultProgressStyle = "default-horizontal"
    }

    private val ownedSkins = mutableListOf<Skin>()
    private val ownedTextures = mutableListOf<Texture>()

    /**
     * Builds a Scene2D actor tree from a decoded `.krui` document.
     *
     * The [payload] provides simple string bindings for text placeholders and
     * progress values. For the MVP, the builder loads the Skin and Image textures
     * directly from project-relative paths; asset-service-backed caching and
     * disposal ownership should replace this later.
     */
    fun build(
        document: UiSceneDocument,
        payload: Map<String, String>,
        actionHandler: RuntimeUiActionHandler?,
    ): Actor {
        // TODO: Cache Skin instances by project-relative path once runtime UI scene usage grows.
        val skin = Skin(Gdx.files.internal(document.skin))
        ownedSkins += skin
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
        ownedTextures.forEach(Texture::dispose)
        ownedTextures.clear()
        ownedSkins.forEach(Skin::dispose)
        ownedSkins.clear()
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
            UiSceneNodeType.Image -> buildImage(node)
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
            node.children.forEachIndexed { index, child ->
                val childActor = buildNode(child, skin, payload, actionHandler, isRoot = false)
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
            wrap = true
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
        val action = node.action?.takeIf(String::isNotBlank)
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

    private fun buildImage(node: UiSceneNode): Image {
        val texturePath = node.texture?.takeIf(String::isNotBlank)
        if (texturePath == null) {
            logger.warn(TAG) { "Image '${node.id}' has no texture path; using an empty Actor-sized Image." }
            return Image()
        }

        // TODO: Load images through the asset service with caching and lifetime tracking.
        val texture = Texture(Gdx.files.internal(texturePath))
        ownedTextures += texture
        return Image(texture).apply {
            setScaling(toGdxScaling(node.scaling))
        }
    }

    private fun Table.applyPadding(spacing: UiSceneSpacing) {
        pad(spacing.top, spacing.left, spacing.bottom, spacing.right)
    }

    private fun Container<Actor>.applyPadding(spacing: UiSceneSpacing) {
        pad(spacing.top, spacing.left, spacing.bottom, spacing.right)
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
