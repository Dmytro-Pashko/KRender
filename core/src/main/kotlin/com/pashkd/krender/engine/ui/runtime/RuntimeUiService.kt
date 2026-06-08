package com.pashkd.krender.engine.ui.runtime

import com.pashkd.krender.engine.api.Logger

/**
 * Engine-facing runtime UI service that keeps active screens and delegates rendering to a backend.
 */
class RuntimeUiService(
    private val backend: RuntimeUiBackend,
    private val logger: Logger,
) {
    private val layers = linkedMapOf<String, RuntimeUiScreen>()

    fun setActionHandler(handler: RuntimeUiActionHandler?) {
        logger.debug(TAG) { "Runtime UI action handler updated." }
        backend.setActionHandler(handler)
    }

    fun setLayer(
        layer: String,
        screen: RuntimeUiScreen?,
    ) {
        if (screen == null) {
            layers.remove(layer)
            logger.debug(TAG) { "Cleared runtime UI layer '$layer'." }
        } else {
            layers[layer] = screen
            logger.debug(TAG) { "Set runtime UI layer '$layer' to screen '${screen.id}'." }
        }
        backend.syncLayers(activeLayers())
    }

    fun clearLayer(layer: String) {
        if (layers.remove(layer) != null) {
            logger.debug(TAG) { "Cleared runtime UI layer '$layer'." }
            backend.syncLayers(activeLayers())
        }
    }

    fun activeLayers(): List<RuntimeUiLayerState> {
        val ordered = mutableListOf<RuntimeUiLayerState>()
        RuntimeUiLayers.DefaultOrder.forEach { layer ->
            layers[layer]?.let { screen -> ordered += RuntimeUiLayerState(layer, screen) }
        }
        layers.forEach { (layer, screen) ->
            if (layer !in RuntimeUiLayers.DefaultOrder) {
                ordered += RuntimeUiLayerState(layer, screen)
            }
        }
        return ordered
    }

    fun clear() {
        if (layers.isEmpty()) {
            backend.clear()
            return
        }
        logger.debug(TAG) { "Cleared all runtime UI layers." }
        layers.clear()
        backend.clear()
    }

    fun update(dt: Float) {
        backend.update(dt)
    }

    fun render() {
        backend.render()
    }

    fun resize(
        width: Int,
        height: Int,
    ) {
        backend.resize(width, height)
    }

    fun dispose() {
        layers.clear()
        backend.dispose()
    }

    companion object {
        private const val TAG = "RuntimeUiService"
    }
}
