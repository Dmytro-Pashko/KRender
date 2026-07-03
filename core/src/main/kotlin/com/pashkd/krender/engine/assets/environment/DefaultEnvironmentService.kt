package com.pashkd.krender.engine.assets.environment

import com.pashkd.krender.engine.scene.SceneFileService

/**
 * Default [EnvironmentService] implementation backed by [EnvironmentLoader]
 * and [EnvironmentValidator].
 *
 * This lives in core so editor tools can instantiate it directly from the
 * [SceneFileService] available on [com.pashkd.krender.engine.api.EngineContext].
 */
class DefaultEnvironmentService(
    private val fileService: SceneFileService,
) : EnvironmentService {
    override fun load(manifestPath: String): Environment = EnvironmentLoader.load(manifestPath, fileService)

    override fun save(environment: Environment) = EnvironmentLoader.save(environment, fileService)

    override fun validate(environment: Environment): EnvironmentValidationReport = EnvironmentValidator.validate(environment, fileService)
}
