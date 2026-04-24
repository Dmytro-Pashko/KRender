package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent

class DemoScene : Scene("engine_demo") {
    override fun show() {
        world.systems.add(ModelRenderSystem())
        world.systems.add(SpinSystem(speedDegreesPerSecond = 35f))

        val camera = world.createEntity("Main Camera")
        camera.transform.position.set(0f, 2.4f, 6f)
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                lookAt = Vec3.zero(),
            ),
        )

        val light = world.createEntity("Sun")
        light.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.95f, 0.85f),
                intensity = 1.2f,
            ),
        )

        val ambient = world.createEntity("Ambient Light")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.35f, 0.42f, 0.5f),
                intensity = 0.45f,
            ),
        )

        val cube = world.createEntity("Primitive Cube")
        cube.add(
            ModelComponent(
                model = AssetRef.primitiveModel("cube"),
                material = Material(baseColor = Color(0.1f, 0.62f, 0.82f)),
            ),
        )
        cube.add(SpinTag)
    }

    override fun update(dt: Float) {
        super.update(dt)

        val input = engine.input.snapshot()
        engine.debug.put("Input", input.keysDown.joinToString().ifBlank { "none" })
        engine.debug.line("WASD/QE/F1-F4 are normalized by the engine input layer.")

        if (input.wasPressed(Key.Escape)) {
            engine.logger.info("DemoScene") { "Escape pressed in stable input snapshot" }
        }
    }
}

private data object SpinTag : com.pashkd.krender.engine.api.Component

private class SpinSystem(
    private val speedDegreesPerSecond: Float,
) : System() {
    override fun update(world: SceneWorld, dt: Float) {
        world.query<TransformComponent, SpinTag>().forEach { entity ->
            entity.transform.eulerDegrees.y += speedDegreesPerSecond * dt
        }
    }
}
