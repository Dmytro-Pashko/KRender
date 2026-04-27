package com.pashkd.krender.engine.api

import com.pashkd.krender.engine.ui.UiService
import kotlin.math.min

/**
 * Tunable parameters for the core engine game loop.
 */
data class EngineConfig(
    /** Fixed-step interval in seconds. */
    val fixedStepSeconds: Float = 1f / 60f,
    /** Maximum variable delta accepted for a single frame. */
    val maxFrameDeltaSeconds: Float = 0.25f,
)

/**
 * Stable facade exposed to scenes and systems for engine-wide services.
 */
interface EngineContext {
    /** Scene stack manager used for transitions. */
    val scenes: SceneManager
    /** Shared asset service. */
    val assets: AssetService
    /** Shared normalized input service. */
    val input: InputService
    /** Shared ImGui-backed UI service. */
    val ui: UiService
    /** Shared event bus. */
    val events: EventBus
    /** Shared structured logger. */
    val logger: Logger
    /** Shared structured log history and sinks. */
    val logs: LogService
    /** Shared async task service. */
    val tasks: TaskService

    /** Requests application shutdown at the next safe point. */
    fun requestExit()
}

/**
 * Runtime adapter contract implemented by a concrete platform backend.
 *
 * The core runtime talks to this interface instead of using platform APIs directly.
 */
interface EngineBackend {
    /** Backend input implementation. */
    val input: InputService
    /** Backend UI implementation. */
    val ui: UiService
    /** Backend asset implementation. */
    val assets: AssetService
    /** Backend logger implementation. */
    val logger: Logger
    /** Backend log history implementation. */
    val logs: LogService
    /** Backend runtime telemetry implementation. */
    val runtimeStats: RuntimeStatsService
    /** Backend profiler implementation. */
    val profiler: ProfilerService
    /** Backend debug overlay visibility state. */
    val debugOverlay: DebugOverlayState
    /** Backend task implementation. */
    val tasks: TaskService
    /** Backend renderer implementation. */
    val renderer: Renderer

    /** Requests process or window shutdown from the platform backend. */
    fun requestExit()
}

/**
 * Advances one frame of the engine using a fixed-step plus variable-step loop.
 */
class GameLoop(
    private val runtime: EngineRuntime,
    private val backend: EngineBackend,
    private val config: EngineConfig,
) {
    private var accumulator = 0f

    /** Runs one frame of update, render collection, and renderer submission. */
    fun renderFrame(rawDelta: Float) {
        val delta = min(rawDelta, config.maxFrameDeltaSeconds)
        var fixedUpdates = 0

        backend.runtimeStats.beginFrame()
        backend.profiler.beginFrame(backend.runtimeStats.frame)
        backend.input.beginFrame()
        val inputSnapshot = backend.input.snapshot()
        if (!inputSnapshot.uiCapturesKeyboard && inputSnapshot.wasPressed(Key.Backtick)) {
            backend.debugOverlay.toggleLogs()
        }

        backend.profiler.measure("tasks.flush") {
            backend.tasks.flushMainThreadQueue()
        }

        backend.profiler.measure("assets.update") {
            backend.assets.update()
        }

        runtime.scenes.applyPendingTransitions(runtime)
        val scene = runtime.scenes.currentScene
        if (scene == null) {
            backend.input.endFrame()
            backend.runtimeStats.endFrame(delta, fixedUpdates)
            backend.profiler.endFrame(backend.runtimeStats.frame)
            return
        }

        backend.runtimeStats.put("Scene", scene.id)
        backend.runtimeStats.put("Scene state", scene.state)
        backend.runtimeStats.put("Entities", scene.world.all().size)
        backend.runtimeStats.put("Commands", scene.world.commands.size())
        backend.runtimeStats.put("Jobs", backend.tasks.inFlightJobs)
        putJvmMemoryStats(backend.runtimeStats)

        accumulator += delta
        backend.profiler.measure("fixedUpdate") {
            while (accumulator >= config.fixedStepSeconds) {
                scene.fixedUpdate(config.fixedStepSeconds)
                accumulator -= config.fixedStepSeconds
                fixedUpdates += 1
            }
        }

        val alpha = accumulator / config.fixedStepSeconds

        backend.profiler.measure("update") {
            scene.update(delta)
        }
        if (runtime.completeExitIfRequested()) {
            backend.input.endFrame()
            backend.runtimeStats.endFrame(delta, fixedUpdates)
            backend.profiler.endFrame(backend.runtimeStats.frame)
            return
        }

        backend.profiler.measure("lateUpdate") {
            scene.lateUpdate(delta)
        }
        if (runtime.completeExitIfRequested()) {
            backend.input.endFrame()
            backend.runtimeStats.endFrame(delta, fixedUpdates)
            backend.profiler.endFrame(backend.runtimeStats.frame)
            return
        }

        backend.profiler.measure("render.collect") {
            scene.render(alpha)
            scene.debugRender()
        }

        backend.profiler.measure("render.submit") {
            backend.renderer.render(
                RenderContext(
                    scene = scene,
                    alpha = alpha,
                    deltaSeconds = delta,
                    commands = scene.world.renderCommands.snapshot(),
                ),
            )
        }

        backend.input.endFrame()
        backend.runtimeStats.endFrame(delta, fixedUpdates)
        backend.profiler.endFrame(backend.runtimeStats.frame)
    }

    private fun putJvmMemoryStats(runtimeStats: RuntimeStatsService) {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        val max = runtime.maxMemory()
        runtimeStats.put("JVM Memory", "")
        runtimeStats.put("Used", formatMemoryMb(used))
        runtimeStats.put("Free", formatMemoryMb(free))
        runtimeStats.put("Total", formatMemoryMb(total))
        runtimeStats.put("Max", formatMemoryMb(max))
    }

    private fun formatMemoryMb(bytes: Long): String =
        "%.1f MB".format(bytes / 1024f / 1024f)
}

