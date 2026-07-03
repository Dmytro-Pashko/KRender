package com.pashkd.krender.engine.sceneplayer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentGltfRendererSettingsFactory
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.scene.RuntimeSceneValidator
import com.pashkd.krender.engine.scene.RuntimeTerrainMaterialLibraryService
import com.pashkd.krender.engine.scene.SceneDependencyCollector
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.scene.SceneValidationReport
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
    val environment: Environment?,
)

data class ScenePlayerBuildResult(
    val activeCameraEntityId: Long,
    val terrainPrepared: Boolean,
    val environmentEnabled: Boolean,
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
        val dependencyGraph = SceneDependencyCollector(engine.sceneFiles).collect(request.descriptor)
        val validationReport = RuntimeSceneValidator.validate(request.descriptor, dependencyGraph)
        RuntimeSceneValidator.requireValid(request.descriptor, validationReport)

        SceneSerializer.applyToWorld(request.descriptor, world, engine.logger)
        val activeCamera = RuntimeSceneValidator.requireActiveCamera(world, request.descriptor)
        activeCamera.add(ActiveCameraComponent())
        installAmbientLight(world, request.descriptor)

        val materialBakeService = prepareTerrain(world, request, activeCamera)
        val terrainPrepared = materialBakeService != null
        registerSystems(world, request.environment, terrainPrepared, materialBakeService)

        return ScenePlayerBuildResult(
            activeCameraEntityId = activeCamera.id,
            terrainPrepared = terrainPrepared,
            environmentEnabled = request.environment != null,
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
        environment: Environment?,
        terrainPrepared: Boolean,
        materialBakeService: TerrainMaterialBakeService?,
    ) {
        val gltfRendererSettings = environment?.let(EnvironmentGltfRendererSettingsFactory::create)
        world.systems.add(ModelRenderSystem(gltfRendererSettings = { gltfRendererSettings }))
        if (terrainPrepared) {
            world.systems.add(TerrainRenderSystem())
        }
        if (terrainPrepared && materialBakeService != null) {
            world.systems.add(
                RuntimeTerrainMeshSystem(
                    materialBakeService = materialBakeService,
                    logger = engine.logger,
                ),
            )
        }
    }

    private fun installAmbientLight(
        world: SceneWorld,
        descriptor: SceneDescriptor,
    ) {
        val lighting = descriptor.settings.lighting
        world.createEntity("Scene Ambient Light").add(
            LightComponent(
                type = LightType.Ambient,
                color = lighting.ambientColor.copy(),
                intensity = lighting.ambientIntensity,
            ),
        )
    }
}
