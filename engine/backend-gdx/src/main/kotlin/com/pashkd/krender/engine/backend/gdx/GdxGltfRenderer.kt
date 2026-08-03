@file:Suppress("ReturnCount", "TooGenericExceptionCaught")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.math.Vector3
import com.pashkd.krender.engine.api.AnimationPlaybackView
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.GltfRendererSettings
import com.pashkd.krender.engine.api.Logger
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
 * glTF renderer backed by gdx-gltf SceneManager.
 */
internal class GdxGltfRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val entries = mutableMapOf<ModelCacheKey, GltfSceneEntry>()
    private val warnedKeys = mutableSetOf<String>()
    private val gltfEnvironment = GdxGltfEnvironment(logger)

    fun render(
        command: DrawModel,
        camera: Camera,
        meshPartFilter: (ModelInstance, Set<Int>?) -> Unit,
    ): Boolean {
        val settings = command.gltfRenderer?.takeIf { it.enabled } ?: return false
        if (!command.model.isGltf()) {
            warnOnce("not-gltf-${command.model.path}") {
                "glTF renderer is unavailable for '${command.model.path}' because the asset is not a glTF/glb model."
            }
            return false
        }

        assets.queue(command.model)
        val sceneAsset = assets.gltfScene(command.model)
        if (sceneAsset == null) {
            warnOnce("not-loaded-${command.model.path}") {
                "glTF renderer is unavailable because asset '${command.model.path}' is not loaded yet."
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
                    logger.info(TAG) { "Created glTF renderer scene for '${command.model.path}'." }
                    GltfSceneEntry(
                        scene = scene,
                        manager = manager,
                        maxBones = maxBones,
                        shaderConfigKey = settings.shaderConfigKey(),
                    )
                }

            entry.ensureShaderConfiguration(settings)
            applyTransform(entry.scene.modelInstance, command)
            applyAnimationPreview(entry.scene, command.animation)
            meshPartFilter(entry.scene.modelInstance, command.visibleMeshPartIndices)
            configureEnvironment(entry, settings)
            entry.manager.setCamera(camera)
            entry.manager.update(if (command.animation != null) 0f else Gdx.graphics.deltaTime)
            entry.manager.render()
            true
        } catch (error: Throwable) {
            warnOnce("error-${command.model.path}-${error::class.qualifiedName}") {
                "glTF renderer failed for '${command.model.path}': ${error.message ?: error::class.simpleName}"
            }
            false
        }
    }

    fun dispose() {
        entries.values.forEach(GltfSceneEntry::dispose)
        entries.clear()
        gltfEnvironment.dispose()
    }

    private fun configureEnvironment(
        entry: GltfSceneEntry,
        settings: GltfRendererSettings,
    ) {
        val preset =
            gltfEnvironment.preset(
                settings.environmentPreset,
                settings.environmentCacheKey ?: settings.environmentPreset,
                settings.environmentManifestText,
                settings.ambientIntensity.coerceAtLeast(0f),
                settings.environmentIntensity.coerceAtLeast(0f),
            )
        val direction = gltfLightDirection(settings.directionalLightYawDegrees, settings.directionalLightPitchDegrees)
        val environmentState = resolveEnvironmentState(settings)
        val fallbackPlan = fallbackPlan(preset, settings)
        entry.manager.environment.clear()
        applyAmbientLight(entry, environmentState.intensity)
        applyDirectionalLight(entry, settings, direction)
        applyEnvironmentRotation(entry, settings)
        syncEnvironmentFallback(entry, direction, environmentState.intensity, fallbackPlan)
        applyEnvironmentMaps(entry, preset)
        applySkybox(entry, preset, settings)
    }

    private fun GltfSceneEntry.ensureShaderConfiguration(settings: GltfRendererSettings) {
        val nextKey = settings.shaderConfigKey()
        if (shaderConfigKey == nextKey) return
        manager.skyBox = null
        manager.removeScene(scene)
        manager.dispose()
        manager = createSceneManager(maxBones, settings)
        manager.addScene(scene, false)
        shaderConfigKey = nextKey
        logger.info(TAG) {
            "Recreated glTF renderer shaders gamma=${settings.gammaCorrection} sRGB=${settings.srgbTextures}."
        }
    }

    private fun createSceneManager(
        maxBones: Int,
        settings: GltfRendererSettings,
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
        entry: GltfSceneEntry,
        direction: Vector3,
        intensity: Float,
        fallbackPlan: ResolvedEnvironmentFallbackPlan,
    ) {
        if (fallbackPlan.requiresProceduralFallback) {
            entry.ensureIbl(direction, intensity.coerceAtLeast(0.01f))
        } else {
            entry.disposeProceduralEnvironment()
        }
    }

    private fun fallbackPlan(
        preset: GdxGltfEnvironmentPreset?,
        settings: GltfRendererSettings,
    ): ResolvedEnvironmentFallbackPlan {
        val hasSkybox = preset?.skybox != null
        val hasIrradiance = preset?.irradiance != null
        val hasRadiance = preset?.radiance != null
        return when {
            preset == null ->
                ResolvedEnvironmentFallbackPlan(
                    requiresProceduralFallback = true,
                    reason = "Preset unavailable; use procedural fallback if possible.",
                )
            hasSkybox && (!hasIrradiance || !hasRadiance) ->
                ResolvedEnvironmentFallbackPlan(
                    requiresProceduralFallback = false,
                    reason = "Skybox is available; skip procedural fallback even though irradiance/radiance are missing.",
                )
            !hasIrradiance || !hasRadiance ->
                ResolvedEnvironmentFallbackPlan(
                    requiresProceduralFallback = true,
                    reason = "IBL maps are incomplete and no runtime skybox is available.",
                )
            settings.showSkybox && !hasSkybox ->
                ResolvedEnvironmentFallbackPlan(
                    requiresProceduralFallback = true,
                    reason = "Skybox background is requested but no runtime skybox is available.",
                )
            else ->
                ResolvedEnvironmentFallbackPlan(
                    requiresProceduralFallback = false,
                    reason = "Runtime environment resources are sufficient; procedural fallback is not needed.",
                )
        }
    }

    private fun GltfSceneEntry.ensureIbl(
        direction: Vector3,
        intensity: Float,
    ): Boolean {
        val nextKey =
            GltfEnvironmentFallbackKey(
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
                "glTF renderer IBL/skybox is unavailable; continuing with direct lighting only: " +
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
        private const val TAG = "GdxGltfRenderer"
    }

    private fun applyAnimationPreview(
        scene: GltfScene,
        preview: AnimationPlaybackView?,
    ) {
        applyAnimationPreview(scene.modelInstance, scene.animationController, preview)
    }

    private fun applyAnimationPreview(
        instance: ModelInstance,
        controller: AnimationController?,
        preview: AnimationPlaybackView?,
    ) {
        if (controller == null || instance.animations.isEmpty) return
        val animationName = preview?.animationName
        if (animationName.isNullOrBlank()) {
            controller.setAnimation(null as String?)
            controller.update(0f)
            return
        }
        val animation =
            instance.getAnimation(animationName) ?: run {
                controller.setAnimation(null as String?)
                controller.update(0f)
                return
            }
        controller.paused = false
        controller.setAnimation(animationName, if (preview.loop) -1 else 1, 1f, null)
        controller.current?.time = normalizedAnimationTime(animation, preview.timeSeconds, preview.loop)
        controller.update(0f)
    }

    private fun applyTransform(
        instance: ModelInstance,
        command: DrawModel,
    ) {
        val transform = command.transform
        instance.transform.idt()
        instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
    }
}

