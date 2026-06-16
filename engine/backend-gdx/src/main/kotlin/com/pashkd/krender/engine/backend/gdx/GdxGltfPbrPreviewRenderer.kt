@file:Suppress("ReturnCount", "TooGenericExceptionCaught")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.PbrPreviewView
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.SceneSkybox
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider
import net.mgsx.gltf.scene3d.utils.IBLBuilder
import java.lang.Math
import kotlin.math.cos
import kotlin.math.sin
import net.mgsx.gltf.scene3d.scene.Scene as GltfScene
import net.mgsx.gltf.scene3d.scene.SceneManager as GltfSceneManager

/**
 * glTF PBR preview renderer backed by gdx-gltf SceneManager.
 */
internal class GdxGltfPbrPreviewRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val entries = mutableMapOf<ModelCacheKey, PbrSceneEntry>()
    private val warnedKeys = mutableSetOf<String>()

    fun render(
        command: DrawModel,
        camera: Camera,
        meshPartFilter: (ModelInstance, Set<Int>?) -> Unit,
    ): Boolean {
        val settings = command.pbrPreview?.takeIf { it.enabled } ?: return false
        if (!command.model.isGltf()) {
            warnOnce("not-gltf-${command.model.path}") {
                "PBR preview unavailable: '${command.model.path}' is not a glTF/glb model."
            }
            return false
        }

        assets.queue(command.model)
        val sceneAsset = assets.gltfScene(command.model)
        if (sceneAsset == null) {
            warnOnce("not-loaded-${command.model.path}") {
                "PBR preview unavailable: glTF asset '${command.model.path}' is not loaded yet."
            }
            return false
        }

        return try {
            settings.skyboxTexture?.let { ref -> assets.queue(AssetRef.texture(ref.id)) }
            val cacheKey = ModelCacheKey(command.entityId, command.model.path)
            val entry =
                entries.getOrPut(cacheKey) {
                    val scene = GltfScene(sceneAsset.scene)
                    val manager =
                        GltfSceneManager(
                            PBRShaderProvider.createDefault(sceneAsset.maxBones.coerceAtLeast(1)),
                            PBRShaderProvider.createDefaultDepth(sceneAsset.maxBones.coerceAtLeast(1)),
                        )
                    manager.addScene(scene, false)
                    logger.info(TAG) { "Created PBR preview scene for '${command.model.path}'." }
                    PbrSceneEntry(scene = scene, manager = manager)
                }

            applyTransform(entry.scene.modelInstance, command)
            meshPartFilter(entry.scene.modelInstance, command.visibleMeshPartIndices)
            configureEnvironment(entry, settings)
            entry.manager.setCamera(camera)
            entry.manager.update(Gdx.graphics.deltaTime)
            entry.manager.render()
            true
        } catch (error: Throwable) {
            warnOnce("error-${command.model.path}-${error::class.qualifiedName}") {
                "PBR preview unavailable for '${command.model.path}': ${error.message ?: error::class.simpleName}"
            }
            false
        }
    }

    fun dispose() {
        entries.values.forEach(PbrSceneEntry::dispose)
        entries.clear()
    }

    private fun configureEnvironment(
        entry: PbrSceneEntry,
        settings: PbrPreviewView,
    ) {
        val direction = pbrLightDirection(settings.directionalLightYawDegrees, settings.directionalLightPitchDegrees)
        val intensity = (settings.environmentIntensity * settings.exposure).coerceAtLeast(0f)
        entry.manager.environment.clear()
        entry.manager.environment.set(
            ColorAttribute(
                ColorAttribute.AmbientLight,
                0.08f * intensity,
                0.09f * intensity,
                0.1f * intensity,
                1f,
            ),
        )
        if (settings.directionalLightEnabled) {
            entry.manager.environment.add(
                DirectionalLightEx().set(
                    Color.WHITE,
                    direction,
                    intensity.coerceAtLeast(0.01f),
                ),
            )
        }
        val skyboxCubemap =
            settings.skyboxTexture
                ?.id
                ?.takeIf { path -> settings.showSkybox && path.isNotBlank() }
                ?.let { path -> entry.ensureSkyboxCubemap(path) }
        if (skyboxCubemap != null) {
            entry.manager.environment.set(PBRCubemapAttribute.createDiffuseEnv(skyboxCubemap))
            entry.manager.environment.set(PBRCubemapAttribute.createSpecularEnv(skyboxCubemap))
            entry.manager.skyBox = entry.skybox
            return
        } else {
            entry.manager.skyBox = null
        }

        if (settings.showSkybox) {
            val iblAvailable = entry.ensureIbl(direction, intensity.coerceAtLeast(0.01f))
            if (iblAvailable) {
                entry.irradianceMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createDiffuseEnv(map)) }
                entry.radianceMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createSpecularEnv(map)) }
                entry.manager.skyBox = entry.skybox
            }
        }
    }

    private fun PbrSceneEntry.ensureSkyboxCubemap(path: String): Cubemap? {
        if (skyboxTexturePath == path && skyboxCubemap != null) return skyboxCubemap
        disposeSkyboxCubemap()
        return try {
            val cubemap = cubemapFromSingleTexture(path)
            try {
                SceneSkybox.enableMipmaps(cubemap)
            } catch (error: Throwable) {
                warnOnce("skybox-mipmaps-$path-${error::class.qualifiedName}") {
                    "PBR preview skybox mipmaps unavailable; roughness reflections may be less accurate: " +
                        (error.message ?: error::class.simpleName)
                }
            }
            skyboxTexturePath = path
            skyboxCubemap = cubemap
            skybox = SceneSkybox(cubemap)
            cubemap
        } catch (error: Throwable) {
            disposeSkyboxCubemap()
            warnOnce("skybox-texture-$path-${error::class.qualifiedName}") {
                "PBR preview skybox texture '$path' unavailable; continuing without asset skybox: " +
                    (error.message ?: error::class.simpleName)
            }
            null
        }
    }

    private fun PbrSceneEntry.ensureIbl(
        direction: Vector3,
        intensity: Float,
    ): Boolean {
        val nextKey =
            PbrEnvironmentKey(
                intensity = "%.3f".format(intensity),
                directionX = "%.3f".format(direction.x),
                directionY = "%.3f".format(direction.y),
                directionZ = "%.3f".format(direction.z),
            )
        if (environmentKey == nextKey) return iblAvailable
        disposeEnvironment()
        val light = DirectionalLightEx().set(Color.WHITE, direction, intensity)
        val builder = IBLBuilder.createOutdoor(light)
        try {
            envMap = builder.buildEnvMap(64)
            irradianceMap = builder.buildIrradianceMap(16)
            radianceMap = builder.buildRadianceMap(64)
            skybox = envMap?.let(::SceneSkybox)
            environmentKey = nextKey
            iblAvailable = true
            return true
        } catch (error: Throwable) {
            disposeEnvironment()
            environmentKey = nextKey
            iblAvailable = false
            warnOnce("ibl-unavailable-${error::class.qualifiedName}") {
                "PBR preview IBL/skybox unavailable; continuing with direct lighting only: " +
                    (error.message ?: error::class.simpleName)
            }
            return false
        } finally {
            builder.dispose()
        }
    }

    private fun warnOnce(
        key: String,
        message: () -> String,
    ) {
        if (warnedKeys.add(key)) {
            logger.warn(TAG, message = message)
        }
    }

    companion object {
        private const val TAG = "GdxGltfPbrPreviewRenderer"
    }
}

