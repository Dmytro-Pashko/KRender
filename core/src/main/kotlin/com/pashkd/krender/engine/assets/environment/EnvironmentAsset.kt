package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.Serializable

/**
 * Unique identifier for an environment asset, typically the manifest-relative path.
 */
@JvmInline
value class EnvironmentAssetId(val path: String)

/**
 * Platform-independent representation of a complete Environment asset.
 *
 * An Environment owns all resources and parameters related to scene lighting/background
 * for PBR rendering: source HDR maps, generated IBL cubemaps, skybox faces, and runtime settings.
 */
data class EnvironmentAsset(
    val id: EnvironmentAssetId,
    val name: String,
    val manifestPath: String,
    val version: Int,
    val type: EnvironmentType,
    val description: String? = null,
    val sources: List<EnvironmentSourceVariant> = emptyList(),
    val generated: EnvironmentGeneratedResources = EnvironmentGeneratedResources(),
    val settings: EnvironmentSettings = EnvironmentSettings(),
    val generation: EnvironmentGenerationSettings? = null,
    val metadata: EnvironmentMetadata = EnvironmentMetadata(),
)

/**
 * Top-level environment type. Only [HdrIbl] is practically supported in the MVP.
 */
@Serializable
enum class EnvironmentType {
    HdrIbl,
    ProceduralSky,
    SolidColor,
    GradientSky,
}

/**
 * Simple metadata bag for optional extra environment information.
 */
data class EnvironmentMetadata(
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val modifiedAt: String? = null,
)
