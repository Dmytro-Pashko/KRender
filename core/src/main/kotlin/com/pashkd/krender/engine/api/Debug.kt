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
    /** Wall-clock timestamp in milliseconds when the entry was recorded. */
    val timestampMillis: Long,
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
 * One runtime metric captured for debug or profiling UI.
 */
data class RuntimeMetric(
    /** Human-readable metric label. */
    val label: String,
    /** Already formatted metric value. */
    val value: String,
)

/**
 * Summarizes runtime telemetry captured for one completed frame.
 */
data class RuntimeFrameSnapshot(
    /** Frame index represented by this snapshot. */
    val frame: Long,
    /** Raw delta used for the frame. */
    val deltaSeconds: Float,
    /** Number of fixed updates executed this frame. */
    val fixedUpdates: Int,
    /** Runtime metrics collected before the frame completed. */
    val metrics: List<RuntimeMetric>,
)

/**
 * Summarizes profiler timings captured for one completed frame.
 */
data class ProfileFrameSnapshot(
    /** Frame index represented by this snapshot. */
    val frame: Long,
    /** Timed phases recorded for the frame. */
    val timings: List<PhaseTiming>,
)

/**
 * One destination that receives structured log entries.
 */
interface LogSink {
    /** Writes one structured log entry to the sink. */
    fun write(entry: LogEntry)

    /** Flushes any buffered sink output. */
    fun flush() = Unit

    /** Releases any sink resources. */
    fun dispose() = Unit
}

/**
 * Structured log storage used by backend loggers and debug UIs.
 */
interface LogService {
    /** Returns recently recorded structured logs. */
    val recentEntries: List<LogEntry>
    /** Minimum accepted log level for storage and sinks. */
    var minLevel: LogLevel

    /** Records a structured log entry for later display. */
    fun record(entry: LogEntry)

    /** Clears buffered log history. */
    fun clear()

    /** Registers one sink that receives accepted log entries. */
    fun addSink(sink: LogSink)

    /** Unregisters one sink that receives accepted log entries. */
    fun removeSink(sink: LogSink)
}

/**
 * Per-frame runtime telemetry collector for overlay values.
 */
interface RuntimeStatsService {
    /** Returns the current frame index. */
    val frame: Long
    /** Returns the runtime metrics currently collected for this frame. */
    val metrics: List<RuntimeMetric>
    /** Returns the most recent completed frame snapshot. */
    val lastCompletedFrame: RuntimeFrameSnapshot?

    /** Starts a new telemetry frame and clears per-frame metrics. */
    fun beginFrame()
    /** Finalizes the frame telemetry snapshot. */
    fun endFrame(deltaSeconds: Float, fixedUpdates: Int)
    /** Stores a formatted key/value metric. */
    fun put(label: String, value: Any?)
}

/**
 * Per-frame profiler used by the game loop.
 */
interface ProfilerService {
    /** Returns the most recent completed profiler snapshot. */
    val lastCompletedFrame: ProfileFrameSnapshot?

    /** Starts a new profiling frame and clears in-flight timings. */
    fun beginFrame(frame: Long)

    /** Finalizes the current profiling frame. */
    fun endFrame(frame: Long)

    /** Measures a block and records its elapsed time under the given name. */
    fun <T> measure(name: String, block: () -> T): T
}

/**
 * UI-facing visibility state for optional debug overlay windows.
 */
interface DebugOverlayState {
    /** Controls whether statistics panels are shown. */
    var statsVisible: Boolean
    /** Controls whether recent logs are shown. */
    var logsVisible: Boolean

    /** Toggles log visibility. */
    fun toggleLogs()

    /** Toggles statistics visibility. */
    fun toggleStats()
}

/**
 * Default in-memory structured log service used by the engine runtime.
 */
