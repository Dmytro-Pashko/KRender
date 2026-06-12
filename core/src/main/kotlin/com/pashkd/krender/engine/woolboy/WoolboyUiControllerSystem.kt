package com.pashkd.krender.engine.woolboy

import com.pashkd.krender.engine.api.*
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
 * in the Woolboy runtime UI factory.
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
        private const val FullHeartTexturePath = "textures/woolboy/hud_heart_full.png"
        private const val EmptyHeartTexturePath = "textures/woolboy/hud_heart_empty.png"
    }

    private var loadingPresented = false
    private var lastPayloadKey: String? = null

    /** Registers this controller as the active Runtime UI action handler. */
    override fun onAdded(world: SceneWorld) {
        runtimeUi.setActionHandler(this)
    }

    /** Advances the loading-to-menu transition, handles Esc, and synchronizes Runtime UI layers. */
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

    /** Applies button actions emitted by the Woolboy runtime UI factory. */
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
            id = "woolboy.loading",
            payload =
                mapOf(
                    "title" to "Loading...",
                    "progress" to gameState.loadingProgress.toString(),
                ),
        )

    private fun mainMenuScreen(): RuntimeUiScreen {
        val gameStarted = gameState.gameStarted
        return RuntimeUiScreen(
            id = "woolboy.main_menu",
            payload =
                mapOf(
                    "gameStarted" to gameStarted.toString(),
                    "primaryButtonText" to if (gameStarted) "Continue" else "Start Game",
                    "primaryButtonAction" to if (gameStarted) "woolboy.continue" else "woolboy.start",
                ),
        )
    }

    private fun hudScreen(): RuntimeUiScreen {
        val lives = gameState.lives.coerceIn(0, HeartSlots)
        return RuntimeUiScreen(
            id = "woolboy.hud",
            payload =
                mapOf(
                    "healthLabel" to "${gameState.health}/${gameState.maxHealth}",
                    "scores" to gameState.scores.toString(),
                    "life1Texture" to lifeTexture(slot = 1, lives = lives),
                    "life2Texture" to lifeTexture(slot = 2, lives = lives),
                    "life3Texture" to lifeTexture(slot = 3, lives = lives),
                ),
        )
    }

    private fun lifeTexture(
        slot: Int,
        lives: Int,
    ): String = if (slot <= lives) FullHeartTexturePath else EmptyHeartTexturePath

    private fun finalResultsScreen(): RuntimeUiScreen =
        RuntimeUiScreen(
            id = "woolboy.final_results",
            payload =
                mapOf(
                    "scores" to gameState.scores.toString(),
                ),
        )
}
