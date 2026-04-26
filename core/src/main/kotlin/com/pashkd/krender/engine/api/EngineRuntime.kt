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
    /** Shared debug collector. */
    val debug: DebugService
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
    /** Backend debug collector implementation. */
    val debug: DebugService
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

        backend.debug.beginFrame()
        backend.input.beginFrame()
        val inputSnapshot = backend.input.snapshot()
        if (!inputSnapshot.uiCapturesKeyboard && inputSnapshot.wasPressed(Key.Backtick)) {
            backend.debug.toggle()
        }
        if (!inputSnapshot.uiCapturesKeyboard && inputSnapshot.wasPressed(Key.Tab)) {
            backend.debug.toggleStats()
        }

        backend.debug.measure("tasks.flush") {
            backend.tasks.flushMainThreadQueue()
        }

        backend.debug.measure("assets.update") {
            backend.assets.update()
        }

        runtime.scenes.applyPendingTransitions(runtime)
        val scene = runtime.scenes.currentScene
        if (scene == null) {
            backend.input.endFrame()
            backend.debug.endFrame(delta, fixedUpdates)
            return
        }

        backend.debug.put("Scene", scene.id)
        backend.debug.put("Scene state", scene.state)
        backend.debug.put("Entities", scene.world.all().size)
        backend.debug.put("Commands", scene.world.commands.size())
        backend.debug.put("Jobs", backend.tasks.inFlightJobs)
        putJvmMemoryStats(backend.debug)

        accumulator += delta
        backend.debug.measure("fixedUpdate") {
            while (accumulator >= config.fixedStepSeconds) {
                scene.fixedUpdate(config.fixedStepSeconds)
                accumulator -= config.fixedStepSeconds
                fixedUpdates += 1
            }
        }

        val alpha = accumulator / config.fixedStepSeconds

        backend.debug.measure("update") {
            scene.update(delta)
        }
        if (runtime.completeExitIfRequested()) {
            backend.input.endFrame()
            backend.debug.endFrame(delta, fixedUpdates)
            return
        }

        backend.debug.measure("lateUpdate") {
            scene.lateUpdate(delta)
        }
        if (runtime.completeExitIfRequested()) {
            backend.input.endFrame()
            backend.debug.endFrame(delta, fixedUpdates)
            return
        }

        backend.debug.measure("render.collect") {
            scene.render(alpha)
            scene.debugRender()
        }

        backend.debug.measure("render.submit") {
            backend.renderer.render(
                RenderContext(
                    scene = scene,
                    alpha = alpha,
                    deltaSeconds = delta,
                    commands = scene.world.renderCommands.snapshot(),
                    debug = backend.debug,
                ),
            )
        }

        backend.input.endFrame()
        backend.debug.endFrame(delta, fixedUpdates)
    }

    private fun putJvmMemoryStats(debug: DebugService) {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        val max = runtime.maxMemory()
        debug.put("JVM Memory", "")
        debug.put("Used", formatMemoryMb(used))
        debug.put("Free", formatMemoryMb(free))
        debug.put("Total", formatMemoryMb(total))
        debug.put("Max", formatMemoryMb(max))
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
    /** Shared debug service exposed to scenes. */
    override val debug: DebugService = backend.debug
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
