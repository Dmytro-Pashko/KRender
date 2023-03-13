package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3

class EditorSceneCameraController() : InputProcessor {

    private val moveSpeed = 10f
    private val zoomSpeed = 1f
    private var forward = false
    private var backward = false
    private var left = false
    private var right = false

    private var isZoomedIn = false
    private var isZoomedOut = false

    override fun keyDown(keycode: Int): Boolean {
        when (keycode) {
            Keys.W -> forward = true
            Keys.S -> backward = true
            Keys.A -> left = true
            Keys.D -> right = true
            else -> return false
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            Keys.W -> forward = false
            Keys.S -> backward = false
            Keys.A -> left = false
            Keys.D -> right = false
            else -> return false
        }
        return true
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        if (amountY < 0) {
            isZoomedOut = true
        } else if (amountY > 0) {
            isZoomedIn = true
        }
        return true
    }

    fun update(camera: PerspectiveCamera, delta: Float) {
        val moveAmount = delta * moveSpeed

        val newPosition = camera.direction.cpy().scl(moveAmount)
        newPosition.z = 0f

        if (forward) {
            camera.translate(newPosition)
        }
        if (backward) {
            camera.translate(newPosition.cpy().scl(-1f))
        }
        if (left) {
            camera.translate(newPosition.cpy().rotate(Vector3.Z, 90f))
        }
        if (right) {
            camera.translate(newPosition.cpy().rotate(Vector3.Z, -90f))
        }

        if (isZoomedIn) {
            camera.translate(camera.direction.cpy().scl(zoomSpeed))
            isZoomedIn = false
        }
        if (isZoomedOut) {
            camera.translate(camera.direction.cpy().scl(-zoomSpeed))
            isZoomedOut = false
        }
        camera.update()
    }
}