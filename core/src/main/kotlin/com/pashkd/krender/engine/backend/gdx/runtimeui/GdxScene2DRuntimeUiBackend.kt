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
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
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
 * Scene2D-backed runtime UI backend used by the LibGDX runtime.
 */
class GdxScene2DRuntimeUiBackend(
    private val logger: Logger,
    private val input: GdxInputService,
) : RuntimeUiBackend {
    private val skin = GdxRuntimeUiSkinFactory().create()
    private val screenFactory = GdxRuntimeUiScreenFactory(skin) { actionHandler }
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

    companion object {
        private const val TAG = "GdxScene2DRuntimeUiBackend"
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

private class GdxRuntimeUiScreenFactory(
    private val skin: Skin,
    private val actionHandlerProvider: () -> RuntimeUiActionHandler?,
) {
    fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor = when (screen.id) {
        "woolboy.loading" -> loadingScreen(screen)
        "woolboy.main_menu" -> mainMenuScreen(screen)
        "woolboy.hud" -> hudScreen(screen)
        "woolboy.final_results" -> finalResultsScreen(screen)
        else -> fallbackScreen(screen, layer)
    }

    private fun loadingScreen(screen: RuntimeUiScreen): Actor {
        val root = fullScreenTable()
        val content = dialogTable().apply {
            add(label(screen.payload["title"] ?: "Loading", "title")).padBottom(20f).row()
            add(bodyLabel(screen.payload["message"] ?: "Preparing scene...")).width(520f)
        }
        root.add(content).width(640f).pad(40f)
        return root
    }

    private fun mainMenuScreen(screen: RuntimeUiScreen): Actor {
        val stack = Stack().apply { setFillParent(true) }
        stack.add(
            Table().apply {
                setFillParent(true)
                setBackground(skin.getDrawable("overlay"))
            },
        )
        stack.add(
            Table().apply {
                setFillParent(true)
                add(
                    dialogTable().apply {
                        add(label(screen.payload["title"] ?: "Woolboy", "title")).padBottom(28f).row()
                        add(bodyLabel(screen.payload["subtitle"] ?: "Runtime UI MVP")).padBottom(28f).row()
                        add(actionButton("Start", "woolboy.start")).width(420f).height(72f).padBottom(16f).row()
                        if (screen.payload["showContinue"] == "true") {
                            add(actionButton("Continue", "woolboy.continue")).width(420f).height(72f).padBottom(16f).row()
                        }
                        add(actionButton("Settings", "woolboy.settings")).width(420f).height(72f).padBottom(16f).row()
                        add(actionButton("Exit", "woolboy.exit")).width(420f).height(72f)
                    },
                ).width(720f).pad(40f)
            },
        )
        return stack
    }

    private fun hudScreen(screen: RuntimeUiScreen): Actor {
        val stack = Stack().apply { setFillParent(true) }
        stack.add(
            Table().apply {
                setFillParent(true)
                add(label(screen.payload["title"] ?: "Woolboy HUD")).expand().top().left().pad(24f)
            },
        )
        stack.add(
            Table().apply {
                setFillParent(true)
                val healthPercent = screen.payload["healthPercent"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                val healthText = screen.payload["healthLabel"] ?: "Health"
                val content = Table().apply {
                    add(label(healthText)).left().padBottom(8f).row()
                    add(progressBar(healthPercent)).width(360f).height(28f)
                }
                add(content).expand().bottom().left().pad(24f)
            },
        )
        return stack
    }

    private fun finalResultsScreen(screen: RuntimeUiScreen): Actor {
        val root = fullScreenTable()
        root.setBackground(skin.getDrawable("overlay"))
        val content = dialogTable().apply {
            add(label(screen.payload["title"] ?: "Run Complete", "title")).padBottom(24f).row()
            add(bodyLabel(screen.payload["summary"] ?: "Results are not implemented yet.")).width(520f).padBottom(24f).row()
            add(actionButton("Restart", "woolboy.restart")).width(360f).height(72f).padBottom(16f).row()
            add(actionButton("Exit", "woolboy.exit")).width(360f).height(72f)
        }
        root.add(content).width(640f).pad(40f)
        return root
    }

    private fun fallbackScreen(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor {
        val root = fullScreenTable()
        val content = dialogTable().apply {
            add(label(screen.payload["title"] ?: screen.id, "title")).padBottom(20f).row()
            add(
                bodyLabel(
                    screen.payload["message"]
                        ?: "No Scene2D runtime UI builder is registered for layer '$layer'.",
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
            setBackground(skin.getDrawable("panel"))
            pad(32f)
            defaults().center()
        }

    private fun label(
        text: String,
        styleName: String = "default",
    ): Label =
        Label(text, skin.get(styleName, Label.LabelStyle::class.java)).apply {
            setAlignment(Align.center)
        }

    private fun bodyLabel(text: String): Label =
        label(text).apply {
            wrap = true
        }

    private fun progressBar(value: Float): Container<ProgressBar> {
        val progressBar = ProgressBar(0f, 1f, 0.01f, false, skin.get("runtime", ProgressBar.ProgressBarStyle::class.java))
        progressBar.value = value
        return Container(progressBar).apply {
            fill()
        }
    }

    private fun actionButton(
        text: String,
        action: String,
    ): TextButton =
        TextButton(text, skin).apply {
            addListener(
                object : ClickListener() {
                    override fun clicked(
                        event: InputEvent?,
                        x: Float,
                        y: Float,
                    ) {
                        actionHandlerProvider()?.onRuntimeUiAction(action)
                    }
                },
            )
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
