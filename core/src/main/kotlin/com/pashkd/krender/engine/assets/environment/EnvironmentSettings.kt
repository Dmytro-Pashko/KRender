package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.Serializable

/**
 * Runtime environment parameters that control how an environment is applied to a scene.
 */
@Serializable
data class EnvironmentSettings(
    val exposure: Float = 1.0f,
    val rotationDegrees: Float = 0.0f,
    val skyboxIntensity: Float = 1.0f,
    val diffuseIntensity: Float = 1.0f,
    val specularIntensity: Float = 1.0f,
    val backgroundMode: BackgroundMode = BackgroundMode.Skybox,
    val backgroundColor: EnvironmentColor? = null,
)

/**
 * How the environment background is displayed.
 */
@Serializable
enum class BackgroundMode {
    /** Displays the configured skybox while keeping IBL active. */
    Skybox,

    /** Clears the viewport with [EnvironmentSettings.backgroundColor]. */
    SolidColor,

    /** Clears the viewport with zero alpha when the target supports transparency. */
    Transparent,

    /** Disables background drawing while keeping environment lighting active. */
    None,
}

/**
 * Simple serializable RGBA color used in environment settings.
 *
 * Kept separate from [com.pashkd.krender.engine.api.Color] which is mutable and
 * not annotated with `@Serializable`.
 */
@Serializable
data class EnvironmentColor(
    val r: Float = 0f,
    val g: Float = 0f,
    val b: Float = 0f,
    val a: Float = 1f,
)
