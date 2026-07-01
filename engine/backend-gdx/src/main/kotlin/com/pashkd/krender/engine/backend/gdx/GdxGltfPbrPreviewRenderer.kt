@file:Suppress("ReturnCount", "TooGenericExceptionCaught")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.PbrPreviewView
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.SceneSkybox
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig
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
    private val gltfEnvironment = GdxGltfEnvironment(logger)

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
            val cacheKey = ModelCacheKey(command.entityId, command.model.path)
            val entry =
                entries.getOrPut(cacheKey) {
                    val scene = GltfScene(sceneAsset.scene)
                    val maxBones = sceneAsset.maxBones.coerceAtLeast(1)
                    val manager = createSceneManager(maxBones, settings)
                    manager.addScene(scene, false)
                    logger.info(TAG) { "Created PBR preview scene for '${command.model.path}'." }
                    PbrSceneEntry(
                        scene = scene,
                        manager = manager,
                        maxBones = maxBones,
                        shaderConfigKey = settings.shaderConfigKey(),
                    )
                }

            entry.ensureShaderConfiguration(settings)
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
        gltfEnvironment.dispose()
    }

    private fun configureEnvironment(
        entry: PbrSceneEntry,
        settings: PbrPreviewView,
    ) {
        val preset = gltfEnvironment.preset(settings.environmentPreset)
        val direction = pbrLightDirection(settings.directionalLightYawDegrees, settings.directionalLightPitchDegrees)
        val environmentState = resolveEnvironmentState(preset, settings)
        entry.manager.environment.clear()
        applyAmbientLight(entry, environmentState.intensity, environmentState.presetAmbientIntensity)
        applyDirectionalLight(entry, settings, direction)
        applyEnvironmentRotation(entry, settings)
        syncEnvironmentFallback(entry, settings, preset, direction, environmentState.intensity)
        applyEnvironmentMaps(entry, preset)
        applySkybox(entry, preset, settings)
    }

    private fun PbrSceneEntry.ensureShaderConfiguration(settings: PbrPreviewView) {
        val nextKey = settings.shaderConfigKey()
        if (shaderConfigKey == nextKey) return
        manager.skyBox = null
        manager.removeScene(scene)
        manager.dispose()
        manager = createSceneManager(maxBones, settings)
        manager.addScene(scene, false)
        shaderConfigKey = nextKey
        logger.info(TAG) {
            "Recreated PBR preview shaders gamma=${settings.gammaCorrection} sRGB=${settings.srgbTextures}."
        }
    }

    private fun createSceneManager(
        maxBones: Int,
        settings: PbrPreviewView,
    ): GltfSceneManager {
        val config =
            PBRShaderProvider.createDefaultConfig().apply {
                numBones = maxBones
                manualGammaCorrection = settings.gammaCorrection
                manualSRGB =
                    if (settings.srgbTextures) {
                        PBRShaderConfig.SRGB.ACCURATE
                    } else {
                        PBRShaderConfig.SRGB.NONE
                    }
            }
        return GltfSceneManager(
            PBRShaderProvider.createDefault(config),
            PBRShaderProvider.createDefaultDepth(maxBones),
        )
    }

    private fun syncEnvironmentFallback(
        entry: PbrSceneEntry,
        settings: PbrPreviewView,
        preset: GdxGltfEnvironmentPreset?,
        direction: Vector3,
        intensity: Float,
    ) {
        val needsProceduralFallback =
            preset?.irradiance == null ||
                preset.radiance == null ||
                (settings.showSkybox && preset.skybox == null)
        if (needsProceduralFallback) {
            entry.ensureIbl(direction, intensity.coerceAtLeast(0.01f))
        } else {
            entry.disposeProceduralEnvironment()
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
        disposeProceduralEnvironment()
        val light = DirectionalLightEx().set(Color.WHITE, direction, intensity)
        val builder = IBLBuilder.createOutdoor(light)
        try {
            envMap = builder.buildEnvMap(64)
            irradianceMap = builder.buildIrradianceMap(16)
            radianceMap = builder.buildRadianceMap(64)
            environmentKey = nextKey
            iblAvailable = true
            return true
        } catch (error: Throwable) {
            disposeProceduralEnvironment()
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

private data class ResolvedEnvironmentState(
    val intensity: Float,
    val presetAmbientIntensity: Float,
)

private fun GdxGltfPbrPreviewRenderer.resolveEnvironmentState(
    preset: GdxGltfEnvironmentPreset?,
    settings: PbrPreviewView,
): ResolvedEnvironmentState {
    val presetExposure = preset?.defaults?.exposure?.toFloat() ?: 1f
    val presetAmbientIntensity = preset?.defaults?.ambientIntensity?.toFloat() ?: 1f
    val intensity = (settings.environmentIntensity * settings.exposure * presetExposure).coerceAtLeast(0f)
    return ResolvedEnvironmentState(
        intensity = intensity,
        presetAmbientIntensity = presetAmbientIntensity,
    )
}

private fun GdxGltfPbrPreviewRenderer.applyAmbientLight(
    entry: PbrSceneEntry,
    intensity: Float,
    presetAmbientIntensity: Float,
) {
    entry.manager.environment.set(
        ColorAttribute(
            ColorAttribute.AmbientLight,
            0.08f * intensity * presetAmbientIntensity,
            0.09f * intensity * presetAmbientIntensity,
            0.1f * intensity * presetAmbientIntensity,
            1f,
        ),
    )
}

private fun GdxGltfPbrPreviewRenderer.applyDirectionalLight(
    entry: PbrSceneEntry,
    settings: PbrPreviewView,
    direction: Vector3,
) {
    if (!settings.directionalLightEnabled) return
    val lightColor = settings.directionalLightColor
    entry.manager.environment.add(
        DirectionalLightEx().set(
            Color(lightColor.r, lightColor.g, lightColor.b, lightColor.a),
            direction,
            settings.directionalLightIntensity.coerceAtLeast(0f),
        ),
    )
}

private fun GdxGltfPbrPreviewRenderer.applyEnvironmentRotation(
    entry: PbrSceneEntry,
    settings: PbrPreviewView,
) {
    entry.manager.environment.set(
        net.mgsx.gltf.scene3d.attributes.PBRMatrixAttribute.createEnvRotation(
            settings.environmentRotationDegrees,
        ),
    )
}

private fun GdxGltfPbrPreviewRenderer.applyEnvironmentMaps(
    entry: PbrSceneEntry,
    preset: GdxGltfEnvironmentPreset?,
) {
    val diffuseMap = preset?.irradiance ?: entry.irradianceMap
    val specularMap = preset?.radiance ?: entry.radianceMap
    diffuseMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createDiffuseEnv(map)) }
    specularMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createSpecularEnv(map)) }
    preset?.brdfLut?.let { lut ->
        entry.manager.environment.set(PBRTextureAttribute.createBRDFLookupTexture(lut))
    }
}