private data class PbrSceneEntry(
    val scene: GltfScene,
    val manager: GltfSceneManager,
    var environmentKey: PbrEnvironmentKey? = null,
    var envMap: Cubemap? = null,
    var irradianceMap: Cubemap? = null,
    var radianceMap: Cubemap? = null,
    var skyboxTexturePath: String? = null,
    var skyboxCubemap: Cubemap? = null,
    var skybox: SceneSkybox? = null,
    var iblAvailable: Boolean = false,
) {
    fun dispose() {
        disposeEnvironment()
        disposeSkyboxCubemap()
        manager.dispose()
    }

    fun disposeEnvironment() {
        if (skyboxCubemap == null) {
            skybox?.dispose()
            skybox = null
        }
        envMap?.dispose()
        envMap = null
        irradianceMap?.dispose()
        irradianceMap = null
        radianceMap?.dispose()
        radianceMap = null
        iblAvailable = false
    }

    fun disposeSkyboxCubemap() {
        skybox?.dispose()
        skybox = null
        skyboxCubemap?.dispose()
        skyboxCubemap = null
        skyboxTexturePath = null
    }
}

private data class PbrEnvironmentKey(
    val intensity: String,
    val directionX: String,
    val directionY: String,
    val directionZ: String,
)

private fun pbrLightDirection(
    yawDegrees: Float,
    pitchDegrees: Float,
): Vector3 {
    val yaw = Math.toRadians(yawDegrees.toDouble())
    val pitch = Math.toRadians(pitchDegrees.toDouble())
    val x = (cos(pitch) * cos(yaw)).toFloat()
    val y = sin(pitch).toFloat()
    val z = (cos(pitch) * sin(yaw)).toFloat()
    return Vector3(-x, -y, -z).nor()
}
