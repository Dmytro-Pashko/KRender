package com.pashkd.krender.engine.api

import kotlin.math.min

data class EngineConfig(
    val fixedStepSeconds: Float = 1f / 60f,
    val maxFrameDeltaSeconds: Float = 0.25f,
)

/**
 * Stable facade exposed to scenes and systems for engine-wide services.
 */
interface EngineContext {
    val scenes: SceneManager
    val assets: AssetService
    val input: InputService
    val events: EventBus
    val logger: Logger
    val debug: DebugService
    val tasks: TaskService

    fun requestExit()
}

/**
 * Runtime adapter contract implemented by a concrete platform backend.
 *
 * The core runtime talks to this interface instead of using platform APIs directly.
 */
interface EngineBackend {
    val input: InputService
    val assets: AssetService
    val logger: Logger
    val debug: DebugService
    val tasks: TaskService
    val renderer: Renderer

    fun requestExit()
}

class GameLoop(
    private val runtime: EngineRuntime,
    private val backend: EngineBackend,
    private val config: EngineConfig,
) {
    private var accumulator = 0f

    fun renderFrame(rawDelta: Float) {
        val delta = min(rawDelta, config.maxFrameDeltaSeconds)
        var fixedUpdates = 0

        backend.debug.beginFrame()
        backend.input.beginFrame()
        if (backend.input.snapshot().wasPressed(Key.Backtick)) {
            backend.debug.toggle()
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
}

class EngineRuntime(
    private val backend: EngineBackend,
    private val config: EngineConfig = EngineConfig(),
) : EngineContext {
    override val scenes: SceneManager = SceneManager()
    override val assets: AssetService = backend.assets
    override val input: InputService = backend.input
    override val events: EventBus = EventBus()
    override val logger: Logger = backend.logger
    override val debug: DebugService = backend.debug
    override val tasks: TaskService = backend.tasks

    private val gameLoop = GameLoop(this, backend, config)
    private var running = false
    private var exitRequested = false

    fun start(scene: Scene) {
        running = true
        scenes.replace(scene)
        logger.info(TAG) { "Runtime started" }
    }

    fun renderFrame(deltaSeconds: Float) {
        if (running) {
            gameLoop.renderFrame(deltaSeconds)
        }
    }

    override fun requestExit() {
        exitRequested = true
    }

    internal fun completeExitIfRequested(): Boolean {
        if (!exitRequested) return false
        dispose()
        backend.requestExit()
        return true
    }

    fun resize(width: Int, height: Int) {
        scenes.resize(width, height)
        backend.renderer.resize(width, height)
    }

    fun dispose() {
        running = false
        scenes.disposeAll()
        backend.renderer.dispose()
        tasks.dispose()
    }

    companion object {
        private const val TAG = "EngineRuntime"
    }
}

typealias Engine = EngineRuntime