/**
 * High-level runtime facade that owns scenes and backend services.
 */
class EngineRuntime(
    private val backend: EngineBackend,
    private val config: EngineConfig = EngineConfig(),
) : EngineContext {
    /** Scene stack manager used by scenes and the game loop. */
    override val scenes: SceneManager = SceneManager()
    /** Shared asset service exposed to scenes. */
    override val assets: AssetService = backend.assets
    /** Shared input service exposed to scenes. */
    override val input: InputService = backend.input
    /** Shared UI service exposed to scenes. */
    override val ui: UiService = backend.ui
    /** Shared event bus exposed to scenes. */
    override val events: EventBus = EventBus()
    /** Shared logger exposed to scenes. */
    override val logger: Logger = backend.logger
    /** Shared log service exposed to scenes. */
    override val logs: LogService = backend.logs
    /** Shared task service exposed to scenes. */
    override val tasks: TaskService = backend.tasks

    private val gameLoop = GameLoop(this, backend, config)
    private var running = false
    private var exitRequested = false

    /** Starts the runtime and schedules the initial scene. */
    fun start(scene: Scene) {
        running = true
        scenes.replace(scene)
        logger.info(TAG) { "Runtime started" }
    }

    /** Advances one rendered frame when the runtime is active. */
    fun renderFrame(deltaSeconds: Float) {
        if (running) {
            gameLoop.renderFrame(deltaSeconds)
        }
    }

    /** Marks the runtime for shutdown. */
    override fun requestExit() {
        exitRequested = true
    }

    /** Completes a pending exit request and returns whether shutdown occurred. */
    internal fun completeExitIfRequested(): Boolean {
        if (!exitRequested) return false
        dispose()
        backend.requestExit()
        return true
    }

    /** Propagates a resize event to scenes, UI, and renderer. */
    fun resize(width: Int, height: Int) {
        scenes.resize(width, height)
        backend.ui.resize(width, height)
        backend.renderer.resize(width, height)
    }

    /** Disposes scenes and backend services owned by the runtime. */
    fun dispose() {
        running = false
        scenes.disposeAll()
        backend.renderer.dispose()
        backend.ui.dispose()
        tasks.dispose()
    }

    companion object {
        private const val TAG = "EngineRuntime"
    }
}

/** Convenience alias for the main runtime type. */
typealias Engine = EngineRuntime
