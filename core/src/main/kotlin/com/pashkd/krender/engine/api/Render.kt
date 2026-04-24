package com.pashkd.krender.engine.api

import com.pashkd.krender.engine.render3d.Material

data class TransformSnapshot(
    val position: Vec3 = Vec3.zero(),
    val rotation: Quat = Quat.identity(),
    val eulerDegrees: Vec3 = Vec3.zero(),
    val scale: Vec3 = Vec3.one(),
)

/**
 * Backend-neutral draw request produced by render systems and consumed by renderers.
 */
sealed interface RenderCommand {
    val sortKey: Int
}

data class DrawModel(
    val entityId: EntityId,
    val model: AssetRef<ModelAsset>,
    val transform: TransformSnapshot,
    val material: Material,
    override val sortKey: Int = 0,
) : RenderCommand

data class DrawLine(
    val from: Vec3,
    val to: Vec3,
    val color: Color = Color.white(),
    override val sortKey: Int = 10,
) : RenderCommand

data class DrawText(
    val text: String,
    val position: Vec2,
    val color: Color = Color.white(),
    override val sortKey: Int = 100,
) : RenderCommand

class RenderCommandBuffer {
    private val commands = mutableListOf<RenderCommand>()

    fun submit(command: RenderCommand) {
        commands += command
    }

    fun clear() {
        commands.clear()
    }

    fun snapshot(): List<RenderCommand> = commands.sortedBy { it.sortKey }
}

data class RenderContext(
    val scene: Scene,
    val alpha: Float,
    val deltaSeconds: Float,
    val commands: List<RenderCommand>,
    val debug: DebugService,
)

/**
 * Platform renderer that submits collected [RenderCommand] instances to the graphics API.
 */
interface Renderer {
    fun render(context: RenderContext)
    fun resize(width: Int, height: Int)
    fun dispose()
}
