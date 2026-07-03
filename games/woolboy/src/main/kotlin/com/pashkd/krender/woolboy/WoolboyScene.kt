package com.pashkd.krender.woolboy

import com.pashkd.krender.engine.animation.AnimationComponent
import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.VelocityComponent
import com.pashkd.krender.engine.assets.environment.DefaultEnvironmentService
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentGltfRendererSettingsFactory
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.scene.SceneDependencyCollector
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.terrain.TerrainAssetSyncSystem
import com.pashkd.krender.engine.terrain.TerrainRenderSystem

/**
 * Lightweight playable sandbox for validating Woolboy movement and clip switching.
 *
 * The authored scene descriptor provides terrain, lights, camera, and environment
 * settings. This runtime scene only adds the controllable Woolboy player and
 * gameplay prototype systems.
 */
class WoolboyScene : Scene(SceneId) {
    companion object {
        private const val TAG = "WoolboyScene"
        const val SceneId = "woolboy-demo"
        private const val SceneAssetPath = "assets/woolboy/scenes/woolboy_sandbox_scene.krscene"
        private const val WoolboyModelPath = "assets/woolboy/model/wool_boy_animated.glb"
        private const val TerrainMaterialLibraryPath = "assets/woolboy/materials/terrain_materials.json"
        private const val PlayerBaseY = 0f
        private const val PlayerScale = 1f
    }

    private val woolboyModel = AssetRef.model(WoolboyModelPath)
    private var descriptorCache: SceneDescriptor? = null
    private var environmentCache: Environment? = null
    private val gameState = WoolboyGameState()

    override val config: SceneConfig = SceneConfigPresets.RuntimeGame16By9

    override val requiredAssets: List<AssetPack> =
        listOf(
            object : AssetPack {
                override val assets: List<AssetRef<*>> = listOf(woolboyModel)
            },
        )

    override fun scheduleAssets(assets: AssetService) {
        val descriptor = loadSceneDescriptor()
        descriptorCache = descriptor
        val environment = loadEnvironment(descriptor)
        environmentCache = environment
        val dependencyGraph = SceneDependencyCollector(engine.sceneFiles).collect(descriptor)
        engine.logger.info(TAG) {
            "Woolboy demo scheduleAssets scene='$SceneAssetPath' model='$WoolboyModelPath' " +
                "dependencies=${
                    dependencyGraph.dependencies.joinToString { dependency ->
                        "${dependency.kind}:${dependency.path}:${dependency.requirement}"
                    }
                }"
        }
        super.scheduleAssets(assets)
        dependencyGraph.schedulableAssets.forEach(assets::queue)
    }

    override fun show() {
        val descriptor = descriptorCache ?: loadSceneDescriptor().also { descriptorCache = it }
        engine.logger.info(TAG) {
            "Woolboy demo started engine='KRender' module='games:woolboy' assets='bundled assets/woolboy'"
        }
        engine.logger.info(TAG) { "WoolboyScene show start scene='$SceneAssetPath'" }
        SceneSerializer.applyToWorld(descriptor, world, engine.logger)
        markAuthoredCameraActive(descriptor)
        installAmbientLight(descriptor)
        createPlayer()
        createSystems(environmentCache ?: loadEnvironment(descriptor).also { environmentCache = it })
        engine.logger.info(TAG) { "WoolboyScene show complete entities=${world.all().size}" }
    }

    override fun hide() {
        engine.input.setCursorCaptured(false)
        engine.runtimeUi.setActionHandler(null)
        engine.runtimeUi.clear()
    }

    private fun createPlayer() {
        val player = world.createEntity("Woolboy Player")
        player.transform.position.set(0f, PlayerBaseY, 0f)
        player.transform.scale.set(PlayerScale, PlayerScale, PlayerScale)
        player.add(
            ModelComponent(
                model = woolboyModel,
                material = Material(baseColor = Color.white()),
            ),
        )
        player.add(PlayerControllerComponent())
        player.add(VelocityComponent())
        player.add(AnimationComponent(currentAnimation = WoolboyAnimations.Idle, loop = true))
        engine.logger.info(TAG) { "Woolboy player created id=${player.id} model='$WoolboyModelPath'" }
    }

    private fun createSystems(environment: Environment?) {
        addSystem(
            "TerrainAssetSyncSystem",
            TerrainAssetSyncSystem(
                logger = engine.logger,
                materialLibraryPath = TerrainMaterialLibraryPath,
            ),
        )
        addSystem(
            "WoolboyUiControllerSystem",
            WoolboyUiControllerSystem(
                runtimeUi = engine.runtimeUi,
                gameState = gameState,
                input = engine.input,
                logger = engine.logger,
                requestExit = engine::requestExit,
            ),
        )
        addSystem(
            "PlayerControllerSystem",
            PlayerControllerSystem(
                input = engine.input,
                logger = engine.logger,
                inputEnabled = { gameState.playerInputEnabled },
            ),
        )
        addSystem("CharacterAnimationSystem", CharacterAnimationSystem(engine.assets, engine.logger))
        addSystem(
            "ThirdPersonCameraFollowSystem",
            ThirdPersonCameraFollowSystem(
                input = engine.input,
                logger = engine.logger,
                inputEnabled = { gameState.playerInputEnabled },
            ),
        )
        addSystem("TerrainRenderSystem", TerrainRenderSystem())
        val gltfRendererSettings = environment?.let(EnvironmentGltfRendererSettingsFactory::create)
        addSystem(
            "EnvironmentAwareModelRenderSystem",
            AnimatedModelRenderSystem(gltfRendererSettings),
        )
    }

    private fun markAuthoredCameraActive(descriptor: SceneDescriptor) {
        val camera =
            descriptor.settings.activeCameraEntityId
                ?.let(world::getEntity)
                ?.takeIf { entity -> entity.get<PerspectiveCameraComponent>() != null }
                ?: world.query<PerspectiveCameraComponent>().firstOrNull()
        camera?.add(ActiveCameraComponent())
        engine.logger.info(TAG) {
            "Woolboy authored scene loaded path='$SceneAssetPath' terrainEntityId=${descriptor.settings.activeTerrainEntityId ?: "<none>"} " +
                "cameraEntityId=${camera?.id ?: "<none>"} lights=${world.query<LightComponent>().size}"
        }
    }

    private fun loadSceneDescriptor(): SceneDescriptor {
        engine.logger.info(TAG) { "Loading Woolboy authored scene path='$SceneAssetPath'" }
        return SceneSerializer.decode(engine.sceneFiles.readText(SceneAssetPath))
    }

    private fun loadEnvironment(descriptor: SceneDescriptor): Environment? {
        val environmentPath = descriptor.settings.environment.environmentAssetPath ?: return null
        return runCatching {
            DefaultEnvironmentService(engine.sceneFiles).load(environmentPath)
        }.getOrElse { error ->
            engine.logger.warn(TAG, error) {
                "Woolboy environment '$environmentPath' could not be loaded: ${error.message}"
            }
            null
        }
    }

    private fun installAmbientLight(descriptor: SceneDescriptor) {
        val lighting = descriptor.settings.lighting
        world.createEntity("Scene Ambient Light").add(
            LightComponent(
                type = LightType.Ambient,
                color = lighting.ambientColor.copy(),
                intensity = lighting.ambientIntensity,
            ),
        )
    }

    private fun addSystem(
        name: String,
        system: System,
    ) {
        world.systems.add(system)
        engine.logger.info(TAG) { "Woolboy system added name='$name'" }
    }
}
