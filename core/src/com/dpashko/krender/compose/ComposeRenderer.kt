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
class ComposeRenderer(dispatcher: CoroutineDispatcher) : InputProcessor {

    private val scene = ComposeScene(dispatcher, Density(1f)) {
        Gdx.graphics.requestRendering()
    }
    private var surface: Surface? = null
    private lateinit var context: DirectContext

    fun init(content: @Composable () -> Unit) {
        context = DirectContext.makeGL()
        surface = createSurface()
        scene.setContent(content)
    }

    fun render() {
        surface?.canvas.also {
            if (it != null) {
                context.resetAll()
                scene.render(it, System.nanoTime())
                context.flush()
            }
        }
    }

    fun dispose() {
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

    override fun keyDown(keycode: Int): Boolean {
        if (keycode == Keys.LEFT) {
            scene.sendPointerEvent(
                PointerEventType.Press,
                position = Offset(screenX.toFloat(), screenY.toFloat()),
            )
            return true
        }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        if (keycode == Keys.LEFT) {
            scene.sendPointerEvent(
                PointerEventType.Release,
                position = Offset(screenX.toFloat(), screenY.toFloat()),
            )
            return true
        }
        return false
    }

    private val stubComponent = StubAwtComponent()
    override fun keyTyped(character: Char): Boolean {
        val time = System.nanoTime() / 1_000_000
        scene.sendKeyEvent(
            androidx.compose.ui.input.key.KeyEvent(
                KeyEvent(
                    stubComponent, KEY_TYPED, time, 0, 0, character, KEY_LOCATION_UNKNOWN
                )
            )
        )
        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        scene.sendPointerEvent(
            PointerEventType.Press,
            position = Offset(screenX.toFloat(), screenY.toFloat()),
        )
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        scene.sendPointerEvent(
            PointerEventType.Release,
            position = Offset(screenX.toFloat(), screenY.toFloat()),
        )
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return false
    }

    private var screenX: Int = 0
    private var screenY: Int = 0

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        this.screenX = screenX
        this.screenY = screenY
        scene.sendPointerEvent(
            PointerEventType.Move,
            position = Offset(screenX.toFloat(), screenY.toFloat()),
        )
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }

    class StubAwtComponent : Component() {
        override fun paint(g: Graphics) {}
    }
}