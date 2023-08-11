package com.dpashko.krender

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.dpashko.krender.compose.ComposeManager
import com.dpashko.krender.compose.DesktopComposeManager

// Please note that on macOS your application needs to be started with the -XstartOnFirstThread
// JVM argument
object DesktopLauncher {
    @JvmStatic
    fun main(arg: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration().apply {
            setForegroundFPS(60)
            setWindowedMode(1024, 768)
            setResizable(false)
            setTitle("KRender")
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2)
        }
        val composeManager = DesktopComposeManager()
        Lwjgl3Application(AppEntryPoint(composeManager), config)
    }
}