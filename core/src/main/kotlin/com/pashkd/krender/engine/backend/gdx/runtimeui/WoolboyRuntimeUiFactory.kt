package com.pashkd.krender.engine.backend.gdx.runtimeui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
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
import com.badlogic.gdx.utils.Scaling
import com.pashkd.krender.engine.runtimeui.RuntimeUiActionHandler
import com.pashkd.krender.engine.runtimeui.RuntimeUiScreen

/**
 * Runtime UI actor factory for the Woolboy sandbox.
 *
 * This class owns Woolboy-specific screen ids, payload interpretation, layout,
 * button-to-action dispatch, and the Woolboy skin loaded from assets. The
 * generic backend only asks this factory whether it can build a given
 * [RuntimeUiScreen].
 */
internal class WoolboyRuntimeUiFactory(
    private val actionHandlerProvider: () -> RuntimeUiActionHandler?,
) : RuntimeUiActorFactory {
    companion object {
        private const val SkinPath = "ui/skins/craftacular-ui.json"
        private const val FullHeartTexturePath = "textures/woolboy/hud_heart_full.png"
        private const val EmptyHeartTexturePath = "textures/woolboy/hud_heart_empty.png"
        private const val LoadingScreenId = "woolboy.loading"
        private const val MainMenuScreenId = "woolboy.main_menu"
        private const val HudScreenId = "woolboy.hud"
        private const val FinalResultsScreenId = "woolboy.final_results"
        private const val HeartSlots = 3
        private const val HeartSize = 48f
    }

    private val uiSkin = Skin(Gdx.files.internal(SkinPath))
    private val fullHeartTexture = Texture(Gdx.files.internal(FullHeartTexturePath))
    private val emptyHeartTexture = Texture(Gdx.files.internal(EmptyHeartTexturePath))

    override fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor? = when (screen.id) {
        LoadingScreenId -> loadingScreen(screen)
        MainMenuScreenId -> mainMenuScreen(screen)
        HudScreenId -> hudScreen(screen)
        FinalResultsScreenId -> finalResultsScreen(screen)
        else -> null
    }

    override fun dispose() {
        fullHeartTexture.dispose()
        emptyHeartTexture.dispose()
        uiSkin.dispose()
    }

    private fun loadingScreen(screen: RuntimeUiScreen): Actor {
        val progress = screen.payload["progress"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        val root = fullScreenTable()
        val content = dialogTable().apply {
            add(label(screen.payload["title"] ?: "Loading...", "title")).padBottom(24f).row()
            add(progressBar(progress)).width(520f).height(28f)
        }
        root.add(content).width(640f).pad(40f)
        return root
    }

    private fun mainMenuScreen(screen: RuntimeUiScreen): Actor {
        val gameStarted = screen.payload["gameStarted"] == "true"
        val firstText = if (gameStarted) "Continue" else "Start Game"
        val firstAction = if (gameStarted) "woolboy.continue" else "woolboy.start"

        val stack = Stack().apply { setFillParent(true) }
        stack.add(
            Table().apply {
                setFillParent(true)
                setBackground(uiSkin.newDrawable("white", Color(0f, 0f, 0f, 0.72f)))
            },
        )
        stack.add(
            Table().apply {
                setFillParent(true)
                add(
                    dialogTable().apply {
                        add(label("Woolboy", "title")).padBottom(32f).row()
                        add(actionButton(firstText, firstAction)).width(400f).height(100f).padBottom(24f).row()
                        add(actionButton("Settings", "woolboy.settings")).width(400f).height(100f).padBottom(24f).row()
                        add(actionButton("Restart", "woolboy.restart")).width(400f).height(100f).padBottom(24f).row()
                        add(actionButton("Exit", "woolboy.exit")).width(400f).height(100f)
                    },
                ).width(640f).pad(40f)
            },
        )
        return stack
    }

    private fun hudScreen(screen: RuntimeUiScreen): Actor {
        val stack = Stack().apply { setFillParent(true) }

        val healthLabel = screen.payload["healthLabel"] ?: "100/100"
        val scores = screen.payload["scores"] ?: "0"
        val lives = screen.payload["lives"]?.toIntOrNull()?.coerceIn(0, HeartSlots) ?: HeartSlots

        val healthTable = Table().apply {
            add(leftLabel("Health: $healthLabel")).left()
        }

        stack.add(
            Container(healthTable).apply {
                setFillParent(true)
                align(Align.topLeft)
                padTop(32f)
                padLeft(32f)
            },
        )

        stack.add(
            Container(label("Score: $scores", "hud-score-title")).apply {
                setFillParent(true)
                align(Align.top)
                padTop(32f)
            },
        )

        stack.add(
            Container(livesTable(lives)).apply {
                setFillParent(true)
                align(Align.topRight)
                padTop(32f)
                padRight(32f)
            },
        )

        val controls = Table().apply {
            setBackground(uiSkin.getDrawable("window"))
            pad(20f)
            defaults().left()
            add(leftLabel("Controls", "hud-controls")).left().padBottom(12f).row()
            add(leftLabel("W/A/S/D - Move", "hud-controls")).left().row()
            add(leftLabel("RMB - Camera", "hud-controls")).left().row()
            add(leftLabel("Space - Jump", "hud-controls")).left().row()
            add(leftLabel("F - Greeting", "hud-controls")).left().row()
            add(leftLabel("Esc - Menu", "hud-controls")).left().row()
        }

        stack.add(
            Container(controls).apply {
                setFillParent(true)
                align(Align.left)
                padLeft(32f)
            },
        )

        return stack
    }

    private fun livesTable(lives: Int): Table =
        Table().apply {
            repeat(HeartSlots) { index ->
                val texture = if (index < lives) fullHeartTexture else emptyHeartTexture
                add(
                    Image(texture).apply {
                        setScaling(Scaling.fit)
                    },
                ).width(HeartSize).height(HeartSize)
            }
        }

    private fun finalResultsScreen(screen: RuntimeUiScreen): Actor {
        val scores = screen.payload["scores"] ?: "0"

        val root = fullScreenTable()
        root.setBackground(uiSkin.newDrawable("white", Color(0f, 0f, 0f, 0.72f)))
        val content = dialogTable().apply {
            add(label("Final Result", "title")).padBottom(24f).row()
            add(bodyLabel("Score: $scores")).padBottom(32f).row()
            add(actionButton("Restart", "woolboy.restart")).width(400f).height(100f).padBottom(24f).row()
            add(actionButton("Settings", "woolboy.settings")).width(400f).height(100f).padBottom(24f).row()
            add(actionButton("Exit", "woolboy.exit")).width(400f).height(100f)
        }
        root.add(content).width(640f).pad(40f)
        return root
    }

    private fun fullScreenTable(): Table =
        Table().apply {
            setFillParent(true)
        }

    private fun dialogTable(): Table =
        Table().apply {
            setBackground(uiSkin.getDrawable("window"))
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

    private fun leftLabel(
        text: String,
        styleName: String = "default",
    ): Label =
        label(text, styleName).apply {
            setAlignment(Align.left)
        }

    private fun progressBar(
        value: Float,
        styleName: String = "default-horizontal",
    ): Container<ProgressBar> {
        val progressBar = ProgressBar(0f, 1f, 0.01f, false, uiSkin.get(styleName, ProgressBar.ProgressBarStyle::class.java))
        progressBar.value = value
        return Container(progressBar).apply {
            fill()
        }
    }

    private fun actionButton(
        text: String,
        action: String,
    ): TextButton =
        TextButton(text, uiSkin).apply {
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
