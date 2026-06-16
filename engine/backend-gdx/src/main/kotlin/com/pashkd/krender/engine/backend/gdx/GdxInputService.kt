package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.ui.UiCaptureState

/**
 * LibGDX-backed input service that snapshots keyboard, mouse, pointer, and UI capture state.
 */
class GdxInputService :
    InputService,
    InputProcessor {
    private val keysDown = mutableSetOf<Key>()
    private val pressedThisFrame = mutableSetOf<Key>()
    private val releasedThisFrame = mutableSetOf<Key>()
    private val mouseButtonsDown = mutableSetOf<MouseButton>()
    private val mouseButtonsPressedThisFrame = mutableSetOf<MouseButton>()
    private val mouseButtonsReleasedThisFrame = mutableSetOf<MouseButton>()
    private val pointers = mutableMapOf<Int, PointerState>()
    private val processors = mutableListOf<InputProcessor>()
    private val actions =
        mapOf(
            Action("MoveLeft") to setOf(Key.A),
            Action("MoveRight") to setOf(Key.D),
            Action("MoveForward") to setOf(Key.W),
            Action("MoveBackward") to setOf(Key.S),
            Action("Jump") to setOf(Key.Space),
        )

    private var currentSnapshot = InputSnapshot()
    private var uiCaptureProvider: () -> UiCaptureState = { UiCaptureState() }
    private var mouseX: Int = 0
    private var mouseY: Int = 0
    private var mouseDeltaX: Float = 0f
    private var mouseDeltaY: Float = 0f
    private var scrollDelta: Float = 0f
    private var cursorCaptured: Boolean = false
    private var ignoredWarpX: Int? = null
    private var ignoredWarpY: Int? = null

    /** Captures the current raw input state into the frame snapshot. */
    override fun beginFrame() {
        val uiCapture = uiCaptureProvider()
        currentSnapshot =
            InputSnapshot(
                keysDown = keysDown.toSet(),
                keysPressedThisFrame = pressedThisFrame.toSet(),
                keysReleasedThisFrame = releasedThisFrame.toSet(),
                mouseButtonsDown = mouseButtonsDown.toSet(),
                mouseButtonsPressedThisFrame = mouseButtonsPressedThisFrame.toSet(),
                mouseButtonsReleasedThisFrame = mouseButtonsReleasedThisFrame.toSet(),
                mousePosition = Vec2(mouseX.toFloat(), mouseY.toFloat()),
                mouseDelta = Vec2(mouseDeltaX, mouseDeltaY),
                scrollDelta = scrollDelta,
                pointers = pointers.values.toList(),
                viewportSize = Vec2(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()),
                uiCapturesMouse = uiCapture.mouse,
                uiCapturesKeyboard = uiCapture.keyboard,
            )
        keepCursorInsideWindow()
    }

    /** Returns the immutable input snapshot prepared in [beginFrame]. */
    override fun snapshot(): InputSnapshot = currentSnapshot

    /** Clears one-frame input deltas and retires finished pointer events. */
    override fun endFrame() {
        pressedThisFrame.clear()
        releasedThisFrame.clear()
        mouseButtonsPressedThisFrame.clear()
        mouseButtonsReleasedThisFrame.clear()
        mouseDeltaX = 0f
        mouseDeltaY = 0f
        scrollDelta = 0f
        pointers.entries.removeIf { it.value.phase == PointerPhase.Up || it.value.phase == PointerPhase.Cancelled }
    }

    /** Enables or disables cursor recentering used for captured mouse input. */
    override fun setCursorCaptured(captured: Boolean) {
        if (cursorCaptured == captured) return
        cursorCaptured = captured
        Gdx.input.isCursorCatched = false
        mouseX = Gdx.input.x
        mouseY = Gdx.input.y
        mouseDeltaX = 0f
        mouseDeltaY = 0f
        if (captured) {
            moveCursorToWindowCenter()
        } else {
            ignoredWarpX = null
            ignoredWarpY = null
        }
    }

    /** Returns whether any key bound to the action is currently held. */
    override fun isActionPressed(action: Action): Boolean = actions[action]?.any { it in currentSnapshot.keysDown } == true

    /** Returns whether any key bound to the action was pressed this frame. */
    override fun isActionJustPressed(action: Action): Boolean = actions[action]?.any { it in currentSnapshot.keysPressedThisFrame } == true

    /** Resolves named digital axes from the current snapshot. */
    override fun axis(axis: Axis): Float =
        when (axis.name) {
            "Horizontal" -> currentSnapshot.booleanAxis(Key.A, Key.D)
            "Vertical" -> currentSnapshot.booleanAxis(Key.S, Key.W)
            else -> 0f
        }

    /** Registers an additional LibGDX input processor for mirrored callbacks. */
    fun addProcessor(processor: InputProcessor) {
        processors += processor
    }

    /** Unregisters a mirrored LibGDX input processor. */
    fun removeProcessor(processor: InputProcessor) {
        processors -= processor
    }

    /** Supplies the current UI capture state used when building snapshots. */
    fun setUiCaptureProvider(provider: () -> UiCaptureState) {
        uiCaptureProvider = provider
    }

    /** Records a key-down event and forwards it to child processors. */
    override fun keyDown(keycode: Int): Boolean {
        val key = mapKey(keycode)
        if (key !in keysDown) {
            pressedThisFrame += key
        }
        keysDown += key
        processors.forEach { it.keyDown(keycode) }
        return false
    }

    /** Records a key-up event and forwards it to child processors. */
    override fun keyUp(keycode: Int): Boolean {
        val key = mapKey(keycode)
        keysDown -= key
        releasedThisFrame += key
        processors.forEach { it.keyUp(keycode) }
        return false
    }

    /** Updates mouse position and delta, ignoring synthetic warp events when needed. */
    override fun mouseMoved(
        screenX: Int,
        screenY: Int,
    ): Boolean {
        if (screenX == ignoredWarpX && screenY == ignoredWarpY) {
            ignoredWarpX = null
            ignoredWarpY = null
        } else {
            ignoredWarpX = null
            ignoredWarpY = null
            mouseDeltaX += screenX - mouseX
            mouseDeltaY += screenY - mouseY
        }
        mouseX = screenX
        mouseY = screenY
        processors.forEach { it.mouseMoved(screenX, screenY) }
        return false
    }

    /** Treats a dragged pointer as both mouse movement and pointer motion. */
    override fun touchDragged(
        screenX: Int,
        screenY: Int,
        pointer: Int,
    ): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Move, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchDragged(screenX, screenY, pointer) }
        return false
    }

    /** Starts a tracked pointer interaction. */
    override fun touchDown(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int,
    ): Boolean {
        mouseMoved(screenX, screenY)
        val mouseButton = mapMouseButton(button)
        if (mouseButton !in mouseButtonsDown) {
            mouseButtonsPressedThisFrame += mouseButton
        }
        mouseButtonsDown += mouseButton
        pointers[pointer] = PointerState(pointer, PointerPhase.Down, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchDown(screenX, screenY, pointer, button) }
        return false
    }

    /** Ends a tracked pointer interaction. */
    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int,
    ): Boolean {
        mouseMoved(screenX, screenY)
        val mouseButton = mapMouseButton(button)
        mouseButtonsDown -= mouseButton
        mouseButtonsReleasedThisFrame += mouseButton
        pointers[pointer] = PointerState(pointer, PointerPhase.Up, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchUp(screenX, screenY, pointer, button) }
        return false
    }

    /** Marks a tracked pointer as cancelled. */
    override fun touchCancelled(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int,
    ): Boolean {
        mouseMoved(screenX, screenY)
        val mouseButton = mapMouseButton(button)
        mouseButtonsDown -= mouseButton
        mouseButtonsReleasedThisFrame += mouseButton
        pointers[pointer] = PointerState(pointer, PointerPhase.Cancelled, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchCancelled(screenX, screenY, pointer, button) }
        return false
    }

    /** Accumulates scroll input for the current frame. */
    override fun scrolled(
        amountX: Float,
        amountY: Float,
    ): Boolean {
        scrollDelta += amountY
        processors.forEach { it.scrolled(amountX, amountY) }
        return false
    }

    /** Forwards typed-character events to child processors. */
    override fun keyTyped(character: Char): Boolean {
        processors.forEach { it.keyTyped(character) }
        return false
    }

    /** Maps LibGDX key codes to engine key enums. */
    private fun mapKey(keycode: Int): Key =
        when (keycode) {
            Input.Keys.W -> Key.W
            Input.Keys.A -> Key.A
            Input.Keys.S -> Key.S
            Input.Keys.D -> Key.D
            Input.Keys.G -> Key.G
            Input.Keys.R -> Key.R
            Input.Keys.F -> Key.F
            Input.Keys.Y -> Key.Y
            Input.Keys.Z -> Key.Z
            Input.Keys.Q -> Key.Q
            Input.Keys.E -> Key.E
            Input.Keys.F1 -> Key.F1
            Input.Keys.F2 -> Key.F2
            Input.Keys.F3 -> Key.F3
            Input.Keys.F4 -> Key.F4
            Input.Keys.F5 -> Key.F5
            Input.Keys.GRAVE -> Key.Backtick
            Input.Keys.SPACE -> Key.Space
            Input.Keys.ESCAPE -> Key.Escape
            Input.Keys.TAB -> Key.Tab
            Input.Keys.SHIFT_LEFT -> Key.ShiftLeft
            Input.Keys.SHIFT_RIGHT -> Key.ShiftRight
            Input.Keys.CONTROL_LEFT -> Key.ControlLeft
            Input.Keys.CONTROL_RIGHT -> Key.ControlRight
            Input.Keys.ALT_LEFT -> Key.AltLeft
            Input.Keys.ALT_RIGHT -> Key.AltRight
            else -> Key.Unknown
        }

    /** Maps LibGDX mouse button codes into normalized engine buttons. */
    private fun mapMouseButton(button: Int): MouseButton =
        when (button) {
            Input.Buttons.LEFT -> MouseButton.Left
            Input.Buttons.RIGHT -> MouseButton.Right
            Input.Buttons.MIDDLE -> MouseButton.Middle
            Input.Buttons.BACK -> MouseButton.Back
            Input.Buttons.FORWARD -> MouseButton.Forward
            else -> MouseButton.Unknown
        }

    /** Converts two digital keys into a -1..1 axis value. */
    private fun InputSnapshot.booleanAxis(
        negative: Key,
        positive: Key,
    ): Float {
        val left = if (isDown(negative)) 1f else 0f
        val right = if (isDown(positive)) 1f else 0f
        return right - left
    }

    /** Recenters the cursor if captured input reaches the window edge. */
    private fun keepCursorInsideWindow() {
        if (!cursorCaptured) return

        val width = Gdx.graphics.width
        val height = Gdx.graphics.height
        if (
            mouseX <= CURSOR_EDGE_MARGIN ||
            mouseY <= CURSOR_EDGE_MARGIN ||
            mouseX >= width - CURSOR_EDGE_MARGIN ||
            mouseY >= height - CURSOR_EDGE_MARGIN
        ) {
            moveCursorToWindowCenter()
        }
    }

    /** Warps the cursor to the window center and suppresses the synthetic move event. */
    private fun moveCursorToWindowCenter() {
        val centerX = Gdx.graphics.width / 2
        val centerY = Gdx.graphics.height / 2
        ignoredWarpX = centerX
        ignoredWarpY = centerY
        mouseX = centerX
        mouseY = centerY
        Gdx.input.setCursorPosition(centerX, centerY)
    }

    companion object {
        private const val CURSOR_EDGE_MARGIN = 24
    }
}
