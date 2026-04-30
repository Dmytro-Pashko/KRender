package com.pashkd.krender.lwjgl3

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Desktop launcher that starts a second JVM with the same classpath and runtime scene properties.
 */
class Lwjgl3RuntimeWindowLauncher(
    private val logger: Logger,
) : RuntimeWindowLauncher {
    override fun launchRuntimeScene(scenePath: String) {
        val command = buildCommand(scenePath)
        logger.info(TAG) { "Launch command: ${command.joinToString(" ")}" }
        try {
            ProcessBuilder(command)
                .directory(File(System.getProperty("user.dir")))
                .inheritIO()
                .start()
        } catch (error: Exception) {
            logger.error(TAG, error) { "Runtime scene launch failed: ${error.message}" }
            throw error
        }
    }

    private fun buildCommand(scenePath: String): List<String> =
        buildList {
            add(javaExecutable())
            addAll(filteredJvmArguments())
            add("-Dkrender.scene=runtime-scene")
            add("-Dkrender.scene.path=$scenePath")
            add("-cp")
            add(System.getProperty("java.class.path"))
            add(DesktopMainClass)
        }

    private fun filteredJvmArguments(): List<String> =
        ManagementFactory.getRuntimeMXBean()
            .inputArguments
            .filterNot { argument ->
                argument.startsWith("-Dkrender.scene=") ||
                    argument.startsWith("-Dkrender.scene.path=")
            }

    private fun javaExecutable(): String {
        val executable = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "java.exe"
        } else {
            "java"
        }
        return File(File(System.getProperty("java.home"), "bin"), executable).path
    }

    companion object {
        private const val TAG = "RuntimeWindowLauncher"
        private const val DesktopMainClass = "com.pashkd.krender.lwjgl3.Lwjgl3Launcher"
    }
}
