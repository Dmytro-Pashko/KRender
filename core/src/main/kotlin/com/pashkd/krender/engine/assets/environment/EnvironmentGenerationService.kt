package com.pashkd.krender.engine.assets.environment

/**
 * Service interface for generating IBL resources from an environment source.
 *
 * For MVP, the default implementation returns [EnvironmentGenerationResult.NotImplemented].
 * Real generators will be implemented later.
 */
interface EnvironmentGenerationService {
    fun generateSkybox(asset: EnvironmentAsset): EnvironmentGenerationResult

    fun generateIrradiance(asset: EnvironmentAsset): EnvironmentGenerationResult

    fun generateRadiance(asset: EnvironmentAsset): EnvironmentGenerationResult

    fun generateBrdfLut(asset: EnvironmentAsset): EnvironmentGenerationResult

    fun generateAll(asset: EnvironmentAsset): EnvironmentGenerationResult
}

/**
 * Result of a generation action.
 */
sealed class EnvironmentGenerationResult {
    data object Success : EnvironmentGenerationResult()

    data class Failed(
        val message: String,
    ) : EnvironmentGenerationResult()

    data object NotImplemented : EnvironmentGenerationResult()
}

/**
 * Placeholder implementation that reports all generators as unavailable.
 */
object PlaceholderEnvironmentGenerationService : EnvironmentGenerationService {
    override fun generateSkybox(asset: EnvironmentAsset) = EnvironmentGenerationResult.NotImplemented

    override fun generateIrradiance(asset: EnvironmentAsset) = EnvironmentGenerationResult.NotImplemented

    override fun generateRadiance(asset: EnvironmentAsset) = EnvironmentGenerationResult.NotImplemented

    override fun generateBrdfLut(asset: EnvironmentAsset) = EnvironmentGenerationResult.NotImplemented

    override fun generateAll(asset: EnvironmentAsset) = EnvironmentGenerationResult.NotImplemented
}
