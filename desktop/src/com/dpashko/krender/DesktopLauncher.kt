package com.dpashko.krender

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3NativesLoader
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL20.*
import com.badlogic.gdx.utils.GdxRuntimeException
import org.jetbrains.skia.*
import org.jetbrains.skia.FramebufferFormat.Companion.GR_GL_RGBA8
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import kotlin.system.exitProcess

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
//        Lwjgl3Application(AppEntryPoint(), config)
        customLwjgl3Application(AppEntryPoint())
    }

    @Composable
    @Preview
    fun getMainContent() {
        return MaterialTheme {
            Surface(
                border = BorderStroke(width = Dp(3f), brush = SolidColor(Color.Green))
            ) {
                var text by remember { mutableStateOf("") }
                val history by remember { mutableStateOf(mutableListOf<String>()) }

                Column {

                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = ({
                            Text("Some input data...")
                        })
                    )

                    Row(
                        modifier = Modifier
                            .padding(Dp(10f))
                            .align(alignment = Alignment.CenterHorizontally)
                    ) {
                        Button(
                            modifier = Modifier.padding(Dp(10f)),
                            onClick = {
                                history.add(text)
                                text = ""
                            }) {
                            Text("Add")
                        }
                        Button(
                            modifier = Modifier.padding(Dp(10f)),
                            onClick = {
                                history.clear()
                            }) {
                            Text("Clear All")
                        }
                    }
                }
            }
        }
    }

    private fun customLwjgl3Application(listener: ApplicationListener) {
        GLFWErrorCallback.createPrint(System.err).set()

        var composeScene: ComposeScene? = null
        Lwjgl3NativesLoader.load()


        if (!glfwInit()) {
            throw GdxRuntimeException("Unable to initialize GLFW")
        }

        // Create active window.
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)

        val windowHandle = glfwCreateWindow(1024, 768, "KRender", 0, 0)
        if (windowHandle.toInt() == 0) {
            throw GdxRuntimeException("Couldn't create window")
        }

        glfwMakeContextCurrent(windowHandle)
//        glfwSwapInterval(0)
        GL.createCapabilities()

        val app = MockedApp(listener, windowHandle).apply {
            Gdx.app = this
            Gdx.graphics = graphics
            Gdx.files = files
            Gdx.gl = graphics.gL20
            Gdx.gl20 = graphics.gL20
            Gdx.gl30 = graphics.gL30
            listener.create()

            Gdx.gl.glEnable(GL_BLEND)
            Gdx.gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        }

        val context = DirectContext.makeGL()
        val surface: Surface = createSurface(1024, 768, context)
        val glfwDispatcher = GlfwCoroutineDispatcher()

        fun render() {
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            app.graphics.update()
            listener.render()

            // Render main scene.
            surface.canvas.let {
                composeScene?.render(it, System.nanoTime())
            }
            surface.flushAndSubmit()
            glfwSwapBuffers(windowHandle)
        }

        // Draws frame dispatched from Compose Scene.
        val frameDispatcher = FrameDispatcher(glfwDispatcher) { render() }
        composeScene =
            ComposeScene(
                glfwDispatcher,
                Density(1f),
                invalidate = frameDispatcher::scheduleFrame
            )
        composeScene.setContent { getMainContent() }
        composeScene.subscribeToGLFWEvents(windowHandle)

        glfwShowWindow(windowHandle)
        glfwDispatcher.runLoop()

        var isRunning = true
        while (isRunning) {
            if (glfwWindowShouldClose(windowHandle)) {
                isRunning = false
            } else {
                render()
            }
        }
        // When loop is finished.
        surface.close()
        glfwDispatcher.stop()
        composeScene.close()
        glfwDestroyWindow(windowHandle)

        // Call that app disposed.
        listener.dispose()
        exitProcess(0)
    }

    // Skia surface targets into used by Compose
    private fun createSurface(width: Int, height: Int, context: DirectContext): Surface {
        val fbId = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING)
        val renderTarget = BackendRenderTarget.makeGL(
            width = width,
            height = height,
            sampleCnt = 0,
            stencilBits = 8,
            fbId = fbId,
            fbFormat = GR_GL_RGBA8
        )
        return Surface.makeFromBackendRenderTarget(
            context = context,
            rt = renderTarget,
            origin = SurfaceOrigin.BOTTOM_LEFT,
            colorFormat = SurfaceColorFormat.RGBA_8888,
            colorSpace = ColorSpace.sRGB,
        )!!
    }
}