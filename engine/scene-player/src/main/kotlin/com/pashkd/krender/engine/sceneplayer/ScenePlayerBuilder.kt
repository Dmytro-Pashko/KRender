package com.pashkd.krender.engine.sceneplayer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.RuntimeEnvironment
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentFactory
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentSystem
import com.pashkd.krender.engine.scene.RuntimeSceneValidator
import com.pashkd.krender.engine.scene.RuntimeTerrainMaterialLibraryService
import com.pashkd.krender.engine.scene.SceneDependencyCollector
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.scene.SceneValidationReport
import com.pashkd.krender.engine.scene.SkyboxAssetDescriptor
import com.pashkd.krender.engine.terrain.RuntimeTerrainMeshSystem
import com.pashkd.krender.engine.terrain.RuntimeTerrainService
import com.pashkd.krender.engine.terrain.TerrainCameraControllerComponent
import com.pashkd.krender.engine.terrain.TerrainCameraControllerSystem
import com.pashkd.krender.engine.terrain.TerrainMaterialBakeService
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.terrain.TerrainRuntimeLoader

data class ScenePlayerBuildRequest(
    val scenePath: String,
    val descriptor: SceneDescriptor,
    val skybox: SkyboxAssetDescriptor?,
)

data class ScenePlayerBuildResult(
    val activeCameraEntityId: Long,
    val terrainPrepared: Boolean,
    val skyboxEnabled: Boolean,
    val validationReport: SceneValidationReport,
)

class ScenePlayerBuilder(
    private val engine: EngineContext,
    private val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = engine.terrainTextureSamplerFactory,
) {
    fun build(
        world: SceneWorld,
        request: ScenePlayerBuildRequest,
    ): ScenePlayerBuildResult {
        val dependencyGraph = SceneDependencyCollector(engine.sceneFiles).collect(request.descriptor, request.skybox)
        val validationReport = RuntimeSceneValidator.validate(request.descriptor, dependencyGraph)
        RuntimeSceneValidator.requireValid(request.descriptor, validationReport)

        SceneSerializer.applyToWorld(request.descriptor, world, engine.logger)
        val activeCamera = RuntimeSceneValidator.requireActiveCamera(world, request.descriptor)
        activeCamera.add(ActiveCameraComponent())

        val resolvedSkybox = resolveSkybox(request)
        val environment =
            RuntimeEnvironmentFactory.fromSceneSettings(
                settings = request.descriptor.settings,
                skybox = resolvedSkybox,
            )

        val materialBakeService = prepareTerrain(world, request, activeCamera)
        val terrainPrepared = materialBakeService != null
        registerSystems(world, environment, terrainPrepared, materialBakeService)

        return ScenePlayerBuildResult(
            activeCameraEntityId = activeCamera.id,
            terrainPrepared = terrainPrepared,
            skyboxEnabled = environment.showSkybox,
            validationReport = validationReport,
        )
    }

    private fun prepareTerrain(
        world: SceneWorld,
        request: ScenePlayerBuildRequest,
        activeCamera: Entity,
    ): TerrainMaterialBakeService? {
        if (request.descriptor.settings.activeTerrainEntityId == null) {
            return null
        }

        val terrainMaterialLibrary =
            RuntimeTerrainMaterialLibraryService(engine.sceneFiles, engine.logger).loadRequired(
                request.descriptor.settings.terrain.materialLibraryPath,
            )
        val materialBakeService =
            TerrainMaterialBakeService(
                materialLibrary = terrainMaterialLibrary,
                logger = engine.logger,
                textureSamplerFactory = terrainTextureSamplerFactory,
            )
        RuntimeTerrainService(
            logger = engine.logger,
            terrainLoader =
                TerrainRuntimeLoader(
                    logger = engine.logger,
                    persistence = TerrainPersistence(logger = engine.logger, files = engine.sceneFiles),
                ),
            materialBakeService = materialBakeService,
        ).prepareActiveTerrain(world, request.descriptor)
        if (activeCamera.get<TerrainCameraControllerComponent>() == null) {
            activeCamera.add(TerrainCameraControllerComponent())
        }
        world.systems.add(TerrainCameraControllerSystem(engine.input))
        return materialBakeService
    }

    private fun registerSystems(
        world: SceneWorld,
        environment: RuntimeEnvironment,
        terrainPrepared: Boolean,
        materialBakeService: TerrainMaterialBakeService?,
    ) {
        world.systems.add(ModelRenderSystem())
        if (terrainPrepared) {
            world.systems.add(TerrainRenderSystem())
        }
        world.systems.add(RuntimeEnvironmentSystem(environment))
        if (terrainPrepared && materialBakeService != null) {
            world.systems.add(
                RuntimeTerrainMeshSystem(
                    materialBakeService = materialBakeService,
                    logger = engine.logger,
                ),
            )
        }
    }

    private fun resolveSkybox(request: ScenePlayerBuildRequest): SkyboxAssetDescriptor? {
        if (!request.descriptor.settings.environment.showSkybox) {
            return null
        }
        val configuredPath = RuntimeSceneValidator.skyboxPath(request.descriptor)
        return request.skybox ?: run {
            if (configuredPath == null) {
                engine.logger.warn(TAG) {
                    "ScenePlayer skybox disabled scene='${request.scenePath}' because showSkybox=true but no skybox path is configured."
                }
            } else {
                engine.logger.warn(TAG) {
                    "ScenePlayer skybox disabled scene='${request.scenePath}' because skybox '$configuredPath' could not be resolved."
                }
            }
            null
        }
    }

    private companion object {
        private const val TAG = "ScenePlayerBuilder"
    }
}
