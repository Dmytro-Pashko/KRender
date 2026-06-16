package com.pashkd.krender.lwjgl3

import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory

object LinuxStartupPolicy {
    private const val JVM_RESTARTED_ARG = "krender.linux.jvmIsRestarted"
    private const val JRE_ERROR =
        "A Java installation could not be found. If you distribute this app with a bundled JRE, set '__GL_THREADED_OPTIMIZATIONS=0' manually."
    private const val CHILD_LOOP_ERROR =
        "The current JVM process is already a spawned Linux child process; refusing to spawn another one."

    fun startNewJvmIfRequired(
        mainClassName: String,
        inheritIO: Boolean = true,
    ): Boolean =
        if (shouldUseCurrentJvm()) {
            false
        } else {
            val javaExecPath = "${System.getProperty("java.home")}/bin/java"
            preflightError(javaExecPath)?.let { errorMessage ->
                System.err.println(errorMessage)
                false
            } ?: run {
                val command =
                    buildList {
                        add(javaExecPath)
                        add("-D$JVM_RESTARTED_ARG=true")
                        addAll(ManagementFactory.getRuntimeMXBean().inputArguments)
                        add("-cp")
                        add(System.getProperty("java.class.path"))
                        add(mainClassName)
                    }

                startChild(command, inheritIO)
            }
        }

    private fun shouldUseCurrentJvm(): Boolean = !isLinuxNvidia() || System.getenv("__GL_THREADED_OPTIMIZATIONS") == "0"

    private fun preflightError(javaExecPath: String): String? =
        when {
            System.getProperty(JVM_RESTARTED_ARG) == "true" -> CHILD_LOOP_ERROR
            !File(javaExecPath).exists() -> JRE_ERROR
            else -> null
        }

    private fun isLinuxNvidia(): Boolean = File("/proc/driver").list { _, path -> "NVIDIA" in path.uppercase() }.isNullOrEmpty().not()

    private fun startChild(
        command: List<String>,
        inheritIO: Boolean,
    ): Boolean {
        try {
            val processBuilder =
                ProcessBuilder(command).also { builder ->
                    builder.environment()["__GL_THREADED_OPTIMIZATIONS"] = "0"
                }
            if (inheritIO) {
                processBuilder.inheritIO().start().waitFor()
            } else {
                processBuilder.start()
            }
        } catch (error: IOException) {
            System.err.println("There was a problem restarting the JVM for Linux NVIDIA startup.")
            System.err.println(error.stackTraceToString())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            System.err.println("Interrupted while waiting for the Linux NVIDIA child JVM.")
            System.err.println(error.stackTraceToString())
        }
        return true
    }
}