private data class ResolvedEnvironmentState(
    val intensity: Float,
)

private data class ResolvedEnvironmentFallbackPlan(
    val requiresProceduralFallback: Boolean,
    val reason: String,
)

private fun GdxGltfRenderer.resolveEnvironmentState(settings: GltfRendererSettings): ResolvedEnvironmentState {
    val intensity = settings.exposure.coerceAtLeast(0f)
    return ResolvedEnvironmentState(
        intensity = intensity,
    )
}

private fun GdxGltfRenderer.applyAmbientLight(
    entry: GltfSceneEntry,
    intensity: Float,
) {
    entry.manager.environment.set(
        ColorAttribute(
            ColorAttribute.AmbientLight,
            0.08f * intensity,
            0.09f * intensity,
            0.1f * intensity,
            1f,
        ),
    )
}

private fun GdxGltfRenderer.applyDirectionalLight(
    entry: GltfSceneEntry,
    settings: GltfRendererSettings,
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

private fun GdxGltfRenderer.applyEnvironmentRotation(
    entry: GltfSceneEntry,
    settings: GltfRendererSettings,
) {
    entry.manager.environment.set(
        net.mgsx.gltf.scene3d.attributes.PBRMatrixAttribute.createEnvRotation(
            settings.environmentRotationDegrees,
        ),
    )
}

private fun GdxGltfRenderer.applyEnvironmentMaps(
    entry: GltfSceneEntry,
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

private fun GdxGltfRenderer.applySkybox(
    entry: GltfSceneEntry,
    preset: GdxGltfEnvironmentPreset?,
    settings: GltfRendererSettings,
) {
    val skyboxMap = if (settings.showSkybox) preset?.skybox ?: entry.envMap else null
    if (skyboxMap == null) {
        entry.manager.skyBox = null
        return
    }
    val skyboxKey =
        preset?.skybox?.let {
            "preset:${settings.environmentCacheKey ?: settings.environmentPreset}"
        } ?: "procedural"
    entry.ensureSceneSkybox(skyboxKey, skyboxMap)
    entry.skybox?.color?.set(
        settings.skyboxIntensity.coerceAtLeast(0f),
        settings.skyboxIntensity.coerceAtLeast(0f),
        settings.skyboxIntensity.coerceAtLeast(0f),
        1f,
    )
    entry.manager.skyBox = entry.skybox
}

private data class GltfSceneEntry(
    val scene: GltfScene,
    var manager: GltfSceneManager,
    val maxBones: Int,
    var shaderConfigKey: GltfShaderConfigKey,
    var environmentKey: GltfEnvironmentFallbackKey? = null,
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

private data class GltfEnvironmentFallbackKey(
    val intensity: String,
    val directionX: String,
    val directionY: String,
    val directionZ: String,
)

private data class GltfShaderConfigKey(
    val gammaCorrection: Boolean,
    val srgbTextures: Boolean,
)

private fun GltfRendererSettings.shaderConfigKey(): GltfShaderConfigKey =
    GltfShaderConfigKey(
        gammaCorrection = gammaCorrection,
        srgbTextures = srgbTextures,
    )

private fun gltfLightDirection(
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
