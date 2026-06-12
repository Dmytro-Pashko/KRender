package com.pashkd.krender.engine.backend.gdx.ui.runtime

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.ui.runtime.scene.GdxUiSceneBuilder
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler
import com.pashkd.krender.engine.ui.runtime.RuntimeUiScreen
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.ui.scene.UiSceneValidator

/**
 * Runtime UI actor factory for the Woolboy sandbox.
 *
 * This backend runtime factory maps Woolboy-specific [RuntimeUiScreen] ids to
 * `.krui` assets and asks [GdxUiSceneBuilder] to create Scene2D actors. It keeps
 * screen layout out of Kotlin while leaving action handling in the runtime UI
 * flow. MVP limitations remain explicit: `.krui` styles reference existing Skin
 * style names only, image paths are project-relative paths, loading is direct
 * path-based GDX loading, and there is no Skin editing, Asset Browser
 * integration, or UI Composer editor pipeline yet.
 */
internal class WoolboyRuntimeUiFactory(
    private val logger: Logger,
    private val actionHandlerProvider: () -> RuntimeUiActionHandler?,
) : RuntimeUiActorFactory {
    companion object {
        private const val TAG = "WoolboyRuntimeUiFactory"
    }

    private val screenScenePaths =
        mapOf(
            "woolboy.loading" to "ui/scenes/woolboy_loading.krui",
            "woolboy.main_menu" to "ui/scenes/woolboy_main_menu.krui",
            "woolboy.hud" to "ui/scenes/woolboy_hud.krui",
            "woolboy.final_results" to "ui/scenes/woolboy_final_results.krui",
        )
    private val serializer = UiSceneSerializer()
    private val validator = UiSceneValidator()
    private val builder = GdxUiSceneBuilder(logger)

    override fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor? {
        val scenePath = screenScenePaths[screen.id] ?: return null
        val document = serializer.decode(Gdx.files.internal(scenePath).readString())

        // Validation is warning-only at runtime so existing screens can still load
        // while future UI Composer work gains pre-runtime diagnostics.
        validator.validate(document).forEach { issue ->
            logger.warn(TAG) {
                "UI scene '$scenePath' validation issue at ${issue.nodeId ?: "document"}: ${issue.message}"
            }
        }

        return builder.build(document, screen.payload, actionHandlerProvider())
    }

    override fun dispose() {
        builder.dispose()
    }
}
