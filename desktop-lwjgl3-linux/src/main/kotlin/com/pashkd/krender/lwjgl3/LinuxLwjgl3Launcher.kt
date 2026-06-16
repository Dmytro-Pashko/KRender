@file:JvmName("LinuxLwjgl3Launcher")

package com.pashkd.krender.lwjgl3

fun main(args: Array<String>) {
    if (LinuxStartupPolicy.startNewJvmIfRequired(MAIN_CLASS_NAME)) {
        return
    }
    launchKRenderDesktopApplication(args, MAIN_CLASS_NAME)
}

private const val MAIN_CLASS_NAME = "com.pashkd.krender.lwjgl3.LinuxLwjgl3Launcher"
