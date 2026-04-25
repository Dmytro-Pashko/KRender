package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.DrawModelViewerOverlay
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.OverlayLayout
import com.pashkd.krender.engine.api.PointerPhase
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
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
    private val model: AssetRef<ModelAsset>? = null,
    private val availableModels: List<AssetRef<ModelAsset>> = model?.let(::listOf) ?: emptyList(),
    private val modelScale: Float = 1f,
    private var wireframeMode: Boolean = false,
    openDialogOnStart: Boolean = true,
) : Scene("model_viewer") {
    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets = availableModels.filterNotNull()
        },
    )

    private val models = availableModels.filterNotNull()
    private var selectedModelIndex: Int = model?.let(models::indexOf)?.takeIf { it >= 0 } ?: 0
    private var loadedModel: AssetRef<ModelAsset>? = model
    private var modelEntityId: EntityId? = null
    private var dialogVisible: Boolean = openDialogOnStart
    private val overlayLayout = OverlayLayout()

    override fun show() {
        syncCursorCapture()
        world.systems.add(WorldGridSystem(halfExtentCells = 24, cellSize = 1f))
        world.systems.add(ModelRenderSystem())
        world.systems.add(ModelViewerOverlaySystem(this))

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

        loadedModel?.let(::createModelEntity)
    }

    override fun update(dt: Float) {
        handleDialogToggle()
        handleWireframeToggle()
        syncCursorCapture()
        if (dialogVisible) {
            handleDialogInput()
        } else {
            updateCamera(dt)
        }
        super.update(dt)

        val input = engine.input.snapshot()
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
        engine.debug.put("Loaded model", loadedModel?.path ?: "none")
        engine.debug.put("Wireframe", if (wireframeMode) "on" else "off")
        engine.debug.line("W/A/S/D - Navigation")
        engine.debug.line("Ctrl/Shift - Up/Down")
        engine.debug.line("F1 - Toggle wireframe")
        engine.debug.line("` - Shows logs")
        engine.debug.line("Tab - Shows scene statistic")
    }

    override fun hide() {
        engine.input.setCursorCaptured(false)
    }

    fun overlayCommand(): DrawModelViewerOverlay? {
        if (!dialogVisible) return null
        return DrawModelViewerOverlay(
            models = models.map { it.path },
            selectedIndex = selectedModelIndex,
            loadedModel = loadedModel?.path ?: "none",
            layout = overlayLayout,
        )
    }

    private fun createModelEntity(modelRef: AssetRef<ModelAsset>) {
        modelEntityId?.let(world::removeEntity)
        val modelEntity = world.createEntity("Asset Model")
        modelEntityId = modelEntity.id
        modelEntity.transform.scale.set(modelScale, modelScale, modelScale)
        modelEntity.add(
            ModelComponent(
                model = modelRef,
                material = Material(baseColor = Color.white(), wireframe = wireframeMode),
            ),
        )
    }

    private fun handleDialogToggle() {
        if (engine.input.snapshot().wasPressed(Key.Escape)) {
            dialogVisible = !dialogVisible
        }
    }

    private fun handleDialogInput() {
        val input = engine.input.snapshot()
        val click = input.pointers.firstOrNull { it.phase == PointerPhase.Up }?.screenPosition ?: return
        val x = click.x
        val y = click.y

        val panelX = (input.viewportSize.x - overlayLayout.width) * 0.5f
        val panelY = (input.viewportSize.y - overlayLayout.height(models.size)) * 0.5f

        if (x < panelX || x > panelX + overlayLayout.width || y < panelY) return

        val listTop = panelY + overlayLayout.headerHeight
        val listBottom = listTop + models.size * overlayLayout.rowHeight
        if (y in listTop..listBottom) {
            val index = ((y - listTop) / overlayLayout.rowHeight).toInt()
            if (index in models.indices) {
                selectedModelIndex = index
            }
            return
        }

        val buttonY = listBottom + overlayLayout.padding
        if (y in buttonY..(buttonY + overlayLayout.buttonHeight)) {
            val loadX = panelX
            val exitX = panelX + overlayLayout.buttonWidth + overlayLayout.padding
            when {
                x in loadX..(loadX + overlayLayout.buttonWidth) -> reloadWithSelectedModel()
                x in exitX..(exitX + overlayLayout.buttonWidth) -> engine.requestExit()
            }
        }
    }

    private fun reloadWithSelectedModel() {
        val selectedModel = models.getOrNull(selectedModelIndex) ?: return
        engine.logger.info("ModelViewer") { "Reloading scene with '${selectedModel.path}'" }
        engine.scenes.replace(
            ModelViewerScene(
                model = selectedModel,
                availableModels = models,
                modelScale = modelScale,
                wireframeMode = wireframeMode,
                openDialogOnStart = false,
            ),
        )
    }

    private fun handleWireframeToggle() {
        if (!engine.input.snapshot().wasPressed(Key.F1)) return
        wireframeMode = !wireframeMode
        modelEntityId?.let(world::getEntity)?.get<ModelComponent>()?.let { component ->
            component.material = component.material.copy(wireframe = wireframeMode)
        }
    }

    private fun syncCursorCapture() {
        engine.input.setCursorCaptured(!dialogVisible)
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
        var moveY = 0f
        var moveZ = 0f
        if (input.isDown(Key.W)) moveZ += 1f
        if (input.isDown(Key.S)) moveZ -= 1f
        if (input.isDown(Key.D)) moveX += 1f
        if (input.isDown(Key.A)) moveX -= 1f
        if (input.isDown(Key.ShiftLeft)) moveY += 1f
        if (input.isDown(Key.ControlLeft)) moveY -= 1f

        if (moveX != 0f || moveY != 0f || moveZ != 0f) {
            val speed = controller.moveSpeed
            transform.position.x += (forward.x * moveZ + right.x * moveX) * speed * dt
            transform.position.y += moveY * speed * dt
            transform.position.z += (forward.z * moveZ + right.z * moveX) * speed * dt
        }
    }
}

private class ModelViewerOverlaySystem(
    private val scene: ModelViewerScene,
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        scene.overlayCommand()?.let(world.renderCommands::submit)
    }
}
