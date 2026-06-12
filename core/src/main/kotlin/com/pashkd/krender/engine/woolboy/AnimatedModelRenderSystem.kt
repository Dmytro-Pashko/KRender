package com.pashkd.krender.engine.woolboy

import com.pashkd.krender.engine.animation.AnimationComponent
import com.pashkd.krender.engine.animation.toPlaybackView
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.render3d.ModelComponent

/**
 * Emits model draw commands with optional backend-neutral animation playback.
 */
class AnimatedModelRenderSystem : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        world.query<TransformComponent, ModelComponent>().forEach { entity ->
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val model = entity.get<ModelComponent>() ?: return@forEach
            val animation = entity.get<AnimationComponent>()
            world.renderCommands.submit(
                DrawModel(
                    entityId = entity.id,
                    model = model.model,
                    transform = transform.snapshot(),
                    material = model.material,
                    animation = animation?.toPlaybackView(),
                ),
            )
        }
    }
}
