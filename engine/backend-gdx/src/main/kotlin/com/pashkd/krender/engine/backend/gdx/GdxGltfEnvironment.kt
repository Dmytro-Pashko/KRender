package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentDefaults

internal class GdxGltfEnvironment(
    logger: Logger,
) : Disposable {
    private val resolver = GdxHdrEnvironmentResolver(logger)
    private val assetLoader = GdxGltfEnvironmentAssetLoader(logger)
    private val presets = mutableMapOf<String, GdxGltfEnvironmentPreset?>()
    private val presetOwners = mutableMapOf<String, String>()

    fun preset(
        nameOrPath: String,
        cacheKey: String = nameOrPath,
        manifestText: String? = null,
    ): GdxGltfEnvironmentPreset? {
        invalidateStaleEntries(nameOrPath, cacheKey)
        presetOwners[cacheKey] = nameOrPath
        return presets.getOrPut(cacheKey) {
            resolver.resolve(nameOrPath, manifestText)?.let(assetLoader::loadPreset)
        }
    }

    private fun invalidateStaleEntries(
        nameOrPath: String,
        activeCacheKey: String,
    ) {
        val staleKeys =
            presetOwners.entries
                .filter { (cacheKey, owner) -> owner == nameOrPath && cacheKey != activeCacheKey }
                .map { entry -> entry.key }
        staleKeys.forEach { cacheKey ->
            presets.remove(cacheKey)?.dispose()
            presetOwners.remove(cacheKey)
        }
    }

    override fun dispose() {
        presets.values.filterNotNull().forEach(GdxGltfEnvironmentPreset::dispose)
        presets.clear()
        presetOwners.clear()
    }
}

internal data class GdxGltfEnvironmentPreset(
    val name: String,
    val defaults: HdrEnvironmentDefaults,
    val skybox: Cubemap?,
    val irradiance: Cubemap?,
    val radiance: Cubemap?,
    val brdfLut: Texture?,
) : Disposable {
    override fun dispose() {
        skybox?.dispose()
        irradiance?.dispose()
        radiance?.dispose()
        brdfLut?.dispose()
    }
}
