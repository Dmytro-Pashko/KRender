package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.GltfRendererSettings
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.CubemapResource
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentColor
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import com.pashkd.krender.engine.assets.environment.EnvironmentRuntimeCacheKeyFactory
import com.pashkd.krender.engine.assets.environment.EnvironmentSerializer
import com.pashkd.krender.engine.assets.environment.RadianceMipChain
import com.pashkd.krender.engine.assets.environment.SkyboxResourceSet
import com.pashkd.krender.engine.assets.environment.TextureResourceRef
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorConfig
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorState

/**
 * Adapts the currently edited Environment to the shared glTF renderer contract.
 *
 * The controller is backend-neutral: it resolves manifest availability and emits
 * [GltfRendererSettings], while cubemap loading and PBR rendering stay in `backend-gdx`.
 */
class EnvironmentPreviewController(
    private val fileService: SceneFileService,
) {
    val previewModel = AssetRef.model(EnvironmentEditorConfig.defaultPreviewModel.assetPath)

    fun availability(environment: Environment): EnvironmentPreviewAvailability {
        val resources = EnvironmentPreviewAvailability.from(environment, fileService)
        val wantsSkybox = environment.settings.backgroundMode == BackgroundMode.Skybox
        val warnings = resourceWarnings(resources, wantsSkybox)
        return resources.copy(
            effectiveShowSkybox = wantsSkybox && resources.hasSkybox,
            fallbackMode = fallbackMode(resources, warnings),
            warnings = warnings,
        )
    }

    fun gltfRendererSettings(state: EnvironmentEditorState): GltfRendererSettings {
        val environment = state.environment ?: return GltfRendererSettings(enabled = true)
        val availability = availability(environment)
        val settings = environment.settings
        return GltfRendererSettings(
            enabled = true,
            environmentPreset = state.manifestPath,
            environmentCacheKey = rendererCacheKey(environment, state.environmentCacheRevision),
            environmentManifestText = EnvironmentSerializer.encode(environment),
            exposure = settings.exposure.coerceAtLeast(0f),
            backgroundMode = settings.backgroundMode,
            backgroundColor = (settings.backgroundColor ?: EnvironmentEditorConfig.defaultBackgroundColor).toRenderColor(),
            showSkybox = availability.effectiveShowSkybox,
            skyboxIntensity = settings.skyboxIntensity.coerceIn(0f, 1f),
            ambientIntensity = settings.diffuseIntensity.coerceAtLeast(0f),
            environmentIntensity = settings.specularIntensity.coerceIn(0f, 1f),
            environmentRotationDegrees = settings.rotationDegrees,
            directionalLightIntensity = if (availability.hasIblLighting) 0.3f else 0.85f,
        )
    }

    private fun resourceWarnings(
        availability: EnvironmentPreviewAvailability,
        wantsSkybox: Boolean,
    ): List<String> =
        buildList {
            if (!availability.hasIblLighting && !availability.hasBrdfLut) {
                add("Environment IBL resources are missing. Add skybox, irradiance, radiance, and BRDF LUT files for the full PBR preview.")
            }
            if (wantsSkybox && !availability.hasSkybox) {
                add("Skybox map is missing. Preview uses a neutral background.")
            }
            if (!availability.hasIrradiance) add("Irradiance map is missing. Diffuse IBL is unavailable.")
            if (!availability.hasRadiance) add("Radiance map is missing. Specular reflections are unavailable.")
            if (!availability.hasBrdfLut) add("BRDF LUT is missing. Specular BRDF may be incorrect.")
        }

    private fun fallbackMode(
        availability: EnvironmentPreviewAvailability,
        warnings: List<String>,
    ): String =
        when {
            warnings.isEmpty() -> "Full PBR environment preview"
            !availability.hasSkybox && !availability.hasIblLighting -> "Neutral background with direct-light fallback"
            !availability.hasIblLighting || !availability.hasBrdfLut -> "Partial IBL fallback"
            else -> "Skybox-disabled preview"
        }

    private fun rendererCacheKey(
        environment: Environment,
        revision: Long,
    ): String = EnvironmentRuntimeCacheKeyFactory.create(environment, revision)

    companion object {
        val PreviewModelScale = Vec3(1.35f, 1.35f, 1.35f)
        val PreviewModelPosition = Vec3(0f, 0f, 0.35f)
    }
}

/**
 * Availability snapshot used by both preview rendering and status UI.
 *
 * The flags describe resource availability on disk, not successful GPU uploads;
 * backend load failures are reported separately by renderer logs.
 */
data class EnvironmentPreviewAvailability(
    val hasSkybox: Boolean,
    val hasIrradiance: Boolean,
    val hasRadiance: Boolean,
    val hasBrdfLut: Boolean,
    val effectiveShowSkybox: Boolean = false,
    val fallbackMode: String = "",
    val warnings: List<String> = emptyList(),
) {
    val hasIblLighting: Boolean
        get() = hasIrradiance || hasRadiance

    companion object {
        fun from(
            environment: Environment,
            fileService: SceneFileService,
        ): EnvironmentPreviewAvailability =
            EnvironmentPreviewAvailability(
                hasSkybox = environment.skybox.exists(environment.manifestPath, fileService),
                hasIrradiance = environment.irradiance.exists(environment.manifestPath, fileService),
                hasRadiance = environment.radiance.exists(environment.manifestPath, fileService),
                hasBrdfLut = environment.brdfLut.exists(environment.manifestPath, fileService),
            )
    }
}

private fun EnvironmentColor.toRenderColor(): Color = Color(r, g, b, a)

private fun SkyboxResourceSet?.exists(
    manifestPath: String,
    fileService: SceneFileService,
): Boolean =
    this
        ?.faces
        ?.takeIf(Map<String, String>::isNotEmpty)
        ?.values
        ?.all { path -> fileService.exists(EnvironmentPathResolver.resolvePath(manifestPath, path)) } == true

private fun CubemapResource?.exists(
    manifestPath: String,
    fileService: SceneFileService,
): Boolean {
    val path = this?.path ?: return false
    return EnvironmentPathResolver.resolveCubemapFacePaths(manifestPath, path).all(fileService::exists)
}

private fun RadianceMipChain?.exists(
    manifestPath: String,
    fileService: SceneFileService,
): Boolean =
    this
        ?.mips
        ?.takeIf(List<com.pashkd.krender.engine.assets.environment.RadianceMip>::isNotEmpty)
        ?.all { mip -> EnvironmentPathResolver.resolveCubemapFacePaths(manifestPath, mip.path).all(fileService::exists) } == true

private fun TextureResourceRef?.exists(
    manifestPath: String,
    fileService: SceneFileService,
): Boolean {
    val path = this?.path ?: return false
    return fileService.exists(EnvironmentPathResolver.resolvePath(manifestPath, path))
}
