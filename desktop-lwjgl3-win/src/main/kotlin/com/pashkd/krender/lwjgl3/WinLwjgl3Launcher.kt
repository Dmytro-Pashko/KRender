@file:JvmName("WinLwjgl3Launcher")

package com.pashkd.krender.lwjgl3

fun main(args: Array<String>) {
    WinStartupPolicy.prepare()
    launchKRenderDesktopApplication(args, MAIN_CLASS_NAME)
}

private const val MAIN_CLASS_NAME = "com.pashkd.krender.lwjgl3.WinLwjgl3Launcher"
