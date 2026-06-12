package com.pashkd.krender.engine.api

import com.pashkd.krender.engine.scene.SceneConfig
import java.util.*

/**
 * Tracks the lifecycle phase of a scene while it is managed by [SceneManager].
 */
enum class SceneState {
    /** Scene object exists but has not been attached yet. */
    New,

    /** Scene has been attached and is queuing its required assets. */
    Loading,

    /** Scene has scheduled its assets and is ready to be shown. */
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
     * Queues the scene's [requiredAssets] through the engine [AssetService].
     *
     * Called once during activation, before [show]. Asset loading is advanced
     * asynchronously by the game loop, so assets are usually NOT ready yet when
     * [show] runs. Scenes must tolerate "not loaded yet" and poll
     * [AssetService.isLoaded] before using an asset.
     */
    open fun scheduleAssets(assets: AssetService) {
        requiredAssets.flatMap { it.assets }.forEach(assets::queue)
    }

    /**
     * Render-thread activation and runtime binding (build entities/systems/panels).
     *
     * Runs after [scheduleAssets]. Assets may still be loading at this point; do
     * not assume any required asset is available here.
     */
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

    /**
     * Renders backend-owned scene overlays after the main renderer has submitted the frame.
     *
     * This hook exists for editor UI/tool previews that need to draw directly through a backend
     * renderer, such as the Phase 4 UiComposer Scene2D preview. It belongs to editor and backend
     * presentation plumbing, not the shared `.krui` model or future editing pipeline. It
     * intentionally does not add saving, editing, drag/drop, Skin editing, asset-id references, or
     * full Scene2D actor serialization.
     */
    open fun overlayRender() = Unit

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

    /**
     * Attaches and activates a scene on the stack.
     *
     * Effective lifecycle: `scheduleAssets` -> `show` -> active. Asset loading is
     * advanced asynchronously by the game loop, so assets may still be loading
     * after [Scene.show] returns; scenes must tolerate "not loaded yet".
     */
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
        context.logger.info(TAG) {
            "Applying scene config id='${scene.id}' " +
                "window=${scene.config.window.mode} ${scene.config.window.resolution.width}x${scene.config.window.resolution.height} " +
                "viewport=${scene.config.viewport.designWidth.toInt()}x${scene.config.viewport.designHeight.toInt()} " +
                "policy=${scene.config.viewport.scalePolicy}"
        }
        val windowState = context.window.apply(scene.config.window).coerceAtLeast()
        context.logger.info(TAG) {
            "Scene config applied id='${scene.id}' " +
                "actualWindow=${windowState.mode} ${windowState.pixelWidth}x${windowState.pixelHeight}"
        }
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
