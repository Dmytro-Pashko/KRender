package com.dpashko.krender.compose

import androidx.compose.ui.ComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import java.awt.event.KeyEvent

/**
 * Processor that handles key events from OpenGL window via LibGDX implementation and passes into Skia Compose renderer.
 */
class ComposeInputProcessor(private val scene: ComposeScene) : InputProcessor {
    override fun keyDown(keycode: Int): Boolean {
        if (keycode == Input.Keys.LEFT) {
            scene.sendPointerEvent(
                PointerEventType.Press,
                position = Offset(screenX.toFloat(), screenY.toFloat()),
            )
            return true
        }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        if (keycode == Input.Keys.LEFT) {
            scene.sendPointerEvent(
                PointerEventType.Release,
                position = Offset(screenX.toFloat(), screenY.toFloat()),
            )
            return true
        }
        return false
    }

    private val stubComponent = DesktopComposeRenderer.StubAwtComponent()
    override fun keyTyped(character: Char): Boolean {
        val time = System.nanoTime() / 1_000_000
        scene.sendKeyEvent(
            androidx.compose.ui.input.key.KeyEvent(
                KeyEvent(
                    stubComponent, KeyEvent.KEY_TYPED, time, 0, 0, character, KeyEvent.KEY_LOCATION_UNKNOWN
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
}