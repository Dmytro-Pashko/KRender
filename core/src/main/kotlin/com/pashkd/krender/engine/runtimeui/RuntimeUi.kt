package com.pashkd.krender.engine.runtimeui

/**
 * Minimal backend-neutral runtime UI screen request.
 */
data class RuntimeUiScreen(
    val id: String,
    val payload: Map<String, String> = emptyMap(),
)

/**
 * One active runtime UI layer.
 */
data class RuntimeUiLayerState(
    val layer: String,
    val screen: RuntimeUiScreen?,
)

/**
 * Shared runtime UI layer names and default ordering.
 */
object RuntimeUiLayers {
    const val Hud = "hud"
    const val Modal = "modal"
    const val Overlay = "overlay"

    val DefaultOrder: List<String> = listOf(Hud, Modal, Overlay)
}

/**
 * Receives runtime UI actions emitted by a backend.
 */
fun interface RuntimeUiActionHandler {
    fun onRuntimeUiAction(action: String)
}

/**
 * Backend-neutral runtime UI adapter.
 */
interface RuntimeUiBackend {
    fun setActionHandler(handler: RuntimeUiActionHandler?)

    fun syncLayers(layers: List<RuntimeUiLayerState>)

    fun update(dt: Float)

    fun render()

    fun resize(width: Int, height: Int)

    fun clear()

    fun dispose()
}
