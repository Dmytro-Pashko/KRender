package com.pashkd.krender.engine.api

import kotlin.system.measureNanoTime

enum class LogLevel {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
}

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val frame: Long,
    val threadName: String,
    val error: Throwable? = null,
)

/**
 * Lazy structured logger used by engine and gameplay code.
 *
 * Message lambdas are evaluated only when the backend accepts the log level.
 */
interface Logger {
    fun isEnabled(level: LogLevel): Boolean = true
    fun log(level: LogLevel, tag: String, error: Throwable? = null, message: () -> String)

    fun trace(tag: String, message: () -> String) = log(LogLevel.Trace, tag, null, message)
    fun debug(tag: String, message: () -> String) = log(LogLevel.Debug, tag, null, message)
    fun info(tag: String, message: () -> String) = log(LogLevel.Info, tag, null, message)
    fun warn(tag: String, error: Throwable? = null, message: () -> String) = log(LogLevel.Warn, tag, error, message)
    fun error(tag: String, error: Throwable? = null, message: () -> String) = log(LogLevel.Error, tag, error, message)
}

data class PhaseTiming(
    val name: String,
    val millis: Float,
)

data class FrameDebugStats(
    val frame: Long,
    val deltaSeconds: Float,
    val fixedUpdates: Int,
    val timings: List<PhaseTiming>,
)

/**
 * Per-frame debug collector for overlay values, recent logs, and phase timings.
 */
interface DebugService {
    var enabled: Boolean
    val entries: List<String>
    val recentLogs: List<LogEntry>
    val frame: Long

    fun beginFrame()
    fun endFrame(deltaSeconds: Float, fixedUpdates: Int)
    fun put(label: String, value: Any?)
    fun line(text: String)
    fun recordLog(entry: LogEntry)
    fun toggle()
    fun <T> measure(name: String, block: () -> T): T
}

class FrameDebugService(
    override var enabled: Boolean = true,
    private val maxLogs: Int = 8,
) : DebugService {
    private val values = linkedMapOf<String, String>()
    private val lines = mutableListOf<String>()
    private val timings = mutableListOf<PhaseTiming>()
    private val logs = ArrayDeque<LogEntry>()

    private var lastStats: FrameDebugStats = FrameDebugStats(0, 0f, 0, emptyList())

    override var frame: Long = 0
        private set

    override val recentLogs: List<LogEntry>
        get() = logs.toList()

    override val entries: List<String>
        get() {
            val metricLines = values.map { (key, value) -> "$key: $value" }
            val timingLines = lastStats.timings.map { timing -> "${timing.name}: ${"%.2f".format(timing.millis)} ms" }
            val logLines = logs.map { entry -> "[${entry.level}][${entry.tag}] ${entry.message}" }
            return metricLines + lines + timingLines + logLines
        }

    override fun beginFrame() {
        frame += 1
        values.clear()
        lines.clear()
        timings.clear()
        put("Frame", frame)
    }

    override fun endFrame(deltaSeconds: Float, fixedUpdates: Int) {
        put("Delta", "${"%.2f".format(deltaSeconds * 1000f)} ms")
        put("Fixed updates", fixedUpdates)
        lastStats = FrameDebugStats(frame, deltaSeconds, fixedUpdates, timings.toList())
    }

    override fun put(label: String, value: Any?) {
        values[label] = value.toString()
    }

    override fun line(text: String) {
        lines += text
    }

    override fun recordLog(entry: LogEntry) {
        logs += entry
        while (logs.size > maxLogs) {
            logs.removeFirst()
        }
    }

    override fun toggle() {
        enabled = !enabled
    }

    override fun <T> measure(name: String, block: () -> T): T {
        var result: T
        val elapsed = measureNanoTime {
            result = block()
        }
        timings += PhaseTiming(name, elapsed / 1_000_000f)
        return result
    }
}
