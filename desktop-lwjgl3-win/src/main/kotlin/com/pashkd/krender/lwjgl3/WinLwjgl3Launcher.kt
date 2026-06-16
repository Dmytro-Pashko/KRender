@file:JvmName("WinLwjgl3Launcher")

package com.pashkd.krender.lwjgl3

fun main(args: Array<String>) {
    WinStartupPolicy.prepare()
    launchKRenderDesktopApplication(args, MainClassName)
}

private const val MainClassName = "com.pashkd.krender.lwjgl3.WinLwjgl3Launcher"
