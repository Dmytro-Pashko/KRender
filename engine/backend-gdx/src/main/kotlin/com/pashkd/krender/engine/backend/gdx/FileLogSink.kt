package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.pashkd.krender.engine.api.LogEntry
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.LogSink
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes structured engine logs into one session-scoped runtime file.
 */
class FileLogSink(
    logsDirectory: Path = defaultLogsDirectory(),
) : LogSink {
    private val sessionTimestamp = FILE_NAME_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))
    private val logsDirectory = Files.createDirectories(logsDirectory)
    private val logFile = this.logsDirectory.resolve("runtime-$sessionTimestamp.log")
    private val lock = Any()

    init {
        Files.writeString(
            logFile,
            "",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    /** Appends one formatted log line and optional stacktrace to the session file. */
    override fun write(entry: LogEntry) {
        val text =
            buildString {
                appendLine(formatEntry(entry))
                val error = entry.error
                if (error != null) {
                    val stackTrace = formatStackTrace(error)
                    append(stackTrace)
                    if (!stackTrace.endsWith(System.lineSeparator())) {
                        appendLine()
                    }
                }
            }
        synchronized(lock) {
            Files.writeString(
                logFile,
                text,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun formatEntry(entry: LogEntry): String =
        "${TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()))} " +
            "[${entry.level}] [${entry.tag}] [frame=${entry.frame}] ${entry.message}"

    private fun formatStackTrace(error: Throwable): String {
        val buffer = StringWriter()
        PrintWriter(buffer).use(error::printStackTrace)
        return buffer.toString()
    }

    companion object {
        private val FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        private fun defaultLogsDirectory(): Path =
            when (Gdx.app?.type) {
                Application.ApplicationType.Android ->
                    Gdx.files
                        .local("logs")
                        .file()
                        .toPath()

                else -> Path.of("assets", "logs")
            }
    }
}

/**
 * Mirrors structured engine logs into the LibGDX application logger.
 */
internal class GdxAppLogSink : LogSink {
    /** Forwards one structured log entry to the active LibGDX application logger. */
    override fun write(entry: LogEntry) {
        when (entry.level) {
            LogLevel.Trace, LogLevel.Debug -> Gdx.app.debug(entry.tag, entry.message)
            LogLevel.Info -> Gdx.app.log(entry.tag, entry.message)
            LogLevel.Warn -> Gdx.app.log(entry.tag, "WARN: ${entry.message}")
            LogLevel.Error ->
                entry.error?.let { error ->
                    Gdx.app.error(entry.tag, entry.message, error)
                } ?: Gdx.app.error(entry.tag, entry.message)
        }
    }
}