private fun GdxGltfPbrPreviewRenderer.applySkybox(
    entry: PbrSceneEntry,
    preset: GdxGltfEnvironmentPreset?,
    settings: PbrPreviewView,
) {
    val skyboxMap = if (settings.showSkybox) preset?.skybox ?: entry.envMap else null
    if (skyboxMap == null) {
        entry.manager.skyBox = null
        return
    }
    val skyboxKey = preset?.skybox?.let { "preset:${settings.environmentPreset}" } ?: "procedural"
    entry.ensureSceneSkybox(skyboxKey, skyboxMap)
    entry.manager.skyBox = entry.skybox
}

private data class PbrSceneEntry(
    val scene: GltfScene,
    var manager: GltfSceneManager,
    val maxBones: Int,
    var shaderConfigKey: PbrShaderConfigKey,
    var environmentKey: PbrEnvironmentKey? = null,
    var envMap: Cubemap? = null,
    var irradianceMap: Cubemap? = null,
    var radianceMap: Cubemap? = null,
    var skybox: SceneSkybox? = null,
    var skyboxKey: String? = null,
    var iblAvailable: Boolean = false,
) {
    fun dispose() {
        disposeSceneSkybox()
        disposeProceduralEnvironment()
        manager.dispose()
    }

    fun disposeProceduralEnvironment() {
        if (skyboxKey == "procedural") disposeSceneSkybox()
        envMap?.dispose()
        envMap = null
        irradianceMap?.dispose()
        irradianceMap = null
        radianceMap?.dispose()
        radianceMap = null
        environmentKey = null
        iblAvailable = false
    }

    fun ensureSceneSkybox(
        key: String,
        cubemap: Cubemap,
    ) {
        if (skyboxKey == key && skybox != null) return
        disposeSceneSkybox()
        skybox = SceneSkybox(cubemap)
        skyboxKey = key
    }

    fun disposeSceneSkybox() {
        skybox?.dispose()
        skybox = null
        skyboxKey = null
    }
}

private data class PbrEnvironmentKey(
    val intensity: String,
    val directionX: String,
    val directionY: String,
    val directionZ: String,
)

private data class PbrShaderConfigKey(
    val gammaCorrection: Boolean,
    val srgbTextures: Boolean,
)

private fun PbrPreviewView.shaderConfigKey(): PbrShaderConfigKey =
    PbrShaderConfigKey(
        gammaCorrection = gammaCorrection,
        srgbTextures = srgbTextures,
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
