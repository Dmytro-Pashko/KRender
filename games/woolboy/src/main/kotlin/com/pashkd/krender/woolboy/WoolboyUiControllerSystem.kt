package com.pashkd.krender.woolboy

import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler
import com.pashkd.krender.engine.ui.runtime.RuntimeUiLayers
import com.pashkd.krender.engine.ui.runtime.RuntimeUiScreen
import com.pashkd.krender.engine.ui.runtime.RuntimeUiService

/**
 * Bridges Woolboy sandbox state to the backend-neutral Runtime UI service.
 *
 * The system owns the simple Woolboy UI flow, publishes one screen payload per
 * active [WoolboyUiMode], handles Woolboy runtime UI actions, and gates gameplay
 * input through [WoolboyGameState.playerInputEnabled]. It intentionally does
 * not create backend actors directly; backend-specific actor construction lives
 * in the Woolboy desktop app runtime UI factory.
 */
class WoolboyUiControllerSystem(
    private val runtimeUi: RuntimeUiService,
    private val gameState: WoolboyGameState,
    private val input: InputService,
    private val logger: Logger,
    private val requestExit: () -> Unit,
) : System(),
    RuntimeUiActionHandler {
    companion object {
        private const val TAG = "WoolboyUiControllerSystem"
        private const val HeartSlots = 3
        private const val FullHeartTexturePath = "assets/woolboy/textures/woolboy/hud_heart_full.png"
        private const val EmptyHeartTexturePath = "assets/woolboy/textures/woolboy/hud_heart_empty.png"
    }

    private var loadingPresented = false
    private var lastPayloadKey: String? = null

    override fun onAdded(world: SceneWorld) {
        runtimeUi.setActionHandler(this)
    }

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        if (gameState.uiMode == WoolboyUiMode.Loading) {
            gameState.loadingProgress = 1f
            if (loadingPresented) {
                gameState.showMainMenu()
            } else {
                loadingPresented = true
            }
        }

        if (gameState.uiMode == WoolboyUiMode.Gameplay && input.snapshot().wasPressed(Key.Escape)) {
            gameState.showMainMenu()
            syncUi(force = true)
            return
        }

        syncUi()
    }

    override fun onRuntimeUiAction(action: String) {
        when (action) {
            "woolboy.start" -> gameState.startNewGame()
            "woolboy.continue" -> gameState.continueGame()
            "woolboy.restart" -> gameState.restartGame()
            "woolboy.settings" ->
                logger.info(TAG) {
                    "Settings action requested, not implemented yet."
                }

            "woolboy.exit" -> requestExit()
            else -> logger.warn(TAG) { "Unknown Woolboy UI action '$action'" }
        }

        syncUi(force = true)
    }

    private fun syncUi(force: Boolean = false) {
        val payloadKey = payloadKey()
        if (!force && lastPayloadKey == payloadKey) return
        lastPayloadKey = payloadKey

        when (gameState.uiMode) {
            WoolboyUiMode.Loading -> {
                runtimeUi.clearLayer(RuntimeUiLayers.Hud)
                runtimeUi.setLayer(RuntimeUiLayers.Modal, loadingScreen())
            }

            WoolboyUiMode.MainMenu -> {
                runtimeUi.clearLayer(RuntimeUiLayers.Hud)
                runtimeUi.setLayer(RuntimeUiLayers.Modal, mainMenuScreen())
            }

            WoolboyUiMode.Gameplay -> {
                runtimeUi.clearLayer(RuntimeUiLayers.Modal)
                runtimeUi.setLayer(RuntimeUiLayers.Hud, hudScreen())
            }

            WoolboyUiMode.FinalResults -> {
                runtimeUi.clearLayer(RuntimeUiLayers.Hud)
                runtimeUi.setLayer(RuntimeUiLayers.Modal, finalResultsScreen())
            }
        }
    }

    private fun payloadKey(): String =
        "${gameState.uiMode}:${gameState.gameStarted}:${gameState.loadingProgress}:${gameState.health}:" +
            "${gameState.maxHealth}:${gameState.lives}:${gameState.scores}"

    private fun loadingScreen(): RuntimeUiScreen =
        RuntimeUiScreen(
            id = WoolboyRuntimeUiScreens.Loading,
            payload =
                mapOf(
                    "title" to "Woolboy Demo",
                    "subtitle" to "KRender SDK client application",
                    "assetSource" to "Bundled from games:woolboy",
                    "progress" to gameState.loadingProgress.toString(),
                ),
        )

    private fun mainMenuScreen(): RuntimeUiScreen {
        val gameStarted = gameState.gameStarted
        return RuntimeUiScreen(
            id = WoolboyRuntimeUiScreens.MainMenu,
            payload =
                mapOf(
                    "gameStarted" to gameStarted.toString(),
                    "primaryButtonText" to if (gameStarted) "Continue" else "Start Game",
                    "primaryButtonAction" to if (gameStarted) "woolboy.continue" else "woolboy.start",
                    "module" to "games:woolboy",
                    "engine" to "KRender SDK",
                ),
        )
    }

    private fun hudScreen(): RuntimeUiScreen {
        val lives = gameState.lives.coerceIn(0, HeartSlots)
        return RuntimeUiScreen(
            id = WoolboyRuntimeUiScreens.Hud,
            payload =
                mapOf(
                    "healthLabel" to "${gameState.health}/${gameState.maxHealth}",
                    "scores" to gameState.scores.toString(),
                    "life1Texture" to lifeTexture(slot = 1, lives = lives),
                    "life2Texture" to lifeTexture(slot = 2, lives = lives),
                    "life3Texture" to lifeTexture(slot = 3, lives = lives),
                    "demoTitle" to "Woolboy Demo",
                    "demoSubtitle" to "KRender SDK client application",
                ),
        )
    }

    private fun lifeTexture(
        slot: Int,
        lives: Int,
    ): String = if (slot <= lives) FullHeartTexturePath else EmptyHeartTexturePath

    private fun finalResultsScreen(): RuntimeUiScreen =
        RuntimeUiScreen(
            id = WoolboyRuntimeUiScreens.FinalResults,
            payload =
                mapOf(
                    "scores" to gameState.scores.toString(),
                ),
        )
}

object WoolboyRuntimeUiScreens {
    const val Loading = "woolboy.loading"
    const val MainMenu = "woolboy.main_menu"
    const val Hud = "woolboy.hud"
    const val FinalResults = "woolboy.final_results"
}
