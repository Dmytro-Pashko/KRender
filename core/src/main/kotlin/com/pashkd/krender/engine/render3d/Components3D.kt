package com.pashkd.krender.engine.render3d

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.ShaderAsset
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3

data class PerspectiveCameraComponent(
    var fieldOfViewDegrees: Float = 67f,
    var near: Float = 0.1f,
    var far: Float = 100f,
    val lookAt: Vec3 = Vec3.zero(),
) : Component

data class ShaderPipeline(
    val vertexShader: AssetRef<ShaderAsset>? = null,
    val fragmentShader: AssetRef<ShaderAsset>? = null,
    val name: String = "default-lit",
) {
    fun assets(): List<AssetRef<ShaderAsset>> = listOfNotNull(vertexShader, fragmentShader)
}

data class Material(
    val baseColor: Color = Color.white(),
    val roughness: Float = 0.75f,
    val metallic: Float = 0f,
    val shader: ShaderPipeline = ShaderPipeline(),
)

data class ModelComponent(
    val model: AssetRef<ModelAsset>,
    var material: Material = Material(),
) : Component

enum class LightType {
    Directional,
    Point,
    Ambient,
}

data class LightComponent(
    val type: LightType,
    val color: Color = Color.white(),
    var intensity: Float = 1f,
    val direction: Vec3 = Vec3(-1f, -0.8f, -0.2f),
) : Component

class ModelRenderSystem : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        world.query<TransformComponent, ModelComponent>().forEach { entity ->
            val model = entity.get<ModelComponent>() ?: return@forEach
            val transform = entity.get<TransformComponent>() ?: return@forEach
            world.renderCommands.submit(
                DrawModel(
                    entityId = entity.id,
                    model = model.model,
                    transform = transform.snapshot(),
                    material = model.material,
                ),
            )
        }
    }
}
