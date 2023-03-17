package com.dpashko.krender

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3NativesLoader
import com.badlogic.gdx.utils.GdxRuntimeException
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.glfw.GLFW.glfwWindowShouldClose
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11

// Please note that on macOS your application needs to be started with the -XstartOnFirstThread
// JVM argument
object DesktopLauncher {
    @JvmStatic
    fun main2(arg: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration().apply {
            setForegroundFPS(60)
            setWindowedMode(1024, 768)
            setResizable(false)
            setTitle("KRender")
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3)
        }
        Lwjgl3Application(AppEntryPoint(), config)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        initializeGlfw(AppEntryPoint())
    }

    private fun initializeGlfw(listener: ApplicationListener) {
        Lwjgl3NativesLoader.load()

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)

        GLFW.glfwInitHint(GLFW.GLFW_JOYSTICK_HAT_BUTTONS, GLFW.GLFW_FALSE)
        if (!GLFW.glfwInit()) {
            throw GdxRuntimeException("Unable to initialize GLFW")
        }

        // Create active window.
        glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE)
        glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_FALSE)
        glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE)

        val windowHandle = glfwCreateWindow(1024, 768, "KRender", 0, 0)
        if (windowHandle.toInt() == 0) {
            throw GdxRuntimeException("Couldn't create window")
        }

        val monitorHandle = GLFW.glfwGetPrimaryMonitor()
        val areaXPos = org.lwjgl.BufferUtils.createIntBuffer(1)
        val areaYPos = org.lwjgl.BufferUtils.createIntBuffer(1)
        val areaWidth = org.lwjgl.BufferUtils.createIntBuffer(1)
        val areaHeight = org.lwjgl.BufferUtils.createIntBuffer(1)
        GLFW.glfwGetMonitorWorkarea(monitorHandle, areaXPos, areaYPos, areaWidth, areaHeight)
        GLFW.glfwSetWindowPos(windowHandle, -1, -1)
        glfwMakeContextCurrent(windowHandle)
        GLFW.glfwSwapInterval(0)
        GL.createCapabilities()

        for (i in 0..1) {
            GL11.glClearColor(0f, 0f, 0f, 1f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            GLFW.glfwSwapBuffers(windowHandle)
        }

        val app = MockedApp(listener, windowHandle).apply {
            Gdx.app = this
            Gdx.graphics = graphics
            Gdx.files = files
            Gdx.gl = graphics.gL20
            Gdx.gl20 = graphics.gL20
            Gdx.gl30 = graphics.gL30
            listener.create()

        }

        GLFW.glfwShowWindow(windowHandle)

        var running = true
        // Main loop.
        while (running) {
            if (glfwWindowShouldClose(windowHandle)) {
                running = false
            }
            glfwMakeContextCurrent(windowHandle)
            app.graphics.update()
            listener.render()
            GLFW.glfwSwapBuffers(windowHandle)

            GLFW.glfwPollEvents()
        }
        // Call that app disposed.
        listener.dispose()
    }
}