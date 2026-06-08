package com.pashkd.krender.engine.backend.gdx.ui.runtime

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.GdxInputService
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler
import com.pashkd.krender.engine.ui.runtime.RuntimeUiBackend
import com.pashkd.krender.engine.ui.runtime.RuntimeUiLayerState
import com.pashkd.krender.engine.ui.runtime.RuntimeUiScreen

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
        private const val DefaultSkinPath = "ui/skins/default_ui.json"
    }

    private val skin = Skin(Gdx.files.internal(DefaultSkinPath))
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
        screenFactory.dispose()
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
interface RuntimeUiActorFactory {
    fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor?

    fun dispose() = Unit
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

    fun dispose() {
        factories.forEach(RuntimeUiActorFactory::dispose)
        fallbackFactory.dispose()
    }
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
            add(label(screen.payload["title"] ?: screen.id)).padBottom(20f).row()
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
            setBackground(uiSkin.getDrawable("default-window"))
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
