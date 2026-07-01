package com.pashkd.krender.engine.assets.environment

import com.pashkd.krender.engine.scene.SceneFileService

/**
 * Default [EnvironmentService] implementation backed by [EnvironmentManifestIO]
 * and [EnvironmentValidator].
 *
 * This lives in core so editor tools can instantiate it directly from the
 * [SceneFileService] available on [com.pashkd.krender.engine.api.EngineContext].
 */
class DefaultEnvironmentService(
    private val fileService: SceneFileService,
) : EnvironmentService {

    override fun load(manifestPath: String): EnvironmentAsset =
        EnvironmentManifestIO.load(manifestPath, fileService)

    override fun save(asset: EnvironmentAsset) =
        EnvironmentManifestIO.save(asset, fileService)

    override fun validate(asset: EnvironmentAsset): EnvironmentValidationReport =
        EnvironmentValidator.validate(asset, fileService)
}
