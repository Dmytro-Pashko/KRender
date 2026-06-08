package com.pashkd.krender.game

import com.pashkd.krender.engine.animation.AnimationComponent
import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.*
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.scene.SceneDependencyCollector
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.terrain.TerrainAssetSyncSystem
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.woolboy.*

/**
 * Lightweight playable sandbox for validating Woolboy movement and clip switching.
 *
 * The authored scene descriptor provides terrain, lights, camera, and environment
 * settings. This runtime scene only adds the controllable Woolboy player and
 * gameplay prototype systems.
 */
class WoolboySandboxScene : Scene("woolboy_sandbox_scene") {
    companion object {
        private const val TAG = "WoolboySandboxScene"
        private const val SceneAssetPath = "scenes/woolboy_sandbox_scene.krscene"
        private const val WoolboyModelPath = "model/wool_boy_animated.glb"
        private const val PlayerBaseY = 0f
        private const val PlayerScale = 1f
    }

    private val woolboyModel = AssetRef.model(WoolboyModelPath)
    private var descriptorCache: SceneDescriptor? = null
    private val gameState = WoolboyGameState()

    override val config: SceneConfig = SceneConfigPresets.RuntimeGame16By9

    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets: List<AssetRef<*>> = listOf(woolboyModel)
        },
    )

    /**
     * Queues both the gameplay-only Woolboy model and dependencies referenced
     * by the authored scene descriptor, including its terrain asset.
     */
    override fun scheduleAssets(assets: AssetService) {
        val descriptor = loadSceneDescriptor()
        descriptorCache = descriptor
        val dependencyGraph = SceneDependencyCollector(engine.sceneFiles).collect(descriptor, resolvedSkybox = null)
        engine.logger.info(TAG) {
            "WoolboySandboxScene scheduleAssets scene='$SceneAssetPath' model='$WoolboyModelPath' " +
                "dependencies=${dependencyGraph.dependencies.joinToString { dependency -> "${dependency.kind}:${dependency.path}:${dependency.requirement}" }}"
        }
        super.scheduleAssets(assets)
        dependencyGraph.schedulableAssets.forEach(assets::queue)
    }

    /**
     * Builds the playable scene by first applying authored scene data and then
     * layering the runtime-only player controller setup on top.
     */
    override fun show() {
        val descriptor = descriptorCache ?: loadSceneDescriptor().also { descriptorCache = it }
        engine.logger.info(TAG) { "WoolboySandboxScene show start scene='$SceneAssetPath'" }
        // The descriptor owns terrain, lights, camera, and environment settings.
        SceneSerializer.applyToWorld(descriptor, world, engine.logger)
        markAuthoredCameraActive(descriptor)
        createPlayer()
        createSystems(descriptor)
        engine.logger.info(TAG) { "WoolboySandboxScene show complete entities=${world.all().size}" }
    }

    override fun hide() {
        engine.input.setCursorCaptured(false)
        engine.runtimeUi.setActionHandler(null)
        engine.runtimeUi.clear()
    }

    /**
     * Creates only the gameplay player. Terrain and lights are intentionally
     * not created here because they come from [SceneAssetPath].
     */
    private fun createPlayer() {
        val player = world.createEntity("Woolboy Player")
        // KRender uses Y as vertical height, so this MVP moves on X/Z despite older notes saying x-y.
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

    /**
     * Registers runtime systems in dependency order: terrain sync prepares
     * renderable terrain data before render collection, while gameplay updates
     * run before camera follow and animation/model draw command submission.
     */
    private fun createSystems(descriptor: SceneDescriptor) {
        addSystem("TerrainAssetSyncSystem", TerrainAssetSyncSystem(engine.logger))
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
        addSystem(
            "RuntimeEnvironmentSystem",
            RuntimeEnvironmentSystem(RuntimeEnvironmentFactory.fromSceneSettings(descriptor.settings, skybox = null)),
        )
        addSystem("AnimatedModelRenderSystem", AnimatedModelRenderSystem())
    }

    /**
     * Marks the authored camera as active for the renderer and orbit camera system.
     *
     * If the descriptor does not have a valid active camera id, the first camera
     * entity is used as a safe fallback so the playable scene can still render.
     */
    private fun markAuthoredCameraActive(descriptor: SceneDescriptor) {
        val camera = descriptor.settings.activeCameraEntityId
            ?.let(world::getEntity)
            ?.takeIf { entity -> entity.get<PerspectiveCameraComponent>() != null }
            ?: world.query<PerspectiveCameraComponent>().firstOrNull()
        camera?.add(ActiveCameraComponent())
        engine.logger.info(TAG) {
            "Woolboy authored scene loaded path='$SceneAssetPath' terrainEntityId=${descriptor.settings.activeTerrainEntityId ?: "<none>"} " +
                "cameraEntityId=${camera?.id ?: "<none>"} lights=${world.query<LightComponent>().size}"
        }
    }

    /** Loads the authored scene that provides non-gameplay sandbox content. */
    private fun loadSceneDescriptor(): SceneDescriptor {
        engine.logger.info(TAG) { "Loading Woolboy authored scene path='$SceneAssetPath'" }
        return SceneSerializer.decode(engine.sceneFiles.readText(SceneAssetPath))
    }

    private fun addSystem(name: String, system: com.pashkd.krender.engine.api.System) {
        world.systems.add(system)
        engine.logger.info(TAG) { "Woolboy system added name='$name'" }
    }
}
