package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.Texture
import com.pashkd.krender.engine.api.Logger

internal class GdxGltfEnvironmentAssetLoader(
    private val logger: Logger,
) {
    fun loadPreset(resolved: GdxResolvedHdrEnvironment): GdxGltfEnvironmentPreset =
        GdxGltfEnvironmentPreset(
            name = resolved.preset,
            defaults = resolved.defaults,
            skybox = loadCubemap(resolved, "skybox", resolved.skyboxFaces),
            irradiance = loadCubemap(resolved, "irradiance", resolved.irradianceFaces),
            radiance = loadRadiance(resolved),
            brdfLut = loadBrdfLut(resolved),
        )

    private fun loadCubemap(
        resolved: GdxResolvedHdrEnvironment,
        label: String,
        faces: Map<String, String>,
    ): Cubemap? {
        if (faces.isEmpty() || faces.values.any { !Gdx.files.internal(it).exists() }) {
            logger.warn(TAG) { "HDR environment '${resolved.preset}' has missing $label faces." }
            return null
        }
        return try {
            GdxHdrCubemapLoader.loadFaces(faces)
        } catch (error: Throwable) {
            logger.warn(TAG, error) { "Failed to load $label cubemap for '${resolved.preset}'." }
            null
        }
    }

    private fun loadRadiance(resolved: GdxResolvedHdrEnvironment): Cubemap? {
        val faces = resolved.radianceFaces
        if (faces.isEmpty() || faces.values.flatMap { it.values }.any { !Gdx.files.internal(it).exists() }) {
            logger.warn(TAG) { "HDR environment '${resolved.preset}' has missing radiance mip faces." }
            return null
        }
        return try {
            GdxHdrCubemapLoader.loadMipFaces(faces)
        } catch (error: Throwable) {
            logger.warn(TAG, error) { "Failed to load radiance cubemap for '${resolved.preset}'." }
            null
        }
    }

    private fun loadBrdfLut(resolved: GdxResolvedHdrEnvironment): Texture? {
        val location = resolved.brdfLut ?: return null
        return try {
            Texture(location.file()).also { texture ->
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
            }
        } catch (error: Throwable) {
            logger.warn(TAG, error) { "Failed to load BRDF LUT for '${resolved.preset}'." }
            null
        }
    }

    companion object {
        private const val TAG = "GdxGltfEnvironmentAssetLoader"
    }
}
