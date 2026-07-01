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

    fun preset(nameOrPath: String): GdxGltfEnvironmentPreset? =
        presets.getOrPut(nameOrPath) {
            resolver.resolve(nameOrPath)?.let(assetLoader::loadPreset)
        }

    override fun dispose() {
        presets.values.filterNotNull().forEach(GdxGltfEnvironmentPreset::dispose)
        presets.clear()
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
