@file:JvmName("MacOsLwjgl3Launcher")

package com.pashkd.krender.lwjgl3

fun main(args: Array<String>) {
    if (MacOsStartupPolicy.startNewJvmIfRequired(MainClassName)) {
        return
    }
    launchKRenderDesktopApplication(args, MainClassName)
}

private const val MainClassName = "com.pashkd.krender.lwjgl3.MacOsLwjgl3Launcher"
