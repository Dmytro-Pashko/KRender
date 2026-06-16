package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.GL20
import com.pashkd.krender.engine.api.RuntimeStatsService
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.ui.UiCaptureState
import com.pashkd.krender.engine.ui.UiService
import glm_.vec2.Vec2
import imgui.*
import imgui.ImGui.image
import imgui.classes.Context
import imgui.impl.gl.ImplGL3

/**
 * Owns the shared ImGui context and backend renderer.
 */
class GdxImGuiService(
    private val input: GdxInputService,
    private val runtimeStats: RuntimeStatsService,
) : UiService {
    private val context = Context()
    private val renderer = withContext { ImplGL3() }
    private val inputBridge = GdxImGuiInputBridge(context)
    private var frameOpen = false
    private var frameReady = false

    override var captureState: UiCaptureState = UiCaptureState()
        private set

    /**
     * Configures ImGui and hooks its input bridge into the backend input chain.
     */
    init {
        withCurrentContext {
            val io = ImGui.io
            io.backendPlatformName = "krender_gdx"
            io.configFlags = io.configFlags / ConfigFlag.NavEnableKeyboard / ConfigFlag.NoMouseCursorChange
        }
        input.addProcessor(inputBridge)
        input.setUiCaptureProvider { captureState }
    }

    /**
     * Starts a new ImGui frame and syncs display and mouse state.
     */
    override fun beginFrame(deltaSeconds: Float) {
        frameReady = false
        runtimeStats.put("FPS", Gdx.graphics.framesPerSecond)
        withCurrentContext {
            val io = ImGui.io
            io.deltaTime = deltaSeconds.coerceAtLeast(1f / 1_000f)
            io.displaySize.x = Gdx.graphics.width
            io.displaySize.y = Gdx.graphics.height
            io.displayFramebufferScale.x = 1f
            io.displayFramebufferScale.y = 1f
            // Mouse position is fed to ImGui by [GdxImGuiInputBridge] from libgdx
            // input callbacks; re-adding it here would split a single physical
            // move into two virtual events and could desync hover/active state.

            renderer.newFrame()
            ImGui.newFrame()
            // Refresh capture flags right after NewFrame so consumers sampling
            // capture state at the start of the frame (e.g. InputService) see
            // the current frame's hover decision instead of the previous one.
            captureState =
                UiCaptureState(
                    mouse = ImGui.io.wantCaptureMouse,
                    keyboard = ImGui.io.wantCaptureKeyboard,
                )
        }
        frameOpen = true
    }

    /**
     * Finalizes the ImGui frame and refreshes capture flags.
     */
    override fun endFrame() {
        if (!frameOpen) return

        withCurrentContext {
            ImGui.render()
            captureState =
                UiCaptureState(
                    mouse = ImGui.io.wantCaptureMouse,
                    keyboard = ImGui.io.wantCaptureKeyboard,
                )
        }
        frameOpen = false
        frameReady = true
    }

    /**
     * Submits the prepared ImGui draw data to the GL renderer.
     */
    override fun render() {
        if (!frameReady) return

        withCurrentContext {
            Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(false)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            Gdx.gl.glColorMask(true, true, true, true)
            ImGui.drawData?.let(renderer::renderDrawData)
            forceOpaqueBackBufferAlpha()
        }
        frameReady = false
    }

    /**
     * Accepts resize notifications for API symmetry with the engine UI contract.
     */
    override fun resize(
        width: Int,
        height: Int,
    ) = Unit

    /**
     * Draws a read-only texture preview using the backend GL texture id.
     */
    override fun drawTexturePreview(
        handle: TexturePreviewHandle,
        width: Float,
        height: Float,
    ): Boolean =
        withContext {
            if (handle.id <= 0 || width <= 0f || height <= 0f) return@withContext false
            image(
                handle.id,
                Vec2(width, height),
                Vec2(handle.u0, handle.v0),
                Vec2(handle.u1, handle.v1),
            )
            true
        }

    /**
     * Tears down ImGui renderer resources and unregisters the input bridge.
     */
    override fun dispose() {
        input.removeProcessor(inputBridge)
        input.setUiCaptureProvider { UiCaptureState() }
        withCurrentContext {
            renderer.shutdown()
            context.destroy()
        }
    }

    /**
     * Temporarily makes this service's ImGui context current for a returning block.
     */
    private fun <T> withContext(block: () -> T): T {
        val previous = ImGui.currentContext
        context.setCurrent()
        return try {
            block()
        } finally {
            previous?.setCurrent()
        }
    }

    /**
     * Temporarily makes this service's ImGui context current for a void block.
     */
    private fun withCurrentContext(block: () -> Unit) {
        val previous = ImGui.currentContext
        context.setCurrent()
        try {
            block()
        } finally {
            previous?.setCurrent()
        }
    }

    private fun forceOpaqueBackBufferAlpha() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glColorMask(false, false, false, true)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glColorMask(true, true, true, true)
        Gdx.gl.glDepthMask(true)
    }
}

