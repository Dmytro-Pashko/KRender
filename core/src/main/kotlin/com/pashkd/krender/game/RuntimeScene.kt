package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.TerrainAsset
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentFactory
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentSystem
import com.pashkd.krender.engine.scene.RuntimeSceneValidator
import com.pashkd.krender.engine.scene.RuntimeTerrainMaterialLibraryService
import com.pashkd.krender.engine.scene.SceneAssetCollector
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.scene.SkyboxAssetDescriptor
import com.pashkd.krender.engine.scene.SkyboxAssetService
import com.pashkd.krender.engine.terrain.RuntimeTerrainMeshSystem
import com.pashkd.krender.engine.terrain.RuntimeTerrainService
import com.pashkd.krender.engine.terrain.TerrainCameraControllerComponent
import com.pashkd.krender.engine.terrain.TerrainCameraControllerSystem
import com.pashkd.krender.engine.terrain.TerrainMaterialBakeService
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.terrain.TerrainRuntimeLoader

/**
 * Runtime-only scene loaded from a `.krscene` descriptor.
 */
class RuntimeScene(
    private val scenePath: String,
) : Scene("runtime_scene") {
    private var descriptorCache: SceneDescriptor? = null
    private var skyboxCache: SkyboxAssetDescriptor? = null

    override fun scheduleAssets(assets: AssetService) {
        val descriptor = loadSceneDescriptor()
        val skyboxPath = RuntimeSceneValidator.requireSkyboxPath(descriptor)
        val skybox = SkyboxAssetService(engine.sceneFiles, engine.logger).loadRequired(skyboxPath)
        descriptorCache = descriptor
        skyboxCache = skybox

        val collectedAssets = SceneAssetCollector.collect(descriptor, skybox)
        engine.logger.info(TAG) {
            "RuntimeScene scheduleAssets scene='$scenePath' descriptors=${collectedAssets.descriptorPaths.joinToString()} " +
                "assets=${collectedAssets.assetRefs.joinToString { "${it.type.simpleName}:${it.path}" }}"
        }
        collectedAssets.assetRefs
            .filterNot { asset -> asset.type == TerrainAsset::class }
            .forEach(assets::queue)
    }

    override fun show() {
        val descriptor = descriptorCache ?: loadSceneDescriptor().also { descriptorCache = it }

        engine.logger.info(TAG) {
            "RuntimeScene show scene='$scenePath' id='${descriptor.id}' name='${descriptor.name}' entities=${descriptor.entities.size} " +
                "activeCameraEntityId=${descriptor.settings.activeCameraEntityId ?: "<none>"} " +
                "activeTerrainEntityId=${descriptor.settings.activeTerrainEntityId ?: "<none>"} " +
                "skybox='${descriptor.settings.environment.skyboxAssetPath ?: "<none>"}'"
        }

        SceneSerializer.applyToWorld(descriptor, world, engine.logger)

        val skybox = skyboxCache ?: SkyboxAssetService(engine.sceneFiles, engine.logger).loadRequired(
            RuntimeSceneValidator.requireSkyboxPath(descriptor),
        ).also { skyboxCache = it }
        val environment = RuntimeEnvironmentFactory.fromSceneSettings(
            settings = descriptor.settings,
            skybox = skybox,
        )

        val activeCamera = RuntimeSceneValidator.requireActiveCamera(world, descriptor)
        activeCamera.add(ActiveCameraComponent())
        if (activeCamera.get<TerrainCameraControllerComponent>() == null) {
            activeCamera.add(TerrainCameraControllerComponent())
        }

        val terrainMaterialLibrary = RuntimeTerrainMaterialLibraryService(engine.sceneFiles, engine.logger).loadRequired(
            descriptor.settings.terrain.materialLibraryPath,
        )
        val materialBakeService = TerrainMaterialBakeService(terrainMaterialLibrary, engine.logger)
        RuntimeTerrainService(
            logger = engine.logger,
            terrainLoader = TerrainRuntimeLoader(
                logger = engine.logger,
                persistence = TerrainPersistence(logger = engine.logger, files = engine.sceneFiles),
            ),
            materialBakeService = materialBakeService,
        ).prepareActiveTerrain(world, descriptor)

        world.systems.add(TerrainCameraControllerSystem(engine.input))
        world.systems.add(ModelRenderSystem())
        world.systems.add(TerrainRenderSystem())
        world.systems.add(RuntimeEnvironmentSystem(environment))
        world.systems.add(
            RuntimeTerrainMeshSystem(
                materialBakeService = materialBakeService,
                logger = engine.logger,
            ),
        )
    }

    private fun loadSceneDescriptor(): SceneDescriptor {
        val normalizedPath = scenePath.trim().replace('\\', '/')
        require(normalizedPath.isNotBlank()) { "Runtime scene path must not be blank." }
        engine.logger.info(TAG) { "Loading runtime scene path='$normalizedPath'" }
        val text = engine.sceneFiles.readText(normalizedPath)
        return SceneSerializer.decode(text)
    }

    companion object {
        private const val TAG = "RuntimeScene"
    }
}
