@file:JvmName("Lwjgl3Launcher")

package com.pashkd.krender.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.pashkd.krender.Main
import java.util.Locale

/** Launches the desktop (LWJGL3) application. */
fun main(args: Array<String>) {
    // This handles macOS support and helps on Windows.
    if (StartupHelper.startNewJvmIfRequired()) {
        return
    }
    logStartup(args)
    Lwjgl3Application(
        Main(
            modelPath = args.firstOrNull() ?: Main.configuredModelPath(),
            runtimeWindowLauncherFactory = { logger -> Lwjgl3RuntimeWindowLauncher(logger) },
            editorToolLauncherFactory = { logger -> Lwjgl3EditorToolLauncher(logger) },
        ),
        Lwjgl3ApplicationConfiguration().apply {
            setTitle("KRender")
            // // Vsync limits the frames per second to what your hardware can display, and helps eliminate
            // // screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
            useVsync(desktopBoolean("krender.vsync", default = DefaultVSyncEnabled))
            // // Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
            // // refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
            setForegroundFPS(desktopInt("krender.foregroundFps") ?: DefaultForegroundFps)
            // // If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
            // // useful for testing performance, but can also be very stressful to some hardware.
            // // You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.

            setInitialBackgroundColor(Color(0.08f, 0.09f, 0.11f, 1f))
            setTransparentFramebuffer(false)
            setBackBufferConfig(8, 8, 8, 0, 24, 0, 0)
            configureOpenGlTarget()

            setWindowedMode(DefaultWindowWidth, DefaultWindowHeight)
            // // You can change these files; they are in lwjgl3/src/main/resources/ .
            // // They can also be loaded from the root of assets/ .
            setWindowIcon(*(arrayOf(128, 64, 32, 16).map { "libgdx$it.png" }.toTypedArray()))

            // // This could improve compatibility with Windows machines with buggy OpenGL drivers, Macs
            // // with Apple Silicon that have to emulate compatibility with OpenGL anyway, and more.
            // // This uses the dependency `com.badlogicgames.gdx:gdx-lwjgl3-angle` to function.
            // // You would need to add this line to lwjgl3/build.gradle , below the dependency on `gdx-backend-lwjgl3`:
            // //     implementation "com.badlogicgames.gdx:gdx-lwjgl3-angle:$gdxVersion"
            // // You can choose to add the following line and the mentioned dependency if you want; they
            // // are not intended for games that use GL30 (which is compatibility with OpenGL ES 3.0).
            // // Know that it might not work well in some cases.
//        setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0)
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
            ?: DefaultOpenGlTarget
    when (target.lowercase(Locale.ROOT)) {
        "gl20", "2.0", "20" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 2, 0)
        "gl30", "3.0", "30" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 0)
        "gl31", "3.1", "31" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL31, 3, 1)
        "gl32", "3.2", "32" -> setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
        else -> {
            System.err.println("Unknown krender.gl.target='$target'. Using $DefaultOpenGlTarget.")
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

private const val DefaultOpenGlTarget = "gl30"
private const val DefaultVSyncEnabled = false
private const val DefaultForegroundFps = 60
private const val DefaultWindowWidth = 1920
private const val DefaultWindowHeight = 1080
