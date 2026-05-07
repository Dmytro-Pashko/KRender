package com.pashkd.krender.lwjgl3

import com.pashkd.krender.engine.api.Logger
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.ProcessBuilder.Redirect

/**
 * Starts secondary desktop JVMs with the current Java executable and classpath.
 */
internal class Lwjgl3JvmProcessLauncher(
    private val logger: Logger,
    private val tag: String,
) {
    fun launch(properties: Map<String, String>, failureMessage: String) {
        val command = buildCommand(properties)
        logger.info(tag) { "Launch command: ${command.joinToString(" ")}" }
        try {
            ProcessBuilder(command)
                .directory(File(System.getProperty("user.dir")))
                .redirectOutput(Redirect.DISCARD)
                .redirectError(Redirect.DISCARD)
                .start()
        } catch (error: Exception) {
            logger.error(tag, error) { "$failureMessage: ${error.message}" }
            throw error
        }
    }

    private fun buildCommand(properties: Map<String, String>): List<String> =
        buildList {
            add(javaExecutable())
            addAll(filteredJvmArguments())
            properties.forEach { (key, value) -> add("-D$key=$value") }
            add("-cp")
            add(System.getProperty("java.class.path"))
            add(DesktopMainClass)
        }

    private fun filteredJvmArguments(): List<String> =
        ManagementFactory.getRuntimeMXBean()
            .inputArguments
            .filterNot { argument -> filteredSystemPropertyPrefixes.any(argument::startsWith) }

    private fun javaExecutable(): String {
        val executable = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "java.exe"
        } else {
            "java"
        }
        return File(File(System.getProperty("java.home"), "bin"), executable).path
    }

    companion object {
        private const val DesktopMainClass = "com.pashkd.krender.lwjgl3.Lwjgl3Launcher"

        private val filteredSystemPropertyPrefixes = listOf(
            "-Dkrender.scene=",
            "-Dkrender.scene.path=",
            "-Dkrender.scene.modelPath=",
            "-Dkrender.model=",
            "-Dkrender.model.path=",
            "-Dkrender.terrain.path=",
        )
    }
}
