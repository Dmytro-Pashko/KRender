package com.pashkd.krender.lwjgl3

import com.pashkd.krender.engine.api.Logger
import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Starts secondary desktop JVMs with the current Java executable and classpath.
 */
internal class Lwjgl3JvmProcessLauncher(
    private val logger: Logger,
    private val tag: String,
    private val mainClassName: String,
) {
    fun launch(
        properties: Map<String, String>,
        failureMessage: String,
    ) {
        val command = buildCommand(properties)
        val logFile = childProcessLogFile(properties)
        logger.info(tag) { "Launch command: ${command.joinToString(" ")}" }
        logger.info(tag) { "Child process output: ${logFile.toAbsolutePath()}" }
        try {
            writeLaunchHeader(logFile, command)
            val startedAt = System.nanoTime()
            val process =
                ProcessBuilder(command)
                    .directory(File(System.getProperty("user.dir")))
                    .redirectErrorStream(true)
                    .redirectOutput(Redirect.appendTo(logFile.toFile()))
                    .start()
            logger.info(tag) { "Child process started pid=${process.pid()} log='${logFile.toAbsolutePath()}'" }
            watchProcess(process, startedAt, logFile)
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
            add(mainClassName)
        }

    private fun childProcessLogFile(properties: Map<String, String>): Path {
        val scene = properties["krender.scene"] ?: "tool"
        val path =
            properties["krender.model.path"]
                ?: properties["krender.texture.path"]
                ?: properties["krender.terrain.path"]
                ?: properties["krender.scene.path"]
                ?: "session"
        val timestamp = FILE_NAME_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))
        val fileName = "$timestamp-${sanitize(scene)}-${sanitize(path)}.log"
        val directory = Path.of("logs", "editor-tools")
        Files.createDirectories(directory)
        return directory.resolve(fileName)
    }

    private fun writeLaunchHeader(
        logFile: Path,
        command: List<String>,
    ) {
        Files.writeString(
            logFile,
            buildString {
                appendLine("KRender editor tool process")
                appendLine("Started: ${Instant.now()}")
                appendLine("Working directory: ${File(System.getProperty("user.dir")).absolutePath}")
                appendLine("Command:")
                appendLine(command.joinToString(" "))
                appendLine()
            },
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private fun watchProcess(
        process: Process,
        startedAt: Long,
        logFile: Path,
    ) {
        Thread({
            val exitCode = process.waitFor()
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            if (exitCode != 0) {
                logger.error(tag) {
                    "Child process exited with code=$exitCode after ${elapsedMs}ms pid=${process.pid()} log='${logFile.toAbsolutePath()}'"
                }
            } else if (elapsedMs < QuickExitThresholdMs) {
                logger.warn(tag) {
                    "Child process exited quickly with code=0 after ${elapsedMs}ms pid=${process.pid()} log='${logFile.toAbsolutePath()}'"
                }
            } else {
                logger.info(tag) {
                    "Child process exited with code=0 after ${elapsedMs}ms pid=${process.pid()} log='${logFile.toAbsolutePath()}'"
                }
            }
        }, "krender-editor-tool-watch-${process.pid()}").apply {
            isDaemon = true
            start()
        }
    }

    private fun filteredJvmArguments(): List<String> =
        ManagementFactory
            .getRuntimeMXBean()
            .inputArguments
            .filterNot { argument -> filteredSystemPropertyPrefixes.any(argument::startsWith) }

    private fun javaExecutable(): String {
        val executable =
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                "java.exe"
            } else {
                "java"
            }
        return File(File(System.getProperty("java.home"), "bin"), executable).path
    }

    companion object {
        private const val QuickExitThresholdMs = 5_000L
        private val FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

        private val filteredSystemPropertyPrefixes =
            listOf(
                "-Dkrender.scene=",
                "-Dkrender.scene.path=",
                "-Dkrender.model=",
                "-Dkrender.model.path=",
                "-Dkrender.texture.path=",
                "-Dkrender.terrain.path=",
            )

        private fun sanitize(value: String): String =
            value
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .trim('_')
                .take(80)
                .ifBlank { "value" }
    }
}