/**
 * Forwards backend input events into the active ImGui context.
 */
private class GdxImGuiInputBridge(
    private val context: Context,
) : InputProcessor {
    /**
     * Runs an input callback with this bridge's ImGui context current.
     */
    private fun <T> withContext(block: () -> T): T {
        val previous = ImGui.currentContext
        context.setCurrent()
        return try {
            block()
        } finally {
            previous?.setCurrent()
        }
    }

    /**
     * Pushes a key press into ImGui.
     */
    override fun keyDown(keycode: Int): Boolean =
        withContext {
            ImGui.io.addKeyEvent(mapKey(keycode) ?: return@withContext false, true)
            syncModifierKeys()
            false
        }

    /**
     * Pushes a key release into ImGui.
     */
    override fun keyUp(keycode: Int): Boolean =
        withContext {
            ImGui.io.addKeyEvent(mapKey(keycode) ?: return@withContext false, false)
            syncModifierKeys()
            false
        }

    /**
     * Pushes text input characters into ImGui.
     */
    override fun keyTyped(character: Char): Boolean =
        withContext {
            if (!character.isISOControl() || character == '\n' || character == '\t') {
                ImGui.io.addInputCharacter(character)
            }
            false
        }

    /**
     * Forwards a cancelled pointer interaction to ImGui.
     */
    override fun touchCancelled(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int,
    ): Boolean =
        withContext {
            ImGui.io.addMouseSourceEvent(MouseSource.Mouse)
            ImGui.io.addMousePosEvent(screenX.toFloat(), screenY.toFloat())
            mapMouseButton(button)?.let { ImGui.io.addMouseButtonEvent(it, false) }
            false
        }

    /**
     * Forwards a pointer press to ImGui.
     */
    override fun touchDown(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int,
    ): Boolean =
        withContext {
            ImGui.io.addMouseSourceEvent(MouseSource.Mouse)
            ImGui.io.addMousePosEvent(screenX.toFloat(), screenY.toFloat())
            mapMouseButton(button)?.let { ImGui.io.addMouseButtonEvent(it, true) }
            false
        }

    /**
     * Forwards a pointer release to ImGui.
     */
    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int,
    ): Boolean =
        withContext {
            ImGui.io.addMouseSourceEvent(MouseSource.Mouse)
            ImGui.io.addMousePosEvent(screenX.toFloat(), screenY.toFloat())
            mapMouseButton(button)?.let { ImGui.io.addMouseButtonEvent(it, false) }
            false
        }

    /**
     * Forwards a pointer drag to ImGui.
     */
    override fun touchDragged(
        screenX: Int,
        screenY: Int,
        pointer: Int,
    ): Boolean =
        withContext {
            ImGui.io.addMouseSourceEvent(MouseSource.Mouse)
            ImGui.io.addMousePosEvent(screenX.toFloat(), screenY.toFloat())
            false
        }

    /**
     * Forwards mouse movement to ImGui.
     */
    override fun mouseMoved(
        screenX: Int,
        screenY: Int,
    ): Boolean =
        withContext {
            ImGui.io.addMouseSourceEvent(MouseSource.Mouse)
            ImGui.io.addMousePosEvent(screenX.toFloat(), screenY.toFloat())
            false
        }

    /**
     * Forwards mouse wheel scrolling to ImGui.
     */
    override fun scrolled(
        amountX: Float,
        amountY: Float,
    ): Boolean =
        withContext {
            ImGui.io.addMouseWheelEvent(amountX, -amountY)
            false
        }

    /**
     * Refreshes ImGui's modifier-key state after key events.
     */
    private fun syncModifierKeys() {
        ImGui.io.addKeyEvent(Key.Mod_Ctrl, isModifierPressed(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT))
        ImGui.io.addKeyEvent(Key.Mod_Shift, isModifierPressed(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT))
        ImGui.io.addKeyEvent(Key.Mod_Alt, isModifierPressed(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT))
        ImGui.io.addKeyEvent(Key.Mod_Super, Gdx.input.isKeyPressed(Input.Keys.SYM))
    }

    /**
     * Checks whether either physical key for a modifier is currently held.
     */
    private fun isModifierPressed(
        primary: Int,
        secondary: Int,
    ): Boolean = Gdx.input.isKeyPressed(primary) || Gdx.input.isKeyPressed(secondary)

    /**
     * Converts LibGDX mouse button ids to ImGui button ids.
     */
    private fun mapMouseButton(button: Int): MouseButton? =
        when (button) {
            Input.Buttons.LEFT -> MouseButton.Left
            Input.Buttons.RIGHT -> MouseButton.Right
            Input.Buttons.MIDDLE -> MouseButton.Middle
            Input.Buttons.BACK -> MouseButton._unused0
            Input.Buttons.FORWARD -> MouseButton._unused1
            else -> null
        }

    /**
     * Converts LibGDX keycodes to ImGui key ids.
     */
    private fun mapKey(keycode: Int): Key? =
        when (keycode) {
            Input.Keys.TAB -> Key.Tab
            Input.Keys.LEFT -> Key.LeftArrow
            Input.Keys.RIGHT -> Key.RightArrow
            Input.Keys.UP -> Key.UpArrow
            Input.Keys.DOWN -> Key.DownArrow
            Input.Keys.PAGE_UP -> Key.PageUp
            Input.Keys.PAGE_DOWN -> Key.PageDown
            Input.Keys.HOME -> Key.Home
            Input.Keys.END -> Key.End
            Input.Keys.INSERT -> Key.Insert
            Input.Keys.DEL -> Key.Delete
            Input.Keys.FORWARD_DEL -> Key.Delete
            Input.Keys.BACKSPACE -> Key.Backspace
            Input.Keys.SPACE -> Key.Space
            Input.Keys.ENTER -> Key.Enter
            Input.Keys.ESCAPE -> Key.Escape
            Input.Keys.CONTROL_LEFT -> Key.LeftCtrl
            Input.Keys.CONTROL_RIGHT -> Key.RightCtrl
            Input.Keys.SHIFT_LEFT -> Key.LeftShift
            Input.Keys.SHIFT_RIGHT -> Key.RightShift
            Input.Keys.ALT_LEFT -> Key.LeftAlt
            Input.Keys.ALT_RIGHT -> Key.RightAlt
            Input.Keys.SYM -> Key.LeftSuper
            Input.Keys.MENU -> Key.Menu
            Input.Keys.NUM_0 -> Key.`0`
            Input.Keys.NUM_1 -> Key.`1`
            Input.Keys.NUM_2 -> Key.`2`
            Input.Keys.NUM_3 -> Key.`3`
            Input.Keys.NUM_4 -> Key.`4`
            Input.Keys.NUM_5 -> Key.`5`
            Input.Keys.NUM_6 -> Key.`6`
            Input.Keys.NUM_7 -> Key.`7`
            Input.Keys.NUM_8 -> Key.`8`
            Input.Keys.NUM_9 -> Key.`9`
            Input.Keys.A -> Key.A
            Input.Keys.B -> Key.B
            Input.Keys.C -> Key.C
            Input.Keys.D -> Key.D
            Input.Keys.E -> Key.E
            Input.Keys.F -> Key.F
            Input.Keys.G -> Key.G
            Input.Keys.H -> Key.H
            Input.Keys.I -> Key.I
            Input.Keys.J -> Key.J
            Input.Keys.K -> Key.K
            Input.Keys.L -> Key.L
            Input.Keys.M -> Key.M
            Input.Keys.N -> Key.N
            Input.Keys.O -> Key.O
            Input.Keys.P -> Key.P
            Input.Keys.Q -> Key.Q
            Input.Keys.R -> Key.R
            Input.Keys.S -> Key.S
            Input.Keys.T -> Key.T
            Input.Keys.U -> Key.U
            Input.Keys.V -> Key.V
            Input.Keys.W -> Key.W
            Input.Keys.X -> Key.X
            Input.Keys.Y -> Key.Y
            Input.Keys.Z -> Key.Z
            Input.Keys.F1 -> Key.F1
            Input.Keys.F2 -> Key.F2
            Input.Keys.F3 -> Key.F3
            Input.Keys.F4 -> Key.F4
            Input.Keys.F5 -> Key.F5
            Input.Keys.F6 -> Key.F6
            Input.Keys.F7 -> Key.F7
            Input.Keys.F8 -> Key.F8
            Input.Keys.F9 -> Key.F9
            Input.Keys.F10 -> Key.F10
            Input.Keys.F11 -> Key.F11
            Input.Keys.F12 -> Key.F12
            Input.Keys.APOSTROPHE -> Key.Apostrophe
            Input.Keys.COMMA -> Key.Comma
            Input.Keys.MINUS -> Key.Minus
            Input.Keys.PERIOD -> Key.Period
            Input.Keys.SLASH -> Key.Slash
            Input.Keys.SEMICOLON -> Key.Semicolon
            Input.Keys.EQUALS -> Key.Equal
            Input.Keys.LEFT_BRACKET -> Key.LeftBracket
            Input.Keys.BACKSLASH -> Key.Backslash
            Input.Keys.RIGHT_BRACKET -> Key.RightBracket
            Input.Keys.GRAVE -> Key.GraveAccent
            else -> null
        }
}
