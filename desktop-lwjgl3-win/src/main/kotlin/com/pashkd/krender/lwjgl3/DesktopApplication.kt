package com.pashkd.krender.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import java.util.Locale

fun launchKRenderDesktopApplication(
    args: Array<String>,
    mainClassName: String,
) {
    logStartup(args)
    Lwjgl3Application(
        DesktopMain(
            modelPath = args.firstOrNull() ?: DesktopMain.configuredModelPath(),
            runtimeWindowLauncherFactory = { logger -> Lwjgl3RuntimeWindowLauncher(logger, mainClassName) },
            editorToolLauncherFactory = { logger -> Lwjgl3EditorToolLauncher(logger, mainClassName) },
        ),
        Lwjgl3ApplicationConfiguration().apply {
            setTitle("KRender")
            useVsync(desktopBoolean("krender.vsync", default = DEFAULT_VSYNC_ENABLED))
            setForegroundFPS(desktopInt("krender.foregroundFps") ?: DEFAULT_FOREGROUND_FPS)
            setInitialBackgroundColor(Color(0.08f, 0.09f, 0.11f, 1f))
            setTransparentFramebuffer(false)
            setBackBufferConfig(8, 8, 8, 0, 24, 0, 0)
            configureOpenGlTarget()
            setWindowedMode(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT)
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
            appendLine("KRender LWJGL3 startup")
            appendLine("user.dir=${System.getProperty("user.dir")}")
            appendLine("args=${args.joinToString(prefix = "[", postfix = "]")}")
            appendLine("krender.scene=${System.getProperty("krender.scene")}")
            appendLine("krender.model.path=${System.getProperty("krender.model.path")}")
            appendLine("krender.model=${System.getProperty("krender.model")}")
            appendLine("krender.texture.atlas.path=${System.getProperty("krender.texture.atlas.path")}")
            appendLine("krender.scene.path=${System.getProperty("krender.scene.path")}")
            appendLine("java.version=${System.getProperty("java.version")}")
            appendLine(
                "java.class.path.entries=${System.getProperty("java.class.path")?.split(System.getProperty("path.separator"))?.size ?: 0}",
            )
        },
    )
}

private fun Lwjgl3ApplicationConfiguration.configureOpenGlTarget() {
    val target =
        System.getProperty("krender.gl.target")
            ?: System.getenv("KRENDER_GL_TARGET")
            ?: DEFAULT_OPEN_GL_TARGET
    when (target.lowercase(Locale.ROOT)) {
        "gl20", "2.0", "20" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 2, 0)
        "gl30", "3.0", "30" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 0)
        "gl31", "3.1", "31" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL31, 3, 1)
        "gl32", "3.2", "32" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
        else -> {
            System.err.println("Unknown krender.gl.target='$target'. Using $DEFAULT_OPEN_GL_TARGET.")
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
        }
    }
}

private fun desktopBoolean(
    name: String,
    default: Boolean,
): Boolean {
    val value = desktopProperty(name) ?: return default
    return when (value.trim().lowercase(Locale.ROOT)) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> default
    }
}

private fun desktopInt(name: String): Int? = desktopProperty(name)?.trim()?.toIntOrNull()

private fun desktopProperty(name: String): String? = System.getProperty(name) ?: System.getenv(name.replace('.', '_').uppercase(Locale.ROOT))

private const val DEFAULT_OPEN_GL_TARGET = "gl30"
private const val DEFAULT_VSYNC_ENABLED = false
private const val DEFAULT_FOREGROUND_FPS = 60
private const val DEFAULT_WINDOW_WIDTH = 1920
private const val DEFAULT_WINDOW_HEIGHT = 1080
