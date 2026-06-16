@file:JvmName("MacOsLwjgl3Launcher")

package com.pashkd.krender.lwjgl3

fun main(args: Array<String>) {
    if (MacOsStartupPolicy.startNewJvmIfRequired(MAIN_CLASS_NAME)) {
        return
    }
    launchKRenderDesktopApplication(args, MAIN_CLASS_NAME)
}

private const val MAIN_CLASS_NAME = "com.pashkd.krender.lwjgl3.MacOsLwjgl3Launcher"
