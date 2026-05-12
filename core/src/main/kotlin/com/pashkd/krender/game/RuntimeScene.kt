package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentFactory
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentSystem
import com.pashkd.krender.engine.scene.SceneAssetCollector
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.scene.SkyboxAssetDescriptor
import com.pashkd.krender.engine.scene.SkyboxAssetSerializer
import com.pashkd.krender.engine.terrain.RuntimeTerrainMeshSystem
import com.pashkd.krender.engine.terrain.TerrainCameraControllerComponent
import com.pashkd.krender.engine.terrain.TerrainCameraControllerSystem
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainDataComponent
import com.pashkd.krender.engine.terrain.TerrainMaterialBakeService
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.terrain.TerrainRendererComponent
import com.pashkd.krender.engine.terrain.TerrainRuntimeFactory
import com.pashkd.krender.engine.terrain.runtimeTerrainFinalSplatTextureId

/**
 * Runtime-only scene loaded from a `.krscene` descriptor.
 *
 * Runtime state is data-driven by scene settings, entity components, and
 * referenced assets. The scene does not create fallback terrain or light
 * entities; light sources are expected to be serialized in the scene file.
 */
class RuntimeScene(
    private val scenePath: String,
) : Scene("runtime_scene") {
    private var descriptorCache: SceneDescriptor? = null
    private var skyboxCache: SkyboxAssetDescriptor? = null
    private lateinit var terrainPersistence: TerrainPersistence

    override fun scheduleAssets(assets: AssetService) {
        val descriptor = loadSceneDescriptor()
        val skybox = loadSkyboxAsset(descriptor)
        descriptorCache = descriptor
        skyboxCache = skybox

        val collectedAssets = SceneAssetCollector.collect(descriptor, skybox)
        engine.logger.info(TAG) {
            "RuntimeScene scheduleAssets scene='$scenePath' assets=${collectedAssets.joinToString { "${it.type.simpleName}:${it.path}" }}"
        }
        collectedAssets.forEach(assets::queue)
    }

    override fun show() {
        terrainPersistence = TerrainPersistence(engine.logger)

        val descriptor = descriptorCache ?: loadSceneDescriptor().also { descriptorCache = it }
        val skybox = skyboxCache ?: loadSkyboxAsset(descriptor).also { skyboxCache = it }
        val environment = RuntimeEnvironmentFactory.fromSceneSettings(descriptor.settings, skybox)

        engine.logger.info(TAG) {
            "RuntimeScene show scene='$scenePath' id='${descriptor.id}' name='${descriptor.name}' entities=${descriptor.entities.size} " +
                "activeCameraEntityId=${descriptor.settings.activeCameraEntityId ?: "<none>"} " +
                "activeTerrainEntityId=${descriptor.settings.activeTerrainEntityId ?: "<none>"} " +
                "skybox='${descriptor.settings.environment.skyboxAssetPath ?: "<none>"}'"
        }

        SceneSerializer.applyToWorld(descriptor, world, engine.logger)

        world.systems.add(TerrainCameraControllerSystem(engine.input))
        world.systems.add(ModelRenderSystem())
        world.systems.add(TerrainRenderSystem())
        world.systems.add(RuntimeEnvironmentSystem(environment))

        ensureRuntimeCamera(descriptor)
        if (prepareRuntimeTerrain(descriptor)) {
            world.systems.add(
                RuntimeTerrainMeshSystem(
                    materialBakeService = TerrainMaterialBakeService(loadTerrainMaterialLibrary(), engine.logger),
                    logger = engine.logger,
                ),
            )
        }
    }

    private fun loadSceneDescriptor(): SceneDescriptor {
        val normalizedPath = scenePath.trim().replace('\\', '/')
        require(normalizedPath.isNotBlank()) { "Runtime scene path must not be blank." }
        engine.logger.info(TAG) { "Loading runtime scene path='$normalizedPath'" }
        val text = engine.sceneFiles.readText(normalizedPath)
        return SceneSerializer.decode(text)
    }

    private fun loadSkyboxAsset(descriptor: SceneDescriptor): SkyboxAssetDescriptor? {
        val skyboxPath = descriptor.settings.environment.skyboxAssetPath
            ?.trim()
            ?.replace('\\', '/')
            ?.takeIf(String::isNotBlank)
            ?: return null
        return try {
            val skybox = SkyboxAssetSerializer.decode(engine.sceneFiles.readText(skyboxPath))
            engine.logger.info(TAG) {
                "Runtime skybox descriptor loaded path='$skyboxPath' texture='${skybox.texturePath}' model='${skybox.modelPath ?: "<none>"}'"
            }
            skybox
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "Runtime skybox descriptor load failed path='$skyboxPath': ${error.message}"
            }
            null
        }
    }

    private fun loadTerrainMaterialLibrary(): TerrainMaterialLibrary =
        TerrainMaterialLibrary(engine.logger).also { library ->
            library.load("materials/terrain_materials.json")
            engine.logger.info(TAG) { "Runtime terrain material library loaded materials=${library.all().size}" }
        }

    private fun ensureRuntimeCamera(descriptor: SceneDescriptor) {
        val activeCameraEntityId = descriptor.settings.activeCameraEntityId
        val activeCamera = activeCameraEntityId
            ?.let(world::getEntity)
            ?.takeIf { entity -> entity.get<PerspectiveCameraComponent>() != null }

        if (activeCamera == null) {
            engine.logger.warn(TAG) {
                "No active runtime camera found for scene id='${descriptor.id}' activeCameraEntityId=${activeCameraEntityId ?: "<none>"}; creating runtime camera."
            }
            createRuntimeCamera()
            return
        }

        activeCamera.add(ActiveCameraComponent())
        engine.logger.info(TAG) { "Using runtime camera entityId=${activeCamera.id} name='${activeCamera.name}'" }
    }

    private fun prepareRuntimeTerrain(descriptor: SceneDescriptor): Boolean {
        val terrainEntities = world.all()
            .filter { entity -> entity.get<TerrainComponent>() != null }

        if (terrainEntities.isEmpty()) {
            engine.logger.info(TAG) { "Runtime scene has no terrain entities." }
            return false
        }

        engine.logger.info(TAG) {
            "Runtime scene terrain candidates: " + terrainEntities.joinToString { entity ->
                val terrain = entity.get<TerrainComponent>()
                "entityId=${entity.id} name='${entity.name}' path='${terrain?.terrain?.path ?: "<missing>"}' visible=${terrain?.visible}"
            }
        }

        val activeTerrainEntityId = descriptor.settings.activeTerrainEntityId
        val activeTerrain = activeTerrainEntityId
            ?.let(world::getEntity)
            ?.takeIf { entity -> entity.get<TerrainComponent>() != null }
        val runtimeTerrains = when {
            activeTerrain != null -> listOf(activeTerrain)
            activeTerrainEntityId != null -> {
                engine.logger.warn(TAG) {
                    "Runtime scene activeTerrainEntityId=$activeTerrainEntityId was not found or has no TerrainComponent; preparing all scene terrain entities."
                }
                terrainEntities
            }
            else -> terrainEntities
        }

        return runtimeTerrains.count { entity -> createRuntimeTerrain(entity) } > 0
    }

    private fun createRuntimeTerrain(entity: Entity): Boolean {
        val terrainComponent = entity.get<TerrainComponent>()
        val path = terrainComponent?.terrain?.path?.trim()?.replace('\\', '/').orEmpty()
        if (path.isBlank()) {
            engine.logger.error(TAG) { "Runtime terrain entityId=${entity.id} name='${entity.name}' has no terrain asset path." }
            return false
        }

        val terrainData = try {
            TerrainRuntimeFactory(
                logger = engine.logger,
                persistence = terrainPersistence,
            ).load(terrainFilePath = path)
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "Runtime terrain load failed entityId=${entity.id} path='$path': ${error.message}"
            }
            return false
        }

        val modelId = "runtime_terrain_${entity.id}_${terrainData.width}x${terrainData.height}"
        val finalTextureId = runtimeTerrainFinalSplatTextureId(
            entityId = entity.id,
            modelId = modelId,
        )
        entity.add(TerrainDataComponent(terrainData))
        entity.add(
            TerrainRendererComponent(
                modelId = modelId,
                material = Material(
                    baseColor = Color.white(),
                    diffuseTextureRef = MaterialTextureRef(
                        id = finalTextureId,
                        channel = "baseColor",
                        uvChannel = 0,
                    ),
                ),
                finalSplatResolution = terrainComponent?.bakedTextureResolution ?: 0,
            ),
        )
        engine.logger.info(TAG) {
            "Runtime terrain render state prepared entityId=${entity.id} path='$path' " +
                "size=${terrainData.width}x${terrainData.height} layers=${terrainData.allLayers().size} " +
                "finalSplatResolution=${terrainComponent?.bakedTextureResolution ?: "<missing>"}"
        }
        return true
    }

    private fun createRuntimeCamera() {
        val camera = world.createEntity("Runtime Camera")
        camera.transform.position.set(0f, 24f, 48f)
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 60f,
                near = 0.1f,
                far = 768f,
                lookAt = Vec3.zero(),
            ),
        )
        camera.add(ActiveCameraComponent())
        camera.add(TerrainCameraControllerComponent())
    }

    companion object {
        private const val TAG = "RuntimeScene"
    }
}
