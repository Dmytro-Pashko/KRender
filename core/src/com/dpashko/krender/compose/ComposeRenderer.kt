package com.dpashko.krender.compose

import androidx.compose.runtime.Composable

/**
 * An abstract base class for rendering AndroidX Compose scenes.
 */
abstract class ComposeRenderer {

    /**
     * Set the Composable content to be rendered.
     *
     * @param content The Composable lambda that defines the UI content.
     */
    abstract fun setContent(content: @Composable () -> Unit)

    /**
     * Trigger the rendering process for the defined content.
     */
    abstract fun render()

    /**
     * Clean up resources and perform necessary disposal operations.
     */
    abstract fun dispose()
}
