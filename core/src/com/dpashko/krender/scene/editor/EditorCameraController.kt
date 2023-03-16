package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Input
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.dpashko.krender.scene.editor.EditorCameraController.Companion.maxZoomDistance
import com.dpashko.krender.scene.editor.EditorCameraController.Companion.minZoomDistance
import com.dpashko.krender.scene.editor.EditorCameraController.Companion.moveSpeed
import com.dpashko.krender.scene.editor.EditorCameraController.Companion.zoomSpeed

/**
 * A class that handles camera controls for the editor scene.
 * It implements the InputProcessor interface to receive input events from the user.
 * The camera can be moved using the W, A, S, D keys.
 * It can be rotated using the middle mouse button, and zoomed in and out using the scroll wheel.
 * @property moveSpeed The speed at which the camera moves using keys.
 * @property zoomSpeed The speed at which the camera zooms in and out.
 * @property maxZoomDistance
 * The maximum distance from the target point(Camera direction vector projected onto XY plane).
 * @property minZoomDistance
 * The min distance from the target point(Camera direction vector projected onto XY plane).
 */
class EditorCameraController : InputProcessor {

    companion object {
        // The speed at which the camera moves in units per second.
        private const val moveSpeed = 10f

        // The speed at which the camera zooms in or out in units per scroll tick.
        private const val zoomSpeed = 1f

        // The maximum distance from the intersection point.
        private const val maxZoomDistance = 20f

        // The minimum distance to the intersection point.
        private const val minZoomDistance = 0.5f
    }

    // Flags indicating which movement keys are currently being pressed.
    private var forward = false
    private var backward = false
    private var left = false
    private var right = false

    // Flags indicating whether the camera is currently being zoomed in or out.
    private var isZoomedOut = false
    private var isZoomedIn = false

    // Flags indicating whether the camera is currently being rotated.
    private var isRotating = false

    // The x-coordinate of the last mouse position.
    private var lastMouseX = 0

    // The delta x-coordinate of the current mouse position relative to the last position.
    private var deltaX: Float = 0f

    /**
     * Handles key press events.
     *
     * @param keycode The keycode of the key that was pressed.
     * @return True if the event was handled, false otherwise.
     */
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

    /**
     * Handles key release events.
     *
     * @param keycode The keycode of the key that was released.
     * @return True if the event was handled, false otherwise.
     */
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

    /**
     * Handles key typed events.
     *
     * @param character The character that was typed.
     * @return False, as this event is not handled.
     */

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.MIDDLE) {
            isRotating = true
            lastMouseX = screenX
        }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.MIDDLE) {
            isRotating = false
            lastMouseX = 0
            deltaX = 0f
        }
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (isRotating) {
            deltaX = screenX.toFloat() - lastMouseX.toFloat()
            lastMouseX = screenX
        }
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        if (amountY < 0) {
            isZoomedIn = true
        } else if (amountY > 0) {
            isZoomedOut = true
        }
        return true
    }

    fun update(state: EditorState, delta: Float) {
        state.apply {
            val moveAmount = delta * moveSpeed
            val displacement = Vector3.Zero

            if (forward && !backward) {
                displacement.add(camera.direction)
            }
            if (backward && !forward) {
                displacement.sub(camera.direction)
            }
            if (left && !right) {
                displacement.add(camera.direction.cpy().rotate(Vector3.Z, 90f))
            }
            if (!left && right) {
                displacement.add(camera.direction.cpy().rotate(Vector3.Z, -90f))
            }

            // Update camera position and target point only when changing camera position required.
            if (!displacement.isZero) {
                displacement.scl(moveAmount, moveAmount, 0f)
                camera.translate(displacement)
                camera.update()

                // Calculates the distance between the camera position and the point of intersection
                // of the camera's direction vector with the Z=0 plane in world coordinates.
                // It does this by dividing the negative z-coordinate of the camera position by the
                // z-component of the camera direction vector. This is based on the fact that the
                // intersection point can be represented as cameraPosition + t * cameraDirection.
                val t = -camera.position.z / camera.direction.z
                val intersectionPoint = Vector3(
                    camera.position.x + t * camera.direction.x,
                    camera.position.y + t * camera.direction.y,
                    0f
                )
                // Update target point after camera transition.
                target.set(intersectionPoint)

                // Check if target position is outside the world bounds.
                if (!worldBounds.contains(
                        Vector3(
                            target.x,
                            target.y,
                            0f
                        )
                    )) {
                    // Calculate displacement required to move the camera back into the world bounds.
                    val targetDisplacement = getClosestPoint(worldBounds, intersectionPoint)
                        .sub(intersectionPoint)
                    camera.translate(targetDisplacement)
                    camera.update()
                }
            }


            if (isRotating || isZoomedOut || isZoomedIn) {
                if (isRotating) {
                    val angle = deltaX * delta * 10f
                    camera.rotateAround(target, Vector3.Z, angle)
                    deltaX = 0f
                }

                if (isZoomedOut || isZoomedIn) {
                    val zoomDirection = if (isZoomedOut) -1f else 1f
                    val zoomPosition = camera.direction.cpy().scl(zoomSpeed * zoomDirection)
                    val distance = target.dst(camera.position.cpy().add(zoomPosition))

                    if (isZoomedOut && distance < maxZoomDistance) {
                        camera.translate(zoomPosition)
                    }
                    if (isZoomedIn && distance > minZoomDistance) {
                        camera.translate(zoomPosition)
                    }
                    isZoomedOut = false
                    isZoomedIn = false
                }
                camera.update()
            }
        }
    }

    /**
     *  Checks if each coordinate is outside of the bounding box
     *  (i.e., less than the minimum value or greater than the maximum value).
     *  If a coordinate is less than the minimum value, it is set to the minimum value of
     *  the corresponding dimension of the bounding box.
     *  If a coordinate is greater than the maximum value,
     *  it is set to the maximum value of the corresponding dimension of the bounding box.
     */
    private fun getClosestPoint(box: BoundingBox, point: Vector3): Vector3 {
        var x = point.x
        var y = point.y
        var z = point.z
        if (x < box.min.x) x = box.min.x else if (x > box.max.x) x = box.max.x
        if (y < box.min.y) y = box.min.y else if (y > box.max.y) y = box.max.y
        if (z < box.min.z) z = box.min.z else if (z > box.max.z) z = box.max.z
        return Vector3(x, y, z)
    }
}