package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.FreeCameraControllerComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.render3d.WorldGridSystem
import kotlin.math.cos
import kotlin.math.sin

class ModelViewerScene(
    private val model: AssetRef<ModelAsset>,
    private val modelScale: Float = 1f,
) : Scene("model_viewer") {
    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets = listOf(model)
        },
    )

    override fun show() {
        world.systems.add(WorldGridSystem(halfExtentCells = 24, cellSize = 1f))
        world.systems.add(ModelRenderSystem())

        val camera = world.createEntity("Viewer Camera")
        camera.transform.position.set(0f, 1.6f, 5f)
        camera.transform.eulerDegrees.set(0f, 180f, 0f)
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                near = 0.05f,
                far = 250f,
            ),
        )
        camera.add(FreeCameraControllerComponent())

        val light = world.createEntity("Key Light")
        light.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.96f, 0.88f),
                intensity = 1.25f,
                direction = Vec3(-0.5f, -0.8f, -0.35f),
            ),
        )

        val ambient = world.createEntity("Ambient Light")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.45f, 0.5f, 0.58f),
                intensity = 0.55f,
            ),
        )

        val modelEntity = world.createEntity("Asset Model")
        modelEntity.transform.scale.set(modelScale, modelScale, modelScale)
        modelEntity.add(
            ModelComponent(
                model = model,
                material = Material(baseColor = Color.white()),
            ),
        )
    }

    override fun update(dt: Float) {
        updateCamera(dt)
        super.update(dt)

        val input = engine.input.snapshot()
        if (input.wasPressed(Key.Escape)) {
            engine.scenes.pop()
            return
        }

        val camera = world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull()
        val cameraPosition = camera?.transform?.position
        if (cameraPosition != null) {
            engine.debug.put(
                "Camera",
                "%.2f, %.2f, %.2f".format(cameraPosition.x, cameraPosition.y, cameraPosition.z),
            )
        }
        engine.debug.put("Input", input.keysDown.joinToString().ifBlank { "none" })
        engine.debug.put("Mouse delta", "${input.mouseDelta.x.toInt()}, ${input.mouseDelta.y.toInt()}")
        engine.debug.line("WASD moves the camera. Mouse rotates the view.")
    }

    private fun updateCamera(dt: Float) {
        val camera = world.query<TransformComponent, PerspectiveCameraComponent, FreeCameraControllerComponent>()
            .firstOrNull() ?: return
        val transform = camera.transform
        val controller = camera.get<FreeCameraControllerComponent>() ?: return
        val input = engine.input.snapshot()

        transform.eulerDegrees.y -= input.mouseDelta.x * controller.mouseSensitivity
        transform.eulerDegrees.x = (transform.eulerDegrees.x - input.mouseDelta.y * controller.mouseSensitivity)
            .coerceIn(controller.minPitchDegrees, controller.maxPitchDegrees)

        val yaw = Math.toRadians(transform.eulerDegrees.y.toDouble())
        val forward = Vec3(
            x = sin(yaw).toFloat(),
            y = 0f,
            z = cos(yaw).toFloat(),
        )
        val right = Vec3(
            x = -cos(yaw).toFloat(),
            y = 0f,
            z = sin(yaw).toFloat(),
        )

        var moveX = 0f
        var moveZ = 0f
        if (input.isDown(Key.W)) moveZ += 1f
        if (input.isDown(Key.S)) moveZ -= 1f
        if (input.isDown(Key.D)) moveX += 1f
        if (input.isDown(Key.A)) moveX -= 1f

        if (moveX != 0f || moveZ != 0f) {
            val speed = controller.moveSpeed * if (input.isDown(Key.ShiftLeft)) controller.sprintMultiplier else 1f
            transform.position.x += (forward.x * moveZ + right.x * moveX) * speed * dt
            transform.position.z += (forward.z * moveZ + right.z * moveX) * speed * dt
        }
    }
}
