package com.pashkd.krender.engine.api

import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.runtime.RuntimeUiBackend
import com.pashkd.krender.engine.ui.runtime.RuntimeUiService
import com.pashkd.krender.engine.viewport.RuntimeViewportConfig
import com.pashkd.krender.engine.viewport.RuntimeViewportService
import com.pashkd.krender.engine.window.WindowService
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

    /** Shared project asset registry used by editor tools for scanning and metadata. */
    val assetRegistry: AssetRegistryService

    /** Shared scene file service. */
    val sceneFiles: SceneFileService

    /** Shared launcher for opening saved scenes in a separate runtime window. */
    val runtimeLauncher: RuntimeWindowLauncher

    /** Shared launcher for opening editor tools in separate windows. */
    val editorToolLauncher: EditorToolLauncher

    /** Shared normalized input service. */
    val input: InputService

    /** Shared ImGui-backed UI service. */
    val ui: UiService

    /** Shared runtime game UI service. */
    val runtimeUi: RuntimeUiService

    /** Shared event bus. */
    val events: EventBus

    /** Shared structured logger. */
    val logger: Logger

    /** Shared structured log history and sinks. */
    val logs: LogService

    /** Shared runtime statistics. */
    val runtimeStats: RuntimeStatsService

    /** Shared frame profiler snapshots. */
    val profiler: ProfilerService

    /** Shared async task service. */
    val tasks: TaskService

    /** Shared runtime UI viewport state. */
    val viewport: RuntimeViewportService

    /** Shared physical window state and configuration service. */
    val window: WindowService

    /** Optional backend-provided terrain material texture sampling factory used by runtime terrain baking. */
    val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory?

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

    /** Backend runtime UI implementation. */
    val runtimeUi: RuntimeUiBackend

    /** Backend asset implementation. */
    val assets: AssetService

    /** Backend project asset registry implementation. */
    val assetRegistry: AssetRegistryService

    /** Backend scene file implementation. */
    val sceneFiles: SceneFileService

    /** Backend runtime scene window launcher. */
    val runtimeLauncher: RuntimeWindowLauncher

    /** Backend editor tool window launcher. */
    val editorToolLauncher: EditorToolLauncher

    /** Backend logger implementation. */
    val logger: Logger

    /** Backend log history implementation. */
    val logs: LogService

    /** Backend runtime telemetry implementation. */
    val runtimeStats: RuntimeStatsService

    /** Backend profiler implementation. */
    val profiler: ProfilerService

    /** Backend task implementation. */
    val tasks: TaskService

    /** Optional backend terrain material texture sampling factory. */
    val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory?

    /** Backend physical window implementation. */
    val window: WindowService

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
        var uiFrameEnded = false

        try {
            backend.runtimeStats.beginFrame()
            backend.profiler.beginFrame(backend.runtimeStats.frame)
            // Open the UI frame before sampling input so that ImGui's hover-based
            // capture flags are computed for the *current* frame and become
            // available to the input snapshot consumed by all systems below.
            backend.ui.beginFrame(delta)
            backend.input.beginFrame()
            backend.input.snapshot()
            backend.profiler.measure("tasks.flush") {
                backend.tasks.flushMainThreadQueue()
            }

            backend.profiler.measure("assets.update") {
                backend.assets.update()
            }

            if (runtime.scenes.applyPendingTransitions(runtime)) {
                runtime.resize(runtime.window.current.pixelWidth, runtime.window.current.pixelHeight)
            }
            val scene = runtime.scenes.currentScene ?: return

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
            if (runtime.completeExitIfRequested()) return

            backend.profiler.measure("lateUpdate") {
                scene.lateUpdate(delta)
            }
            if (runtime.completeExitIfRequested()) return

            backend.profiler.measure("runtimeUi.update") {
                runtime.runtimeUi.update(delta)
            }

            // Close the editor UI frame after panels have drawn but before the
            // final overlay passes are submitted.
            backend.ui.endFrame()
            uiFrameEnded = true

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
            backend.profiler.measure("scene.overlayRender") {
                scene.overlayRender()
            }
            backend.profiler.measure("runtimeUi.render") {
                runtime.runtimeUi.render()
            }
            backend.profiler.measure("ui.render") {
                backend.ui.render()
            }
        } finally {
            if (!uiFrameEnded) {
                backend.ui.endFrame()
            }
            backend.input.endFrame()
            backend.runtimeStats.endFrame(delta, fixedUpdates)
            backend.profiler.endFrame(backend.runtimeStats.frame)
        }
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
    config: EngineConfig = EngineConfig(),
) : EngineContext {
    /** Scene stack manager used by scenes and the game loop. */
    override val scenes: SceneManager = SceneManager()

    /** Shared asset service exposed to scenes. */
    override val assets: AssetService = backend.assets

    /** Shared project asset registry exposed to scenes. */
    override val assetRegistry: AssetRegistryService = backend.assetRegistry

    /** Shared scene file service exposed to scenes. */
    override val sceneFiles: SceneFileService = backend.sceneFiles

    /** Shared runtime scene window launcher exposed to scenes. */
    override val runtimeLauncher: RuntimeWindowLauncher = backend.runtimeLauncher

    /** Shared editor tool launcher exposed to scenes. */
    override val editorToolLauncher: EditorToolLauncher = backend.editorToolLauncher

    /** Shared input service exposed to scenes. */
    override val input: InputService = backend.input

    /** Shared UI service exposed to scenes. */
    override val ui: UiService = backend.ui

    /** Shared runtime UI service exposed to scenes. */
    override val runtimeUi: RuntimeUiService = RuntimeUiService(backend.runtimeUi, backend.logger)

    /** Shared event bus exposed to scenes. */
    override val events: EventBus = EventBus()

    /** Shared logger exposed to scenes. */
    override val logger: Logger = backend.logger

    /** Shared log service exposed to scenes. */
    override val logs: LogService = backend.logs

    /** Shared runtime statistics exposed to scenes. */
    override val runtimeStats: RuntimeStatsService = backend.runtimeStats

    /** Shared profiler snapshots exposed to scenes. */
    override val profiler: ProfilerService = backend.profiler

    /** Shared task service exposed to scenes. */
    override val tasks: TaskService = backend.tasks

    /** Runtime UI viewport state derived from surface size and active scene policy. */
    override val viewport: RuntimeViewportService = RuntimeViewportService()

    /** Physical window state and configuration exposed to scenes and scene manager. */
    override val window: WindowService = backend.window

    /** Optional backend terrain texture sampler factory exposed to scenes. */
    override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? =
        backend.terrainTextureSamplerFactory

    private val gameLoop = GameLoop(this, backend, config)
    private var running = false
    private var exitRequested = false

    /** Starts the runtime and schedules the initial scene. */
    fun start(scene: Scene) {
        running = true
        scenes.replace(scene)
        if (scenes.applyPendingTransitions(this)) {
            resize(window.current.pixelWidth, window.current.pixelHeight)
        }
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

    /**
     * Propagates a resize event to scenes, UI, and renderer.
     *
     * The active scene's viewport policy is reapplied on every resize so that
     * platform callbacks and scene-driven window changes remain idempotent.
     */
    fun resize(width: Int, height: Int) {
        val viewportConfig = scenes.currentScene?.config?.viewport ?: RuntimeViewportConfig()
        viewport.resize(width, height, viewportConfig)
        val currentSceneId = scenes.currentScene?.id ?: "<none>"
        logger.info(TAG) {
            "Resize scene='$currentSceneId' " +
                "window=${width.coerceAtLeast(1)}x${height.coerceAtLeast(1)} " +
                "viewportLogical=${"%.2f".format(viewport.current.logicalWidth)}x${"%.2f".format(viewport.current.logicalHeight)} " +
                "scale=${"%.4f".format(viewport.current.scale)} " +
                "offset=${"%.2f".format(viewport.current.offsetX)},${"%.2f".format(viewport.current.offsetY)}"
        }
        scenes.resize(width, height)
        runtimeUi.resize(width, height)
        backend.ui.resize(width, height)
        backend.renderer.resize(width, height)
    }

    /** Disposes scenes and backend services owned by the runtime. */
    fun dispose() {
        running = false
        scenes.disposeAll()
        backend.renderer.dispose()
        runtimeUi.dispose()
        backend.ui.dispose()
        tasks.dispose()
    }

    companion object {
        private const val TAG = "EngineRuntime"
    }
}

/** Convenience alias for the main runtime type. */
typealias Engine = EngineRuntime
