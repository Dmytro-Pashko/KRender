package com.pashkd.krender.game

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentFactory
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentSystem
import com.pashkd.krender.engine.scene.*
import com.pashkd.krender.engine.terrain.*

data class RuntimeSceneBuildRequest(
    val scenePath: String,
    val descriptor: SceneDescriptor,
    val skybox: SkyboxAssetDescriptor?,
)

data class RuntimeSceneBuildResult(
    val activeCameraEntityId: Long,
    val terrainPrepared: Boolean,
    val skyboxEnabled: Boolean,
    val validationReport: SceneValidationReport,
)

class RuntimeSceneBuilder(
    private val engine: EngineContext,
    private val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = engine.terrainTextureSamplerFactory,
) {
    fun build(
        world: SceneWorld,
        request: RuntimeSceneBuildRequest,
    ): RuntimeSceneBuildResult {
        val dependencyGraph = SceneDependencyCollector(engine.sceneFiles).collect(request.descriptor, request.skybox)
        val validationReport = RuntimeSceneValidator.validate(request.descriptor, dependencyGraph)
        RuntimeSceneValidator.requireValid(request.descriptor, validationReport)

        SceneSerializer.applyToWorld(request.descriptor, world, engine.logger)
        val activeCamera = RuntimeSceneValidator.requireActiveCamera(world, request.descriptor)
        activeCamera.add(ActiveCameraComponent())

        val resolvedSkybox = resolveSkybox(request)
        val environment = RuntimeEnvironmentFactory.fromSceneSettings(
            settings = request.descriptor.settings,
            skybox = resolvedSkybox,
        )

        var terrainPrepared = false
        var materialBakeService: TerrainMaterialBakeService? = null
        if (request.descriptor.settings.activeTerrainEntityId != null) {
            val terrainMaterialLibrary =
                RuntimeTerrainMaterialLibraryService(engine.sceneFiles, engine.logger).loadRequired(
                    request.descriptor.settings.terrain.materialLibraryPath,
                )
            materialBakeService = TerrainMaterialBakeService(
                materialLibrary = terrainMaterialLibrary,
                logger = engine.logger,
                textureSamplerFactory = terrainTextureSamplerFactory,
            )
            RuntimeTerrainService(
                logger = engine.logger,
                terrainLoader = TerrainRuntimeLoader(
                    logger = engine.logger,
                    persistence = TerrainPersistence(logger = engine.logger, files = engine.sceneFiles),
                ),
                materialBakeService = materialBakeService,
            ).prepareActiveTerrain(world, request.descriptor)
            terrainPrepared = true
            if (activeCamera.get<TerrainCameraControllerComponent>() == null) {
                activeCamera.add(TerrainCameraControllerComponent())
            }
            world.systems.add(TerrainCameraControllerSystem(engine.input))
        }

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

        return RuntimeSceneBuildResult(
            activeCameraEntityId = activeCamera.id,
            terrainPrepared = terrainPrepared,
            skyboxEnabled = environment.showSkybox,
            validationReport = validationReport,
        )
    }

    private fun resolveSkybox(request: RuntimeSceneBuildRequest): SkyboxAssetDescriptor? {
        if (!request.descriptor.settings.environment.showSkybox) {
            return null
        }
        request.skybox?.let { return it }

        val configuredPath = RuntimeSceneValidator.skyboxPath(request.descriptor)
        if (configuredPath == null) {
            engine.logger.warn(TAG) {
                "Runtime skybox disabled scene='${request.scenePath}' because showSkybox=true but no skybox path is configured."
            }
        } else {
            engine.logger.warn(TAG) {
                "Runtime skybox disabled scene='${request.scenePath}' because skybox '$configuredPath' could not be resolved."
            }
        }
        return null
    }

    private companion object {
        private const val TAG = "RuntimeSceneBuilder"
    }
}

