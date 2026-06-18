package com.pashkd.krender.woolboy.app

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.backend.gdx.ui.runtime.RuntimeUiActorFactoryProvider
import com.pashkd.krender.woolboy.WoolboyScene

/** Launches the standalone Woolboy desktop demo application. */
object WoolboyDesktopLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        logStartup(args)
        Lwjgl3Application(
            GdxEngineApplication(
                initialScene = { WoolboyScene() },
                runtimeUiDefaultSkinPath = "assets/woolboy/ui/skins/default_ui.json",
                runtimeUiActorFactoryProvider =
                    RuntimeUiActorFactoryProvider { _, actionHandlerProvider ->
                        listOf(WoolboyRuntimeUiFactory(actionHandlerProvider))
                    },
            ),
            Lwjgl3ApplicationConfiguration().apply {
                setTitle(WindowTitle)
                useVsync(false)
                setForegroundFPS(60)
                setInitialBackgroundColor(Color(0.08f, 0.09f, 0.11f, 1f))
                setTransparentFramebuffer(false)
                setBackBufferConfig(8, 8, 8, 0, 24, 0, 0)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 0)
                setWindowedMode(DefaultWindowWidth, DefaultWindowHeight)
                setWindowIcon(
                    "logo/Krender_logo_256.png",
                    "logo/Krender_logo_128.png",
                    "logo/Krender_logo_64.png",
                    "logo/Krender_logo_48.png",
                    "logo/Krender_logo_32.png",
                    "logo/Krender_logo_16.png",
                )
            },
        )
    }

    private fun logStartup(args: Array<String>) {
        println(
            buildString {
                appendLine("Woolboy demo started")
                appendLine("Engine: KRender")
                appendLine("Module: games:woolboy")
                appendLine("Assets: bundled assets/woolboy")
                appendLine("Window: $WindowTitle (${DefaultWindowWidth}x$DefaultWindowHeight)")
                appendLine("Controls: WASD move, RMB orbit camera, mouse wheel zoom, Space jump, F greeting, Esc menu/exit")
                appendLine("args=${args.joinToString(prefix = "[", postfix = "]")}")
                appendLine("java.version=${System.getProperty("java.version")}")
                appendLine("user.dir=${System.getProperty("user.dir")}")
            },
        )
    }

    private const val WindowTitle = "KRender — Woolboy Demo"
    private const val DefaultWindowWidth = 1280
    private const val DefaultWindowHeight = 720
}
