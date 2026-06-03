package com.pashkd.krender.engine.backend.gdx.runtimeui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.GdxInputService
import com.pashkd.krender.engine.runtimeui.RuntimeUiActionHandler
import com.pashkd.krender.engine.runtimeui.RuntimeUiBackend
import com.pashkd.krender.engine.runtimeui.RuntimeUiLayerState
import com.pashkd.krender.engine.runtimeui.RuntimeUiScreen

/**
 * LibGDX runtime UI backend that renders backend-neutral runtime UI screens.
 */
class GdxRuntimeUiBackend(
    private val logger: Logger,
    private val input: GdxInputService,
    screenFactoryProvider: (Skin, () -> RuntimeUiActionHandler?) -> List<RuntimeUiActorFactory> = { _, _ ->
        emptyList()
    },
) : RuntimeUiBackend {
    companion object {
        private const val TAG = "GdxRuntimeUiBackend"
    }

    private val skin = GdxRuntimeUiSkinFactory().create()
    private val screenFactory = CompositeRuntimeUiActorFactory(
        factories = screenFactoryProvider(skin) { actionHandler },
        fallbackFactory = FallbackRuntimeUiActorFactory(skin),
    )
    private val stage = Stage(ScreenViewport())
    private var actionHandler: RuntimeUiActionHandler? = null

    init {
        input.addProcessor(stage)
    }

    override fun setActionHandler(handler: RuntimeUiActionHandler?) {
        actionHandler = handler
    }

    override fun syncLayers(layers: List<RuntimeUiLayerState>) {
        stage.clear()
        layers.forEach { layerState ->
            val screen = layerState.screen ?: return@forEach
            val actor = screenFactory.create(screen, layerState.layer)
            stage.addActor(actor)
        }
        logger.debug(TAG) { "Synced ${layers.size} runtime UI layer(s)." }
    }

    override fun update(dt: Float) {
        stage.act(dt)
    }

    override fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glColorMask(true, true, true, true)
        stage.draw()
        forceOpaqueBackBufferAlpha()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun clear() {
        stage.clear()
    }

    override fun dispose() {
        input.removeProcessor(stage)
        stage.dispose()
        skin.dispose()
    }

    private fun forceOpaqueBackBufferAlpha() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glColorMask(false, false, false, true)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glColorMask(true, true, true, true)
        Gdx.gl.glDepthMask(true)
    }
}

/**
 * Creates a LibGDX actor for a backend-neutral runtime UI screen.
 *
 * Implementations return null when they do not own the supplied screen id, which
 * lets the generic backend compose scene-specific factories without embedding
 * scene-specific layout code.
 */
fun interface RuntimeUiActorFactory {
    fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor?
}

private class CompositeRuntimeUiActorFactory(
    private val factories: List<RuntimeUiActorFactory>,
    private val fallbackFactory: RuntimeUiActorFactory,
) {
    fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor = factories.firstNotNullOfOrNull { factory -> factory.create(screen, layer) }
        ?: fallbackFactory.create(screen, layer)
        ?: error("Fallback runtime UI factory did not create a screen for '${screen.id}'.")
}

private class FallbackRuntimeUiActorFactory(
    private val uiSkin: Skin,
) : RuntimeUiActorFactory {
    override fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor {
        val root = fullScreenTable()
        val content = dialogTable().apply {
            add(label(screen.payload["title"] ?: screen.id, "title")).padBottom(20f).row()
            add(
                bodyLabel(
                    screen.payload["message"]
                        ?: "No runtime UI builder is registered for layer '$layer'.",
                ),
            ).width(540f)
        }
        root.add(content).width(680f).pad(40f)
        return root
    }

    private fun fullScreenTable(): Table =
        Table().apply {
            setFillParent(true)
        }

    private fun dialogTable(): Table =
        Table().apply {
            setBackground(uiSkin.getDrawable("panel"))
            pad(32f)
            defaults().center()
        }

    private fun label(
        text: String,
        styleName: String = "default",
    ): Label =
        Label(text, uiSkin.get(styleName, Label.LabelStyle::class.java)).apply {
            setAlignment(Align.center)
        }

    private fun bodyLabel(text: String): Label =
        label(text).apply {
            wrap = true
        }
}

