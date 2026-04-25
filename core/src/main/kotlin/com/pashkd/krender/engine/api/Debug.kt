package com.pashkd.krender.engine.api

import kotlin.system.measureNanoTime

/**
 * Describes the severity of a log message.
 */
enum class LogLevel {
    /** Fine-grained diagnostic output for deep tracing. */
    Trace,
    /** Developer-focused diagnostic output. */
    Debug,
    /** General runtime information. */
    Info,
    /** Recoverable issue or suspicious state. */
    Warn,
    /** Failure or severe runtime problem. */
    Error,
}

/**
 * Captures one structured log message emitted by the engine or gameplay code.
 */
data class LogEntry(
    /** Severity attached to this log line. */
    val level: LogLevel,
    /** Logical subsystem or feature tag. */
    val tag: String,
    /** Final rendered log message. */
    val message: String,
    /** Frame index when the log was recorded. */
    val frame: Long,
    /** Thread that produced the log entry. */
    val threadName: String,
    /** Optional error attached to the log entry. */
    val error: Throwable? = null,
)

/**
 * Lazy structured logger used by engine and gameplay code.
 *
 * Message lambdas are evaluated only when the backend accepts the log level.
 */
interface Logger {
    /** Returns whether the backend currently accepts the given log level. */
    fun isEnabled(level: LogLevel): Boolean = true

    /** Emits one structured log event. */
    fun log(level: LogLevel, tag: String, error: Throwable? = null, message: () -> String)

    /** Emits a trace-level message. */
    fun trace(tag: String, message: () -> String) = log(LogLevel.Trace, tag, null, message)
    /** Emits a debug-level message. */
    fun debug(tag: String, message: () -> String) = log(LogLevel.Debug, tag, null, message)
    /** Emits an info-level message. */
    fun info(tag: String, message: () -> String) = log(LogLevel.Info, tag, null, message)
    /** Emits a warning-level message. */
    fun warn(tag: String, error: Throwable? = null, message: () -> String) = log(LogLevel.Warn, tag, error, message)
    /** Emits an error-level message. */
    fun error(tag: String, error: Throwable? = null, message: () -> String) = log(LogLevel.Error, tag, error, message)
}

/**
 * Stores one measured phase duration for the current frame.
 */
data class PhaseTiming(
    /** Logical phase name shown in the debug UI. */
    val name: String,
    /** Measured duration in milliseconds. */
    val millis: Float,
)

/**
 * Summarizes timing and fixed-step activity for one frame.
 */
data class FrameDebugStats(
    /** Frame index represented by this snapshot. */
    val frame: Long,
    /** Raw delta used for the frame. */
    val deltaSeconds: Float,
    /** Number of fixed updates executed this frame. */
    val fixedUpdates: Int,
    /** Timed phases recorded for the frame. */
    val timings: List<PhaseTiming>,
)

/**
 * Per-frame debug collector for overlay values, recent logs, and phase timings.
 */
interface DebugService {
    /** Controls whether statistics panels are shown. */
    var enabled: Boolean
    /** Controls whether recent logs are shown. */
    var logsEnabled: Boolean
    /** Returns the combined textual debug output for the current frame. */
    val entries: List<String>
    /** Returns formatted key/value statistics for the current frame. */
    val statEntries: List<String>
    /** Returns helper text lines for controls and hints. */
    val helperLines: List<String>
    /** Returns recently recorded structured logs. */
    val recentLogs: List<LogEntry>
    /** Returns the current frame index. */
    val frame: Long

    /** Starts a new debug frame and clears per-frame collectors. */
    fun beginFrame()
    /** Finalizes the frame statistics snapshot. */
    fun endFrame(deltaSeconds: Float, fixedUpdates: Int)
    /** Stores a formatted key/value metric. */
    fun put(label: String, value: Any?)
    /** Appends one free-form helper line. */
    fun line(text: String)
    /** Records a structured log entry for later display. */
    fun recordLog(entry: LogEntry)
    /** Toggles log visibility. */
    fun toggle()
    /** Toggles statistics visibility. */
    fun toggleStats()
    /** Measures a block and records its elapsed time under the given name. */
    fun <T> measure(name: String, block: () -> T): T
}

/**
 * Default in-memory debug collector used by the engine runtime.
 */
class FrameDebugService(
    override var enabled: Boolean = true,
    override var logsEnabled: Boolean = true,
    private val maxLogs: Int = 8,
) : DebugService {
    private val values = linkedMapOf<String, String>()
    private val lines = mutableListOf<String>()
    private val timings = mutableListOf<PhaseTiming>()
    private val logs = ArrayDeque<LogEntry>()

    private var lastStats: FrameDebugStats = FrameDebugStats(0, 0f, 0, emptyList())

    override var frame: Long = 0
        private set

    /** Exposes recent logs as a stable list snapshot. */
    override val recentLogs: List<LogEntry>
        get() = logs.toList()

    /** Combines metrics and timings into the statistics window format. */
    override val statEntries: List<String>
        get() {
            val metricLines = values.map { (key, value) -> "$key: $value" }
            val timingLines = lastStats.timings.map { timing -> "${timing.name}: ${"%.2f".format(timing.millis)} ms" }
            return metricLines + timingLines
        }

    /** Exposes helper lines as a stable list snapshot. */
    override val helperLines: List<String>
        get() = lines.toList()

    /** Returns metrics, helper lines, and logs as one merged textual list. */
    override val entries: List<String>
        get() {
            val metricLines = statEntries
            val logLines = logs.map { entry -> "[${entry.level}][${entry.tag}] ${entry.message}" }
            return metricLines + lines + logLines
        }

    /** Begins a new frame and seeds the collector with the frame number. */
    override fun beginFrame() {
        frame += 1
        values.clear()
        lines.clear()
        timings.clear()
        put("Frame", frame)
    }

    /** Finalizes the summary snapshot for the current frame. */
    override fun endFrame(deltaSeconds: Float, fixedUpdates: Int) {
        put("Delta", "${"%.2f".format(deltaSeconds * 1000f)} ms")
        put("Fixed updates", fixedUpdates)
        lastStats = FrameDebugStats(frame, deltaSeconds, fixedUpdates, timings.toList())
    }

    /** Stores one formatted metric value. */
    override fun put(label: String, value: Any?) {
        values[label] = value.toString()
    }

    /** Appends a helper line for the current frame. */
    override fun line(text: String) {
        lines += text
    }

    /** Adds a log entry while enforcing the configured retention cap. */
    override fun recordLog(entry: LogEntry) {
        logs += entry
        while (logs.size > maxLogs) {
            logs.removeFirst()
        }
    }

    /** Toggles log visibility for the debug UI. */
    override fun toggle() {
        logsEnabled = !logsEnabled
    }

    /** Toggles statistics visibility for the debug UI. */
    override fun toggleStats() {
        enabled = !enabled
    }

    /** Measures a block and records its duration as a named phase. */
    override fun <T> measure(name: String, block: () -> T): T {
        var result: T
        val elapsed = measureNanoTime {
            result = block()
        }
        timings += PhaseTiming(name, elapsed / 1_000_000f)
        return result
    }
}
