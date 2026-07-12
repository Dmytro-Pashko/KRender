package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Platform-independent Environment asset model and `.environment.json` schema.
 *
 * The same type is used for persistence and runtime/editor state so the feature stays easy to
 * understand: `Environment` is the Environment.
 */
@Serializable
data class Environment(
    val schema: String = ENVIRONMENT_SCHEMA,
    val schemaVersion: Int = ENVIRONMENT_SCHEMA_VERSION,
    val assetType: String = "Environment",
    val id: String,
    val name: String,
    val description: String? = null,
    val settings: EnvironmentSettings = EnvironmentSettings(),
    val skybox: SkyboxResourceSet? = null,
    val irradiance: CubemapResource? = null,
    val radiance: RadianceMipChain? = null,
    val brdfLut: TextureResourceRef? = null,
    @Transient
    val manifestPath: String = "",
    @Transient
    val metadata: EnvironmentMetadata = EnvironmentMetadata(),
)

const val ENVIRONMENT_SCHEMA = "krender.environment"
const val ENVIRONMENT_SCHEMA_VERSION = 1

/**
 * Simple metadata bag for optional extra environment information.
 */
data class EnvironmentMetadata(
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val modifiedAt: String? = null,
)
