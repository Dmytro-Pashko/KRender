@file:JvmName("LinuxLwjgl3Launcher")

package com.pashkd.krender.lwjgl3

fun main(args: Array<String>) {
    if (LinuxStartupPolicy.startNewJvmIfRequired(MainClassName)) {
        return
    }
    launchKRenderDesktopApplication(args, MainClassName)
}

private const val MainClassName = "com.pashkd.krender.lwjgl3.LinuxLwjgl3Launcher"
