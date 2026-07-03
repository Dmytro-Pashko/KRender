package com.pashkd.krender.engine.assets.environment

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.GltfRendererSettings

/**
 * Converts a persisted [EnvironmentAsset] into runtime glTF/PBR renderer settings.
 *
 * Runtime scenes, Scene Editor, and other non-editor preview flows share this mapping so
 * `.environment.json` remains the single source of truth for environment behavior.
 */
object EnvironmentGltfRendererSettingsFactory {
    fun create(asset: EnvironmentAsset): GltfRendererSettings {
        val settings = asset.settings
        return GltfRendererSettings(
            enabled = true,
            environmentPreset = asset.manifestPath,
            environmentCacheKey = EnvironmentRuntimeCacheKeyFactory.create(asset),
            exposure = settings.exposure.coerceAtLeast(0f),
            backgroundMode = settings.backgroundMode,
            backgroundColor = (settings.backgroundColor ?: DefaultBackgroundColor).toRenderColor(),
            showSkybox = settings.backgroundMode == BackgroundMode.Skybox && asset.skybox?.faces?.isNotEmpty() == true,
            skyboxIntensity = settings.skyboxIntensity.coerceIn(0f, 1f),
            ambientIntensity = settings.diffuseIntensity.coerceAtLeast(0f),
            environmentIntensity = settings.specularIntensity.coerceIn(0f, 1f),
            environmentRotationDegrees = settings.rotationDegrees,
        )
    }

    private val DefaultBackgroundColor = EnvironmentColor(0.08f, 0.09f, 0.11f, 1f)
}

private fun EnvironmentColor.toRenderColor(): Color = Color(r, g, b, a)
