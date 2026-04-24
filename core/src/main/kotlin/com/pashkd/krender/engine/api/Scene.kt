package com.pashkd.krender.engine.api

import java.util.ArrayDeque

enum class SceneState {
    New,
    Loading,
    Preparing,
    Ready,
    Active,
    Paused,
    Disposing,
    Disposed,
}

data class SceneLoadContext(
    val assets: AssetService,
    val tasks: TaskService,
    val events: EventBus,
    val logger: Logger,
)

abstract class Scene(
    val id: String,
) {
    val world: SceneWorld = SceneWorld()
    open val requiredAssets: List<AssetPack> = emptyList()

    var state: SceneState = SceneState.New
        private set

    protected lateinit var engine: EngineContext
        private set

    internal fun attach(context: EngineContext) {
        engine = context
    }

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
    open fun show() = Unit
    open fun fixedUpdate(dt: Float) = world.fixedUpdate(dt)
    open fun update(dt: Float) = world.update(dt)
    open fun lateUpdate(dt: Float) = world.lateUpdate(dt)
    open fun render(alpha: Float) = world.render(alpha)
    open fun debugRender() = world.debugRender()
    open fun resize(width: Int, height: Int) = Unit
    open fun hide() = Unit
    open fun dispose() {
        world.flushCommands()
    }
}

/**
 * Deferred scene stack operation applied at a safe point in the game loop.
 */
sealed interface SceneOp {
    data class Replace(val scene: Scene) : SceneOp
    data class Push(val scene: Scene) : SceneOp
    data object Pop : SceneOp
}

class SceneManager {
    private val stack = ArrayDeque<Scene>()
    private val pendingOps = ArrayDeque<SceneOp>()

    val currentScene: Scene?
        get() = stack.peek()

    val currentStack: List<Scene>
        get() = stack.toList()

    fun replace(scene: Scene) {
        pendingOps += SceneOp.Replace(scene)
    }

    fun push(scene: Scene) {
        pendingOps += SceneOp.Push(scene)
    }

    fun pop() {
        pendingOps += SceneOp.Pop
    }

    fun applyPendingTransitions(context: EngineContext) {
        while (pendingOps.isNotEmpty()) {
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
                    stack.peek()?.setState(SceneState.Active)
                }
            }
        }
    }

    fun resize(width: Int, height: Int) {
        stack.forEach { it.resize(width, height) }
    }

    fun disposeAll() {
        while (stack.isNotEmpty()) {
            disposeScene(stack.pop())
        }
    }

    private fun activateScene(scene: Scene, context: EngineContext) {
        scene.attach(context)
        scene.setState(SceneState.Loading)
        scene.scheduleAssets(context.assets)
        scene.setState(SceneState.Ready)
        stack.push(scene)
        scene.show()
        scene.setState(SceneState.Active)
        context.logger.info("Scene") { "Switched to '${scene.id}'" }
    }

    private fun disposeScene(scene: Scene) {
        scene.setState(SceneState.Disposing)
        scene.hide()
        scene.dispose()
        scene.setState(SceneState.Disposed)
    }
}
