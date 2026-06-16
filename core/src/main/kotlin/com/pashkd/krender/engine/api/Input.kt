package com.pashkd.krender.engine.api

/**
 * Enumerates the normalized keys used by the engine input layer.
 */
enum class Key {
    /** Forward movement key. */
    W,

    /** Left movement key. */
    A,

    /** Backward movement key. */
    S,

    /** Right movement key. */
    D,

    /** Auxiliary gameplay key used by tools and scenes. */
    G,

    /** Vertical camera movement key. */
    R,

    /** Vertical camera movement key. */
    F,

    /** Undo/redo shortcut key. */
    Y,

    /** Undo/redo shortcut key. */
    Z,

    /** Secondary action key often used for left rotation. */
    Q,

    /** Secondary action key often used for right rotation. */
    E,

    /** First function key. */
    F1,

    /** Second function key. */
    F2,

    /** Third function key. */
    F3,

    /** Fourth function key. */
    F4,

    /** Fifth function key. */
    F5,

    /** Console or debug toggle key. */
    Backtick,

    /** Spacebar key. */
    Space,

    /** Escape key. */
    Escape,

    /** Tab key. */
    Tab,

    /** Left shift key. */
    ShiftLeft,

    /** Right shift key. */
    ShiftRight,

    /** Left control key. */
    ControlLeft,

    /** Right control key. */
    ControlRight,

    /** Left alt key. */
    AltLeft,

    /** Right alt key. */
    AltRight,

    /** Fallback value for unmapped keys. */
    Unknown,
}

/**
 * Describes the lifecycle phase of a pointer interaction.
 */
enum class PointerPhase {
    /** Pointer was pressed this frame. */
    Down,

    /** Pointer moved while still active. */
    Move,

    /** Pointer was released this frame. */
    Up,

    /** Pointer interaction was cancelled by the platform. */
    Cancelled,
}

/**
 * Enumerates normalized mouse buttons used by tools and editor cameras.
 */
enum class MouseButton {
    /** Primary mouse button. */
    Left,

    /** Secondary mouse button. */
    Right,

    /** Middle mouse button or wheel click. */
    Middle,

    /** Browser/back auxiliary mouse button. */
    Back,

    /** Browser/forward auxiliary mouse button. */
    Forward,

    /** Fallback value for unmapped buttons. */
    Unknown,
}

/**
 * Stores the state of one pointer for the current frame.
 */
data class PointerState(
    /** Backend-specific pointer identifier. */
    val id: Int,
    /** Current phase of the pointer interaction. */
    val phase: PointerPhase,
    /** Pointer position in viewport pixel space. */
    val screenPosition: Vec2,
)

/** Identifies a named digital input action. */
data class Action(
    val name: String,
)

/** Identifies a named analog input axis. */
data class Axis(
    val name: String,
)

/**
 * Immutable snapshot of all normalized input state for one frame.
 */
data class InputSnapshot(
    /** Keys currently held down. */
    val keysDown: Set<Key> = emptySet(),
    /** Keys pressed during this frame. */
    val keysPressedThisFrame: Set<Key> = emptySet(),
    /** Keys released during this frame. */
    val keysReleasedThisFrame: Set<Key> = emptySet(),
    /** Mouse buttons currently held down. */
    val mouseButtonsDown: Set<MouseButton> = emptySet(),
    /** Mouse buttons pressed during this frame. */
    val mouseButtonsPressedThisFrame: Set<MouseButton> = emptySet(),
    /** Mouse buttons released during this frame. */
    val mouseButtonsReleasedThisFrame: Set<MouseButton> = emptySet(),
    /** Current mouse position in viewport pixel space. */
    val mousePosition: Vec2 = Vec2.zero(),
    /** Mouse delta accumulated this frame. */
    val mouseDelta: Vec2 = Vec2.zero(),
    /** Scroll wheel delta accumulated this frame. */
    val scrollDelta: Float = 0f,
    /** Active pointer states for this frame. */
    val pointers: List<PointerState> = emptyList(),
    /** Current viewport size used for screen-space calculations. */
    val viewportSize: Vec2 = Vec2.zero(),
    /** Indicates whether the UI consumed mouse input this frame. */
    val uiCapturesMouse: Boolean = false,
    /** Indicates whether the UI consumed keyboard input this frame. */
    val uiCapturesKeyboard: Boolean = false,
) {
    /** Returns whether the given key is currently held. */
    fun isDown(key: Key): Boolean = key in keysDown

    /** Returns whether the given key was pressed during this frame. */
    fun wasPressed(key: Key): Boolean = key in keysPressedThisFrame

    /** Returns whether the given key was released during this frame. */
    fun wasReleased(key: Key): Boolean = key in keysReleasedThisFrame

    /** Returns whether the given mouse button is currently held. */
    fun isMouseDown(button: MouseButton): Boolean = button in mouseButtonsDown

    /** Returns whether the given mouse button was pressed during this frame. */
    fun wasMousePressed(button: MouseButton): Boolean = button in mouseButtonsPressedThisFrame

    /** Returns whether the given mouse button was released during this frame. */
    fun wasMouseReleased(button: MouseButton): Boolean = button in mouseButtonsReleasedThisFrame

    /** Returns whether any UI layer is currently consuming user input. */
    fun isCapturedByUI(): Boolean = uiCapturesMouse || uiCapturesKeyboard
}

/**
 * Frame-stable input API combining physical state, transient events, actions, and axes.
 */
interface InputService {
    /** Begins input collection for a new frame. */
    fun beginFrame()

    /** Returns the immutable snapshot for the current frame. */
    fun snapshot(): InputSnapshot

    /** Finalizes the current frame and clears transient input state. */
    fun endFrame()

    /** Enables or disables cursor capture for first-person style controls. */
    fun setCursorCaptured(captured: Boolean)

    /** Returns whether a named action is currently active. */
    fun isActionPressed(action: Action): Boolean

    /** Returns whether a named action became active this frame. */
    fun isActionJustPressed(action: Action): Boolean

    /** Returns the current value of a named axis. */
    fun axis(axis: Axis): Float
}
