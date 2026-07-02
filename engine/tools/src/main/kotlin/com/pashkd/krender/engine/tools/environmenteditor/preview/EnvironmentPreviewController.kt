package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.GltfRendererSettings
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorConfig
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorState

class EnvironmentPreviewController {
    val previewModel = AssetRef.model(EnvironmentEditorConfig.defaultPreviewModel.assetPath)
    val defaultBackgroundColor = EnvironmentEditorConfig.defaultBackgroundColor

    fun availability(
        env: EnvironmentAsset,
        preview: EnvironmentPreviewState,
    ): EnvironmentPreviewAvailability {
        val hasSkybox = env.generated.skybox?.faces?.isNotEmpty() == true
        val hasIrradiance = env.generated.irradiance != null
        val hasRadiance = env.generated.radiance?.mips?.isNotEmpty() == true
        val hasBrdfLut = env.generated.brdfLut != null
        val wantsSkyboxBackground = env.settings.backgroundMode == BackgroundMode.Skybox
        val effectiveShowSkybox = wantsSkyboxBackground && hasSkybox
        val warnings = buildList {
            if (!hasIrradiance && !hasRadiance && !hasBrdfLut) {
                add("Generated IBL maps are missing. Run Generate All before using full PBR preview.")
            }
            if (wantsSkyboxBackground && !hasSkybox) {
                add("Skybox map is missing. Preview uses neutral background.")
            }
            if (!hasIrradiance) {
                add("Irradiance map is missing. Diffuse IBL is unavailable.")
            }
            if (!hasRadiance) {
                add("Radiance map is missing. Specular reflections are unavailable.")
            }
            if (!hasBrdfLut) {
                add("BRDF LUT is missing. Specular BRDF may be incorrect.")
            }
        }
        val fallbackMode =
            when {
                warnings.isEmpty() -> "Full PBR environment preview"
                !hasSkybox && !hasIrradiance && !hasRadiance -> "Neutral background with direct-light fallback"
                !hasRadiance || !hasIrradiance || !hasBrdfLut -> "Partial IBL fallback"
                else -> "Skybox-disabled preview"
            }
        return EnvironmentPreviewAvailability(
            hasSkybox = hasSkybox,
            hasIrradiance = hasIrradiance,
            hasRadiance = hasRadiance,
            hasBrdfLut = hasBrdfLut,
            effectiveShowSkybox = effectiveShowSkybox,
            fallbackMode = fallbackMode,
            warnings = warnings,
        )
    }

    fun gltfRendererSettings(state: EnvironmentEditorState): GltfRendererSettings {
        val env = state.environment
        if (env == null) return GltfRendererSettings(enabled = true)
        val preview = state.previewState
        val availability = availability(env, preview)
        val settings = env.settings
        val backgroundColor =
            settings.backgroundColor?.let { color ->
                Color(color.r, color.g, color.b, color.a)
            } ?: defaultBackgroundColor.copy()
        return GltfRendererSettings(
            enabled = true,
            environmentPreset = state.manifestPath,
            environmentCacheKey =
                listOf(
                    state.manifestPath,
                    settings.exposure,
                    settings.rotationDegrees,
                    settings.skyboxIntensity,
                    settings.diffuseIntensity,
                    settings.specularIntensity,
                    settings.backgroundMode,
                    settings.backgroundColor?.r,
                    settings.backgroundColor?.g,
                    settings.backgroundColor?.b,
                    settings.backgroundColor?.a,
                    availability.hasSkybox,
                    availability.hasIrradiance,
                    availability.hasRadiance,
                    availability.hasBrdfLut,
                ).joinToString("|"),
            exposure = settings.exposure.coerceAtLeast(0f),
            backgroundVisible = settings.backgroundMode != BackgroundMode.None,
            backgroundMode = settings.backgroundMode,
            backgroundColor = backgroundColor,
            showSkybox = availability.effectiveShowSkybox,
            skyboxIntensity = settings.skyboxIntensity.coerceAtLeast(0f),
            ambientIntensity = settings.diffuseIntensity.coerceAtLeast(0f),
            environmentIntensity = settings.specularIntensity.coerceAtLeast(0f),
            environmentRotationDegrees = settings.rotationDegrees,
            directionalLightIntensity = if (availability.hasIrradiance || availability.hasRadiance) 0.3f else 0.85f,
        )
    }

    fun liveStatusMessage(
        env: EnvironmentAsset,
        preview: EnvironmentPreviewState,
    ): String {
        val availability = availability(env, preview)
        return buildString {
            append("Live preview uses the current editor state. ")
            append("Exposure %.2f, rotation %.1f deg, diffuse %.2f, specular %.2f.".format(
                env.settings.exposure,
                env.settings.rotationDegrees,
                env.settings.diffuseIntensity,
                env.settings.specularIntensity,
            ))
            append(" Background mode ${backgroundModeLabel(env.settings.backgroundMode)}.")
            if (availability.warnings.isNotEmpty()) {
                append(" ${availability.fallbackMode}.")
            }
        }
    }

    private fun backgroundModeLabel(mode: BackgroundMode): String =
        when (mode) {
            BackgroundMode.Skybox -> "Skybox"
            BackgroundMode.SolidColor -> "Solid Color"
            BackgroundMode.Transparent -> "Transparent"
            BackgroundMode.None -> "None"
        }

    companion object {
        val PreviewModelScale = Vec3(1.35f, 1.35f, 1.35f)
        val PreviewModelPosition = Vec3(0f, 0f, 0.35f)
    }
}

data class EnvironmentPreviewAvailability(
    val hasSkybox: Boolean,
    val hasIrradiance: Boolean,
    val hasRadiance: Boolean,
    val hasBrdfLut: Boolean,
    val effectiveShowSkybox: Boolean,
    val fallbackMode: String,
    val warnings: List<String>,
)
