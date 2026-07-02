package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.GltfRendererSettings
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.assets.environment.EnvironmentColor
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorConfig
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorState
import com.pashkd.krender.engine.tools.environmenteditor.displayName

/**
 * Adapts the currently edited Environment to the shared glTF renderer contract.
 *
 * The controller is backend-neutral: it resolves manifest availability and emits
 * [GltfRendererSettings], while cubemap loading and PBR rendering stay in `backend-gdx`.
 */
class EnvironmentPreviewController {
    val previewModel = AssetRef.model(EnvironmentEditorConfig.defaultPreviewModel.assetPath)

    fun availability(environment: EnvironmentAsset): EnvironmentPreviewAvailability {
        val resources = EnvironmentPreviewAvailability.from(environment)
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
            environmentCacheKey = rendererCacheKey(state.manifestPath, environment, availability),
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

    fun liveStatusMessage(environment: EnvironmentAsset): String {
        val availability = availability(environment)
        val settings = environment.settings
        return buildString {
            append("Live preview uses the current editor state. ")
            append(
                "Exposure %.2f, rotation %.1f deg, diffuse %.2f, specular %.2f. ".format(
                    settings.exposure,
                    settings.rotationDegrees,
                    settings.diffuseIntensity,
                    settings.specularIntensity,
                ),
            )
            append("Background mode ${settings.backgroundMode.displayName}.")
            if (availability.warnings.isNotEmpty()) append(" ${availability.fallbackMode}.")
        }
    }

    private fun resourceWarnings(
        availability: EnvironmentPreviewAvailability,
        wantsSkybox: Boolean,
    ): List<String> =
        buildList {
            if (!availability.hasIblLighting && !availability.hasBrdfLut) {
                add("Generated IBL maps are missing. Generate them before using the full PBR preview.")
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
        manifestPath: String,
        environment: EnvironmentAsset,
        availability: EnvironmentPreviewAvailability,
    ): String {
        val settings = environment.settings
        return listOf(
            manifestPath,
            settings.exposure,
            settings.rotationDegrees,
            settings.skyboxIntensity,
            settings.diffuseIntensity,
            settings.specularIntensity,
            settings.backgroundMode,
            settings.backgroundColor,
            availability.hasSkybox,
            availability.hasIrradiance,
            availability.hasRadiance,
            availability.hasBrdfLut,
        ).joinToString("|")
    }

    companion object {
        val PreviewModelScale = Vec3(1.35f, 1.35f, 1.35f)
        val PreviewModelPosition = Vec3(0f, 0f, 0.35f)
    }
}

/**
 * Availability snapshot used by both preview rendering and status UI.
 *
 * The flags describe manifest references, not successful GPU uploads; backend load
 * failures are reported separately by renderer logs.
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
        fun from(environment: EnvironmentAsset): EnvironmentPreviewAvailability =
            EnvironmentPreviewAvailability(
                hasSkybox =
                    environment.generated.skybox
                        ?.faces
                        ?.isNotEmpty() == true,
                hasIrradiance = environment.generated.irradiance != null,
                hasRadiance =
                    environment.generated.radiance
                        ?.mips
                        ?.isNotEmpty() == true,
                hasBrdfLut = environment.generated.brdfLut != null,
            )
    }
}

private fun EnvironmentColor.toRenderColor(): Color = Color(r, g, b, a)
