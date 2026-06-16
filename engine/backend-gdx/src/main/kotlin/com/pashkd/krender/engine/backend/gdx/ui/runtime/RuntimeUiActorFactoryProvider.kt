package com.pashkd.krender.engine.backend.gdx.ui.runtime

import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler

/**
 * Supplies scene-specific runtime UI actor factories to the LibGDX backend.
 *
 * Core provides a no-op default so SDK clients can contribute their own runtime
 * UI screen factories without making the shared backend depend on a specific game.
 */
fun interface RuntimeUiActorFactoryProvider {
    fun create(
        skin: Skin,
        actionHandlerProvider: () -> RuntimeUiActionHandler?,
    ): List<RuntimeUiActorFactory>

    companion object {
        val Empty = RuntimeUiActorFactoryProvider { _, _ -> emptyList() }
    }
}
