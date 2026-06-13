package com.pashkd.krender.woolboy

import com.pashkd.krender.engine.api.Component

data class PlayerControllerComponent(
    var isActive: Boolean = true,
    var isGrounded: Boolean = true,
    var jumpTimeSeconds: Float = 0f,
    var greetingRequested: Boolean = false,
) : Component
