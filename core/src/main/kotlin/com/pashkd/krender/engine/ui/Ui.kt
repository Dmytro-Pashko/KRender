package com.pashkd.krender.engine.ui

import com.pashkd.krender.engine.api.TexturePreviewHandle

/**
 * Describes whether the UI wants to consume mouse or keyboard input this frame.
 */
data class UiCaptureState(
    /** True when the UI is actively consuming mouse input. */
    val mouse: Boolean = false,
    /** True when the UI is actively consuming keyboard input. */
    val keyboard: Boolean = false,
)

data class UiTextureTint(
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
    val alpha: Float = 1f,
)

/**
 * Shared contract for frame-based UI backends.
 */
interface UiContext {
    /** Current input-capture state reported by the UI backend. */
    val captureState: UiCaptureState

    /** Starts a new UI frame using the elapsed frame time in seconds. */
    fun beginFrame(deltaSeconds: Float)

    /** Finalizes the current UI frame after all panels have been drawn. */
    fun endFrame()

    /** Submits the prepared UI draw data to the active renderer. */
    fun render()

    /** Informs the UI backend about viewport size changes. */
    fun resize(
        width: Int,
        height: Int,
    )

    /** Draws an opaque backend texture preview at the requested UI size. */
    fun drawTexturePreview(
        handle: TexturePreviewHandle,
        width: Float,
        height: Float,
        tint: UiTextureTint = UiTextureTint(),
    ): Boolean = false

    /** Releases all resources owned by the UI backend. */
    fun dispose()
}

/**
 * Concrete engine-facing UI service.
 */
interface UiService : UiContext

/**
 * Minimal UI backend used on platforms where the desktop ImGui renderer is not available.
 */
class NoOpUiService : UiService {
    override val captureState: UiCaptureState = UiCaptureState()

    override fun beginFrame(deltaSeconds: Float) = Unit

    override fun endFrame() = Unit

    override fun render() = Unit

    override fun resize(
        width: Int,
        height: Int,
    ) = Unit

    override fun dispose() = Unit
}
