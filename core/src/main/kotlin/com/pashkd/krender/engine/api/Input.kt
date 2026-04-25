package com.pashkd.krender.engine.api

enum class Key {
    W,
    A,
    S,
    D,
    G,
    Q,
    E,
    F1,
    F2,
    F3,
    F4,
    Backtick,
    Space,
    Escape,
    Tab,
    ShiftLeft,
    ControlLeft,
    Unknown,
}

enum class PointerPhase {
    Down,
    Move,
    Up,
    Cancelled,
}

data class PointerState(
    val id: Int,
    val phase: PointerPhase,
    val screenPosition: Vec2,
)

data class Action(val name: String)
data class Axis(val name: String)

data class InputSnapshot(
    val keysDown: Set<Key> = emptySet(),
    val keysPressedThisFrame: Set<Key> = emptySet(),
    val keysReleasedThisFrame: Set<Key> = emptySet(),
    val mousePosition: Vec2 = Vec2.zero(),
    val mouseDelta: Vec2 = Vec2.zero(),
    val scrollDelta: Float = 0f,
    val pointers: List<PointerState> = emptyList(),
    val viewportSize: Vec2 = Vec2.zero(),
) {
    fun isDown(key: Key): Boolean = key in keysDown
    fun wasPressed(key: Key): Boolean = key in keysPressedThisFrame
    fun wasReleased(key: Key): Boolean = key in keysReleasedThisFrame
}

/**
 * Frame-stable input API combining physical state, transient events, actions, and axes.
 */
interface InputService {
    fun beginFrame()
    fun snapshot(): InputSnapshot
    fun endFrame()
    fun setCursorCaptured(captured: Boolean)
    fun isActionPressed(action: Action): Boolean
    fun isActionJustPressed(action: Action): Boolean
    fun axis(axis: Axis): Float
}
