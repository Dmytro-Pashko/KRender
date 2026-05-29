package com.pashkd.krender.engine.viewport

/**
 * Optional scene contract for runtime UI viewport policy.
 *
 * A scene may implement this interface to describe its logical design resolution and
 * [UiScalePolicy]. The engine reads this during resize propagation to calculate the
 * runtime UI viewport. Implementing this interface must never change the physical
 * backend window size; it only describes how UI coordinates should be interpreted
 * inside whatever render target the backend provides.
 */
interface SceneViewportConfig {
    /** Runtime UI viewport preferences for this scene. */
    val viewportConfig: RuntimeViewportConfig
}
