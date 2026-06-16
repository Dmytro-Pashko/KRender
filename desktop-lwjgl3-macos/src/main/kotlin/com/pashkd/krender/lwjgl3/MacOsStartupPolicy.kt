package com.pashkd.krender.lwjgl3

import org.lwjgl.system.JNI
import org.lwjgl.system.macosx.LibC
import org.lwjgl.system.macosx.ObjCRuntime
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory

object MacOsStartupPolicy {
    private const val JVM_RESTARTED_ARG = "krender.macos.jvmIsRestarted"
    private const val JRE_ERROR =
        "A Java installation could not be found. If you distribute this app with a bundled JRE, set '-XstartOnFirstThread' manually."
    private const val CHILD_LOOP_ERROR =
        "The current JVM process is already a spawned macOS child process; refusing to spawn another one."

    fun startNewJvmIfRequired(
        mainClassName: String,
        inheritIO: Boolean = true,
    ): Boolean {
        if (System.getProperty("org.graalvm.nativeimage.imagecode", "").isNotEmpty()) return false
        if (isMainThread()) return false

        val processId = LibC.getpid()
        if (System.getenv("JAVA_STARTED_ON_FIRST_THREAD_$processId") == "1") return false

        if (System.getProperty(JVM_RESTARTED_ARG) == "true") {
            System.err.println(CHILD_LOOP_ERROR)
            return false
        }

        val javaExecPath = "${System.getProperty("java.home")}/bin/java"
        if (!File(javaExecPath).exists()) {
            System.err.println(JRE_ERROR)
            return false
        }

        val command =
            buildList {
                add(javaExecPath)
                add("-XstartOnFirstThread")
                add("-D$JVM_RESTARTED_ARG=true")
                addAll(ManagementFactory.getRuntimeMXBean().inputArguments)
                add("-cp")
                add(System.getProperty("java.class.path"))
                add(mainClassName)
            }

        return startChild(command, inheritIO)
    }

    private fun isMainThread(): Boolean {
        val objcMsgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend")
        val nsThread = ObjCRuntime.objc_getClass("NSThread")
        val currentThread = JNI.invokePPP(nsThread, ObjCRuntime.sel_getUid("currentThread"), objcMsgSend)
        return JNI.invokePPZ(currentThread, ObjCRuntime.sel_getUid("isMainThread"), objcMsgSend)
    }

    private fun startChild(
        command: List<String>,
        inheritIO: Boolean,
    ): Boolean {
        try {
            val processBuilder = ProcessBuilder(command)
            if (inheritIO) {
                processBuilder.inheritIO().start().waitFor()
            } else {
                processBuilder.start()
            }
        } catch (error: IOException) {
            System.err.println("There was a problem restarting the JVM for macOS first-thread startup.")
            System.err.println(error.stackTraceToString())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            System.err.println("Interrupted while waiting for the macOS first-thread child JVM.")
            System.err.println(error.stackTraceToString())
        }
        return true
    }
}
