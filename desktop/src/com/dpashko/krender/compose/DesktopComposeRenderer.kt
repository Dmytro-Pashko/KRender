package com.dpashko.krender.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.skia.*
import java.awt.Component
import java.awt.Graphics
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_LOCATION_UNKNOWN
import java.awt.event.KeyEvent.KEY_TYPED

/**
 * Class uses to render Composes scenes on scope of OpenGL context.
 */
class DesktopComposeRenderer(dispatcher: CoroutineDispatcher) : ComposeRenderer() {

    internal val scene = ComposeScene(dispatcher, Density(1f)) {
        Gdx.graphics.requestRendering()
    }
    private var surface: Surface? = null
    private lateinit var context: DirectContext

    internal fun init() {
        context = DirectContext.makeGL()
        surface = createSurface()
    }

    override fun setContent(content: @Composable () -> Unit) {
        scene.setContent(content)
    }

    override fun render() {
        surface?.canvas.also {
            if (it != null) {
                context.resetAll()
                scene.render(it, System.nanoTime())
                context.flush()
            }
        }
    }

    override fun dispose() {
        context.close()
        surface?.close()
    }

    /**
     * Creates surface in using Skikko AWT wrapper over OpenGL.
     * Skikko and Skia Compose team doesn't provide any api to create
     * custom implementation of context.
     */
    private fun createSurface(): Surface? {
        val renderTarget = BackendRenderTarget.makeGL(
            width = Gdx.graphics.width,
            height = Gdx.graphics.height,
            sampleCnt = 0,
            stencilBits = 8,
            fbId = 0,
            fbFormat = FramebufferFormat.GR_GL_RGBA8
        )

        return Surface.makeFromBackendRenderTarget(
            context,
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.sRGB,
            SurfaceProps(PixelGeometry.RGB_H)
        )
    }

    class StubAwtComponent : Component() {
        override fun paint(g: Graphics) {}
    }
}