class EngineLogService(
    private val frameProvider: () -> Long = { 0L },
    private val maxEntries: Int = 2_000,
    override var minLevel: LogLevel = LogLevel.Trace,
) : LogService, Logger {
    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>()
    private val sinks = linkedSetOf<LogSink>()

    /** Exposes recent logs as a stable list snapshot. */
    override val recentEntries: List<LogEntry>
        get() = synchronized(lock) { entries.toList() }

    /** Returns whether the service currently accepts the given log level. */
    override fun isEnabled(level: LogLevel): Boolean = level.ordinal >= minLevel.ordinal

    /** Adds a prebuilt log entry while enforcing the configured retention cap. */
    override fun record(entry: LogEntry) {
        if (!isEnabled(entry.level)) return

        val sinkSnapshot = synchronized(lock) {
            entries += entry
            while (entries.size > maxEntries) {
                entries.removeFirst()
            }
            sinks.toList()
        }
        sinkSnapshot.forEach { sink ->
            sink.write(entry)
        }
    }

    /** Builds and records one structured log entry when the level is accepted. */
    override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) {
        if (!isEnabled(level)) return
        record(
            LogEntry(
                level = level,
                tag = tag,
                message = message(),
                frame = frameProvider(),
                threadName = Thread.currentThread().name,
                timestampMillis = java.lang.System.currentTimeMillis(),
                error = error,
            ),
        )
    }

    /** Clears buffered log history. */
    override fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    /** Registers one sink for accepted log entries. */
    override fun addSink(sink: LogSink) {
        synchronized(lock) {
            sinks += sink
        }
    }

    /** Unregisters one sink and disposes it. */
    override fun removeSink(sink: LogSink) {
        val removed = synchronized(lock) {
            sinks.remove(sink)
        }
        if (removed) {
            sink.flush()
            sink.dispose()
        }
    }
}

/**
 * Default in-memory runtime telemetry collector used by the engine runtime.
 */
class FrameRuntimeStatsService : RuntimeStatsService {
    private val values = linkedMapOf<String, String>()
    private var lastFrame: RuntimeFrameSnapshot? = null

    override var frame: Long = 0
        private set

    /** Exposes current metrics as a stable ordered snapshot. */
    override val metrics: List<RuntimeMetric>
        get() = values.map { (label, value) -> RuntimeMetric(label, value) }

    /** Exposes the last completed frame snapshot. */
    override val lastCompletedFrame: RuntimeFrameSnapshot?
        get() = lastFrame

    /** Begins a new frame and seeds the collector with the frame number. */
    override fun beginFrame() {
        frame += 1
        values.clear()
        put("Frame", frame)
    }

    /** Finalizes the summary snapshot for the current frame. */
    override fun endFrame(deltaSeconds: Float, fixedUpdates: Int) {
        put("Delta", "${"%.2f".format(deltaSeconds * 1000f)} ms")
        put("Fixed updates", fixedUpdates)
        lastFrame = RuntimeFrameSnapshot(frame, deltaSeconds, fixedUpdates, metrics)
    }

    /** Stores one formatted metric value. */
    override fun put(label: String, value: Any?) {
        values[label] = value.toString()
    }
}

/**
 * Default in-memory frame profiler used by the engine runtime.
 */
class FrameProfilerService : ProfilerService {
    private val timings = mutableListOf<PhaseTiming>()
    private var lastFrame: ProfileFrameSnapshot? = null

    /** Exposes the last completed profiler snapshot. */
    override val lastCompletedFrame: ProfileFrameSnapshot?
        get() = lastFrame

    /** Clears in-flight timings for the new frame. */
    override fun beginFrame(frame: Long) {
        timings.clear()
    }

    /** Finalizes the current profiling frame. */
    override fun endFrame(frame: Long) {
        lastFrame = ProfileFrameSnapshot(frame, timings.toList())
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

/**
 * Default mutable overlay visibility state used by backend debug UI.
 */
class DefaultDebugOverlayState(
    override var statsVisible: Boolean = true,
    override var logsVisible: Boolean = true,
) : DebugOverlayState {
    /** Toggles log visibility. */
    override fun toggleLogs() {
        logsVisible = !logsVisible
    }

    /** Toggles statistics visibility. */
    override fun toggleStats() {
        statsVisible = !statsVisible
    }
}
