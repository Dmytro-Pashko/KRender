package com.pashkd.krender.woolboy.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.pashkd.krender.engine.backend.gdx.ui.runtime.RuntimeUiActorFactory
import com.pashkd.krender.engine.backend.gdx.ui.runtime.scene.GdxUiSceneBuilder
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler
import com.pashkd.krender.engine.ui.runtime.RuntimeUiScreen
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.ui.scene.UiSceneValidator
import com.pashkd.krender.woolboy.WoolboyRuntimeUiScreens

/**
 * Standalone Woolboy runtime UI actor factory owned by the desktop demo app.
 */
internal class WoolboyRuntimeUiFactory(
    private val actionHandlerProvider: () -> RuntimeUiActionHandler?,
) : RuntimeUiActorFactory {
    private val screenScenePaths =
        mapOf(
            WoolboyRuntimeUiScreens.Loading to "assets/woolboy/ui/scenes/woolboy_loading.krui",
            WoolboyRuntimeUiScreens.MainMenu to "assets/woolboy/ui/scenes/woolboy_main_menu.krui",
            WoolboyRuntimeUiScreens.Hud to "assets/woolboy/ui/scenes/woolboy_hud.krui",
            WoolboyRuntimeUiScreens.FinalResults to "assets/woolboy/ui/scenes/woolboy_final_results.krui",
        )
    private val serializer = UiSceneSerializer()
    private val validator = UiSceneValidator()
    private val logger = AppRuntimeLogger()
    private val builder = GdxUiSceneBuilder(logger)

    override fun create(
        screen: RuntimeUiScreen,
        layer: String,
    ): Actor? {
        val scenePath = screenScenePaths[screen.id] ?: return null
        val document = serializer.decode(Gdx.files.internal(scenePath).readString())
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

    private companion object {
        private const val TAG = "WoolboyRuntimeUiFactory"
    }
}
