package com.pashkd.krender.engine.api

import com.pashkd.krender.engine.scene.SceneConfig
import java.util.ArrayDeque

/**
 * Tracks the lifecycle phase of a scene while it is managed by [SceneManager].
 */
enum class SceneState {
    /** Scene object exists but has not been attached yet. */
    New,
    /** Scene is preparing to load resources. */
    Loading,
    /** Scene is performing non-render-thread preparation. */
    Preparing,
    /** Scene is ready to be shown. */
    Ready,
    /** Scene is currently active on the top of the stack. */
    Active,
    /** Scene remains on the stack but is not active. */
    Paused,
    /** Scene is being torn down. */
    Disposing,
    /** Scene has finished disposal. */
    Disposed,
}

/**
 * Services exposed to a scene during asynchronous load preparation.
 */
data class SceneLoadContext(
    /** Asset service available during load. */
    val assets: AssetService,
    /** Task service available during load. */
    val tasks: TaskService,
    /** Event bus available during load. */
    val events: EventBus,
    /** Logger available during load. */
    val logger: Logger,
)

/**
 * Base type for gameplay scenes managed by the engine runtime.
 */
abstract class Scene(
    val id: String,
) {
    /** ECS world owned by this scene. */
    val world: SceneWorld = SceneWorld()
    /** Asset packs that should be queued before the scene is shown. */
    open val requiredAssets: List<AssetPack> = emptyList()
    /** Scene-level viewport and physical window preferences. */
    open val config: SceneConfig = SceneConfig()

    /** Current lifecycle state assigned by the scene manager. */
    var state: SceneState = SceneState.New
        private set

    /** Engine services attached when the scene becomes managed. */
    protected lateinit var engine: EngineContext
        private set

    /** Attaches the runtime context to the scene. */
    internal fun attach(context: EngineContext) {
        engine = context
    }

    /** Updates the internal lifecycle state. */
    internal fun setState(state: SceneState) {
        this.state = state
    }

    /**
     * Stage A: CPU/IO-only preparation. Do not create GL-bound objects here.
     */
    open suspend fun load(context: SceneLoadContext) = Unit

    /**
     * Stage B: schedule scene-local assets through the engine AssetService.
     */
    open fun scheduleAssets(assets: AssetService) {
        requiredAssets.flatMap { it.assets }.forEach(assets::queue)
    }

    /**
     * Stage C: render-thread activation and final runtime binding.
     */
    /** Activates the scene on the render thread. */
    open fun show() = Unit
    /** Runs fixed-step scene logic. */
    open fun fixedUpdate(dt: Float) = world.fixedUpdate(dt)
    /** Runs variable-step scene logic. */
    open fun update(dt: Float) = world.update(dt)
    /** Runs late-step scene logic. */
    open fun lateUpdate(dt: Float) = world.lateUpdate(dt)
    /** Collects render commands for the scene. */
    open fun render(alpha: Float) = world.render(alpha)
    /** Collects debug render commands for the scene. */
    open fun debugRender() = world.debugRender()
    /** Handles surface resize events. */
    open fun resize(width: Int, height: Int) = Unit
    /** Runs when the scene leaves the top of the stack. */
    open fun hide() = Unit
    /** Releases scene resources and flushes deferred world mutations. */
    open fun dispose() {
        world.flushCommands()
    }
}

/**
 * Deferred scene stack operation applied at a safe point in the game loop.
 */
sealed interface SceneOp {
    /** Replaces the full scene stack with one scene. */
    data class Replace(val scene: Scene) : SceneOp
    /** Pushes a scene on top of the existing stack. */
    data class Push(val scene: Scene) : SceneOp
    /** Pops the current top scene. */
    data object Pop : SceneOp
}

/**
 * Manages the active scene stack and applies deferred transitions safely.
 */
class SceneManager {
    private val stack = ArrayDeque<Scene>()
    private val pendingOps = ArrayDeque<SceneOp>()

    /** Returns the current top-most scene, if any. */
    val currentScene: Scene?
        get() = stack.peek()

    /** Returns a snapshot of the current scene stack. */
    val currentStack: List<Scene>
        get() = stack.toList()

    /** Queues a full stack replacement. */
    fun replace(scene: Scene) {
        pendingOps += SceneOp.Replace(scene)
    }

    /** Queues a push operation for a new top-most scene. */
    fun push(scene: Scene) {
        pendingOps += SceneOp.Push(scene)
    }

    /** Queues a pop of the current scene. */
    fun pop() {
        pendingOps += SceneOp.Pop
    }

    /** Applies all queued scene stack transitions. */
    fun applyPendingTransitions(context: EngineContext): Boolean {
        var appliedAny = false
        while (pendingOps.isNotEmpty()) {
            appliedAny = true
            when (val op = pendingOps.removeFirst()) {
                is SceneOp.Replace -> {
                    while (stack.isNotEmpty()) {
                        disposeScene(stack.pop())
                    }
                    activateScene(op.scene, context)
                }

                is SceneOp.Push -> {
                    stack.peek()?.setState(SceneState.Paused)
                    activateScene(op.scene, context)
                }

                SceneOp.Pop -> {
                    if (stack.isNotEmpty()) {
                        disposeScene(stack.pop())
                    }
                    stack.peek()?.let { scene ->
                        scene.setState(SceneState.Active)
                        applySceneConfig(scene, context)
                    }
                }
            }
        }
        return appliedAny
    }

    /** Propagates resize events to all scenes on the stack. */
    fun resize(width: Int, height: Int) {
        stack.forEach { it.resize(width, height) }
    }

    /** Disposes every scene currently on the stack. */
    fun disposeAll() {
        while (stack.isNotEmpty()) {
            disposeScene(stack.pop())
        }
    }

    /** Attaches, loads, and activates a scene on the stack. */
    private fun activateScene(scene: Scene, context: EngineContext) {
        scene.attach(context)
        scene.setState(SceneState.Loading)
        scene.scheduleAssets(context.assets)
        scene.setState(SceneState.Ready)
        stack.push(scene)
        applySceneConfig(scene, context)
        scene.show()
        scene.setState(SceneState.Active)
        context.logger.info(TAG) { "Switched to '${scene.id}'" }
    }

    /**
     * Applies scene window and viewport preferences through the engine context.
     *
     * Scene activation updates the physical window preference first, then
     * recalculates the logical viewport from the resulting surface size. The
     * platform may still emit its own resize callback afterward, so this path
     * intentionally remains idempotent.
     *
     * TODO: renderer and ImGui resize currently follow the main runtime resize
     * path. Backends that do not emit a platform resize callback rely on the
     * runtime to call [EngineRuntime.resize] after transitions.
     */
    private fun applySceneConfig(scene: Scene, context: EngineContext) {
        val windowState = context.window.apply(scene.config.window).coerceAtLeast()
        context.viewport.resize(windowState.pixelWidth, windowState.pixelHeight, scene.config.viewport)
    }

    /** Hides, disposes, and marks a scene as removed. */
    private fun disposeScene(scene: Scene) {
        scene.setState(SceneState.Disposing)
        scene.hide()
        scene.dispose()
        scene.setState(SceneState.Disposed)
    }

    companion object {
        private const val TAG = "SceneManager"
    }
}
