package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Entity
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.DEFAULT_RUNTIME_SKYBOX_TEXTURE
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.render3d.RuntimeEnvironmentSystem
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.terrain.RuntimeTerrainMeshSystem
import com.pashkd.krender.engine.terrain.TerrainMaterialBakeService
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainCameraControllerComponent
import com.pashkd.krender.engine.terrain.TerrainCameraControllerSystem
import com.pashkd.krender.engine.terrain.TerrainDataComponent
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.terrain.TerrainRendererComponent
import com.pashkd.krender.engine.terrain.TerrainRuntimeFactory
import com.pashkd.krender.engine.terrain.runtimeTerrainFinalSplatTextureId

/**
 * Runtime-only scene that can load saved `.krscene` content and always prepares
 * a runtime terrain using the same backend-neutral terrain render pipeline.
 */
class RuntimeScene(
    private val scenePath: String? = null,
    private val terrainFilePath: String? = null,
    private val finalSplatResolution: Int? = null,
    private val skyboxTexturePath: String = DEFAULT_RUNTIME_SKYBOX_TEXTURE,
) : Scene("runtime_scene") {
    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets = listOf(AssetRef.texture(skyboxTexturePath))
        },
    )

    private lateinit var terrainMaterialLibrary: TerrainMaterialLibrary
    private lateinit var terrainPersistence: TerrainPersistence

    override fun scheduleAssets(assets: com.pashkd.krender.engine.api.AssetService) {
        engine.logger.info(TAG) {
            "RuntimeScene scheduleAssets skybox='$skyboxTexturePath' terrain='$terrainFilePath' " +
                "finalSplatResolution=${finalSplatResolution ?: "<scene>"} materialLibrary='materials/terrain_materials.json'"
        }
        super.scheduleAssets(assets)
    }

    override fun show() {
        terrainPersistence = TerrainPersistence(engine.logger)
        terrainMaterialLibrary = TerrainMaterialLibrary(engine.logger).also { library ->
            library.load("materials/terrain_materials.json")
        }
        engine.logger.info(TAG) {
            "RuntimeScene show terrainPath='${terrainFilePath ?: "<scene>"}' " +
                "finalSplatResolution=${finalSplatResolution ?: "<scene>"} skybox='$skyboxTexturePath' " +
                "materials=${terrainMaterialLibrary.all().size}"
        }

        world.systems.add(TerrainCameraControllerSystem(engine.input))
        world.systems.add(
            RuntimeTerrainMeshSystem(
                materialBakeService = TerrainMaterialBakeService(terrainMaterialLibrary, engine.logger),
                logger = engine.logger,
                finalSplatResolution = finalSplatResolution,
            ),
        )
        world.systems.add(ModelRenderSystem())
        world.systems.add(TerrainRenderSystem())
        world.systems.add(RuntimeEnvironmentSystem(skyboxTexturePath))

        val descriptor = loadSceneIfPresent()
        if (descriptor != null) {
            applyAmbientLight(descriptor)
            ensureRuntimeCamera(descriptor)
        } else {
            createRuntimeCamera()
            createRuntimeLights()
        }
        createRuntimeTerrain(descriptor)
    }

    private fun loadSceneIfPresent(): SceneDescriptor? {
        val path = scenePath?.takeIf(String::isNotBlank)
        if (path == null) {
            engine.logger.info(TAG) { "Runtime scene path is missing; starting terrain-only runtime scene." }
            return null
        }

        return try {
            engine.logger.info(TAG) { "Loading runtime scene path='$path'" }
            val text = engine.sceneFiles.readText(path)
            val descriptor = SceneSerializer.decode(text)
            SceneSerializer.applyToWorld(descriptor, world, engine.logger)
            engine.logger.info(TAG) {
                "Runtime scene loaded path='$path' id='${descriptor.id}' name='${descriptor.name}' entities=${descriptor.entities.size} " +
                    "activeCameraEntityId=${descriptor.settings.activeCameraEntityId ?: "<none>"} " +
                    "activeTerrainEntityId=${descriptor.settings.activeTerrainEntityId ?: "<none>"}"
            }
            descriptor
        } catch (error: Exception) {
            engine.logger.error(TAG, error) { "Runtime scene load failed path='$path': ${error.message}" }
            world.clear()
            null
        }
    }

    private fun createRuntimeTerrain(descriptor: SceneDescriptor?) {
        val source = resolveRuntimeTerrainSource(descriptor)
        val terrainData = TerrainRuntimeFactory(
            logger = engine.logger,
            persistence = terrainPersistence,
        ).load(
            terrainFilePath = source.path,
        )

        engine.logger.info(TAG) {
            "Creating runtime terrain render state source=${source.reason} path='${source.path}' " +
                "entityId=${source.entity?.id ?: "<new>"} size=${terrainData.width}x${terrainData.height} layers=${terrainData.allLayers().size}"
        }
        val terrain = source.entity ?: world.createEntity("Runtime Terrain")
        val sceneTerrain = terrain.get<TerrainComponent>()
        val finalTextureId = runtimeTerrainFinalSplatTextureId(
            entityId = terrain.id,
            modelId = "runtime_terrain_${terrain.id}_${terrainData.width}x${terrainData.height}",
        )
        if (sceneTerrain?.visible == false) {
            sceneTerrain.visible = true
            engine.logger.warn(TAG) {
                "Active runtime terrain entityId=${terrain.id} path='${sceneTerrain.terrain.path}' had visible=false; enabling it for runtime rendering."
            }
        }
        terrain.add(TerrainDataComponent(terrainData))
        terrain.add(
            TerrainRendererComponent(
                modelId = "runtime_terrain_${terrain.id}_${terrainData.width}x${terrainData.height}",
                material = Material(
                    baseColor = Color.white(),
                    diffuseTextureRef = MaterialTextureRef(
                        id = finalTextureId,
                        channel = "baseColor",
                        uvChannel = 0,
                    ),
                ),
                finalSplatResolution = source.finalSplatResolution ?: 0,
            ),
        )
        engine.logger.info(TAG) {
            "Runtime terrain final splat requested entityId=${terrain.id} resolution=${source.finalSplatResolution ?: "<system-required>"}"
        }
    }

    private fun resolveRuntimeTerrainSource(descriptor: SceneDescriptor?): RuntimeTerrainSource {
        val terrainEntities = world.all()
            .filter { entity -> entity.get<TerrainComponent>() != null }

        if (terrainEntities.isNotEmpty()) {
            engine.logger.info(TAG) {
                "Runtime scene terrain candidates: " + terrainEntities.joinToString { entity ->
                    val terrain = entity.get<TerrainComponent>()
                    "entityId=${entity.id} name='${entity.name}' path='${terrain?.terrain?.path ?: "<missing>"}' visible=${terrain?.visible}"
                }
            }
        }

        val activeTerrainEntityId = descriptor?.settings?.activeTerrainEntityId
        val activeTerrain = activeTerrainEntityId
            ?.let(world::getEntity)
            ?.takeIf { entity -> entity.get<TerrainComponent>() != null }
        if (activeTerrain != null) {
            val terrain = activeTerrain.get<TerrainComponent>()
            val path = terrain?.terrain?.path?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Active runtime terrain entityId=${activeTerrain.id} has no terrain asset path.")
            engine.logger.info(TAG) {
                "Runtime scene active terrain resolved entityId=${activeTerrain.id} name='${activeTerrain.name}' path='$path'"
            }
            return RuntimeTerrainSource(activeTerrain, path, "scene-active-terrain", finalSplatResolution ?: terrain.bakedTextureResolution)
        }

        if (descriptor != null && activeTerrainEntityId != null) {
            engine.logger.warn(TAG) {
                "Runtime scene activeTerrainEntityId=$activeTerrainEntityId was not found or has no TerrainComponent; trying first terrain entity."
            }
        } else if (descriptor != null) {
            engine.logger.warn(TAG) {
                "Runtime scene has no activeTerrainEntityId; trying first terrain entity before constructor fallback."
            }
        }

        val fallbackTerrain = terrainEntities.firstOrNull()
        if (fallbackTerrain != null) {
            val terrain = fallbackTerrain.get<TerrainComponent>()
            val path = terrain?.terrain?.path?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Runtime terrain entityId=${fallbackTerrain.id} has no terrain asset path.")
            engine.logger.warn(TAG) {
                "Runtime scene using first terrain entity as fallback entityId=${fallbackTerrain.id} name='${fallbackTerrain.name}' path='$path'"
            }
            return RuntimeTerrainSource(fallbackTerrain, path, "scene-first-terrain-fallback", finalSplatResolution ?: terrain.bakedTextureResolution)
        }

        val configuredTerrainPath = terrainFilePath?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException(
                "Runtime scene has no terrain entity and no krender.terrain.path was configured.",
            )
        val configuredFinalSplatResolution = finalSplatResolution
            ?: throw IllegalStateException(
                "Runtime scene has no terrain entity; set krender.terrain.splat.size when using krender.terrain.path.",
            )
        engine.logger.info(TAG) {
            "Runtime scene has no terrain entity; using configured terrain path='$configuredTerrainPath'."
        }
        return RuntimeTerrainSource(
            entity = null,
            path = configuredTerrainPath,
            reason = "configured-terrain",
            finalSplatResolution = configuredFinalSplatResolution,
        )
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

    private fun applyAmbientLight(descriptor: SceneDescriptor) {
        val ambient = world.createEntity("Runtime Ambient Light")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = descriptor.settings.ambientLightColor.copy(),
                intensity = descriptor.settings.ambientLightIntensity,
            ),
        )
        createRuntimeSun()
    }

    private fun createRuntimeLights() {
        createRuntimeSun()
        val ambient = world.createEntity("Runtime Ambient")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.44f, 0.5f, 0.58f),
                intensity = 0.6f,
            ),
        )
    }

    private fun createRuntimeSun() {
        val sun = world.createEntity("Runtime Sun")
        sun.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.95f, 0.84f),
                intensity = 1.3f,
                direction = Vec3(-0.45f, -0.8f, -0.3f),
            ),
        )
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

private data class RuntimeTerrainSource(
    val entity: Entity?,
    val path: String,
    val reason: String,
    val finalSplatResolution: Int?,
)