private class GdxRuntimeUiSkinFactory {
    fun create(): Skin {
        val skin = Skin()
        val bodyFont = BitmapFont()
        val titleFont = BitmapFont().apply { data.setScale(2f) }
        val buttonFont = BitmapFont().apply { data.setScale(1.7f) }

        skin.add("default-font", bodyFont)
        skin.add("title-font", titleFont)
        skin.add("button-font", buttonFont)

        skin.add("white", textureDrawable(skin, "white", Color(1f, 1f, 1f, 1f)), Drawable::class.java)
        skin.add("overlay", textureDrawable(skin, "overlay", Color(0.03f, 0.04f, 0.06f, 0.82f)), Drawable::class.java)
        skin.add("panel", roundedDrawable(skin, "panel", Color(0.1f, 0.11f, 0.14f, 0.96f)), Drawable::class.java)
        skin.add("button-up", roundedDrawable(skin, "button-up", Color(0.08f, 0.08f, 0.1f, 1f)), Drawable::class.java)
        skin.add("button-over", roundedDrawable(skin, "button-over", Color(0.16f, 0.16f, 0.2f, 1f)), Drawable::class.java)
        skin.add("button-down", roundedDrawable(skin, "button-down", Color(0.22f, 0.22f, 0.27f, 1f)), Drawable::class.java)
        skin.add(
            "progress-bg",
            roundedDrawable(skin, "progress-bg", Color(0.45f, 0.08f, 0.08f, 1f), width = 32, height = 20, radius = 8),
            Drawable::class.java,
        )
        skin.add(
            "progress-fill",
            roundedDrawable(skin, "progress-fill", Color(0.14f, 0.72f, 0.22f, 1f), width = 32, height = 20, radius = 8),
            Drawable::class.java,
        )

        skin.add(
            "default",
            Label.LabelStyle(bodyFont, Color.WHITE),
        )
        skin.add(
            "title",
            Label.LabelStyle(titleFont, Color.WHITE),
        )
        skin.add(
            "default",
            TextButton.TextButtonStyle().apply {
                up = skin.getDrawable("button-up")
                over = skin.getDrawable("button-over")
                down = skin.getDrawable("button-down")
                font = buttonFont
                fontColor = Color.WHITE
                overFontColor = Color.WHITE
                downFontColor = Color.WHITE
            },
        )
        skin.add(
            "runtime",
            ProgressBar.ProgressBarStyle().apply {
                background = skin.getDrawable("progress-bg")
                knobBefore = skin.getDrawable("progress-fill")
                knob = skin.getDrawable("progress-fill")
            },
        )

        return skin
    }

    private fun textureDrawable(
        skin: Skin,
        name: String,
        color: Color,
    ): TextureRegionDrawable {
        val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        skin.add("$name-texture", texture)
        return TextureRegionDrawable(TextureRegion(texture))
    }

    private fun roundedDrawable(
        skin: Skin,
        name: String,
        color: Color,
        width: Int = 48,
        height: Int = 48,
        radius: Int = 12,
    ): NinePatchDrawable {
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        pixmap.setColor(0f, 0f, 0f, 0f)
        pixmap.fill()
        pixmap.setColor(color)
        pixmap.fillRectangle(radius, 0, width - radius * 2, height)
        pixmap.fillRectangle(0, radius, width, height - radius * 2)
        pixmap.fillCircle(radius, radius, radius)
        pixmap.fillCircle(width - radius - 1, radius, radius)
        pixmap.fillCircle(radius, height - radius - 1, radius)
        pixmap.fillCircle(width - radius - 1, height - radius - 1, radius)

        val texture = Texture(pixmap)
        pixmap.dispose()
        skin.add("$name-texture", texture)

        return NinePatchDrawable(NinePatch(texture, radius, radius, radius, radius))
    }
}
