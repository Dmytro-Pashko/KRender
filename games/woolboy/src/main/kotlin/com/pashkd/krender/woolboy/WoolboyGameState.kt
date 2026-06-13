package com.pashkd.krender.woolboy

/**
 * High-level UI modes used by the Woolboy sandbox runtime flow.
 *
 * The order represents the simple MVP flow: loading, main menu, gameplay, then
 * final results. Settings is intentionally not modeled as a mode yet.
 */
enum class WoolboyUiMode {
    Loading,
    MainMenu,
    Gameplay,
    FinalResults,
}

/**
 * Mutable runtime state for the Woolboy sandbox UI and player-facing counters.
 *
 * The state deliberately contains only the data needed by the first runtime UI
 * flow. It does not implement damage, death, persistence, settings, or screen
 * loading; systems update this object explicitly and render it through
 * [WoolboyUiControllerSystem].
 */
data class WoolboyGameState(
    /** Currently active Woolboy runtime UI mode. */
    var uiMode: WoolboyUiMode = WoolboyUiMode.Loading,
    /** Tracks whether a game has been started so the menu can show Continue. */
    var gameStarted: Boolean = false,
    /** Normalized loading progress shown by the loading screen. */
    var loadingProgress: Float = 0f,
    /** Maximum player health shown in the gameplay HUD. */
    var maxHealth: Int = 100,
    /** Current player health shown in the gameplay HUD. */
    var health: Int = 100,
    /** Remaining lives shown in the gameplay HUD. */
    var lives: Int = 3,
    /** Current score shown in the gameplay HUD and final results screen. */
    var scores: Int = 0,
) {
    /** Returns whether gameplay input should be accepted by the player controller. */
    val playerInputEnabled: Boolean
        get() = uiMode == WoolboyUiMode.Gameplay

    /** Switches from loading or gameplay back to the main menu. */
    fun showMainMenu() {
        uiMode = WoolboyUiMode.MainMenu
    }

    /** Starts a fresh gameplay run with MVP default counters. */
    fun startNewGame() {
        gameStarted = true
        maxHealth = 100
        health = maxHealth
        lives = 3
        scores = 0
        uiMode = WoolboyUiMode.Gameplay
    }

    /** Continues an existing run or starts a new one if none exists yet. */
    fun continueGame() {
        if (gameStarted) {
            uiMode = WoolboyUiMode.Gameplay
        } else {
            startNewGame()
        }
    }

    /** Restarts gameplay using the same initialization as a new run. */
    fun restartGame() {
        startNewGame()
    }

    /** Switches to final results without applying gameplay damage or death logic. */
    fun showFinalResults() {
        uiMode = WoolboyUiMode.FinalResults
    }
}
