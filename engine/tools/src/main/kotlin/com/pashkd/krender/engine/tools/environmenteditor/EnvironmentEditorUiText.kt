package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.BackgroundMode

internal val BackgroundMode.displayName: String
    get() =
        when (this) {
            BackgroundMode.Skybox -> "Skybox"
            BackgroundMode.SolidColor -> "Solid Color"
            BackgroundMode.Transparent -> "Transparent"
            BackgroundMode.None -> "None"
        }

internal val BackgroundMode.description: String
    get() =
        when (this) {
            BackgroundMode.Skybox -> "Uses the configured skybox cubemap as the preview background."
            BackgroundMode.SolidColor -> "Uses the selected flat color as the preview background."
            BackgroundMode.Transparent -> "Clears with zero alpha when the target backbuffer supports transparency."
            BackgroundMode.None -> "Disables background drawing while keeping environment lighting active."
        }
