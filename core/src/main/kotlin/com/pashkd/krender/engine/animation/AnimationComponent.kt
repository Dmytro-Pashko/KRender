package com.pashkd.krender.engine.animation

import com.pashkd.krender.engine.api.AnimationPlaybackView
import com.pashkd.krender.engine.api.Component

/**
 * Backend-neutral runtime animation playback state for one entity.
 */
data class AnimationComponent(
    var currentAnimation: String? = null,
    var timeSeconds: Float = 0f,
    var playing: Boolean = true,
    var loop: Boolean = true,
    var speed: Float = 1f,
    var lockedUntilFinished: Boolean = false,
) : Component {
    fun play(
        name: String,
        loop: Boolean,
        restart: Boolean = false,
        lockedUntilFinished: Boolean = false,
    ) {
        if (restart || currentAnimation != name) {
            timeSeconds = 0f
        }
        currentAnimation = name
        this.loop = loop
        playing = true
        this.lockedUntilFinished = lockedUntilFinished
    }
}

fun AnimationComponent.toPlaybackView(): AnimationPlaybackView? {
    val name = currentAnimation?.takeIf(String::isNotBlank) ?: return null
    return AnimationPlaybackView(
        animationName = name,
        timeSeconds = timeSeconds,
        loop = loop,
        playing = playing,
        playbackSpeed = speed,
    )
}
