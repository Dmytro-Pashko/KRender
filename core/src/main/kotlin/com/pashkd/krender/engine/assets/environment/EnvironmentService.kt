package com.pashkd.krender.engine.assets.environment

/**
 * Service abstraction for loading, saving, and validating Environment assets.
 *
 * Editor panels and tool code should use this interface rather than calling
 * [EnvironmentManifestIO] or [EnvironmentValidator] directly. This allows
 * future replacement with async or backend-delegating implementations.
 */
interface EnvironmentService {
    fun load(manifestPath: String): EnvironmentAsset

    fun save(asset: EnvironmentAsset)

    fun validate(asset: EnvironmentAsset): EnvironmentValidationReport
}
